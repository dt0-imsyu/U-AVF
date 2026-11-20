param(
    [Parameter(Mandatory = $true)] [string] $AdbPath,
    [Parameter(Mandatory = $true)] [string] $RemoteRunner,
    [Parameter(Mandatory = $true)] [string] $ArtifactDir,
    [Parameter(Mandatory = $true)] [string] $KdPath,
    [string] $PipeName = 'winavf-kd-framed-com1-20260907',
    [ValidateRange(10,600)] [int] $TimeoutSeconds = 115,
    [switch] $ImmediateFrames,
    [switch] $UnlimitedResets,
    [string] $KdCommands = '',
    [string] $GateMarker = '',
    [string] $KdCommandFile = '',
    [ValidateSet('attach','module')] [string] $InitialBreakMode = 'attach'
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $ArtifactDir | Out-Null
Add-Type -TypeDefinition @'
using System;
using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
public static class WinAvfFramedKdBridge {
  static volatile bool gateOpen;
  static ManualResetEventSlim gateReady;
  static string gateMarker;
  static string gateWindow;
  static void LogCopy(Stream src, string file) { using(var f=new FileStream(file,FileMode.Create,FileAccess.Write,FileShare.Read)) { byte[] b=new byte[4096]; int n; while((n=src.Read(b,0,b.Length))>0){f.Write(b,0,n);f.Flush();} } }
  static void EncodeToAdb(Stream src, Stream dst, string file, bool immediate) {
    using(var log=new FileStream(file,FileMode.Create,FileAccess.Write,FileShare.Read)) {
      byte[] read=new byte[4096], pending=new byte[4098]; int carry=0,n;
      while((n=src.Read(read,0,read.Length))>0) {
        // Do not let KD synchronization keys interrupt U-Boot autoboot.
        // Hold the initial KD handshake until Windows starts; never drop it.
        gateReady.Wait();
        if(immediate) {
          log.Write(read,0,n);log.Flush();
          byte[] record=Encoding.ASCII.GetBytes(Convert.ToBase64String(read,0,n)+"\n");
          dst.Write(record,0,record.Length);dst.Flush();continue;
        }
        Buffer.BlockCopy(read,0,pending,carry,n); int total=carry+n, usable=total-total%3;
        if(usable>0) { log.Write(pending,0,usable); log.Flush(); byte[] ascii=Encoding.ASCII.GetBytes(Convert.ToBase64String(pending,0,usable)); dst.Write(ascii,0,ascii.Length); dst.Flush(); }
        carry=total-usable; if(carry>0) Buffer.BlockCopy(pending,usable,pending,0,carry);
      }
      if(carry>0) { log.Write(pending,0,carry); byte[] ascii=Encoding.ASCII.GetBytes(Convert.ToBase64String(pending,0,carry)); dst.Write(ascii,0,ascii.Length); dst.Flush(); }
    }
    try { dst.Close(); } catch {}
  }
  static void DecodeFromAdb(Stream src, Stream dst, string file, string status) {
    using(var log=new FileStream(file,FileMode.Create,FileAccess.Write,FileShare.Read)) using(var r=new StreamReader(src,Encoding.ASCII,false,4096)) {
      string line; while((line=r.ReadLine())!=null) { if(!line.StartsWith("O:")){File.AppendAllText(status,"UNEXPECTED_STDOUT="+line+"\n");continue;} try {
        byte[] b=Convert.FromBase64String(line.Substring(2)); log.Write(b,0,b.Length);log.Flush();
        if(!gateOpen) {
          int previousLength=gateWindow.Length;
          gateWindow+=Encoding.ASCII.GetString(b);
          int markerAt=gateWindow.IndexOf(gateMarker,StringComparison.Ordinal);
          if(markerAt>=0) {
            int afterMarker=markerAt+gateMarker.Length-previousLength;
            gateOpen=true;gateReady.Set();
            File.AppendAllText(status,"TX_GATE_OPEN_UTC="+DateTime.UtcNow.ToString("O")+"\n");
            // A serial read can contain both the marker and the first KD packet.
            if(afterMarker<b.Length){dst.Write(b,afterMarker,b.Length-afterMarker);dst.Flush();}
          } else {if(gateWindow.Length>512)gateWindow=gateWindow.Substring(gateWindow.Length-512);}
          continue;
        }
        dst.Write(b,0,b.Length);dst.Flush();
      } catch(FormatException e) { File.AppendAllText(status,"INVALID_OUTPUT_RECORD="+e.Message+"\n"); }
        catch(IOException e) { File.AppendAllText(status,"PIPE_RX_ERROR="+e.Message+"\n"); } }
    }
  }
  public static int Run(string pipeName,string kdPath,string kdArgs,string adbPath,string runner,string artifactDir,int timeoutSeconds,bool immediate,string gate,string commandFile) {
    gateMarker=gate;gateWindow="";gateOpen=String.IsNullOrEmpty(gate);
    gateReady=new ManualResetEventSlim(gateOpen);
    string status=Path.Combine(artifactDir,"bridge-status.txt");
    using(var pipe=new NamedPipeServerStream(pipeName,PipeDirection.InOut,1,PipeTransmissionMode.Byte,PipeOptions.Asynchronous)) {
      File.WriteAllText(status,"PIPE_LISTENING_UTC="+DateTime.UtcNow.ToString("O")+"\n");
      var kdInfo=new ProcessStartInfo(); kdInfo.FileName=kdPath; kdInfo.Arguments=kdArgs; kdInfo.UseShellExecute=false; kdInfo.CreateNoWindow=true;
      kdInfo.RedirectStandardInput=!String.IsNullOrEmpty(commandFile);
      var kd=Process.Start(kdInfo);
      if(kdInfo.RedirectStandardInput)Task.Run(()=> {
        int sent=0;kd.StandardInput.AutoFlush=true;
        while(!kd.HasExited) {
          try {
            if(File.Exists(commandFile)) {
              string[] lines=File.ReadAllLines(commandFile);
              while(sent<lines.Length && !kd.HasExited) {
                string command=lines[sent++];if(String.IsNullOrWhiteSpace(command))continue;
                kd.StandardInput.WriteLine(command);
                File.AppendAllText(Path.Combine(artifactDir,"kd-input-status.txt"),command+"\n");
              }
            }
          }catch(IOException){}
          Task.Delay(100).Wait();
        }
      });
      pipe.WaitForConnection(); File.AppendAllText(status,"KD_PIPE_CONNECTED_UTC="+DateTime.UtcNow.ToString("O")+"\n");
      var adb=Process.Start(new ProcessStartInfo(adbPath,"shell -T sh "+runner){UseShellExecute=false,CreateNoWindow=true,RedirectStandardInput=true,RedirectStandardOutput=true,RedirectStandardError=true});
      File.AppendAllText(status,"ADB_VM_LAUNCH_UTC="+DateTime.UtcNow.ToString("O")+"\n");
      var tx=Task.Run(()=>EncodeToAdb(pipe,adb.StandardInput.BaseStream,Path.Combine(artifactDir,"raw-serial-tx.bin"),immediate));
      var rx=Task.Run(()=>DecodeFromAdb(adb.StandardOutput.BaseStream,pipe,Path.Combine(artifactDir,"raw-serial-rx.bin"),status));
      tx.ContinueWith(t=>File.AppendAllText(status,"TX_TASK_FAULT="+t.Exception.ToString()+"\n"),TaskContinuationOptions.OnlyOnFaulted);
      rx.ContinueWith(t=>File.AppendAllText(status,"RX_TASK_FAULT="+t.Exception.ToString()+"\n"),TaskContinuationOptions.OnlyOnFaulted);
      var err=Task.Run(()=>LogCopy(adb.StandardError.BaseStream,Path.Combine(artifactDir,"adb.stderr.txt")));
      adb.WaitForExit(timeoutSeconds*1000); if(!adb.HasExited){adb.Kill();File.AppendAllText(status,"ADB_TIMEOUT_KILLED=1\n");}
      gateReady.Set();
      // Release blocked pipe readers/writers before waiting for their tasks.
      if(!kd.HasExited){kd.Kill();File.AppendAllText(status,"KD_STOPPED_AFTER_RUN=1\n");}
      try{Task.WaitAll(new Task[]{tx,rx,err},10000);}catch{}
      File.AppendAllText(status,"ADB_EXIT="+adb.ExitCode+"\n"); if(!kd.WaitForExit(5000)){kd.Kill();File.AppendAllText(status,"KD_STOPPED_AFTER_RUN=1\n");} return adb.ExitCode;
    }
  }
}
'@
$target="com:pipe,port=\\.\pipe\$PipeName,baud=115200"
if($UnlimitedResets){$target+=",resets=0"}
Set-Content -LiteralPath (Join-Path $ArtifactDir 'kd-transport.txt') -Value $target
$initialBreak=if($InitialBreakMode -eq 'module'){'-d'}else{'-b'}
$args="$initialBreak -logo `"$(Join-Path $ArtifactDir 'kd.log.txt')`" -k `"$target`""
if($KdCommands){$args += " -c `"$KdCommands`""}
[WinAvfFramedKdBridge]::Run($PipeName,$KdPath,$args,$AdbPath,$RemoteRunner,$ArtifactDir,$TimeoutSeconds,[bool]$ImmediateFrames,$GateMarker,$KdCommandFile)
