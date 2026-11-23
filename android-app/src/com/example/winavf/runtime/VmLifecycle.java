// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf.runtime;

/** Actual VM lifecycle is independent of the currently visible product screen. */
public final class VmLifecycle {
    public enum State { NOT_CONFIGURED, READY, STARTING, RUNNING, STOPPING, STOPPED, ERROR }
    public enum Install { NONE, PREPARING, INSTALLING, FINALIZING, FIRST_BOOT, READY, FAILED }
    private State state=State.NOT_CONFIGURED;
    private Install install=Install.NONE;
    private Object owner;
    private boolean storageMutation;
    private static final java.util.concurrent.atomic.AtomicBoolean COMPATIBILITY_PROBE =
            new java.util.concurrent.atomic.AtomicBoolean();
    private String error="", stopReason="Unknown";
    public synchronized State state() { return state; }
    public synchronized Install install() { return install; }
    public synchronized String error() { return error; }
    public synchronized String stopReason() { return stopReason; }
    public synchronized Object owner() { return owner; }
    public synchronized boolean owns(Object vm) { return owner==vm; }
    public synchronized void attach(Object vm) { owner=vm; }
    public synchronized boolean beginStart() {
        if (busy() || storageMutation || compatibilityProbeActive()) return false;
        owner=null; state=State.STARTING; error=""; return true;
    }
    public synchronized boolean busy() { return state==State.STARTING || state==State.RUNNING || state==State.STOPPING; }
    public synchronized boolean beginStorageMutation() {
        if(busy() || storageMutation || compatibilityProbeActive())return false;
        storageMutation=true;return true;
    }
    public synchronized void endStorageMutation() {storageMutation=false;}
    public synchronized boolean beginCompatibilityProbe() {
        return !busy() && !storageMutation && COMPATIBILITY_PROBE.compareAndSet(false,true);
    }
    public static boolean compatibilityProbeActive() { return COMPATIBILITY_PROBE.get(); }
    public static void endCompatibilityProbe() { COMPATIBILITY_PROBE.set(false); }
    public synchronized void stopping() { if (busy()) state=State.STOPPING; }
    public synchronized boolean cancelUnattachedStart() {
        if (owner != null || state != State.STARTING) return false;
        state = State.STOPPING;
        return true;
    }
    public synchronized void failStart(String detail) { state=State.ERROR; error=detail; }
    public synchronized void install(Install value) { install=value; }
    public synchronized boolean stopped(Object vm,String reason) {
        if (!owns(vm)) return false;
        state=State.STOPPED; stopReason=reason; return true;
    }
    public synchronized boolean failed(Object vm,String detail) {
        if (!owns(vm)) return false;
        state=State.ERROR; error=detail; return true;
    }
    public synchronized void reconcile(Object vm,String avf,boolean configured) {
        owner=vm;
        String value=avf==null?"":avf.toUpperCase(java.util.Locale.ROOT);
        if (value.equals("RUNNING")) {
            if (state!=State.STOPPING) state=State.RUNNING;
        } else if (value.equals("STARTING")) state=State.STARTING;
        else if (value.equals("STOPPED") || value.equals("DELETED") || vm==null)
            state=configured?State.READY:State.NOT_CONFIGURED;
        else { state=State.ERROR; error="Unknown AVF state: "+avf; }
    }
}
