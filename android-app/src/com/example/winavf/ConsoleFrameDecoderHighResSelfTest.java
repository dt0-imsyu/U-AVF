package com.example.winavf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Host-side check of a deflated 1080p keyframe and compressed tile update. */
public final class ConsoleFrameDecoderHighResSelfTest {
    public static void main(String[] args) {
        final int width = 1920, height = 1080;
        final int[] received = {0};
        ConsoleFrameDecoder decoder = new ConsoleFrameDecoder(new ConsoleFrameDecoder.Listener() {
            @Override public void onFrame(ConsoleFrameDecoder.Frame frame) {
                received[0]++;
                if (frame.width != width || frame.height != height) throw new AssertionError("geometry");
                int end = ((height - 1) * width + width - 1) * 4;
                if (received[0] == 2 && (frame.bgra[end] & 0xff) != 0x55)
                    throw new AssertionError("bottom-right tile");
                if (received[0] == 1 && !frame.fullFrame) throw new AssertionError("keyframe dirty state");
                if (received[0] == 2 && (frame.fullFrame || frame.dirtyRectCount != 1
                        || frame.dirtyRects[0] != width - 64 || frame.dirtyRects[1] != height - 64
                        || frame.dirtyRects[2] != 64 || frame.dirtyRects[3] != 64))
                    throw new AssertionError("delta dirty rectangle");
                if (received[0] == 3 && frame.bgra[0] != (byte) 0xA5)
                    throw new AssertionError("high-entropy keyframe");
            }
            @Override public void onProtocolError(String message) { throw new AssertionError(message); }
        });
        byte[] black = new byte[width * height * 4];
        for (int i = 3; i < black.length; i += 4) black[i] = (byte) 255;
        byte[] key = record(1, 1, width, height, 1, compress(black));
        feedInChunks(decoder, key, 0, key.length, 13 * 1024);

        // Use a full 64x64 tile so compressed length differs from raw length.
        byte[] fullTile = new byte[64 * 64 * 4];
        fullTile[fullTile.length - 4] = 0x55;
        for (int i = 3; i < fullTile.length; i += 4) fullTile[i] = (byte) 255;
        byte[] tileCompressed = compress(fullTile);
        ByteBuffer payload = ByteBuffer.allocate(12 + tileCompressed.length).order(ByteOrder.LITTLE_ENDIAN);
        payload.putShort((short) (width - 64)).putShort((short) (height - 64));
        payload.putShort((short) 64).putShort((short) 64).putInt(tileCompressed.length);
        payload.put(tileCompressed);
        byte[] tiles = record(2, 0, width, height, 2, payload.array());
        feedInChunks(decoder, tiles, 0, tiles.length, 997);
        byte[] highEntropy = new byte[width * height * 4];
        new java.util.Random(7).nextBytes(highEntropy);
        highEntropy[0] = (byte) 0xA5;
        byte[] largeKey = record(1, 1, width, height, 3, compress(highEntropy));
        if (largeKey.length <= 4 * 1024 * 1024) throw new AssertionError("payload not large enough");
        feedInChunks(decoder, largeKey, 0, largeKey.length, 32 * 1024);
        if (received[0] != 3) throw new AssertionError("frames=" + received[0]);
        System.out.println("ConsoleFrameDecoderHighResSelfTest PASS keyBytes=" + key.length
                + " tileBytes=" + tiles.length + " highEntropyBytes=" + largeKey.length);
    }

    private static void feedInChunks(ConsoleFrameDecoder decoder, byte[] bytes,
            int offset, int length, int chunkSize) {
        for (int cursor = 0; cursor < length; cursor += chunkSize) {
            int count = Math.min(chunkSize, length - cursor);
            decoder.feed(bytes, offset + cursor, count);
        }
    }

    private static byte[] compress(byte[] source) {
        Deflater deflater = new Deflater(1);
        try {
            deflater.setInput(source);
            deflater.finish();
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                if (count == 0) throw new AssertionError("compression stalled");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally { deflater.end(); }
    }

    private static byte[] record(int type, int flags, int width, int height,
            int sequence, byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        ByteBuffer out = ByteBuffer.allocate(28 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(ConsoleFrameDecoder.MAGIC).put((byte) 1).put((byte) type)
                .put((byte) flags).put((byte) 0);
        out.putShort((short) width).putShort((short) height).putInt(sequence)
                .putInt(payload.length).putInt((int) crc.getValue()).putInt(0).put(payload);
        return out.array();
    }
}
