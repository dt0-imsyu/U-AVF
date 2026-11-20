package com.example.winavf;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32;
import org.json.*;

/** Isolated APK :vmowner. VM construction copied from proven AvfOwnerBridge. */
public final class WindowsVmOwnerService extends Service {
    private static final String SHA="1E9D3B601D2003F5D8FD5A0D5AFEBFDF0E1B0C8359A2345BDB689D137998E4BE";
    private static final String LIVE_SHA="E45161B4037F586A1847470E04C6143EC52ACEE1EDB605CC450EB2CC363FEE6F";
    private static final String LIVE_NAME_FIX_SHA="A1DE9652E81A173BC4E242CFA53F23C4D8AD31C2D54500397650F894D927DF2A";
    private static final String WDDM_PNP_EFI_SHA="3D81FEEA37CC742FD55389FDA2E7DDC02A750A5469A49C8A639243CA8C13D6FA";
    private volatile Object vm;
    private WindowsAvfOwner avfOwner;
    private volatile String token; private volatile boolean ready,stopping;
    private volatile boolean clipboardEnabled;
    private volatile boolean mavkEnabled,mavkValidation;
    private boolean wddmAdmission;
    private boolean wddmCodec;
    private boolean wddmPnp;
    private static native int serveWddmCodec(String report,int channel);
    private volatile ParcelFileDescriptor mavkChannel;
    private final java.util.concurrent.atomic.AtomicBoolean mavkStarted=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicInteger mavkAttempts=new java.util.concurrent.atomic.AtomicInteger();
    private volatile boolean ownerStarted;
    private volatile boolean embeddedKd;
    private volatile WindowsKdUart embeddedUart;
    private boolean prepareInstallTarget;
    private boolean resumePreparedInstallTarget;
    private boolean resumeWindowsInstallTarget;
    private boolean iddDisposableBootstrap;
    private boolean rtcClockShim;
    private boolean windowsRamGop;
    private boolean dynamicGic,fixedEightCpuGic,matchHostCpus,psciLowMemoryAb,psciOwnedReserve,psciFirstCpuEntry,psciRuntimeReserve,psciRuntimeAllCpus,psciEntryMarker,psciCachePublish;
    private String expectedDiskSha=SHA;
    private Socket host; private ServerSocket server; private PrintWriter report;
    private java.lang.Process ingress,tailer;
    private ParcelFileDescriptor input,output,log,writeInput;
    private synchronized void note(String value){if(report!=null){report.println(value);report.flush();}android.util.Log.i("UAVF-vmowner",value);}
    private static String sha(File file)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[1048576];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}
        StringBuilder result=new StringBuilder();for(byte b:hash.digest())result.append(String.format("%02X",b));return result.toString();
    }
    private static long crc(byte[] bytes,int length){CRC32 c=new CRC32();c.update(bytes,0,length);return c.getValue();}
    private static byte[] grownHeader(byte[] original,long current,long alternate,long entries){
        byte[] result=original.clone();ByteBuffer b=ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN);
        b.putLong(24,current);b.putLong(32,alternate);b.putLong(48,alternate==1?current-33:alternate-33);b.putLong(72,entries);
        b.putInt(16,0);b.putInt(16,(int)crc(result,b.getInt(12)));return result;
    }
    private void growInstallTarget(File disk)throws Exception{
        final long oldSize=9126805504L,newSize=77846282240L,last=newSize/512-1;
        long before=new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes();
        if(before<50L*1024*1024*1024+64L*1024*1024)throw new IOException("Tablet 50GiB reserve required");
        try(RandomAccessFile f=new RandomAccessFile(disk,"rw")){
            byte[] prefix=new byte[1024],entries=new byte[16384],oldTail=new byte[512];f.readFully(prefix);
            byte[] primary=Arrays.copyOfRange(prefix,512,1024);ByteBuffer h=ByteBuffer.wrap(primary).order(ByteOrder.LITTLE_ENDIAN);
            byte[] check=primary.clone();ByteBuffer.wrap(check).order(ByteOrder.LITTLE_ENDIAN).putInt(16,0);
            if(!new String(primary,0,8,StandardCharsets.US_ASCII).equals("EFI PART")||h.getInt(12)!=92||crc(check,92)!=Integer.toUnsignedLong(h.getInt(16))
                ||h.getLong(24)!=1||h.getLong(32)!=oldSize/512-1||h.getLong(40)!=34||h.getLong(48)!=oldSize/512-34||h.getLong(72)!=2||h.getInt(80)!=128||h.getInt(84)!=128)
                throw new SecurityException("Audited primary GPT required");
            f.seek(1024);f.readFully(entries);
            ByteBuffer e=ByteBuffer.wrap(entries).order(ByteOrder.LITTLE_ENDIAN);
            if(crc(entries,entries.length)!=Integer.toUnsignedLong(h.getInt(88))||e.getLong(32)!=2048||e.getLong(40)!=oldSize/512-34)
                throw new SecurityException("Source partition must remain exact");
            for(int i=128;i<entries.length;i++)if(entries[i]!=0)throw new SecurityException("No existing install target may be overwritten");
            f.seek(oldSize-512);f.readFully(oldTail);for(byte v:oldTail)if(v!=0)throw new SecurityException("Unexpected backup GPT state");
            File journal=new File(getFilesDir(),"windows-geometry-before-"+System.currentTimeMillis()+".bin");
            try(FileOutputStream out=new FileOutputStream(journal)){out.write(prefix);out.write(oldTail);out.getFD().sync();}
            byte[] mbr=Arrays.copyOf(prefix,512);ByteBuffer.wrap(mbr).order(ByteOrder.LITTLE_ENDIAN).putInt(458,(int)last);
            byte[] p=grownHeader(primary,1,last,2),b=grownHeader(primary,last,1,last-32);
            try{
                f.setLength(newSize);f.seek(0);f.write(mbr);f.write(p);f.seek((last-32)*512);f.write(entries);f.write(b);f.getFD().sync();
                byte[] actual=new byte[512];f.seek(512);f.readFully(actual);if(!Arrays.equals(actual,p))throw new IOException("Primary readback mismatch");
                f.seek(last*512);f.readFully(actual);if(!Arrays.equals(actual,b))throw new IOException("Backup readback mismatch");
                long after=new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes();
                if(after<50L*1024*1024*1024||before-after>64L*1024*1024)throw new IOException("Sparse growth/reserve check failed");
                note("INSTALL_GEOMETRY_PASS old_bytes="+oldSize+" new_bytes="+newSize+" target_first_lba="+(oldSize/512)+" target_last_lba="+(last-33)+" free_bytes="+after+"; NO_INSTALL_STARTED");
            }catch(Exception failure){f.seek(0);f.write(prefix);f.setLength(oldSize);f.getFD().sync();throw failure;}
        }
    }
    private void verifyPreparedInstallTarget(File disk)throws Exception{
        if(disk.length()!=77846282240L||!LIVE_NAME_FIX_SHA.equals(expectedDiskSha))throw new SecurityException("Prepared size/source mismatch");
        File[] journals=getFilesDir().listFiles((dir,name)->name.startsWith("windows-geometry-before-")&&name.endsWith(".bin"));
        if(journals==null||journals.length!=1)throw new SecurityException("One exact preparation journal required");
        byte[] original=new byte[1024];try(FileInputStream in=new FileInputStream(journals[0])){if(in.read(original)!=1024)throw new IOException("Journal incomplete");}
        MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update(original);
        try(RandomAccessFile f=new RandomAccessFile(disk,"r")){
            byte[] actual=new byte[1024];f.readFully(actual);
            byte[] expected=original.clone();ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN).putInt(458,152043519);
            byte[] primary=Arrays.copyOfRange(original,512,1024),grown=grownHeader(primary,1,152043519,2);
            System.arraycopy(grown,0,expected,512,512);if(!Arrays.equals(actual,expected))throw new SecurityException("Prepared primary mismatch");
            byte[] entries=new byte[16384];f.readFully(entries);for(int i=128;i<entries.length;i++)if(entries[i]!=0)throw new SecurityException("Already partitioned/installed target: no media resume");
            f.seek(77846265344L);byte[] backupEntries=new byte[16384];f.readFully(backupEntries);if(!Arrays.equals(entries,backupEntries))throw new SecurityException("Prepared backup table mismatch");
            byte[] backup=new byte[512];f.readFully(backup);if(!Arrays.equals(backup,grownHeader(primary,152043519,1,152043487)))throw new SecurityException("Prepared backup header mismatch");
            f.seek(1024);long remaining=9126805504L-1024;byte[] buffer=new byte[1048576];
            while(remaining>0){int n=f.read(buffer,0,(int)Math.min(remaining,buffer.length));if(n<=0)throw new IOException("Media EOF");hash.update(buffer,0,n);remaining-=n;}
        }
        StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format("%02X",b));
        if(!expectedDiskSha.equals(digest.toString()))throw new SecurityException("Original media identity changed; no resume");
        if(new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes()<50L*1024*1024*1024)throw new IOException("Tablet reserve");
        note("PREPARED_INSTALL_MEDIA_IDENTITY_PASS; TARGET_UNPARTITIONED; NOT_INSTALLED_BOOT");
    }
    // FAT32 8.3 lookup for frozen EFI/WIM files; no whole mutable-disk SHA.
    private long[] fatLookup(RandomAccessFile f,long dir,String name,int spc,long fat,long data)throws Exception{
        byte[] want=name.getBytes(StandardCharsets.US_ASCII);
        if(want.length!=11)throw new IOException("8.3 name required");
        byte[] cluster=new byte[spc*512];
        for(int hop=0;hop<1024&&dir>=2&&dir<0x0ffffff8L;hop++){
            f.seek(data+(dir-2)*cluster.length);f.readFully(cluster);
            for(int at=0;at<cluster.length;at+=32){
                int first=cluster[at]&255,attr=cluster[at+11]&255;
                if(first==0)throw new IOException("Frozen FAT file missing: "+name);
                if(first==229||attr==15)continue;
                if(Arrays.equals(Arrays.copyOfRange(cluster,at,at+11),want)){
                    ByteBuffer b=ByteBuffer.wrap(cluster).order(ByteOrder.LITTLE_ENDIAN);
                    long start=((long)Short.toUnsignedInt(b.getShort(at+20))<<16)|Short.toUnsignedInt(b.getShort(at+26));
                    return new long[]{start,Integer.toUnsignedLong(b.getInt(at+28)),attr};
                }
            }
            f.seek(fat+dir*4);dir=Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()))&0x0fffffffL;
        }
        throw new IOException("FAT lookup bound: "+name);
    }
    private String frozenFatSha(RandomAccessFile f,String... path)throws Exception{
        byte[] sector=new byte[512];f.seek(1048576);f.readFully(sector);
        ByteBuffer b=ByteBuffer.wrap(sector).order(ByteOrder.LITTLE_ENDIAN);
        int spc=sector[13]&255;
        if(Short.toUnsignedInt(b.getShort(11))!=512||spc<1||spc>128||(spc&(spc-1))!=0||sector[510]!=85||sector[511]!=(byte)170)
            throw new SecurityException("Frozen FAT32 geometry required");
        long fat=1048576L+Short.toUnsignedInt(b.getShort(14))*512L;
        long data=fat+(sector[16]&255)*Integer.toUnsignedLong(b.getInt(36))*512L;
        long dir=Integer.toUnsignedLong(b.getInt(44));long[] entry=null;
        for(int i=0;i<path.length;i++){
            entry=fatLookup(f,dir,path[i],spc,fat,data);dir=entry[0];
            if(i<path.length-1&&(entry[2]&16)==0)throw new SecurityException("FAT directory mismatch");
        }
        if(entry==null||(entry[2]&16)!=0||entry[1]<1||entry[1]>2L*1024*1024*1024)throw new SecurityException("Frozen file size bound");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long remaining=entry[1],cluster=entry[0];byte[] bytes=new byte[spc*512];
        for(long hop=0;remaining>0&&hop<524288;hop++){
            if(cluster<2||cluster>=0x0ffffff8L||data+(cluster-2)*bytes.length+bytes.length>9126788608L)throw new SecurityException("Frozen file cluster bound");
            f.seek(data+(cluster-2)*bytes.length);f.readFully(bytes);int n=(int)Math.min(remaining,bytes.length);digest.update(bytes,0,n);remaining-=n;
            if(remaining>0){f.seek(fat+cluster*4);cluster=Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()))&0x0fffffffL;}
        }
        if(remaining!=0)throw new SecurityException("Frozen file chain incomplete");
        StringBuilder s=new StringBuilder();for(byte v:digest.digest())s.append(String.format("%02X",v));return s.toString();
    }
    private void verifyWindowsInstallTarget(File disk)throws Exception{
        long volumeSerial;
        if(disk.length()!=77846282240L||!LIVE_NAME_FIX_SHA.equals(expectedDiskSha))throw new SecurityException("Isolated install source/size required");
        try(RandomAccessFile f=new RandomAccessFile(disk,"r")){
            byte[] primary=new byte[512],entries=new byte[16384],backup=new byte[512],other=new byte[16384];
            f.seek(512);f.readFully(primary);f.readFully(entries);f.seek(77846265344L);f.readFully(other);f.readFully(backup);
            ByteBuffer h=ByteBuffer.wrap(primary).order(ByteOrder.LITTLE_ENDIAN),bh=ByteBuffer.wrap(backup).order(ByteOrder.LITTLE_ENDIAN);
            byte[] check=primary.clone(),bc=backup.clone();ByteBuffer.wrap(check).order(ByteOrder.LITTLE_ENDIAN).putInt(16,0);ByteBuffer.wrap(bc).order(ByteOrder.LITTLE_ENDIAN).putInt(16,0);
            if(!new String(primary,0,8,StandardCharsets.US_ASCII).equals("EFI PART")||h.getInt(12)!=92||bh.getInt(12)!=92
                ||crc(check,92)!=Integer.toUnsignedLong(h.getInt(16))||crc(bc,92)!=Integer.toUnsignedLong(bh.getInt(16))
                ||h.getLong(24)!=1||h.getLong(32)!=152043519||h.getLong(72)!=2||h.getInt(80)!=128||h.getInt(84)!=128
                ||h.getLong(40)!=34||h.getLong(48)!=152043486||bh.getLong(24)!=152043519||bh.getLong(32)!=1||bh.getLong(72)!=152043487
                ||!Arrays.equals(entries,other)||crc(entries,entries.length)!=Integer.toUnsignedLong(h.getInt(88))
                ||h.getInt(88)!=bh.getInt(88)||!Arrays.equals(Arrays.copyOfRange(primary,56,72),Arrays.copyOfRange(backup,56,72)))throw new SecurityException("Mutable GPT checksum/geometry mismatch");
            byte[] source=android.util.Base64.decode("KHMqwR/40hG6SwCgyT7JOw2RMHRrZE1Ji6R8TH5GL6kACAAAAAAAAN7/DwEAAAAAAAAAAAAAAABXAGkAbgBkAG8AdwBzACAAUwBlAHQAdQBwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",android.util.Base64.DEFAULT);
            ByteBuffer e=ByteBuffer.wrap(entries).order(ByteOrder.LITTLE_ENDIAN);
            if(!Arrays.equals(source,Arrays.copyOf(entries,128))||e.getLong(160)!=17825792||e.getLong(168)!=17858559
                ||e.getLong(288)!=17858560||e.getLong(296)!=150978559)throw new SecurityException("Only approved MSR/root region may change");
            for(int i=384;i<entries.length;i++)if(entries[i]!=0)throw new SecurityException("Unexpected extra partition");
            byte[] ntfs=new byte[512];f.seek(17858560L*512);f.readFully(ntfs);
            if(!new String(ntfs,3,8,StandardCharsets.US_ASCII).equals("NTFS    "))throw new SecurityException("Approved root must be NTFS");
            volumeSerial=ByteBuffer.wrap(ntfs).order(ByteOrder.LITTLE_ENDIAN).getLong(72);
            if(wddmPnp&&volumeSerial!=0xc63ef6813ef669b7L)throw new SecurityException("WDDM PnP requires the retained diagnostic root");
            if(iddDisposableBootstrap&&volumeSerial!=0x4aa580661de89658L)throw new SecurityException("Idd bootstrap requires exact freshly created disposable root, never working Windows");
            String bootSha=psciCachePublish?"0347CB7BC2BB33C34C8940392EACB62795CDBE66D77FB081EEBFA29A88CC9007":psciEntryMarker?"597AC0561E32A1210BC030B1B4A4BC432E2AAB1BBACE945862D25368120EBE29":psciRuntimeAllCpus?"7F33A06FEE99DD373AB2CAB99A6CFABB8E883342882EC3F79FB6BA238363EA9E":psciRuntimeReserve?"20A6364A39A5B58888923DDE027975A4B15771D6F652614397C48C872C8F4AF4":psciFirstCpuEntry?"36943A414743D10960C445102BE9EFCA4C2ADCAA46DAEB144CF7AA4F0E11620A":psciOwnedReserve?"3B1805F0982AC660A9197F98FBC2A111854B99D01C620849F221DDD7231B9ABD":fixedEightCpuGic?"365B733271DA6DEEB5EEA2C262041F6E1793D6DA3C21ECE077BA15FE84B1A865":dynamicGic?"E550FD640FC3864305A6F6F0A794B582A5DB43D7F0556FA69FFD11BE5C1FDC47":rtcClockShim?"AAD53298182B48845DB08F00FC85305A85E4D14EBDFCE171007E883B84446F5D":"893B7859B6E27598B8392F5EC9A9FB33C948FCAF4EF1918F28072ED5AB402E8B";
            if(windowsRamGop)bootSha="359DED6E811F23B011566938B6F3CC8A01DF480D5E8225A320FEFE357CD98E3A";
            if(wddmPnp)bootSha=WDDM_PNP_EFI_SHA;
            if(!frozenFatSha(f,"EFI        ","BOOT       ","BOOTAA64EFI").equals(bootSha)
                ||!frozenFatSha(f,"SOURCES    ","BOOT    WIM").equals(iddDisposableBootstrap?"DD297166CD89B075B7242DD33D3583ABB2159CFB3EDFF533629723B48E7D56B0":"A90BA45CC1404CF18FE2F10CE195E565B8F70E45416583F2F25F3EF4391BEF15"))throw new SecurityException("Frozen boot contract changed");
        }
        long reserve=WindowsChannelPolicy.windowsResumeReserve(wddmAdmission,mavkEnabled,volumeSerial);
        if(new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes()<reserve)throw new IOException("Tablet resume reserve/headroom");
        note("MUTABLE_INSTALL_TARGET_IDENTITY_PASS; EFI_WIM_FROZEN; NOT_INSTALLED_BOOT_PASS");
        if(iddDisposableBootstrap)note("IDD_DISPOSABLE_BOOTSTRAP_IDENTITY_PASS sha=DD297166CD89B075B7242DD33D3583ABB2159CFB3EDFF533629723B48E7D56B0; ROOT_SERIAL=4AA580661DE89658; NOT_DRIVER_OR_FPS_PASS");
        if(rtcClockShim)note("RTC_CLOCK_SHIM_IDENTITY_PASS sha=AAD53298182B48845DB08F00FC85305A85E4D14EBDFCE171007E883B84446F5D; NO_OTHER_PLATFORM_CHANGE");
        if(dynamicGic)note("DYNAMIC_GIC_SHIM_IDENTITY_PASS sha=E550FD640FC3864305A6F6F0A794B582A5DB43D7F0556FA69FFD11BE5C1FDC47; CPU_TOPOLOGY="+(matchHostCpus?"MATCH_HOST":"ONE_CPU"));
        if(wddmPnp)note("WDDM_PNP_EFI_IDENTITY_PASS sha="+WDDM_PNP_EFI_SHA);
        else if(windowsRamGop)note("WINDOWS_RAM_GOP_IDENTITY_PASS sha=359DED6E811F23B011566938B6F3CC8A01DF480D5E8225A320FEFE357CD98E3A; NATIVE_MODE_CANDIDATE=1920x1200; NOT_RUNTIME_PASS");
        if(fixedEightCpuGic)note("FIXED_EIGHT_CPU_GIC_IDENTITY_PASS sha="+(windowsRamGop?"359DED6E811F23B011566938B6F3CC8A01DF480D5E8225A320FEFE357CD98E3A":psciCachePublish?"0347CB7BC2BB33C34C8940392EACB62795CDBE66D77FB081EEBFA29A88CC9007":psciEntryMarker?"597AC0561E32A1210BC030B1B4A4BC432E2AAB1BBACE945862D25368120EBE29":psciRuntimeAllCpus?"7F33A06FEE99DD373AB2CAB99A6CFABB8E883342882EC3F79FB6BA238363EA9E":psciRuntimeReserve?"20A6364A39A5B58888923DDE027975A4B15771D6F652614397C48C872C8F4AF4":psciFirstCpuEntry?"36943A414743D10960C445102BE9EFCA4C2ADCAA46DAEB144CF7AA4F0E11620A":psciOwnedReserve?"3B1805F0982AC660A9197F98FBC2A111854B99D01C620849F221DDD7231B9ABD":"365B733271DA6DEEB5EEA2C262041F6E1793D6DA3C21ECE077BA15FE84B1A865")+"; CPU_TOPOLOGY=MATCH_HOST; HOST_CPUS=8");
    }
    private void archiveOwnDisplayEvidence(File disk)throws Exception{
        // Cold pre-start only: rename one own diagnostic LFN entry. No FAT chain,
        // file bytes, root partition, source WIM or EFI boot code are rewritten.
        final int[] chars={1,3,5,7,9,14,16,18,20,22,24,28,30};
        java.util.HashSet<String> names=new java.util.HashSet<>();long offset=-1;
        byte[] original=null;
        try(RandomAccessFile f=new RandomAccessFile(disk,"r")){
            byte[] sector=new byte[512];f.seek(1048576);f.readFully(sector);ByteBuffer b=ByteBuffer.wrap(sector).order(ByteOrder.LITTLE_ENDIAN);
            int size=(sector[13]&255)*512;long fat=1048576L+Short.toUnsignedInt(b.getShort(14))*512L;
            long data=fat+(sector[16]&255)*Integer.toUnsignedLong(b.getInt(36))*512L,cluster=Integer.toUnsignedLong(b.getInt(44));
            byte[] bytes=new byte[size];boolean ended=false;
            for(int hop=0;hop<1024&&!ended&&cluster>=2&&cluster<0x0ffffff8L;hop++){
                long start=data+(cluster-2)*size;f.seek(start);f.readFully(bytes);
                for(int at=0;at<size;at+=32){
                    if(bytes[at]==0){ended=true;break;}
                    if((bytes[at]&255)!=65||(bytes[at+11]&255)!=15)continue;
                    StringBuilder s=new StringBuilder();ByteBuffer e=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                    for(int c:chars){char v=e.getChar(at+c);if(v==0||v==65535)break;s.append(v);}
                    String name=s.toString();names.add(name);
                    if(!name.equals("UAVF-DISPLAY"))continue;
                    if(offset!=-1||at+64>size||bytes[at+12]!=0||e.getShort(at+26)!=0||(bytes[at+32+11]&16)==0)
                        throw new SecurityException("Diagnostic archive entry bound/type");
                    int sum=0;for(int j=0;j<11;j++)sum=((sum&1)<<7)+(sum>>1)+(bytes[at+32+j]&255)&255;
                    if(sum!=(bytes[at+13]&255))throw new SecurityException("Diagnostic LFN checksum");
                    offset=start+at;original=Arrays.copyOfRange(bytes,at,at+32);
                }
                if(!ended){f.seek(fat+cluster*4);cluster=Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()))&0x0fffffffL;}
            }
        }
        if(original==null){note("DISPLAY_EVIDENCE_DIRECTORY_FRESH");return;}
        String archive=null;for(int i=1;i<1000000;i++){String n=String.format(java.util.Locale.US,"UVFLOG%06d",i);if(!names.contains(n)){archive=n;break;}}
        if(archive==null)throw new IOException("Diagnostic archive names exhausted");
        byte[] changed=original.clone();ByteBuffer e=ByteBuffer.wrap(changed).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<13;i++)e.putChar(chars[i],i<archive.length()?archive.charAt(i):(char)0);
        File journal=new File(getFilesDir(),"display-lfn-archive-"+System.currentTimeMillis()+".bin");
        try(FileOutputStream out=new FileOutputStream(journal)){
            out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(offset).array());out.write(original);out.write(changed);out.getFD().sync();
        }
        try(RandomAccessFile f=new RandomAccessFile(disk,"rw")){
            byte[] check=new byte[32];f.seek(offset);f.readFully(check);if(!Arrays.equals(check,original))throw new SecurityException("Archive preimage changed");
            f.seek(offset);f.write(changed);f.getFD().sync();f.seek(offset);f.readFully(check);
            if(!Arrays.equals(check,changed)){f.seek(offset);f.write(original);f.getFD().sync();throw new IOException("Archive readback failed; restored");}
        }
        note("DISPLAY_EVIDENCE_ARCHIVED="+archive+"; FILES_CHAINS_RETAINED; LFN_ONLY");
    }
    private static Object json(Object value,int depth)throws Exception{
        if(value==null)return JSONObject.NULL;
        if(value instanceof String||value instanceof Number||value instanceof Boolean)return value;
        if(depth>8)throw new IOException("Config nesting bound");
        if(value.getClass().isArray()){JSONArray a=new JSONArray();for(int i=0;i<Array.getLength(value);i++)a.put(json(Array.get(value,i),depth+1));return a;}
        JSONObject result=new JSONObject();for(Field field:value.getClass().getFields())if(!Modifier.isStatic(field.getModifiers()))result.put(field.getName(),json(field.get(value),depth+1));return result;
    }
    private final Binder delegate=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code<1||code>5)return super.onTransact(code,data,reply,flags);
            if(Binder.getCallingUid()!=android.os.Process.myUid())throw new SecurityException("Same APK UID only");
            data.enforceInterface(AvfOwnerBridge.INTERFACE);
            if(token==null||!token.equals(data.readString()))throw new SecurityException("Wrong owner token");
            if(code==3){stopVm();stopSelf();reply.writeNoException();return true;}
            if(code==5){reply.writeNoException();reply.writeInt(embeddedKd&&ready&&!stopping?1:0);return true;}
            if(code==2&&embeddedKd)throw new SecurityException("Embedded KD controls readiness");
            if(code==2){ready=true;note("WINPE_VIOSOCK_READY");reply.writeNoException();return true;}
            if(code==4){
                if(!WindowsChannelPolicy.mavkAllowed(4057L,ready,mavkEnabled)||vm==null)throw new SecurityException("Explicit isolated Mavk channel not ready/enabled");
                boolean started=mavkStarted.compareAndSet(false,true);
                if(started&&mavkAttempts.incrementAndGet()>3){mavkStarted.set(false);started=false;}
                if(started)new Thread(WindowsVmOwnerService.this::runMavkBridge,"Mavk-approved-AVF-channel").start();
                reply.writeNoException();reply.writeInt(started?1:0);return true;
            }
            int port=data.readInt();
            if(!WindowsChannelPolicy.allowed(port,ready,clipboardEnabled)||vm==null)throw new IllegalStateException("Windows channel not ready/allowed");
            long identity=Binder.clearCallingIdentity();
            try(ParcelFileDescriptor fd=avfOwner.connectVsock(vm,port)){
                note("APP_OWNED_AVF_FD_READY");reply.writeNoException();fd.writeToParcel(reply,Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            }catch(Exception e){note("APP_CONNECT_ERROR="+e);reply.writeException(new IllegalStateException("App owner connect failed",e));}
            finally{Binder.restoreCallingIdentity(identity);}return true;
        }
    };
    @Override public IBinder onBind(Intent intent){return delegate;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(token!=null)return START_NOT_STICKY;
        token=intent==null?null:intent.getStringExtra("token");
        if(token==null||!token.matches("[0-9a-f]{64}")){stopSelf();return START_NOT_STICKY;}
        String candidate=intent.getStringExtra("candidate_sha");
        prepareInstallTarget=intent.getBooleanExtra("prepare_install_target",false);
        resumePreparedInstallTarget=intent.getBooleanExtra("resume_prepared_install_target",false);
        resumeWindowsInstallTarget=intent.getBooleanExtra("resume_windows_install_target",false);
        iddDisposableBootstrap=intent.getBooleanExtra("idd_disposable_bootstrap",false);
        clipboardEnabled=intent.getBooleanExtra("clipboard_enabled",false);
        mavkEnabled=intent.getBooleanExtra("mavk_bridge_enabled",false);
        wddmAdmission=intent.getBooleanExtra("wddm_admission",false);
        wddmCodec=intent.getBooleanExtra("wddm_codec",false);
        wddmPnp=intent.getBooleanExtra("wddm_pnp",false);
        if(wddmCodec&&!wddmAdmission)throw new SecurityException("WDDM codec requires isolated admission");
        if(wddmPnp&&!wddmAdmission)throw new SecurityException("WDDM PnP requires isolated admission");
        mavkValidation=intent.getBooleanExtra("mavk_validation",false);
        if(mavkEnabled&&!resumeWindowsInstallTarget)throw new SecurityException("Mavk requires isolated installed Windows");
        if(clipboardEnabled&&!resumeWindowsInstallTarget)throw new SecurityException("Clipboard experiment requires isolated installed Windows");
        rtcClockShim=intent.getBooleanExtra("rtc_clock_shim",false);
        dynamicGic=intent.getBooleanExtra("dynamic_gic",false);
        fixedEightCpuGic=intent.getBooleanExtra("fixed_eight_cpu_gic",false);
        psciLowMemoryAb=intent.getBooleanExtra("psci_low_memory_ab",false);
        psciOwnedReserve=intent.getBooleanExtra("psci_owned_reserve",false);
        psciFirstCpuEntry=intent.getBooleanExtra("psci_first_cpu_entry",false);
        psciRuntimeReserve=intent.getBooleanExtra("psci_runtime_reserve",false);
        psciRuntimeAllCpus=intent.getBooleanExtra("psci_runtime_all_cpus",false);
        psciEntryMarker=intent.getBooleanExtra("psci_entry_marker",false);
        psciCachePublish=intent.getBooleanExtra("psci_cache_publish",false);
        windowsRamGop=intent.getBooleanExtra("windows_ram_gop",false);
        matchHostCpus=intent.getBooleanExtra("match_host_cpus",false);
        embeddedKd=intent.getBooleanExtra("embedded_kd",false);
        if(wddmAdmission&&(!resumeWindowsInstallTarget||!mavkEnabled||iddDisposableBootstrap
                ||!fixedEightCpuGic||!matchHostCpus||!windowsRamGop))
            throw new SecurityException("WDDM admission requires the existing eight CPU diagnostic resume");
        if(iddDisposableBootstrap&&(!resumeWindowsInstallTarget||!windowsRamGop||!fixedEightCpuGic||!matchHostCpus||clipboardEnabled))throw new SecurityException("Idd bootstrap is an explicit disposable eight CPU/native1200p job only");
        if(dynamicGic&&(!resumeWindowsInstallTarget||rtcClockShim))throw new SecurityException("GIC test requires isolated installed profile and no RTC trial");
        if(fixedEightCpuGic&&(!resumeWindowsInstallTarget||rtcClockShim||dynamicGic||!matchHostCpus||Runtime.getRuntime().availableProcessors()!=8))throw new SecurityException("Fixed GIC bank requires exact isolated eight CPU host/profile");
        if(psciLowMemoryAb&&!fixedEightCpuGic)throw new SecurityException("PSCI low-address A/B requires fixed eight CPU profile");
        if(psciOwnedReserve&&(!fixedEightCpuGic||psciLowMemoryAb))throw new SecurityException("Own reserve requires exact fixed eight CPU/4GiB profile");
        if(psciFirstCpuEntry&&!psciOwnedReserve)throw new SecurityException("First CPU entry requires own reserve");
        if(psciRuntimeReserve&&(!psciOwnedReserve||psciFirstCpuEntry))throw new SecurityException("Runtime mapping requires separate own reserve control");
        if(psciRuntimeAllCpus&&!psciRuntimeReserve)throw new SecurityException("All CPU pages require owned runtime mapping");
        if(psciEntryMarker&&!psciRuntimeAllCpus)throw new SecurityException("Entry marker requires separate all CPU runtime pages");
        if(psciCachePublish&&!psciEntryMarker)throw new SecurityException("Cache publication requires exact own marker profile");
        if(windowsRamGop&&(!psciCachePublish||!fixedEightCpuGic||!matchHostCpus||!resumeWindowsInstallTarget||rtcClockShim||dynamicGic))throw new SecurityException("RAM GOP requires isolated proven eight CPU/cache profile");
        if(matchHostCpus&&!dynamicGic&&!fixedEightCpuGic)throw new SecurityException("SMP requires explicitly paired GIC candidate");
        if(rtcClockShim&&!resumeWindowsInstallTarget)throw new SecurityException("RTC trial only on isolated installed disk");
        if(resumeWindowsInstallTarget&&(prepareInstallTarget||resumePreparedInstallTarget))throw new SecurityException("Separate mutable install mode");
        if(prepareInstallTarget&&resumePreparedInstallTarget)throw new SecurityException("Choose prepare or resume");
        if(candidate!=null){
            if(!candidate.equals(SHA)&&!candidate.equals(LIVE_SHA)&&!candidate.equals(LIVE_NAME_FIX_SHA))throw new SecurityException("Unapproved Windows candidate");
            expectedDiskSha=candidate;
        }
        if(embeddedKd&&(!LIVE_NAME_FIX_SHA.equals(expectedDiskSha)||prepareInstallTarget
                ||resumePreparedInstallTarget||resumeWindowsInstallTarget||matchHostCpus))
            throw new SecurityException("Embedded KD requires the exact disposable one-CPU WinPE profile");
        NotificationManager notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("windows-owner","Windows diagnostic VM",NotificationManager.IMPORTANCE_LOW));
        startForeground(2114,new Notification.Builder(this,"windows-owner").setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("U-AVF Windows VM").setContentText("Isolated Windows diagnostic owner").setOngoing(true).build());
        new Thread(this::run,"Windows-raw-owner").start();return START_NOT_STICKY;
    }
    private void run(){
        try{
            report=new PrintWriter(new FileOutputStream(new File(getExternalFilesDir(null),"service-owner-report.txt")));
            Context context=this;
            File disk=new File(getFilesDir(),embeddedKd?"windows-embedded-kd.img":"windows-diagnostic.img");
            File kernel=new File(getFilesDir(),"u-boot-wrapper-v24.Image");
            if(resumeWindowsInstallTarget){
                verifyWindowsInstallTarget(disk);
                if(!wddmAdmission)archiveOwnDisplayEvidence(disk);
            }
            else if(resumePreparedInstallTarget)verifyPreparedInstallTarget(disk);
            else if(disk.length()!=9126805504L||!expectedDiskSha.equals(sha(disk)))throw new SecurityException("Exact shell candidate identity failed");
            if(prepareInstallTarget){
                if(!LIVE_NAME_FIX_SHA.equals(expectedDiskSha))throw new SecurityException("Install geometry requires proven live media");
                growInstallTarget(disk);
            }
            if(!"93EDA7C4BD54C33F85ADA6F05158C74EC6F5C3232F5CBA73846E5B442895F234".equals(sha(kernel)))throw new SecurityException("Wrapper differs");
            note("IDENTITY_PASS uid="+android.os.Process.myUid()+" package="+getPackageName());
            if(!embeddedKd){
                server=new ServerSocket(39101,1,InetAddress.getLoopbackAddress());server.setSoTimeout(120000);
                host=server.accept();host.setTcpNoDelay(true);host.setSoTimeout(10000);
                if(!token.equals(new String(host.getInputStream().readNBytes(64),StandardCharsets.US_ASCII)))throw new SecurityException("KD token mismatch");
                host.getOutputStream().write("UAVF_WINDOWS_OWNER_OK\n".getBytes(StandardCharsets.US_ASCII));host.getOutputStream().flush();host.setSoTimeout(0);
            }
            if(psciLowMemoryAb)note("PSCI_LOW_MEMORY_AB=2147483648; REASON=CPU_ON_ENTRY_ABOVE_4GB_INVALID_PARAMS; NOT_PRODUCTION_DEFAULT");
            Object raw=WindowsAvfOwner.rawConfig(context,kernel,disk,
                    psciLowMemoryAb?2147483648L:4294967296L,matchHostCpus);
            JSONObject snapshot=new JSONObject();snapshot.put("raw",json(raw,0));snapshot.put("uid",android.os.Process.myUid());snapshot.put("package",getPackageName());snapshot.put("source_media_sha256_before_growth",expectedDiskSha);snapshot.put("disk_bytes",disk.length());snapshot.put("install_geometry",prepareInstallTarget||resumePreparedInstallTarget);
            try(PrintWriter state=new PrintWriter(new File(getExternalFilesDir(null),"service-effective-config.json"))){state.println(snapshot.toString(2));}
            avfOwner=new WindowsAvfOwner(raw);
            note("AVF_SERVICE_READY");
            if(embeddedKd){
                runEmbeddedKd();
                return;
            }
            File serialFile=new File(getFilesDir(),"owner-serial.bin");
            output=ParcelFileDescriptor.open(serialFile,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY);
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();input=pipe[0];writeInput=pipe[1];
            log=ParcelFileDescriptor.open(new File(getFilesDir(),"service-crosvm.log"),ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY);
            String nativeDir=getApplicationInfo().nativeLibraryDir;
            ingress=new ProcessBuilder(nativeDir+"/libuavf_ingress.so","--paced","--log",new File(getFilesDir(),"owner-decoded-host-tx.bin").getAbsolutePath(),
                "--written-log",new File(getFilesDir(),"owner-written-uart-tx.bin").getAbsolutePath())
                .redirectOutput(new File("/proc/self/fd/"+writeInput.getFd())).redirectError(new File(getFilesDir(),"ingress-stderr.txt")).start();
            tailer=new ProcessBuilder(nativeDir+"/libuavf_console_tail.so",serialFile.getAbsolutePath())
                .redirectError(new File(getFilesDir(),"tailer-stderr.txt")).start();
            Socket connection=host;
            Thread rx=new Thread(()->{try(BufferedReader serial=new BufferedReader(new InputStreamReader(tailer.getInputStream(),StandardCharsets.US_ASCII))){String line;while(!stopping&&(line=serial.readLine())!=null){if(line.startsWith("O:")){byte[] bytes=android.util.Base64.decode(line.substring(2),android.util.Base64.DEFAULT);connection.getOutputStream().write(bytes);connection.getOutputStream().flush();}}}catch(Exception e){if(!stopping)note("SERIAL_RX="+e);}},"Windows-native-tail");rx.start();
            vm=avfOwner.create(output,input,log);
            avfOwner.start(vm);ownerStarted=true;note("AVF_OWNER_RUNNING_NATIVE_UART");
            // Exact proven ingress binary; TCP adapter only supplies its Base64 records.
            try(OutputStream uart=ingress.getOutputStream()){
                byte[] b=new byte[3072];int n;while(!stopping&&(n=host.getInputStream().read(b))!=-1){byte[] bytes=java.util.Arrays.copyOf(b,n);uart.write(android.util.Base64.encode(bytes,android.util.Base64.NO_WRAP));uart.write('\n');uart.flush();}
            }
        }catch(Throwable e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();note("OWNER_ERROR="+e+" ROOT="+cause);if(report!=null){e.printStackTrace(report);report.flush();}}
        finally{
            if(ownerStarted&&!stopping&&!embeddedKd){
                note("KD_OBSERVER_DISCONNECTED_VM_RETAINED_BY_APP_SERVICE");
                try{if(host!=null)host.close();if(server!=null)server.close();}catch(IOException ignored){}
                if(ingress!=null)ingress.destroy();if(tailer!=null)tailer.destroy();
            }else{stopVm();stopSelf();}
        }
    }
    private void runEmbeddedKd()throws IOException,ReflectiveOperationException{
        File nativeDir=new File(getApplicationInfo().nativeLibraryDir);
        try(WindowsKdUart uart=new WindowsKdUart(nativeDir,new File(getFilesDir(),"embedded-winpe-uart"))){
            synchronized(this){
                if(stopping)return;
                embeddedUart=uart;
                vm=uart.createVm(avfOwner);
                avfOwner.start(vm);
                ownerStarted=true;
            }
            note("AVF_OWNER_RUNNING_NATIVE_UART");
            serveEmbeddedKd(uart);
            if(!stopping)throw new EOFException("Embedded Windows KD UART closed");
        }finally{embeddedUart=null;}
    }
    private void serveEmbeddedKd(WindowsKdUart uart)throws IOException{
        Handler watchdog=new Handler(Looper.getMainLooper());
        Runnable deadline=()->{
            if(!ready&&!stopping){
                note("EMBEDDED_KD_BOOT_TIMEOUT");
                // The UART worker owns VM teardown.
                uart.close();
            }
        };
        watchdog.postDelayed(deadline,WindowsKdUart.BOOT_TIMEOUT_MS);
        try{
            uart.serve(()->{
                ready=true;
                watchdog.removeCallbacks(deadline);
                note("EMBEDDED_KD_KERNEL_RESUMED; WAIT_FOR_VSOCK");
            });
        }finally{watchdog.removeCallbacks(deadline);}
    }
    private synchronized void stopVm(){
        stopping=true;if(vm!=null){try{avfOwner.stop(vm);}catch(Exception e){note("STOP_ERROR="+e);}vm=null;}
        WindowsKdUart uart=embeddedUart;
        if(uart!=null)uart.close();
        try{if(host!=null)host.close();if(server!=null)server.close();}catch(IOException ignored){}
        if(ingress!=null)ingress.destroy();if(tailer!=null)tailer.destroy();
        for(ParcelFileDescriptor fd:new ParcelFileDescriptor[]{input,output,log,writeInput})if(fd!=null)try{fd.close();}catch(IOException ignored){}
    }
    private void runMavkBridge(){
        JSONObject result=new JSONObject();
        String stage="admission";
        try {
            if(!WindowsChannelPolicy.mavkAllowed(4057L,ready,mavkEnabled)||stopping||vm==null)throw new SecurityException("Mavk owner no longer ready");
            byte[] shader;try(InputStream input=getAssets().open("mavk/verify.comp.spv")){shader=input.readAllBytes();}
            if(shader.length<20||shader.length>65536||shader.length%4!=0)throw new IOException("Trusted Mavk shader bound");
            stage="connect_vsock";
            ParcelFileDescriptor connected=null;
            // Guest launch/normal UAC is asynchronous. Bounded connection retry
            // avoids rebooting a healthy VM just because its listener is late.
            long until=android.os.SystemClock.elapsedRealtime()+30000;
            for(;;){
                try{connected=avfOwner.connectVsock(vm,4057);break;}
                catch(java.lang.reflect.InvocationTargetException error){
                    if(error.getCause()==null||!"android.os.ServiceSpecificException".equals(error.getCause().getClass().getName())||stopping||android.os.SystemClock.elapsedRealtime()>=until)throw error;
                    Thread.sleep(500);
                }
            }
            try(ParcelFileDescriptor fd=connected){
                mavkChannel=fd;
                stage="serve_native";
                if(wddmCodec){
                    System.loadLibrary("mavk_runtime_probe");
                    File codecReport=new File(getFilesDir(),"mavk-wddm-codec.txt");
                    int status=serveWddmCodec(codecReport.getAbsolutePath(),fd.getFd());
                    result.put("codec_exit",status);
                    result.put("result",status==0?"WDDM_CODEC_SESSION_COMPLETE":"FAIL");
                }else{
                    Class<?> endpoint=Class.forName("org.mavk.bridge.BridgeEndpoint");
                    result=(JSONObject)endpoint.getMethod("serve",ParcelFileDescriptor.class,byte[].class,boolean.class).invoke(null,fd,shader,mavkValidation);
                }
            }finally{mavkChannel=null;}
            result.put("channel","app-owned AVF connectVsock4057");
        }catch(Throwable error){try{
            Throwable cause=error;
            for(int depth=0;depth<8&&cause.getCause()!=null;depth++)cause=cause.getCause();
            result.put("result","FAIL");result.put("stage",stage);
            result.put("error_category",error.getClass().getSimpleName());
            result.put("cause_category",cause.getClass().getSimpleName());
            if(cause instanceof android.system.ErrnoException){
                android.system.ErrnoException errno=(android.system.ErrnoException)cause;
                result.put("errno",errno.errno);
            }
        }catch(JSONException ignored){}}
        try(FileOutputStream file=new FileOutputStream(new File(getFilesDir(),"mavk-bridge-report.json"))){file.write(result.toString().getBytes(StandardCharsets.UTF_8));file.getFD().sync();}
        catch(IOException e){note("MAVK_REPORT_WRITE_FAILED");}
        note("MAVK_BRIDGE_FINISHED result="+result.optString("result","FAIL")+"; NOT_GUEST_ICD_PASS");
        // At most three sequential sessions per owner, never overlapping Core.
        mavkStarted.set(false);
    }
    @Override public void onDestroy(){
        ParcelFileDescriptor channel=mavkChannel;if(channel!=null){try{android.system.Os.shutdown(channel.getFileDescriptor(),android.system.OsConstants.SHUT_RDWR);}catch(Exception ignored){}}
        stopVm();super.onDestroy();
    }
}
