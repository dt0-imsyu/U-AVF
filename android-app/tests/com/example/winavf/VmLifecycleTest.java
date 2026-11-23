package com.example.winavf;

import com.example.winavf.runtime.VmLifecycle;
public final class VmLifecycleTest {
    private static void check(boolean value) { if(!value) throw new AssertionError(); }
    public static void main(String[] args) {
        VmLifecycle model=new VmLifecycle(); Object old=new Object(),current=new Object();
        check(model.beginStart()); check(!model.beginStart());
        model.attach(old); model.reconcile(old,"RUNNING",true);
        check(model.state()==VmLifecycle.State.RUNNING);
        model.stopping(); model.reconcile(old,"RUNNING",true);
        check(model.state()==VmLifecycle.State.STOPPING);
        check(model.stopped(old,"STOP_REASON_SHUTDOWN"));
        check(model.beginStart()); model.attach(current);
        check(!model.stopped(old,"stale"));
        model.reconcile(current,"RUNNING",true); check(model.busy());
        check(!model.beginCompatibilityProbe());
        model.failed(current,"real error"); check(!model.busy());
        model.reconcile(null,"STOPPED",true); check(model.state()==VmLifecycle.State.READY);
        model.reconcile(null,"",false); check(model.state()==VmLifecycle.State.NOT_CONFIGURED);
        model.reconcile(current,"unexpected",true); check(model.state()==VmLifecycle.State.ERROR);
        check(model.beginCompatibilityProbe());
        VmLifecycle recreated=new VmLifecycle();
        try {
            check(VmLifecycle.compatibilityProbeActive());
            check(!recreated.beginCompatibilityProbe());
            check(!recreated.beginStart());
            check(!recreated.beginStorageMutation());
        } finally {
            VmLifecycle.endCompatibilityProbe();
        }
        check(recreated.beginStorageMutation());
        check(!recreated.beginCompatibilityProbe());
        recreated.endStorageMutation();
        check(recreated.beginStart());
        check(recreated.cancelUnattachedStart());
        check(recreated.state() == VmLifecycle.State.STOPPING);
        check(!recreated.beginStart());
        check(recreated.stopped(null, "Launch cancelled"));
        check(recreated.beginStart());
        recreated.attach(current);
        check(!recreated.cancelUnattachedStart());
        System.out.println("VM_LIFECYCLE_TEST=PASS");
    }
}
