package nfc;

import javax.smartcardio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SmartMifareReader {

    // Simple debounce map to avoid immediate duplicates when calling repeatedly
    private static final ConcurrentHashMap<String, Long> lastSeen = new ConcurrentHashMap<>();
    private static final long DEBOUNCE_MS = 500; // ignore duplicates within 500ms

    /**
     * Read UID with default 20s timeout. Returns UID (hex, upper-case, no spaces)
     * or null on timeout/error.
     */
    public static String readUID() {
        return readUID(20_000);
    }
    /*
     * Read UID, blocking up to timeoutMs milliseconds. Returns UID (hex) or null.
     */
    public static String readUID(long timeoutMs) {
        ReadResult r = readUIDWithData(timeoutMs);
        return (r == null) ? null : r.uid;
    }
    /*
     * Read UID + attempt to read printable data. Default timeout 20s. Returns
     * ReadResult or null on timeout/error.
     */
    public static ReadResult readUIDWithData() {
        return readUIDWithData(20_000);
    }
    /*
     * Read UID and some readable data. Blocks up to timeoutMs milliseconds waiting
     * for a card.
     * Returns a ReadResult (uid non-null) or null if timed out / no reader / error.
     */
    public static ReadResult readUIDWithData(long timeoutMs) {
        try {
            TerminalFactory factory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = factory.terminals().list();
            if (terminals == null || terminals.isEmpty()) {
                System.err.println("SmartMifareReader: no NFC reader detected.");
                return null;
            }
            CardTerminal terminal = terminals.get(0);

            // waitForCardPresent accepts milliseconds. If timeoutMs <=0, we wait
            // indefinitely.
            boolean present;
            if (timeoutMs <= 0) {
                terminal.waitForCardPresent(0);
                present = true;
            } else {
                present = terminal.waitForCardPresent(timeoutMs);
            }

            if (!present) {
                return null;
            }

            Card card = null;
            try {
                card = terminal.connect("*");
                CardChannel channel = card.getBasicChannel();

                // UID
                CommandAPDU getUidCmd = new CommandAPDU(new byte[] {
                        (byte) 0xFF, (byte) 0xCA, 0x00, 0x00, 0x00
                });
                ResponseAPDU uidResp = channel.transmit(getUidCmd);
                String uid = bytesToHex(uidResp.getData()).replace(" ", "");

                if (uid == null || uid.isEmpty()) {
                    return null;
                }

                // Debounce: avoid same UID reported repeatedly in short succession
                long now = System.currentTimeMillis();
                Long last = lastSeen.get(uid);
                if (last != null && (now - last) < DEBOUNCE_MS) {
                    // treat as no new read — still return uid (caller can decide) or return null to
                    // indicate "ignored".
                    // We'll return uid here (so behavior matches a straightforward read); adjust if
                    // you prefer ignoring.
                    return null;
                }
                lastSeen.put(uid, now);

                // Try to probe readable data (best effort)
                String readableData = probeReadableData(channel);

                return new ReadResult(uid, readableData);

            } finally {
                try {
                    if (card != null)
                        card.disconnect(false);
                } catch (Exception ignored) {
                }
                // ensure we wait for card absent before returning, to avoid immediate re-detect
                // loops
                try {
                    terminal.waitForCardAbsent(200); // small wait to avoid flapping; non-blocking
                } catch (Exception ignored) {
                }
            }
        } catch (CardException ce) {
            System.err.println("SmartMifareReader CardException: " + ce.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("SmartMifareReader unexpected error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // --- Result POJO ---
    public static class ReadResult {
        public final String uid;
        public final String data; // any printable text extracted from the card (may be empty)

        public ReadResult(String uid, String data) {
            this.uid = uid;
            this.data = data == null ? "" : data;
        }

        @Override
        public String toString() {
            return "ReadResult{uid=" + uid + ", data=" + data + "}";
        }
    }

    private static String probeReadableData(CardChannel channel) {
        try {
            byte[][] commonKeys = new byte[][] {
                    hex("FFFFFFFFFFFF"),
                    hex("000000000000"),
            };

            int keySlot = 0x00;
            boolean anyAuth = false;
            StringBuilder readableData = new StringBuilder();

            for (int sector = 0; sector < 16; sector++) {
                int firstBlockOfSector = sector * 4;
                int probeBlock = (sector == 0) ? 1 : firstBlockOfSector;

                AuthResult successfulAuth = null;

                for (byte[] key : commonKeys) {
                    if (!loadKey(channel, keySlot, key))
                        continue;
                    AuthResult ar = tryAuthAsAorB(channel, probeBlock, (byte) keySlot);
                    if (ar.success) {
                        successfulAuth = ar;
                        anyAuth = true;
                        break;
                    }
                }

                if (successfulAuth == null)
                    continue;

                for (int b = firstBlockOfSector; b < firstBlockOfSector + 4; b++) {
                    if (sector == 0 && b == 0)
                        continue; // skip manufacturer block
                    if ((b % 4) == 3)
                        continue; // skip trailer
                    byte[] data = readBlock(channel, b);
                    if (data != null) {
                        String text = new String(data, StandardCharsets.UTF_8)
                                .trim()
                                .replaceAll("[^\\p{Print}]", "");
                        if (!text.isEmpty())
                            readableData.append(text).append(" ");
                    }
                }
            }

            if (!anyAuth) {
                return "";
            } else {
                return readableData.toString().trim();
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static class AuthResult {
        boolean success;
        byte keyType;

        AuthResult(boolean s, byte t) {
            success = s;
            keyType = t;
        }
    }

    private static AuthResult tryAuthAsAorB(CardChannel channel, int blockNumber, byte keySlot) {
        boolean aOk = authWithKeySlot(channel, blockNumber, (byte) 0x60, keySlot);
        if (aOk)
            return new AuthResult(true, (byte) 0x60);
        boolean bOk = authWithKeySlot(channel, blockNumber, (byte) 0x61, keySlot);
        if (bOk)
            return new AuthResult(true, (byte) 0x61);
        return new AuthResult(false, (byte) 0x00);
    }

    private static boolean loadKey(CardChannel channel, int keySlot, byte[] key) {
        try {
            byte[] apdu = new byte[11];
            apdu[0] = (byte) 0xFF;
            apdu[1] = (byte) 0x82;
            apdu[2] = 0x00;
            apdu[3] = (byte) keySlot;
            apdu[4] = 0x06;
            System.arraycopy(key, 0, apdu, 5, 6);
            ResponseAPDU resp = channel.transmit(new CommandAPDU(apdu));
            return resp.getSW() == 0x9000;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean authWithKeySlot(CardChannel channel, int blockNumber, byte keyType, byte keySlot) {
        try {
            byte[] apdu = new byte[] {
                    (byte) 0xFF, (byte) 0x86, 0x00, 0x00, 0x05,
                    0x01, 0x00, (byte) blockNumber, keyType, keySlot
            };
            ResponseAPDU resp = channel.transmit(new CommandAPDU(apdu));
            return resp.getSW() == 0x9000;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] readBlock(CardChannel channel, int blockNumber) {
        try {
            byte[] apdu = new byte[] { (byte) 0xFF, (byte) 0xB0, 0x00, (byte) blockNumber, 0x10 };
            ResponseAPDU resp = channel.transmit(new CommandAPDU(apdu));
            if (resp.getSW() == 0x9000)
                return resp.getData();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] hex(String s) {
        s = s.replaceAll("[^0-9A-Fa-f]", "");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null)
            return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02X ", b));
        return sb.toString().trim();
    }
}