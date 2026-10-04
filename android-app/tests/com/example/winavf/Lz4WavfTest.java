package com.example.winavf;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32;

public final class Lz4WavfTest {
    static byte[] record(int kind, int flags, byte[] payload) {
        CRC32 crc = new CRC32(); crc.update(payload);
        return ByteBuffer.allocate(28 + payload.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(ConsoleFrameDecoder.MAGIC).put((byte)1).put((byte)kind)
                .put((byte)flags).put((byte)0).putShort((short)1).putShort((short)1)
                .putInt(kind).putInt(payload.length).putInt((int)crc.getValue())
                .putInt(0).put(payload).array();
    }
    public static void main(String[] args) {
        final int[] count = {0};
        ConsoleFrameDecoder decoder = new ConsoleFrameDecoder(new ConsoleFrameDecoder.Listener() {
            public void onFrame(ConsoleFrameDecoder.Frame frame) {
                count[0]++;
                if (count[0] == 2 && !Arrays.equals(frame.bgra, new byte[]{1,2,3,(byte)255}))
                    throw new AssertionError("LZ4 pixel mismatch");
            }
            public void onProtocolError(String message) { throw new AssertionError(message); }
        });
        byte[] key = record(1,0,new byte[]{0,0,0,(byte)255});
        decoder.feed(key,0,key.length);
        byte[] tile = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short)0).putShort((short)0).putShort((short)1).putShort((short)1)
                .putInt(4).put(new byte[]{1,2,3,(byte)255}).array();
        byte[] lz4 = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(16).put((byte)0xf0).put((byte)1).put(tile).array();
        byte[] update = record(2,2,lz4);
        for (int i=0;i<update.length;i+=3) decoder.feed(update,i,Math.min(3,update.length-i));
        if (count[0] != 2) throw new AssertionError("missing frame");
        System.out.println("LZ4_WAVF_FRAGMENTED_DELTA=PASS");
    }
}
