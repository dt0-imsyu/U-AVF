package com.example.winavf;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Serial framing used by the pinned Windows ARM64 loader and kernel debugger. */
final class WindowsKdWire {
    static final int MANIPULATE = 2;
    static final int ACK = 4;
    static final int RESEND = 5;
    static final int RESET = 6;
    static final int STATE_CHANGE = 7;
    static final int FILE_IO = 11;
    static final int SYNC_BIT = 0x800;
    static final int INITIAL_ID = 0x80800000;
    static final int MANIPULATE_BYTES = 56;

    private static final int DATA_LEADER = 0x30303030;
    private static final int CONTROL_LEADER = 0x69696969;
    private static final int HEADER_BYTES = 16;
    private static final int MAX_BODY = 16384;
    private static final byte TRAILER = (byte) 0xaa;
    private static final byte[] LOADER_GATE =
            "WG:LOAD=PASS,START_WINDOWS".getBytes(StandardCharsets.US_ASCII);
    private static final int[] LOADER_PREFIX = prefixLengths(LOADER_GATE);

    record Packet(boolean control, int type, int id, byte[] body) {}

    private WindowsKdWire() {}

    static ByteBuffer littleEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    static byte[] control(int type, int id) {
        if (!controlType(type)) throw new IllegalArgumentException("Invalid KD control type");
        return littleEndian(new byte[HEADER_BYTES])
                .putInt(CONTROL_LEADER).putShort((short) type).putShort((short) 0)
                .putInt(id).putInt(0).array();
    }

    static byte[] data(int type, int id, byte[] body) {
        if (!validData(type, id, body.length)) throw new IllegalArgumentException("Invalid KD data");
        return littleEndian(new byte[HEADER_BYTES + body.length + 1])
                .putInt(DATA_LEADER).putShort((short) type).putShort((short) body.length)
                .putInt(id).putInt(checksum(body, 0, body.length)).put(body).put(TRAILER).array();
    }

    private static boolean controlType(int type) {
        return type == ACK || type == RESEND || type == RESET;
    }

    private static boolean validData(int type, int id, int size) {
        int minimum = switch (type) {
            case MANIPULATE -> MANIPULATE_BYTES;
            case 3 -> 16; // Debug I/O
            case STATE_CHANGE -> 80;
            case FILE_IO -> 64;
            default -> MAX_BODY + 1;
        };
        return size >= minimum && size <= MAX_BODY && (id & ~(SYNC_BIT | 1)) == INITIAL_ID;
    }

    private static int checksum(byte[] bytes, int start, int end) {
        int result = 0;
        for (int i = start; i < end; i++) result += Byte.toUnsignedInt(bytes[i]);
        return result;
    }

    private static int[] prefixLengths(byte[] marker) {
        int[] lengths = new int[marker.length];
        for (int i = 1; i < marker.length; i++) {
            int prefix = lengths[i - 1];
            while (prefix > 0 && marker[i] != marker[prefix]) prefix = lengths[prefix - 1];
            lengths[i] = marker[i] == marker[prefix] ? prefix + 1 : 0;
        }
        return lengths;
    }

    /** One reader per direction. The transport supplies cancellation and the read deadline. */
    static final class Reader {
        private final InputStream input;
        private final byte[] buffer = new byte[HEADER_BYTES + MAX_BODY + 1];
        private final ByteBuffer header = littleEndian(buffer);
        private int start;
        private int end;

        Reader(InputStream input) {
            this.input = new BufferedInputStream(input);
        }

        void awaitLoader() throws IOException {
            if (start != 0 || end != 0) throw new IllegalStateException("KD framing already started");
            int matched = 0;
            while (matched < LOADER_GATE.length) {
                int value;
                try { value = input.read(); }
                catch (SocketTimeoutException idle) { continue; }
                if (value == -1) throw new IOException("UART closed before Windows loader gate");
                while (matched > 0 && value != LOADER_GATE[matched]) {
                    matched = LOADER_PREFIX[matched - 1];
                }
                if (value == LOADER_GATE[matched]) matched++;
            }
        }

        Packet next() throws IOException {
            while (ensure(HEADER_BYTES)) {
                int leader = header.getInt(start);
                int type = Short.toUnsignedInt(header.getShort(start + 4));
                int size = Short.toUnsignedInt(header.getShort(start + 6));
                int id = header.getInt(start + 8);
                int expectedChecksum = header.getInt(start + 12);
                if (leader == CONTROL_LEADER && size == 0 && expectedChecksum == 0
                        && controlType(type)) {
                    start += HEADER_BYTES;
                    return new Packet(true, type, id, new byte[0]);
                }
                if (leader == DATA_LEADER && validData(type, id, size)
                        && ensure(HEADER_BYTES + size + 1)) {
                    int bodyStart = start + HEADER_BYTES;
                    int bodyEnd = bodyStart + size;
                    if (buffer[bodyEnd] == TRAILER
                            && checksum(buffer, bodyStart, bodyEnd) == expectedChecksum) {
                        start = bodyEnd + 1;
                        return new Packet(false, type, id, Arrays.copyOfRange(buffer, bodyStart, bodyEnd));
                    }
                }
                // Loader text or a damaged packet can precede the next valid leader.
                start++;
            }
            return null;
        }

        private boolean ensure(int count) throws IOException {
            if (end - start >= count) return true;
            if (start + count > buffer.length) {
                end -= start;
                System.arraycopy(buffer, start, buffer, 0, end);
                start = 0;
            }
            while (end - start < count) {
                int read = input.read(buffer, end, buffer.length - end);
                if (read < 0) return false;
                end += read;
            }
            return true;
        }
    }
}
