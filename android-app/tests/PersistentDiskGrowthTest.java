package com.example.winavf;
import java.io.*;
import java.nio.*;
import java.lang.reflect.*;
import java.util.Arrays;

/** Disposable sparse fixture only; never reads a user disk. */
public final class PersistentDiskGrowthTest {
    public static void main(String[] args)throws Exception {
        File disk=new File(args[0]);if(!disk.getName().equals("growth-test.raw")||disk.length()!=0)throw new Exception("Expected empty disposable sparse fixture");
        long iso=512,total=PersistentUbuntuDisk.expectedDiskBytes(iso)/512;
        Method header=PersistentUbuntuDisk.class.getDeclaredMethod("makeHeader",long.class,long.class,long.class,long.class,byte[].class);header.setAccessible(true);
        Method partition=PersistentUbuntuDisk.class.getDeclaredMethod("putPartition",byte[].class,int.class,String.class,java.util.UUID.class,long.class,long.class,String.class);partition.setAccessible(true);
        byte[] table=new byte[16384];
        partition.invoke(null,table,0,PersistentUbuntuDisk.ESP_TYPE,java.util.UUID.randomUUID(),2048L,260095L,"U-AVF Platform");
        partition.invoke(null,table,1,PersistentUbuntuDisk.LINUX_TYPE,java.util.UUID.randomUUID(),262144L,262144L,"Ubuntu ISO");
        long first=264192;
        partition.invoke(null,table,2,PersistentUbuntuDisk.LINUX_TYPE,java.util.UUID.randomUUID(),first,first+PersistentUbuntuDisk.TARGET_BYTES/512-1,"U-AVF Ubuntu Root");
        byte[] identity;
        try(RandomAccessFile out=new RandomAccessFile(disk,"rw")) {
            out.setLength(total*512);byte[] primary=(byte[])header.invoke(null,1L,total-1,2L,total,table);
            identity=Arrays.copyOfRange(primary,56,72);
            out.seek(512);out.write(primary);out.write(table);out.seek((total-33)*512);out.write(table);out.write((byte[])header.invoke(null,total-1,1L,total-33,total,table));
            out.seek(first*512);out.writeUTF("ROOT_SENTINEL");out.seek(1048576);out.writeUTF("ESP_SENTINEL");out.seek(134217728);out.writeUTF("ISO_SENTINEL");
        }
        if(!PersistentUbuntuDisk.audit(disk,iso,PersistentUbuntuDisk.TARGET_BYTES))throw new Exception("Initial audit");
        File journal=new File(disk.getParentFile(),"growth-test.intent");
        PersistentUbuntuDisk.grow(disk,iso,40L*1024*1024*1024,journal);
        if(PersistentUbuntuDisk.rootBytes(disk,iso)!=40L*1024*1024*1024)throw new Exception("Wrong expanded size");
        try(RandomAccessFile in=new RandomAccessFile(disk,"r")) {
            byte[] read=new byte[16];in.seek(512+56);in.readFully(read);if(!Arrays.equals(identity,read))throw new Exception("Disk GUID changed");
            in.seek(first*512);if(!in.readUTF().equals("ROOT_SENTINEL"))throw new Exception("Root changed");
            in.seek(1048576);if(!in.readUTF().equals("ESP_SENTINEL"))throw new Exception("ESP changed");
            in.seek(134217728);if(!in.readUTF().equals("ISO_SENTINEL"))throw new Exception("ISO changed");
        }
        try{PersistentUbuntuDisk.grow(disk,iso,32L*1024*1024*1024,journal);throw new AssertionError("Shrink accepted");}catch(IllegalArgumentException expected){}
        try(RandomAccessFile out=new RandomAccessFile(disk,"rw")) {out.seek(disk.length()-33*512);out.writeByte(99);}
        if(PersistentUbuntuDisk.audit(disk,iso,PersistentUbuntuDisk.TARGET_BYTES))throw new Exception("Corrupt backup accepted");
        System.out.println("GROW_GPT=PASS IDENTITY_AND_DATA=PASS SHRINK_REJECTED=PASS BACKUP_CRC=PASS");
    }
}
