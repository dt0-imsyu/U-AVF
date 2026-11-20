"""Bounded GNOME Sysprof D-Bus capture; no root or compositor restart.

Binary layout: GNOME/sysprof 46.0 sysprof-capture-types.h. This reader checks
every frame bound and only summarizes duration marks, not CPU sample rates.
"""
import base64
import os
import struct
from pathlib import Path
from gi.repository import Gio, GLib

class Profile:
    def __init__(self):
        self.path=Path('/tmp/uavf-mutter-source.syscap')
        self.bus=Gio.bus_get_sync(Gio.BusType.SESSION,None)
        self.active=False

    def start(self):
        fd=os.open(self.path,os.O_CREAT|os.O_TRUNC|os.O_RDWR,0o600)
        try:
            fds=Gio.UnixFDList.new(); index=fds.append(fd)
            self.bus.call_with_unix_fd_list_sync('org.gnome.Shell','/org/gnome/Sysprof3/Profiler',
                'org.gnome.Sysprof3.Profiler','Start',GLib.Variant('(a{sv}h)',({},index)),
                None,Gio.DBusCallFlags.NONE,10000,fds,None)
            self.active=True
        finally: os.close(fd)

    def finish(self,start_ns,end_ns):
        if not self.active: return {'error':'Profile was not started'}
        self.bus.call_sync('org.gnome.Shell','/org/gnome/Sysprof3/Profiler',
            'org.gnome.Sysprof3.Profiler','Stop',None,None,Gio.DBusCallFlags.NONE,10000,None)
        self.active=False
        data=self.path.read_bytes()
        if len(data)<256 or struct.unpack_from('<I',data)[0]!=0xFDCA975E:
            return {'error':'Invalid Sysprof header','bytes':len(data)}
        endian='<' if struct.unpack_from('<I',data,4)[0] & 256 else '>'
        pos=256; groups={}; frame_count=0
        while pos<len(data):
            if pos+24>len(data): raise ValueError('Truncated Sysprof frame header')
            length,cpu,pid,at,kind,padding=struct.unpack_from(endian+'HhiqII',data,pos)
            if length<24 or pos+length>len(data): raise ValueError('Invalid Sysprof frame length')
            frame_count+=1
            if kind & 255 == 10 and length>=96 and start_ns<=at<end_ns:
                duration=struct.unpack_from(endian+'q',data,pos+24)[0]/1e6
                group=data[pos+32:pos+56].split(b'\0')[0].decode(errors='replace')
                name=data[pos+56:pos+96].split(b'\0')[0].decode(errors='replace')
                if duration>=0: groups.setdefault(group+'/'+name,[]).append(duration)
            pos+=length
        summary={name:{'count':len(v),'mean_ms':sum(v)/len(v),
                       'p95_ms':sorted(v)[int(.95*(len(v)-1))],'max_ms':max(v)}
                 for name,v in groups.items()}
        return {'frames':frame_count,'bytes':len(data),'common_window_marks':summary,
                'syscap_base64':base64.b64encode(data).decode()}
