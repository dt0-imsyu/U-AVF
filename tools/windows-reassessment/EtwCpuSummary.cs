// Local CPU instruction summary only. Never prints command lines, credentials,
// raw payloads or stacks. No capture, process control or target modifications.
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Microsoft.Diagnostics.Tracing;
using Microsoft.Diagnostics.Tracing.Parsers.Kernel;

public static class UavfEtwCpuSummary {
    public sealed class Module { public int Pid; public ulong Base,Size; public string Name; }
    public sealed class Sample { public int Pid,Tid; public ulong Pc; public double Time; }
    public sealed class CpuRow { public int Pid,Count; public string Name; public double Percent; }
    public sealed class CodeRow { public int Pid,Tid,Count; public string Process,Module,Pc,Rva; }
    public sealed class EventRow { public string Type; public int Count; }
    public sealed class ThreadEntryRow { public int Pid,Tid,Priority; public string Entry,Module,Rva; }
    public sealed class ModuleRow { public string Name,Base; public ulong Size; }
    public sealed class Report { public long EventsLost; public int Samples; public double DurationMs;
        public CpuRow[] Cpu; public CodeRow[] HotCode; public EventRow[] EventTypes; public ThreadEntryRow[] LsassThreads; public ModuleRow[] LsassModules; public string Limitation; }
    static Module Resolve(Sample sample,List<Module> modules) {
        return modules.LastOrDefault(m=>(m.Pid==sample.Pid || (sample.Pc>=0x800000000000UL && (m.Pid==0||m.Pid==4)))
            && sample.Pc>=m.Base && sample.Pc-m.Base<m.Size);
    }
    public static Report Read(string path) {
        var info=new FileInfo(path);
        if(!info.Exists||info.Length==0||info.Length>32L*1024*1024)throw new Exception("Bounded own ETL required");
        var names=new Dictionary<int,string>();var threads=new Dictionary<int,int>();
        var modules=new List<Module>();var samples=new List<Sample>();long lost;
        var types=new Dictionary<string,int>();int eventCount=0;
        var entries=new Dictionary<int,Tuple<int,ulong,int>>();
        using(var source=new ETWTraceEventSource(path)) {
            source.AllEvents+=e=>{
                if(++eventCount>500000)throw new Exception("Bounded event catalogue exceeded");
                string type=e.ProviderName+":"+e.EventName;
                types[type]=types.TryGetValue(type,out int count)?count+1:1;
            };
            Action<ProcessTraceData> process=e=>names[e.ProcessID]=Path.GetFileName(e.ImageFileName);
            source.Kernel.ProcessStartGroup+=process;
            source.Kernel.ProcessDCStop+=process;
            Action<ThreadTraceData> thread=e=>{
                threads[e.ThreadID]=e.ProcessID;
                if(entries.Count>=20000)throw new Exception("Bounded thread metadata exceeded");
                entries[e.ThreadID]=Tuple.Create(e.ProcessID,e.Win32StartAddr,(int)e.BasePriority);
            };
            source.Kernel.ThreadStartGroup+=thread;
            source.Kernel.ThreadDCStop+=thread;
            Action<ImageLoadTraceData> image=e=>{
                if(modules.Count>=20000)throw new Exception("Bounded loader metadata exceeded");
                modules.Add(new Module{Pid=e.ProcessID,Base=e.ImageBase,Size=(ulong)e.ImageSize,Name=Path.GetFileName(e.FileName)});
            };
            source.Kernel.ImageLoadGroup+=image;
            source.Kernel.ImageDCStop+=image;
            source.Kernel.PerfInfoSample+=e=>{
                if(samples.Count>=250000)throw new Exception("Bounded code samples exceeded");
                int pid=e.ProcessID;
                if(pid<0&&threads.TryGetValue(e.ThreadID,out int owner))pid=owner;
                samples.Add(new Sample{Pid=pid,Tid=e.ThreadID,Pc=e.InstructionPointer,Time=e.TimeStampRelativeMSec});
            };
            source.Process();lost=source.EventsLost;
        }
        var report=new Report{EventsLost=lost,Samples=samples.Count,
            DurationMs=samples.Count>1?samples.Last().Time-samples.First().Time:0,
            Limitation="Sampled CPU instruction addresses, not wall CPU time or PMU support. Nearest symbols require matching images. Private ETL metadata remains local; not a credential/heap/stack dump."};
        report.Cpu=samples.GroupBy(s=>s.Pid).OrderByDescending(g=>g.Count()).Take(20)
            .Select(g=>new CpuRow{Pid=g.Key,Count=g.Count(),Name=names.TryGetValue(g.Key,out string name)?name:"unknown",Percent=samples.Count==0?0:100.0*g.Count()/samples.Count}).ToArray();
        report.HotCode=samples.GroupBy(s=>new {s.Pid,s.Tid,s.Pc}).OrderByDescending(g=>g.Count()).Take(50)
            .Select(g=>{
                Module module=Resolve(g.First(),modules);
                return new CodeRow{Pid=g.Key.Pid,Tid=g.Key.Tid,Count=g.Count(),Pc="0x"+g.Key.Pc.ToString("x"),
                    Process=names.TryGetValue(g.Key.Pid,out string name)?name:"unknown",
                    Module=module==null?"unknown":module.Name,Rva=module==null?"unknown":"0x"+(g.Key.Pc-module.Base).ToString("x")};
            }).ToArray();
        report.EventTypes=types.OrderByDescending(pair=>pair.Value).Take(40)
            .Select(pair=>new EventRow{Type=pair.Key,Count=pair.Value}).ToArray();
        report.LsassThreads=entries.Where(pair=>names.TryGetValue(pair.Value.Item1,out string name)&&
            String.Equals(name,"lsass.exe",StringComparison.OrdinalIgnoreCase)).Take(64).Select(pair=>{
                Module module=Resolve(new Sample{Pid=pair.Value.Item1,Pc=pair.Value.Item2},modules);
                return new ThreadEntryRow{Pid=pair.Value.Item1,Tid=pair.Key,Priority=pair.Value.Item3,
                    Entry="0x"+pair.Value.Item2.ToString("x"),Module=module==null?"unknown":module.Name,
                    Rva=module==null?"unknown":"0x"+(pair.Value.Item2-module.Base).ToString("x")};
            }).ToArray();
        report.LsassModules=modules.Where(m=>names.TryGetValue(m.Pid,out string name)&&
            String.Equals(name,"lsass.exe",StringComparison.OrdinalIgnoreCase))
            .GroupBy(m=>new{m.Base,m.Name}).Select(g=>g.Last()).Take(256)
            .Select(m=>new ModuleRow{Name=m.Name,Base="0x"+m.Base.ToString("x"),Size=m.Size}).ToArray();
        return report;
    }
    public static void SelfTest() {
        var modules=new List<Module>{new Module{Pid=728,Base=0x1000,Size=0x100,Name="lsasrv.dll"},
            new Module{Pid=42,Base=0x1000,Size=0x100,Name="other.dll"}};
        if(Resolve(new Sample{Pid=728,Pc=0x1020},modules).Name!="lsasrv.dll")throw new Exception("Owner/module selection");
        if(Resolve(new Sample{Pid=999,Pc=0x1020},modules)!=null)throw new Exception("Foreign module accepted");
        if(Resolve(new Sample{Pid=728,Pc=0x1100},modules)!=null)throw new Exception("Module upper bound accepted");
    }
}
