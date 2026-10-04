package com.example.winavf;
import java.io.*;
import java.lang.reflect.*;
import java.util.UUID;

/** Sparse disposable fixture. Never opens a real workspace. */
public final class InstalledEspRecoveryTest {
    public static void main(String[] args) throws Exception {
        File dir=new File(args[0]);
        if (!dir.getName().startsWith("esp-recovery-test-") || !dir.isDirectory()
                || dir.list().length!=1 || !new File(dir,"fixture.raw").isFile()
                || new File(dir,"fixture.raw").length()!=0) throw new Exception("Empty sparse fixture required");
        File disk=new File(dir,"fixture.raw"), backups=new File(dir,"backups");
        backups.mkdir();
        long iso=512, total=PersistentUbuntuDisk.expectedDiskBytes(iso)/512;
        Method header=PersistentUbuntuDisk.class.getDeclaredMethod("makeHeader",long.class,long.class,long.class,long.class,byte[].class);
        Method partition=PersistentUbuntuDisk.class.getDeclaredMethod("putPartition",byte[].class,int.class,String.class,UUID.class,long.class,long.class,String.class);
        header.setAccessible(true);partition.setAccessible(true);
        byte[] table=new byte[16384]; long root=264192;
        partition.invoke(null,table,0,PersistentUbuntuDisk.ESP_TYPE,UUID.randomUUID(),2048L,260095L,"U-AVF Platform");
        partition.invoke(null,table,1,PersistentUbuntuDisk.LINUX_TYPE,UUID.randomUUID(),262144L,262144L,"Ubuntu ISO");
        partition.invoke(null,table,2,PersistentUbuntuDisk.LINUX_TYPE,UUID.randomUUID(),root,root+PersistentUbuntuDisk.TARGET_BYTES/512-1,"U-AVF Ubuntu Root");
        try(RandomAccessFile f=new RandomAccessFile(disk,"rw")) {
            f.setLength(total*512);f.seek(512);f.write((byte[])header.invoke(null,1L,total-1,2L,total,table));
            f.write(table);f.seek((total-33)*512);f.write(table);f.write((byte[])header.invoke(null,total-1,1L,total-33,total,table));
            f.seek(root*512);f.writeUTF("ROOT_PRESERVED");f.seek(134217728);f.writeUTF("ISO_PRESERVED");
        }
        Method range=PersistentUbuntuDisk.class.getDeclaredMethod("sha256Range",File.class,long.class,long.class);
        range.setAccessible(true);
        String live=(String)range.invoke(null,disk,1048576L,126L*1048576);
        File backup=new File(dir,"source.bin");
        try(RandomAccessFile f=new RandomAccessFile(backup,"rw")) {
            f.setLength(126L*1048576);f.seek(1048570); // split across scan chunks
            f.write("version=1\nroot_partition=U-AVF Ubuntu Root\n".getBytes("US-ASCII"));
        }
        String old=(String)range.invoke(null,backup,0L,backup.length());
        File named=new File(backups,"platform-esp-backup-"+old+".bin");
        if(!backup.renameTo(named))throw new Exception("Rename failed");
        if(!PersistentUbuntuDisk.recoverInstalledEsp(disk,iso,"0".repeat(64),backups).equals("CURRENT_ESP_NOT_MANAGED_LIVE"))throw new AssertionError("Unknown ESP modified");
        String result=PersistentUbuntuDisk.recoverInstalledEsp(disk,iso,live,backups);
        if(!result.equals("RESTORED SHA256="+old)||!PersistentUbuntuDisk.hasInstalledBootCandidate(disk))throw new AssertionError(result);
        if(!PersistentUbuntuDisk.recoverInstalledEsp(disk,iso,live,backups).equals("NOT_NEEDED"))throw new AssertionError("Not idempotent");
        try(RandomAccessFile f=new RandomAccessFile(disk,"r")) {
            f.seek(root*512);if(!f.readUTF().equals("ROOT_PRESERVED"))throw new AssertionError("Root changed");
            f.seek(134217728);if(!f.readUTF().equals("ISO_PRESERVED"))throw new AssertionError("ISO changed");
        }
        System.out.println("INSTALLED_ESP_RECOVERY=PASS UNKNOWN_ESP_REFUSED=PASS ROOT_ISO_GPT_PRESERVED=PASS IDEMPOTENT=PASS");
    }
}
