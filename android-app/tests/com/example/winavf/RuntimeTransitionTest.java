package com.example.winavf;

/** Host-only lifecycle evidence regression. */
public final class RuntimeTransitionTest {
    private static void check(boolean value,String detail) {if(!value)throw new AssertionError(detail);}
    public static void main(String[] args) {
        RuntimeTransition t=new RuntimeTransition();int boot=t.begin(RuntimeTransition.Mode.BOOT,1000);
        t.serial(boot,"Linux ver",2000);t.serial(boot,"sion 6.8\n",2100);
        check(t.events().containsKey("Linux kernel"),"split serial marker");
        check(!t.events().containsKey("UEFI started"),"no inferred UEFI success");
        t.serial(boot,"Started unrelated.service\nStarting gdm.service\n",2200);
        check(!t.events().containsKey("GDM started"),"starting is not started");
        t.serial(boot,"Started \u001b[1;39mgdm.service\u001b[0m\n",2300);
        check(t.events().containsKey("GDM started"),"ANSI GDM marker");
        t.serial(boot,"Started NetworkManager.service\n",2400);
        check(!t.events().containsKey("Network online target"),"no fake network check");
        t.desktop(2500);check(t.mode()==RuntimeTransition.Mode.COMPLETE,"last desktop milestone is visible");
        check(t.events().containsKey("Display connected"),"display handshake/final frame recorded");
        check(t.finishDesktop(boot),"valid desktop finishes boot after UI acknowledgement");
        int shut=t.begin(RuntimeTransition.Mode.SHUTDOWN,3000);
        t.desktop(3100);check(t.visible(),"late old frame cannot dismiss shutdown");
        t.serial(boot,"Power down",3200);check(!t.events().containsKey("Guest powered off"),"stale generation");
        t.serial(shut,"Stopping gdm.service\nSyncing filesystems\nPower down",3300);
        check(t.events().containsKey("Guest powered off"),"poweroff evidence");
        t.begin(RuntimeTransition.Mode.REBOOT,4000);t.desktop(4100);
        t.event("Display connected","Old stream reconnect",4101);
        check(!t.events().containsKey("Display connected"),"old stream cannot confirm new reboot display");
        check(t.visible(),"pre-reboot old frame rejected");
        t.event("VM stopped","AVF",4200);t.desktop(4300);check(t.mode()==RuntimeTransition.Mode.COMPLETE,"new reboot frame");
        check(!t.finishDesktop(boot),"stale finish cannot hide new reboot");
        t.finishDesktop(t.generation());
        t.begin(RuntimeTransition.Mode.BOOT,5000);check(!t.waiting(36000),"no premature recovery after UEFI delay");
        check(!t.waiting(64999),"recovery forbidden before one minute");
        check(t.waiting(65000),"one minute and no new evidence");
        t.event("Linux kernel","Serial",65001);check(!t.waiting(66000),"fresh progress keeps waiting normally");
        t.keepWaiting(96000);check(!t.waiting(97000),"keep waiting resets inactivity not elapsed");
        check(t.elapsed(97000)==92,"elapsed never reset");
        t.failure("Test failure",98000);t.desktop(99000);check(t.visible(),"frame cannot hide error");
        t.begin(RuntimeTransition.Mode.REBOOT,100000);t.failure("Rejected",100001);
        check(t.operation()==RuntimeTransition.Mode.REBOOT,"error retains restart intent and rows");
        int current=t.begin(RuntimeTransition.Mode.BOOT,110000);
        t.serial(current,"Started gdm.service - GNOME Display Manager\n",110001);
        check(t.events().containsKey("Linux kernel") && t.events().containsKey("systemd userspace"),"running GDM proves kernel and managed userspace, not missing serial lines");
        t.begin(RuntimeTransition.Mode.BOOT,120000);t.guestReady(true,120001);
        check(t.events().containsKey("systemd userspace"),"real loginctl control evidence");
        check(!t.events().containsKey("GDM started"),"loginctl alone does not fabricate GDM");
        current=t.begin(RuntimeTransition.Mode.BOOT,130000);
        t.serial(current,"[  OK  ] Finished systemd-binfmt.service - Additional formats.\n",130001);
        check(t.events().containsKey("Linux kernel") && t.events().containsKey("systemd userspace"),"quiet installed kernel and systemd status format");
        t.serial(current,"UAVF_GDM_SHARE=START DISPLAY=:0 UID=1000\n",130002);
        check(t.events().containsKey("GDM started"),"sharing witness advances actual GDM stage");
        if(args.length>0) {
            t.begin(RuntimeTransition.Mode.BOOT,140000);
            String serial;
            try {serial=java.nio.file.Files.readString(java.nio.file.Path.of(args[0]));}
            catch(Exception e){throw new AssertionError("Serial replay input",e);}
            int replay=t.generation();
            for(int offset=0;offset<serial.length();offset+=256)
                t.serial(replay,serial.substring(offset,Math.min(offset+256,serial.length())),140001+offset);
            for(String marker:new String[]{"UEFI started","Linux kernel","systemd userspace","GDM started"})
                check(t.events().containsKey(marker),"actual serial replay: "+marker);
            System.out.println("INSTALLED_SERIAL_REPLAY=PASS "+t.events().keySet());
        }
        System.out.println("RUNTIME_TRANSITION_TEST=PASS");
    }
}
