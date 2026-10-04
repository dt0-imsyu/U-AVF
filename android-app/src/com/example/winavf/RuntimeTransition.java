// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import java.util.LinkedHashMap;
import java.util.Map;

/** Observations plus proven prerequisites, with provenance (not invented serial timestamps). */
final class RuntimeTransition {
    enum Mode { NONE, BOOT, SHUTDOWN, REBOOT, COMPLETE, ERROR }
    private Mode mode=Mode.NONE;
    private Mode operation=Mode.NONE;
    private long started,lastEvent;
    private int generation;
    private String detail="";
    private final LinkedHashMap<String,String> events=new LinkedHashMap<>();
    private final StringBuilder tail=new StringBuilder();
    synchronized int begin(Mode next,long now) {
        mode=operation=next; started=lastEvent=now; generation++; events.clear(); tail.setLength(0); detail="";
        event(next==Mode.BOOT?"Start requested":next==Mode.REBOOT?"Restart requested":"Shutdown requested","App",now);
        return generation;
    }
    synchronized int generation() { return generation; }
    synchronized Mode mode() { return mode; }
    synchronized Mode operation() { return operation; }
    synchronized boolean visible() { return mode!=Mode.NONE; }
    synchronized boolean acceptsDesktop() { return mode==Mode.NONE || mode==Mode.COMPLETE || mode==Mode.BOOT || (mode==Mode.REBOOT && events.containsKey("VM stopped")); }
    synchronized void event(String name,String source,long now) {
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
    synchronized void guestReady(boolean logindReady,long now) {
        if(mode!=Mode.BOOT && !(mode==Mode.REBOOT && events.containsKey("VM stopped"))) return;
        event("Linux kernel","Linux guest agent UCTL HELLO",now);
        if(logindReady) event("systemd userspace","Successful guest loginctl query",now);
    }
    synchronized void serial(int expected,String chunk,long now) {
        if(expected!=generation || !visible() || mode==Mode.ERROR) return;
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
    synchronized void failure(String value,long now) { mode=Mode.ERROR; detail=value; lastEvent=now; }
    synchronized boolean desktop(long now) {
        if(mode==Mode.NONE || mode==Mode.COMPLETE || !acceptsDesktop()) return false;
        event("Display connected","Validated display frame",now);
        event("Desktop presented","Validated Surface frame",now);mode=Mode.COMPLETE;return true;
    }
    synchronized boolean finishDesktop(int expected) {if(generation!=expected || mode!=Mode.COMPLETE)return false;mode=Mode.NONE;return true;}
    synchronized void dismiss() { mode=Mode.NONE; }
    synchronized void keepWaiting(long now) { lastEvent=now; }
    synchronized long elapsed(long now) { return Math.max(0,now-started)/1000; }
    synchronized boolean waiting(long now) { return mode!=Mode.NONE && mode!=Mode.COMPLETE && mode!=Mode.ERROR && now-started>=60000 && now-lastEvent>=30000; }
    synchronized String detail() { return detail; }
    synchronized Map<String,String> events() { return new LinkedHashMap<>(events); }
}
