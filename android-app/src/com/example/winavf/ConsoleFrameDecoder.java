// Mixed provenance: inherited material retains Apache-2.0.
// Original new U-AVF contributions: see LICENSE_SCOPE.md and LICENSE-UAVF.txt.
// No third-party or previously granted rights are withdrawn.
package com.example.winavf;

import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Decoder for the bounded GOP/console transport. The stream may contain normal
 * serial text; the decoder scans for WAVF records and resynchronizes after
 * malformed input. Pixels are BGRA8888, stored row-major in the frame buffer.
 */
final class ConsoleFrameDecoder {
    static final int MAGIC = 0x46564157; // bytes: W A V F
    static final int VERSION = 1;
    static final int TYPE_KEYFRAME = 1;
    static final int TYPE_TILES = 2;
    static final int FLAG_DEFLATE = 1;
    static final int FLAG_LZ4 = 2; // uint32 LE decoded size followed by one LZ4 block
    static final int HEADER_SIZE = 28;
    static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;
    // A 1920x1080 BGRA keyframe is 8,294,400 bytes before compression.
    // Bound the transport above that size so high-detail screens remain valid.
    static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;
    private static final int MAX_TRACKED_DIRTY_RECTS = 512;

    interface Listener {
        void onFrame(Frame frame);
        void onProtocolError(String message);
    }

    static final class Frame {
        final int width;
        final int height;
        final int sequence;
        final byte[] bgra;
        final boolean fullFrame;
        final int[] dirtyRects;
        final int dirtyRectCount;

        Frame(int width, int height, int sequence, byte[] bgra,
                boolean fullFrame, int[] dirtyRects, int dirtyRectCount) {
            this.width = width;
            this.height = height;
            this.sequence = sequence;
            this.bgra = bgra;
            this.fullFrame = fullFrame;
            this.dirtyRects = dirtyRects;
            this.dirtyRectCount = dirtyRectCount;
        }
    }

    private final Listener listener;
    private static final int MAX_RECORD_BYTES = HEADER_SIZE + MAX_PAYLOAD_BYTES;
    private byte[] pending = new byte[64 * 1024];
    private int pendingStart;
    private int pendingEnd;
    private byte[] frame;
    private int frameWidth;
    private int frameHeight;
    private final int[] dirtyRects = new int[MAX_TRACKED_DIRTY_RECTS * 4];
    private int dirtyRectCount;
    private boolean dirtyRectOverflow;
    private long crcNanos;
    private long lz4Nanos;

    synchronized long[] costSnapshot() {
        return new long[]{crcNanos, lz4Nanos};
    }

    ConsoleFrameDecoder(Listener listener) {
        this.listener = listener;
    }

    synchronized void feed(byte[] bytes, int offset, int length) {
        if (length <= 0) return;
        if (offset < 0 || length < 0 || offset > bytes.length - length)
            throw new IndexOutOfBoundsException("invalid input range");
        int inputEnd = offset + length;
        while (offset < inputEnd) {
            parseAvailable();
            ensureWritable();
            int count = Math.min(inputEnd - offset, pending.length - pendingEnd);
            System.arraycopy(bytes, offset, pending, pendingEnd, count);
            pendingEnd += count;
            offset += count;
        }
        parseAvailable();
    }

    private void parseAvailable() {
        while (pendingEnd - pendingStart >= 4) {
            int cursor = pendingStart;
            int magic = le32(pending, cursor);
            if (magic != MAGIC) {
                pendingStart++;
                continue;
            }
            if (pendingEnd - cursor < HEADER_SIZE) return;
            int version = pending[cursor + 4] & 0xff;
            int type = pending[cursor + 5] & 0xff;
            int flags = pending[cursor + 6] & 0xff;
            int width = le16(pending, cursor + 8);
            int height = le16(pending, cursor + 10);
            int sequence = le32(pending, cursor + 12);
            int payloadLength = le32(pending, cursor + 16);
            int expectedCrc = le32(pending, cursor + 20);
            int reserved = le32(pending, cursor + 24);
            if (version != VERSION || reserved != 0 || width == 0 || height == 0
                    || width > 4096 || height > 4096 || payloadLength < 0
                    || payloadLength > MAX_PAYLOAD_BYTES) {
                error("invalid header");
                pendingStart++;
                continue;
            }
            long frameBytes = (long) width * height * 4L;
            if (frameBytes > MAX_FRAME_BYTES) {
                error("frame too large");
                pendingStart++;
                continue;
            }
            int recordLength = HEADER_SIZE + payloadLength;
            if (pendingEnd - cursor < recordLength) return;
            int payloadOffset = cursor + HEADER_SIZE;
            CRC32 crc = new CRC32();
            long crcStarted = System.nanoTime();
            crc.update(pending, payloadOffset, payloadLength);
            crcNanos += System.nanoTime() - crcStarted;
            if ((int) crc.getValue() != expectedCrc) {
                error("CRC mismatch");
                pendingStart++;
                continue;
            }
            if ((flags & ~(FLAG_DEFLATE | FLAG_LZ4)) != 0
                    || flags == (FLAG_DEFLATE | FLAG_LZ4)) {
                error("unknown flags");
                pendingStart += recordLength;
                continue;
            }
            byte[] decoded = null;
            if (type == TYPE_KEYFRAME) {
                decoded = flags == FLAG_LZ4
                        ? decodeLz4(pending, payloadOffset, payloadLength, (int) frameBytes)
                        : (flags & FLAG_DEFLATE) != 0
                        ? inflate(pending, payloadOffset, payloadLength, (int) frameBytes)
                        : copyRange(pending, payloadOffset, payloadLength);
                if (decoded == null) {
                    pendingStart += recordLength;
                    continue;
                }
                if (decoded.length != frameBytes) {
                    error("keyframe size mismatch");
                } else {
                    frameWidth = width;
                    frameHeight = height;
                    frame = decoded;
                    emit(sequence, true, null, 0);
                }
            } else if (type == TYPE_TILES) {
                if (frame == null || frameWidth != width || frameHeight != height) {
                    error("tile frame without matching keyframe");
                } else {
                    dirtyRectCount = 0;
                    dirtyRectOverflow = false;
                    byte[] tileStream = flags == FLAG_LZ4
                            ? decodeLz4(pending, payloadOffset, payloadLength, MAX_PAYLOAD_BYTES)
                            : (flags & FLAG_DEFLATE) != 0
                            ? inflate(pending, payloadOffset, payloadLength, (int) frameBytes) : pending;
                    int tileOffset = flags != 0 ? 0 : payloadOffset;
                    if (tileStream != null && applyTiles(tileStream, tileOffset,
                            tileStream == pending ? payloadLength : tileStream.length))
                        emit(sequence, dirtyRectOverflow, dirtyRects, dirtyRectCount);
                }
            } else {
                error("unknown record type");
            }
            pendingStart += recordLength;
        }
        compactIfUseful();
    }

    private boolean applyTiles(byte[] payload, int offset, int payloadLength) {
        int cursor = offset;
        int end = offset + payloadLength;
        while (cursor < end) {
            if (end - cursor < 12) {
                error("truncated tile header");
                return false;
            }
            int x = le16(payload, cursor);
            int y = le16(payload, cursor + 2);
            int width = le16(payload, cursor + 4);
            int height = le16(payload, cursor + 6);
            int tileLength = le32(payload, cursor + 8);
            cursor += 12;
            long rawLength = (long) width * height * 4L;
            if (width == 0 || height == 0 || x + width > frameWidth || y + height > frameHeight
                    || tileLength < 0 || rawLength > MAX_FRAME_BYTES || tileLength > end - cursor) {
                error("invalid tile");
                return false;
            }
            int tileOffset = cursor;
            cursor += tileLength;
            byte[] raw = payload;
            int rawOffset = tileOffset;
            if (tileLength != rawLength) {
                raw = inflate(payload, tileOffset, tileLength, (int) rawLength);
                if (raw == null) return false;
                rawOffset = 0;
            }
            for (int row = 0; row < height; row++) {
                int source = rawOffset + row * width * 4;
                int target = ((y + row) * frameWidth + x) * 4;
                System.arraycopy(raw, source, frame, target, width * 4);
            }
            if (dirtyRectCount < MAX_TRACKED_DIRTY_RECTS) {
                int rect = dirtyRectCount++ * 4;
                dirtyRects[rect] = x;
                dirtyRects[rect + 1] = y;
                dirtyRects[rect + 2] = width;
                dirtyRects[rect + 3] = height;
            } else {
                dirtyRectOverflow = true;
            }
        }
        return cursor == end;
    }

    private byte[] decodeLz4(byte[] payload, int offset, int length, int limit) {
        if (length < 4) { error("LZ4 header truncated"); return null; }
        long started = System.nanoTime();
        try {
            return Lz4Block.decode(payload, offset + 4, length - 4, le32(payload, offset), limit);
        } catch (IllegalArgumentException invalid) {
            error(invalid.getMessage()); return null;
        } finally {
            lz4Nanos += System.nanoTime() - started;
        }
    }

    private byte[] inflate(byte[] compressed, int offset, int length, int expectedLength) {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed, offset, length);
        byte[] output = new byte[expectedLength];
        try {
            int written = 0;
            while (!inflater.finished() && written < output.length) {
                int count = inflater.inflate(output, written, output.length - written);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                written += count;
            }
            if (!inflater.finished() || written != expectedLength) {
                error("deflate size mismatch");
                return null;
            }
            return output;
        } catch (DataFormatException error) {
            error("invalid deflate payload");
            return null;
        } finally {
            inflater.end();
        }
    }

    private static byte[] copyRange(byte[] source, int offset, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(source, offset, copy, 0, length);
        return copy;
    }

    private void ensureWritable() {
        if (pendingEnd < pending.length) return;
        compact();
        if (pendingEnd < pending.length) return;
        if (pending.length >= MAX_RECORD_BYTES)
            throw new IllegalArgumentException("record buffer limit exceeded");
        int next = Math.min(MAX_RECORD_BYTES, Math.max(pending.length * 2, pendingEnd + 1));
        byte[] grown = new byte[next];
        System.arraycopy(pending, pendingStart, grown, 0, pendingEnd - pendingStart);
        pendingEnd -= pendingStart;
        pendingStart = 0;
        pending = grown;
    }

    private void compactIfUseful() {
        if (pendingStart == pendingEnd) {
            pendingStart = pendingEnd = 0;
        } else if (pendingStart >= pending.length / 2) {
            compact();
        }
    }

    private void compact() {
        if (pendingStart == 0) return;
        int remaining = pendingEnd - pendingStart;
        if (remaining > 0) System.arraycopy(pending, pendingStart, pending, 0, remaining);
        pendingStart = 0;
        pendingEnd = remaining;
    }

    private void emit(int sequence, boolean fullFrame, int[] rectangles, int rectangleCount) {
        // The frame data is valid for the duration of this synchronous
        // callback. Consumers that need to retain it must copy it; the normal
        // Android path copies straight into its Bitmap before feed() resumes.
        listener.onFrame(new Frame(frameWidth, frameHeight, sequence, frame,
                fullFrame, rectangles, rectangleCount));
    }

    private void error(String message) {
        listener.onProtocolError(message);
    }

    private static int le16(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static int le32(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16) | ((data[offset + 3] & 0xff) << 24);
    }
}
