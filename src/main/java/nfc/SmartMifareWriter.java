package nfc;

import javax.smartcardio.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public class SmartMifareWriter {

    private static final byte[][] COMMON_KEYS = new byte[][] {
            hex("FFFFFFFFFFFF"),
            hex("000000000000"),
    };
    private static final int KEY_SLOT = 0x00;

    public static final long DEFAULT_PRESENT_TIMEOUT_MS = 10_000L;
    public static final long DEFAULT_ABSENT_TIMEOUT_MS = 5_000L;

    public static class WriteResult {
        public final String uid;
        public final List<Integer> blocks;
        public final String textWritten;
        public final Instant timestamp;

        public WriteResult(String uid, List<Integer> blocks, String textWritten, Instant timestamp) {
            this.uid = uid;
            this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
            this.textWritten = textWritten;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "WriteResult{uid=" + uid + ", blocks=" + blocks + ", textWritten=" + textWritten + ", timestamp="
                    + timestamp + "}";
        }
    }

    public static WriteResult writeText(String text) throws Exception {
        return writeText(text, DEFAULT_PRESENT_TIMEOUT_MS, DEFAULT_ABSENT_TIMEOUT_MS);
    }

    public static WriteResult writeText(String text, long presentTimeoutMs, long absentTimeoutMs) throws Exception {
        if (text == null)
            throw new IllegalArgumentException("text is null");
        String trimmed = text.trim();
        if (trimmed.isEmpty())
            throw new IllegalArgumentException("text is empty");

        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = factory.terminals().list();
        if (terminals == null || terminals.isEmpty()) {
            throw new Exception("No NFC reader detected");
        }
        CardTerminal terminal = terminals.get(0);

        // Wait for card present
        final long chunkMs = 500L;
        long deadline = System.currentTimeMillis() + presentTimeoutMs;
        boolean present = false;
        while (System.currentTimeMillis() < deadline) {
            try {
                present = terminal.waitForCardPresent((int) chunkMs);
            } catch (CardException ignore) {
            }
            if (present)
                break;
        }
        if (!present)
            throw new Exception("Timed out waiting for card (ms=" + presentTimeoutMs + ")");

        Card card = null;
        List<Integer> writtenBlocks = new ArrayList<>();
        String uid = "";
        try {
            card = terminal.connect("*");
            CardChannel channel = card.getBasicChannel();

            // read UID
            CommandAPDU uidCmd = new CommandAPDU(new byte[] { (byte) 0xFF, (byte) 0xCA, 0x00, 0x00, 0x00 });
            ResponseAPDU rUid = channel.transmit(uidCmd);
            uid = bytesToHex(rUid.getData()).replace(" ", "");
            System.out.println("DEBUG: UID=" + uid + " SW=" + Integer.toHexString(rUid.getSW()));

            // prepare chunks (16 bytes)
            byte[] payload = trimmed.getBytes(StandardCharsets.UTF_8);
            List<byte[]> chunks = chunkBytes(payload, 16);

            // DISCOVERY PASS: find all blocks we can authenticate and write to 
            // We record for each discovered block: block index and the keyType that worked
            List<BlockAuth> writableBlocks = discoverWritableBlocks(channel);
            System.out.println("DEBUG: discovered writable blocks count=" + writableBlocks.size());

            // Check capacity
            int needed = chunks.size();
            if (writableBlocks.size() < needed) {
                // helpful diagnostic
                String msg = "Insufficient authenticated writable blocks: need " + needed + ", found "
                        + writableBlocks.size() + ".";
                // include a brief listing
                StringBuilder sb = new StringBuilder(msg).append(" Blocks:");
                for (BlockAuth ba : writableBlocks)
                    sb.append(' ').append(ba.blockIndex);
                throw new Exception(sb.toString());
            }

            // WRITE PASS: write chunks sequentially into discovered blocks
            for (int i = 0; i < chunks.size(); i++) {
                byte[] chunk = chunks.get(i);
                BlockAuth target = writableBlocks.get(i);

                // load the same key we discovered (we already ensured loadKey works in
                // discovery, but load again to be safe)
                boolean loaded = loadKey(channel, KEY_SLOT, target.keyBytes);
                if (!loaded) {
                    // if load fails unexpectedly, try to reload any other key that might work for
                    // that block
                    loaded = loadKey(channel, KEY_SLOT, target.keyBytes);
                }
                if (!loaded) {
                    throw new Exception("Failed to load key into reader for block " + target.blockIndex);
                }

                // final auth using discovered key type
                boolean finalAuth = authWithKeySlot(channel, target.blockIndex, target.keyType, (byte) KEY_SLOT);
                if (!finalAuth) {
                    throw new Exception("Final auth failed for block " + target.blockIndex);
                }

                // attempt write (writeBlock verifies after write)
                writeBlock(channel, target.blockIndex, chunk);
                writtenBlocks.add(target.blockIndex);
                System.out.println("DEBUG: wrote chunk " + (i + 1) + " -> block " + target.blockIndex);
            }

            return new WriteResult(uid, writtenBlocks, trimmed, Instant.now());

        } catch (Exception e) {
            throw new Exception("Write failed: " + e.getMessage(), e);
        } finally {
            if (card != null) {
                try {
                    card.disconnect(false);
                } catch (Exception ignored) {
                }
            }
            // wait for card absent (best-effort)
            long absentDeadline = System.currentTimeMillis() + absentTimeoutMs;
            while (System.currentTimeMillis() < absentDeadline) {
                try {
                    if (terminal.waitForCardAbsent(500))
                        break;
                } catch (CardException ignored) {
                }
            }
        }
    }

    // --- Discovery helpers ---

    private static class BlockAuth {
        final int blockIndex;
        final byte keyType; // 0x60 or 0x61
        final byte[] keyBytes; // actual key bytes used (6 bytes)

        BlockAuth(int idx, byte kt, byte[] kb) {
            this.blockIndex = idx;
            this.keyType = kt;
            this.keyBytes = kb;
        }
    }

    /**
     * Scan blocks 4..62 (skip trailers) and record blocks where we can:
     * - load a candidate key into KEY_SLOT
     * - authenticate as A or B (0x60/0x61) using that key slot
     *
     * Returns a list sorted by block index ascending.
     */
    private static List<BlockAuth> discoverWritableBlocks(CardChannel channel) {
        List<BlockAuth> out = new ArrayList<>();
        for (int block = 4; block < 64; block++) {
            if (isTrailerBlock(block))
                continue;

            boolean found = false;
            for (byte[] key : COMMON_KEYS) {
                boolean loaded = loadKey(channel, KEY_SLOT, key);
                System.out.println(
                        "DEBUG: discover loadKey block=" + block + " key=" + bytesToHex(key) + " -> " + loaded);
                if (!loaded)
                    continue;

                // try auth A
                if (authWithKeySlot(channel, block, (byte) 0x60, (byte) KEY_SLOT)) {
                    out.add(new BlockAuth(block, (byte) 0x60, key));
                    found = true;
                    break;
                }
                // try auth B
                if (authWithKeySlot(channel, block, (byte) 0x61, (byte) KEY_SLOT)) {
                    out.add(new BlockAuth(block, (byte) 0x61, key));
                    found = true;
                    break;
                }
            }
            if (!found) {
                // block not writable with common keys - skip
            }
        }
        // list is naturally in ascending block order; return as-is
        return out;
    }

    // --- Internal helper classes & methods (from your prior code) ---

    private static boolean loadKey(CardChannel c, int slot, byte[] key) {
        try {
            byte[] apdu = new byte[11];
            apdu[0] = (byte) 0xFF;
            apdu[1] = (byte) 0x82;
            apdu[2] = 0x00;
            apdu[3] = (byte) slot;
            apdu[4] = 0x06;
            System.arraycopy(key, 0, apdu, 5, 6);
            ResponseAPDU r = c.transmit(new CommandAPDU(apdu));
            System.out.println("DEBUG: LOAD KEY SW=" + Integer.toHexString(r.getSW()));
            return r.getSW() == 0x9000;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean authWithKeySlot(CardChannel c, int b, byte type, byte slot) {
        try {
            byte[] apdu = new byte[] { (byte) 0xFF, (byte) 0x86, 0x00, 0x00, 0x05, 0x01, 0x00, (byte) b, type, slot };
            ResponseAPDU r = c.transmit(new CommandAPDU(apdu));
            System.out.println("DEBUG: AUTH SW for block " + b + " -> " + Integer.toHexString(r.getSW()));
            return r.getSW() == 0x9000;
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeBlock(CardChannel c, int b, byte[] data) throws Exception {
        if (isTrailerBlock(b))
            throw new Exception("Refusing to write to trailer block " + b);
        if (data.length != 16)
            throw new Exception("Invalid data size: " + data.length + " (expected 16)");

        byte[] apdu = new byte[21];
        apdu[0] = (byte) 0xFF;
        apdu[1] = (byte) 0xD6;
        apdu[2] = 0x00;
        apdu[3] = (byte) b;
        apdu[4] = 0x10;
        System.arraycopy(data, 0, apdu, 5, 16);

        ResponseAPDU r = c.transmit(new CommandAPDU(apdu));
        System.out.println("DEBUG: WRITE SW for block " + b + " -> " + Integer.toHexString(r.getSW()));
        if (r.getSW() != 0x9000) {
            throw new Exception("Write failed SW=" + Integer.toHexString(r.getSW()));
        }

        byte[] verify = readBlock(c, b);
        if (verify == null)
            throw new Exception("Write verification failed - couldn't read back block " + b);
        if (!Arrays.equals(data, verify))
            throw new Exception("Write verification failed - data mismatch in block " + b);
    }

    private static byte[] readBlock(CardChannel c, int b) {
        try {
            byte[] cmd = new byte[] { (byte) 0xFF, (byte) 0xB0, 0x00, (byte) b, 0x10 };
            ResponseAPDU r = c.transmit(new CommandAPDU(cmd));
            System.out.println("DEBUG: READ SW for block " + b + " -> " + Integer.toHexString(r.getSW()));
            if (r.getSW() == 0x9000) {
                return r.getData();
            }
        } catch (Exception e) {
            // ignore / return null
        }
        return null;
    }

    private static boolean isTrailerBlock(int b) {
        return (b % 4) == 3;
    }

    private static byte[] hex(String s) {
        s = s.replaceAll("[^0-9A-Fa-f]", "");
        int len = s.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++)
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    private static List<byte[]> chunkBytes(byte[] src, int size) {
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < src.length; i += size) {
            int len = Math.min(size, src.length - i);
            byte[] chunk = new byte[size];
            Arrays.fill(chunk, (byte) 0x00);
            System.arraycopy(src, i, chunk, 0, len);
            out.add(chunk);
        }
        if (out.isEmpty()) {
            byte[] empty = new byte[size];
            Arrays.fill(empty, (byte) 0x00);
            out.add(empty);
        }
        return out;
    }

    private static String bytesToHex(byte[] b) {
        if (b == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (byte v : b)
            sb.append(String.format("%02X:", v));
        return sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";
    }
}
