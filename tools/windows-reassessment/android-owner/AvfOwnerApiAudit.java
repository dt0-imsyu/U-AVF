package com.example.winavf;

/** Read-only audit of the shipped shell-owner AVF API; never creates a VM. */
public final class AvfOwnerApiAudit {
    public static void main(String[] args) throws Exception {
        System.out.println("AUDIT_UID="+android.os.Process.myUid());
        for(String name:new String[]{"android.system.virtualizationservice.IVirtualizationService",
                "android.system.virtualizationservice.IVirtualMachine",
                "android.system.virtualmachine.VirtualMachineConfig",
                "android.system.virtualmachine.VirtualMachineManager",
                "android.system.virtualmachine.VirtualMachine",
                "android.system.virtualmachine.VirtualMachineDescriptor",
                "android.system.virtualmachine.VirtualizationService"}) {
            Class<?> type=Class.forName(name);
            System.out.println("CLASS="+type.getName());
            for(java.lang.reflect.Method m:type.getDeclaredMethods()) {
                String n=m.getName();
                if(name.equals("android.system.virtualizationservice.IVirtualizationService")
                        ||name.equals("android.system.virtualizationservice.IVirtualMachine")
                        ||n.equals("createVm")||n.equals("connectVsock")||n.equals("getInstance")
                        ||n.equals("get")||n.equals("getOrCreate")||n.equals("importFromDescriptor")
                        ||n.equals("toDescriptor")||n.equals("getVm")||n.equals("getVmById")
                        ||n.equals("getBinder")||n.equals("toVsRawConfig")||n.equals("start")||n.equals("stop"))
                    System.out.println(m.toGenericString());
            }
        }
    }
}
