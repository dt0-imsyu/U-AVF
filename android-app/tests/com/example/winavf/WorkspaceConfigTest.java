package com.example.winavf;
public final class WorkspaceConfigTest {
    public static void main(String[] args) {
        WorkspaceConfig normal=new WorkspaceConfig(4,false,"1280x800");
        if(normal.memoryBytes()!=4294967296L || !normal.topology().equals("CPU_TOPOLOGY_MATCH_HOST")) throw new AssertionError();
        WorkspaceConfig changed=new WorkspaceConfig(3,true,"1920x1200");
        if(changed.height!=1200 || changed.width!=1920 || !changed.oneCpu) throw new AssertionError();
        try {new WorkspaceConfig(9,false,"1280x800"); throw new AssertionError();} catch(IllegalArgumentException expected) {}
        try {new WorkspaceConfig(4,false,"invalid"); throw new AssertionError();} catch(IllegalArgumentException expected) {}
        System.out.println("WORKSPACE_CONFIG_TEST=PASS");
    }
}
