package nfc;

import javax.smartcardio.*;
import java.util.*;

public class SmartMifareEraser {

    private static final byte[][] COMMON_KEYS = new byte[][] {
            hex("FFFFFFFFFFFF"),
            hex("000000000000"),
    };

    private static final int KEY_SLOT = 0x00;

    /**
     * Wait for a card to be presented (blocks indefinitely) and attempt to
     * overwrite all writable data blocks (4..63) with zeros for sectors that
     * can be authenticated using COMMON_KEYS.
     * This method is blocking and should NOT be called on the JavaFX UI thread.
     *
     * When the erase finishes the method returns immediately (no waiting for card
     * absent).
     * @throws Exception on fatal errors (no reader, card connect failure, etc.)
     */
    public static void eraseMemory() throws Exception {
        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = factory.terminals().list();
        if (terminals == null || terminals.isEmpty()) {
            throw new Exception("No NFC reader detected");
        }

        CardTerminal terminal = terminals.get(0);

        // wait indefinitely for card to be presented
        terminal.waitForCardPresent(0);
        Card card = terminal.connect("*");
        try {
            CardChannel channel = card.getBasicChannel();
            eraseOnChannel(channel);
        } finally {
            try {
                card.disconnect(false);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Erase writable data blocks on the given channel. This does not
     * connect/disconnect the card; the caller must manage the card lifecycle if
     * using this method directly.
     *
     * @param channel connected CardChannel
     * @throws Exception on unexpected errors
     */
    public static void eraseOnChannel(CardChannel channel) throws Exception {
        if (channel == null)
            throw new IllegalArgumentException("channel is null");
        byte[] zero16 = new byte[16];

        // iterate user blocks 4..63
        for (int block = 4; block < 64; block++) {
            if (isTrailerBlock(block))
                continue;

            boolean authed = false;

            for (byte[] candidate : COMMON_KEYS) {
                boolean loaded = loadKey(channel, KEY_SLOT, candidate);
                if (!loaded)
                    continue;
                if (authWithKeySlot(channel, block, (byte) 0x60, (byte) KEY_SLOT)) {
                    authed = true;
                    break;
                }
                if (authWithKeySlot(channel, block, (byte) 0x61, (byte) KEY_SLOT)) {
                    authed = true;
                    break;
                }
            }

            if (!authed)
                continue;

            try {
                writeBlock(channel, block, zero16);
            } catch (Exception ignored) {
            }
        }
    }

    // Load key into reader key slot (FF 82)
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

    // Authenticate using key in a slot (FF 86 referencing key slot)
    private static boolean authWithKeySlot(CardChannel channel, int blockNumber, byte keyType, byte keySlot) {
        try {
            byte[] apdu = new byte[] {
                    (byte) 0xFF, (byte) 0x86, 0x00, 0x00, 0x05,
                    0x01, 0x00, (byte) blockNumber, keyType, keySlot
            };
            ResponseAPDU authResp = channel.transmit(new CommandAPDU(apdu));
            return authResp.getSW() == 0x9000;
        } catch (Exception e) {
            return false;
        }
    }

    // Write a 16-byte block (FF D6). Refuses trailer blocks.
    private static void writeBlock(CardChannel channel, int block, byte[] data16) throws Exception {
        if (isTrailerBlock(block)) {
            throw new Exception("Refusing to write to a sector trailer block (" + block + ")");
        }
        if (data16 == null || data16.length != 16) {
            throw new Exception("data16 must be exactly 16 bytes");
        }

        byte[] write = new byte[21];
        write[0] = (byte) 0xFF;
        write[1] = (byte) 0xD6;
        write[2] = 0x00;
        write[3] = (byte) block;
        write[4] = 0x10;
        System.arraycopy(data16, 0, write, 5, 16);

        ResponseAPDU resp = channel.transmit(new CommandAPDU(write));
        if (resp.getSW() != 0x9000) {
            throw new Exception("Write failed SW=" + Integer.toHexString(resp.getSW()));
        }
    }

    // helper to check trailer
    private static boolean isTrailerBlock(int block) {
        return (block % 4) == 3;
    }

    private static byte[] hex(String s) {
        s = s.replaceAll("[^0-9A-Fa-f]", "");
        int len = s.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++)
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
