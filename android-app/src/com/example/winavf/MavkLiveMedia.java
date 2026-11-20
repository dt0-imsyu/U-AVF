package com.example.winavf;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Journalled prefix swap for one verified disposable Live cache ONLY.
 * The ISO bytes and all installed disks are excluded from the write range.
 * Rollback bytes already exist in the signed APK: no second 4 GB ISO copy.
 */
final class MavkLiveMedia {
    private static final String DISK="winavf-P7-virgl-gbm-runtime.img";
    private static final String JOURNAL="mavk-live-prefix.intent";
    private static final long LIVE_BYTES=4102029312L;
    private static File checked(Context context,File folder)throws IOException {
        File canonical=folder.getCanonicalFile();
        String root=context.getFilesDir().getCanonicalPath()+File.separator;
        if(!canonical.getPath().startsWith(root)||!"frame-bridge-test-payload".equals(canonical.getName()))
            throw new IOException("Mavk may only change its temporary Live cache");
        File disk=new File(canonical,DISK);
        if(!disk.isFile()||disk.length()!=LIVE_BYTES)throw new IOException("Unexpected Live-only media layout");
        return disk;
    }
    private static String prefixSha(File disk)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(disk)){
            byte[] buffer=new byte[1024*1024];long left=MavkRuntimeAssets.PREFIX_BYTES;
            while(left>0){int n=in.read(buffer,0,(int)Math.min(left,buffer.length));if(n<=0)throw new EOFException();hash.update(buffer,0,n);left-=n;}
        }
        StringBuilder out=new StringBuilder();for(byte b:hash.digest())out.append(String.format(java.util.Locale.ROOT,"%02X",b));return out.toString();
    }
    private static void writeAsset(Context context,File disk,String name,String expected)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        // Validate immutable asset fully before the first write to the Live cache.
        try(InputStream in=context.getAssets().open(name)){
            byte[] buffer=new byte[1024*1024];long bytes=0;int n;
            while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>MavkRuntimeAssets.PREFIX_BYTES)throw new IOException("Prefix exceeds its reserved range");hash.update(buffer,0,n);}
            if(bytes!=MavkRuntimeAssets.PREFIX_BYTES)throw new IOException("Truncated prefix asset");
        }
        StringBuilder sum=new StringBuilder();for(byte b:hash.digest())sum.append(String.format(java.util.Locale.ROOT,"%02X",b));
        if(!expected.equalsIgnoreCase(sum.toString()))throw new SecurityException("Mavk prefix SHA mismatch");
        try(InputStream in=context.getAssets().open(name);RandomAccessFile out=new RandomAccessFile(disk,"rw")){
            byte[] buffer=new byte[1024*1024];long written=0;int n;
            while((n=in.read(buffer))!=-1){if(written+n>MavkRuntimeAssets.PREFIX_BYTES)throw new IOException("Prefix write outside ESP");out.write(buffer,0,n);written+=n;}
            if(written!=MavkRuntimeAssets.PREFIX_BYTES||out.length()!=LIVE_BYTES)throw new IOException("Live media size changed");
            out.getFD().sync();
        }
        if(!expected.equalsIgnoreCase(prefixSha(disk)))throw new SecurityException("Written prefix verification failed");
    }
    static synchronized void restore(Context context,File folder)throws Exception {
        File journal=new File(folder,JOURNAL);if(!journal.isFile())return;
        File disk=checked(context,folder);
        byte[] bytes=java.nio.file.Files.readAllBytes(journal.toPath());if(bytes.length>4096)throw new IOException("Live rollback journal bound");
        JSONObject intent=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!DISK.equals(intent.getString("disk"))||!MavkRuntimeAssets.BASE_SHA.equals(intent.getString("base_sha256")))
            throw new SecurityException("Unexpected Live rollback identity");
        writeAsset(context,disk,MavkRuntimeAssets.BASE,MavkRuntimeAssets.BASE_SHA);
        if(!journal.delete())throw new IOException("Cannot finish verified Live rollback");
    }
    static synchronized void enable(Context context,File folder,File media,MavkRuntimeAssets assets)throws Exception {
        File disk=checked(context,folder);
        if(!disk.getCanonicalFile().equals(media.getCanonicalFile())||!MavkRuntimeAssets.BASE_SHA.equals(prefixSha(disk)))
            throw new SecurityException("Mavk requires the verified baseline Live prefix");
        File journal=new File(folder,JOURNAL);
        if(journal.exists())throw new IOException("Complete the previous Live rollback first");
        JSONObject intent=new JSONObject();intent.put("disk",DISK);intent.put("base_sha256",MavkRuntimeAssets.BASE_SHA);intent.put("mavk_sha256",assets.sha256);
        File pending=new File(folder,JOURNAL+".tmp");
        try(FileOutputStream out=new FileOutputStream(pending)){out.write(intent.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        android.system.Os.rename(pending.getAbsolutePath(),journal.getAbsolutePath());
        // Persist the journal's directory entry before mutating any media byte.
        java.io.FileDescriptor directory=android.system.Os.open(folder.getAbsolutePath(),android.system.OsConstants.O_RDONLY,0);
        try{android.system.Os.fsync(directory);}finally{android.system.Os.close(directory);}
        try{writeAsset(context,disk,MavkRuntimeAssets.PREFIX,assets.sha256);}
        catch(Exception error){restore(context,folder);throw error;}
    }
}
