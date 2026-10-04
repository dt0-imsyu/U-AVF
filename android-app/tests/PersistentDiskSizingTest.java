package com.example.winavf;

/** Pure geometry checks: no user disk or sparse fixture is created. */
public final class PersistentDiskSizingTest {
    public static void main(String[] args) {
        long gib=1024L*1024*1024,iso=3967463424L;
        for(int size:new int[]{32,40,48,64,96,128,256,1024}) {
            long bytes=PersistentUbuntuDisk.expectedDiskBytes(iso,size*gib);
            long tail=bytes/512-8011776L-size*gib/512;
            if(bytes%(1024*1024)!=0 || tail<34 || tail>2081)
                throw new AssertionError("Bad protected layout: "+size);
            if(bytes-PersistentUbuntuDisk.expectedDiskBytes(iso)!=(size-32L)*gib)
                throw new AssertionError("Wrong root size: "+size);
        }
        for(long invalid:new long[]{31*gib,1025*gib,32*gib+1}) {
            try { PersistentUbuntuDisk.expectedDiskBytes(iso,invalid);
                throw new AssertionError("Invalid root size accepted");
            } catch(IllegalArgumentException expected) { }
        }
        System.out.println("WORKSPACE_SIZE_GEOMETRY=PASS PROTECTED_REGIONS=PASS INVALID_SIZE_REJECTED=PASS");
    }
}
