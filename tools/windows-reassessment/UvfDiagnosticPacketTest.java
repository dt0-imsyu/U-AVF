package com.example.winavf;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
final class UvfDiagnosticPacketTest {
    static byte[] packet() {
        ByteBuffer b=ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0,0x31465655);b.putShort(4,(short)1);b.putShort(6,(short)6);
        b.putInt(8,1);b.putInt(12,1);b.putShort(20,(short)1920);b.putShort(22,(short)1200);b.putInt(32,1024);
        return b.array();
    }
    public static void main(String[] args) {
        if(UvfDiagnosticPacket.length(packet(),-1)!=1024 || UvfDiagnosticPacket.length(packet(),1)!=1024)throw new AssertionError();
        int cases=2;
        for(int[] test:new int[][]{{0,0},{8,2},{12,0},{16,1},{24,1},{32,0},{32,1025},{36,1}}) {
            byte[] data=packet();ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(test[0],test[1]);
            try{UvfDiagnosticPacket.length(data,-1);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
        }
        for(int[] test:new int[][]{{4,2},{6,2},{20,0},{20,4097},{22,0},{22,4097}}) {
            byte[] data=packet();ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(test[0],(short)test[1]);
            try{UvfDiagnosticPacket.length(data,-1);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
        }
        try{UvfDiagnosticPacket.length(packet(),2);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
        try{UvfDiagnosticPacket.length(new byte[39],-1);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
        System.out.println("ACTUAL_ANDROID_UVF1_DIAGNOSTIC_VALIDATION_CASES="+cases+" PASS");
    }
}
