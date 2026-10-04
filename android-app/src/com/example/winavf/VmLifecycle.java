// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

/** Actual VM lifecycle is independent of the currently visible product screen. */
final class VmLifecycle {
    enum State { NOT_CONFIGURED, READY, STARTING, RUNNING, STOPPING, STOPPED, ERROR }
    enum Install { NONE, PREPARING, INSTALLING, FINALIZING, FIRST_BOOT, READY, FAILED }
    private State state=State.NOT_CONFIGURED;
    private Install install=Install.NONE;
    private Object owner;
    private boolean storageMutation;
    private String error="", stopReason="Unknown";
    synchronized State state() { return state; }
    synchronized Install install() { return install; }
    synchronized String error() { return error; }
    synchronized String stopReason() { return stopReason; }
    synchronized boolean owns(Object vm) { return owner==vm; }
    synchronized void attach(Object vm) { owner=vm; }
    synchronized boolean beginStart() {
        if (busy() || storageMutation) return false;
        owner=null; state=State.STARTING; error=""; return true;
    }
    synchronized boolean busy() { return state==State.STARTING || state==State.RUNNING || state==State.STOPPING; }
    synchronized boolean beginStorageMutation() {
        if(busy() || storageMutation)return false;
        storageMutation=true;return true;
    }
    synchronized void endStorageMutation() {storageMutation=false;}
    synchronized void stopping() { if (busy()) state=State.STOPPING; }
    synchronized void failStart(String detail) { state=State.ERROR; error=detail; }
    synchronized void install(Install value) { install=value; }
    synchronized boolean stopped(Object vm,String reason) {
        if (!owns(vm)) return false;
        state=State.STOPPED; stopReason=reason; return true;
    }
    synchronized boolean failed(Object vm,String detail) {
        if (!owns(vm)) return false;
        state=State.ERROR; error=detail; return true;
    }
    synchronized void reconcile(Object vm,String avf,boolean configured) {
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
