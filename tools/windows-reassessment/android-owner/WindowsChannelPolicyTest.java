package com.example.winavf;

/** Actual admission predicate, no Android/system Binder calls. */
public final class WindowsChannelPolicyTest {
    public static void main(String[] args) {
        long[] ports = {4052,4053,4054,4055,4056,4057,0,-1,65535,65536,
            Long.MIN_VALUE,Long.MAX_VALUE,4054L+(1L<<32),4055L+(1L<<32)};
        int checks=0;
        for(boolean ready:new boolean[]{false,true})
            for(boolean clipboard:new boolean[]{false,true})
                for(long port:ports) {
                    boolean expected=ready&&(port==4055||(clipboard&&port==4054));
                    if(WindowsChannelPolicy.allowed(port,ready,clipboard)!=expected)
                        throw new AssertionError("Unexpected channel admission");
                    checks++;
                }
        for(boolean ready:new boolean[]{false,true})for(boolean enabled:new boolean[]{false,true})
            for(long port:new long[]{4057,4055,4054,0,-1,4057L+(1L<<32),Long.MAX_VALUE}){
                if(WindowsChannelPolicy.mavkAllowed(port,ready,enabled)!=(ready&&enabled&&port==4057L))throw new AssertionError("Mavk explicit channel admission");checks++;
            }
        long gib=1024L*1024L*1024L, diagnostic=0xc63ef6813ef669b7L;
        if(WindowsChannelPolicy.windowsResumeReserve(false,true,diagnostic)!=52*gib
                ||WindowsChannelPolicy.windowsResumeReserve(true,true,diagnostic)!=8*gib)
            throw new AssertionError("Resume reserve");
        for(boolean mavk:new boolean[]{false,true})for(long serial:new long[]{0,diagnostic,0x4aa580661de89658L}){
            if(mavk&&serial==diagnostic)continue;
            try {
                WindowsChannelPolicy.windowsResumeReserve(true,mavk,serial);
                throw new AssertionError("Unexpected WDDM volume admission");
            }catch(SecurityException expected){}
        }
        System.out.println("ACTUAL_WINDOWS_PORT_OPTIN_READY_LONG_RANGE_CHECKS="+checks+" WDDM_RESUME_SCOPE_PASS");
    }
}
