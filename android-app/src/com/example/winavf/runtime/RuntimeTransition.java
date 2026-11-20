// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/** Observations plus proven prerequisites, with provenance (not invented serial timestamps). */
public final class RuntimeTransition {
    public enum Mode { NONE, BOOT, DISPLAY_RECONNECT, SHUTDOWN, REBOOT, COMPLETE, ERROR }
    private Mode mode=Mode.NONE;
    private Mode operation=Mode.NONE;
    private long started,lastEvent;
    private int generation;
    private String detail="";
    private final LinkedHashMap<String,String> events=new LinkedHashMap<>();
    private final StringBuilder tail=new StringBuilder();
    public synchronized int begin(Mode next,long now) {
        mode=operation=next; started=lastEvent=now; generation++; events.clear(); tail.setLength(0); detail="";
        event(switch(next) {
            case BOOT -> "Start requested";
            case DISPLAY_RECONNECT -> "Display reconnect requested";
            case REBOOT -> "Restart requested";
            default -> "Shutdown requested";
        },"App",now);
        return generation;
    }
    public synchronized int generation() { return generation; }
    public synchronized Mode mode() { return mode; }
    public synchronized Mode operation() { return operation; }
    public synchronized boolean visible() { return mode!=Mode.NONE; }
    public synchronized boolean acceptsDesktop() { return mode==Mode.NONE || mode==Mode.COMPLETE || mode==Mode.BOOT
            || mode==Mode.DISPLAY_RECONNECT || (mode==Mode.REBOOT && events.containsKey("VM stopped")); }
    public synchronized void event(String name,String source,long now) {
        boolean bootEvidence="Virtual machine created".equals(name) || "UEFI started".equals(name) || "Linux kernel".equals(name) ||
            "systemd userspace".equals(name) || "GDM started".equals(name) || "GDM display sharing".equals(name) || "Display connected".equals(name) || "Desktop presented".equals(name);
        if(bootEvidence && (mode==Mode.SHUTDOWN || mode==Mode.ERROR || (mode==Mode.REBOOT && !events.containsKey("VM stopped"))))return;
        if("VM stopped".equals(name)) tail.setLength(0);
        if("systemd userspace".equals(name) || "GDM started".equals(name) || "GDM display sharing".equals(name))
            record("Linux kernel","Prerequisite confirmed by "+name+": "+source,now);
        if("GDM started".equals(name) || "GDM display sharing".equals(name))
            record("systemd userspace","Managed GDM service: "+source,now);
        if("GDM display sharing".equals(name)) record("GDM started","GDM sharing agent found session: "+source,now);
        record(name,source,now);
    }
    private void record(String name,String source,long now) {
        if(!events.containsKey(name)) { events.put(name,source); lastEvent=now; }
    }
    public synchronized void guestReady(boolean logindReady,long now) {
        if(mode!=Mode.BOOT && !(mode==Mode.REBOOT && events.containsKey("VM stopped"))) return;
        event("Linux kernel","Linux guest agent UCTL HELLO",now);
        if(logindReady) event("systemd userspace","Successful guest loginctl query",now);
    }
    public synchronized void serial(int expected,String chunk,long now) {
        if(expected!=generation || !visible() || mode==Mode.ERROR || mode==Mode.DISPLAY_RECONNECT) return;
        tail.append(chunk);
        String value=tail.toString().replaceAll("\u001b\\[[0-9;]*[A-Za-z]","").replace("\r","");
        if(mode==Mode.BOOT || (mode==Mode.REBOOT && events.containsKey("VM stopped"))) {
            if(value.contains("AVF_UEFI") || value.contains("UEFI") || value.contains("EDK II")) event("UEFI started","Serial",now);
            if(value.contains("Linux version ") || value.contains("Booting Linux")) event("Linux kernel","Serial",now);
            if(value.contains("systemd[1]") || value.contains("systemd ") || value.contains("Welcome to Ubuntu") ||
                java.util.regex.Pattern.compile("(?m)^.*(?:Starting|Started|Finished)[^\\n]*systemd-[a-zA-Z0-9@_.\\\\-]+\\.service").matcher(value).find())
                event("systemd userspace","Serial systemd/service status",now);
            if(java.util.regex.Pattern.compile("(?m)^.*Reached target[^\\n]*network-online\\.target").matcher(value).find()) event("Network online target","Serial (not connectivity test)",now);
            if(java.util.regex.Pattern.compile("(?m)^.*Started[^\\n]*gdm\\.service").matcher(value).find()) event("GDM started","Serial",now);
            if(value.contains("UAVF_GDM_SHARE=START")) event("GDM display sharing","Serial",now);
        } else {
            if(java.util.regex.Pattern.compile("(?m)^.*Stopping[^\\n]*(gdm\\.service|gnome)").matcher(value).find()) event("GNOME session ending","Serial",now);
            if(value.contains("Stopping") || value.contains("shutdown.target")) event("systemd stopping services","Serial",now);
            if(value.contains("Syncing filesystems")) event("Filesystem sync","Serial",now);
            if(value.contains("Power down") || value.contains("reboot: Restarting system")) event("Guest powered off","Serial",now);
        }
        if(tail.length()>4096) tail.delete(0,tail.length()-4096);
    }
    public synchronized void failure(String value,long now) { mode=Mode.ERROR; detail=value; lastEvent=now; }
    public synchronized boolean desktop(long now) {
        if(mode==Mode.NONE || mode==Mode.COMPLETE || !acceptsDesktop()) return false;
        event("Display connected","Validated display frame",now);
        event("Desktop presented","Validated Surface frame",now);mode=Mode.COMPLETE;return true;
    }
    public synchronized boolean finishDesktop(int expected) {if(generation!=expected || mode!=Mode.COMPLETE)return false;mode=Mode.NONE;return true;}
    public synchronized void dismiss() { mode=Mode.NONE; }
    public synchronized void keepWaiting(long now) { lastEvent=now; }
    public synchronized long elapsed(long now) { return Math.max(0,now-started)/1000; }
    public synchronized boolean waiting(long now) { return mode!=Mode.NONE && mode!=Mode.COMPLETE && mode!=Mode.ERROR && now-started>=60000 && now-lastEvent>=30000; }
    public synchronized String detail() { return detail; }
    public synchronized Map<String,String> events() { return new LinkedHashMap<>(events); }
}
