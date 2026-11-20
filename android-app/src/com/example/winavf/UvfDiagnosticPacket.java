package com.example.winavf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** UVF1 kind6 is bounded source metadata, never an AVC access unit. */
final class UvfDiagnosticPacket {
    static int length(byte[] header,int generation) {
        if(header.length!=40)throw new IllegalArgumentException("Diagnostic header size");
        ByteBuffer h=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int gen=h.getInt(12), w=h.getShort(20)&65535, height=h.getShort(22)&65535, size=h.getInt(32);
        if(h.getInt(0)!=0x31465655 || h.getShort(4)!=1 || h.getShort(6)!=6 || h.getInt(8)!=1 ||
           gen<1 || (generation!=-1 && gen!=generation) || h.getInt(16)!=0 || h.getLong(24)!=0 ||
           h.getInt(36)!=0 || w<1 || w>4096 || height<1 || height>4096 || size<1 || size>1024)
            throw new IllegalArgumentException("Invalid UVF1 source diagnostic");
        return size;
    }
}
