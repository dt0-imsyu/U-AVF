package com.example.winavf;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Diagnostic shell AVF owner. AVF socket delegation, never raw AF_VSOCK. */
public final class AvfOwnerBridge {
    private static final String ROOT="/data/local/tmp/uavf-windows-uart-gas-20261004";
    static final String INTERFACE="com.example.uavfwinpetest.AvfDisplayOwner";
    private static Object serviceHolder,vm;
    private static final PrintStream progress=new PrintStream(new FileOutputStream(FileDescriptor.out),true);
    private static Object call(Object o,String name,Class<?> type,Object arg)throws Exception {
        return o.getClass().getMethod(name,type).invoke(o,arg);
    }
    private static Object call(Object o,String name)throws Exception {
        return o.getClass().getMethod(name).invoke(o);
    }
    private static String sha(File f)throws Exception {
        MessageDigest h=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(f)){byte[] b=new byte[1048576];int n;while((n=in.read(b))!=-1)h.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:h.digest())s.append(String.format("%02X",b));return s.toString();
    }
    private static Bundle providerCall(String operation,Bundle bundle)throws Exception {
        // Same authorized shell-provider acquisition as AOSP's `content` tool.
        // Use the actual shell attribution, not systemContext's android package.
        String authority="com.example.uavfwinpetest.owner";
        Class<?> am=Class.forName("android.app.ActivityManager"),api=Class.forName("android.app.IActivityManager");
        Object service=am.getMethod("getService").invoke(null);Binder lease=new Binder();Object provider=null;
        try {
            Object holder=api.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class)
                .invoke(service,authority,0,lease,"*uavf-diagnostic-owner*");
            if(holder==null)throw new IOException("Diagnostic provider unavailable");
            provider=holder.getClass().getField("provider").get(holder);
            android.content.AttributionSource caller=new android.content.AttributionSource.Builder(android.os.Process.myUid())
                .setPackageName("com.android.shell").build();
            return (Bundle)Class.forName("android.content.IContentProvider").getMethod("call",android.content.AttributionSource.class,
                String.class,String.class,String.class,Bundle.class).invoke(provider,caller,authority,operation,null,bundle);
        } finally {
            if(provider!=null)api.getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class)
                .invoke(service,authority,lease,0);
        }
    }
    public static void main(String[] args)throws Exception {
        try{run(args);System.exit(0);}catch(Throwable e){
            Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();
            progress.println("H:OWNER_FATAL="+cause);throw e;
        }
    }
    private static void run(String[] args)throws Exception {
        boolean preflight=args.length==3&&"--preflight".equals(args[2]);
        boolean explicitCandidate=args.length==4&&"--candidate-sha".equals(args[2])&&args[3].matches("[0-9A-F]{64}");
        if(android.os.Process.myUid()!=2000||(args.length!=2&&!preflight&&!explicitCandidate)||!args[0].matches("[0-9a-f]{64}"))
            throw new SecurityException("Explicit shell diagnostic owner and token required");
        int limit=Integer.parseInt(args[1]);if(limit!=180&&limit!=300)throw new IllegalArgumentException("Bounded lifetime required");
        String token=args[0];
        String expectedDisk=explicitCandidate?args[3]:"A40B5E60589F97DE86DAB5C04BC999871B3F8B922020F4D532B8FCC06D7ACCA5";
        File disk=new File(ROOT,"windows-kd.img"),kernel=new File(ROOT,"u-boot-wrapper-v24.Image");
        if(!preflight&&(disk.length()!=9126805504L||!sha(disk).equals(expectedDisk)))
            throw new SecurityException("Only pristine disposable Windows candidate accepted");
        if(!sha(kernel).equals("93EDA7C4BD54C33F85ADA6F05158C74EC6F5C3232F5CBA73846E5B442895F234"))throw new SecurityException("Wrapper differs");
        Looper.prepareMainLooper();
        Class<?> at=Class.forName("android.app.ActivityThread");
        Object thread=at.getMethod("systemMain").invoke(null);
        Context systemContext=(Context)at.getMethod("getSystemContext").invoke(thread);
        Context context=systemContext.createPackageContext("com.android.shell",0);
        if(context.getApplicationInfo().uid!=android.os.Process.myUid())throw new SecurityException("Context must match actual owner UID");
        Class<?> custom=Class.forName("android.system.virtualmachine.VirtualMachineCustomImageConfig");
        Object cb=Class.forName(custom.getName()+"$Builder").getConstructor().newInstance();
        call(cb,"setName",String.class,"VmRun");call(cb,"setOsName",String.class,"");
        call(cb,"setKernelPath",String.class,kernel.getAbsolutePath());
        call(cb,"useNetwork",boolean.class,false);call(cb,"useAutoMemoryBalloon",boolean.class,true);
        Class<?> dc=Class.forName(custom.getName()+"$Disk");
        call(cb,"addDisk",dc,dc.getMethod("RWDisk",String.class).invoke(null,disk.getAbsolutePath()));
        Object ci=call(cb,"build");Class<?> config=Class.forName("android.system.virtualmachine.VirtualMachineConfig");
        Object builder=Class.forName(config.getName()+"$Builder").getConstructor(Context.class).newInstance(context);
        call(builder,"setProtectedVm",boolean.class,false);call(builder,"setMemoryBytes",long.class,4294967296L);
        call(builder,"setCpuTopology",int.class,config.getField("CPU_TOPOLOGY_ONE_CPU").getInt(null));
        call(builder,"setConsoleInputDevice",String.class,"ttyS0");call(builder,"setCustomImageConfig",custom,ci);
        Method rawMethod=config.getDeclaredMethod("toVsRawConfig");rawMethod.setAccessible(true);
        Object raw=rawMethod.invoke(call(builder,"build"));
        // VirtualMachine.run normally fills this non-null AIDL array after
        // toVsRawConfig; match that framework step even without input devices.
        Class<?> inputDevice=Class.forName("android.system.virtualizationservice.InputDevice");
        raw.getClass().getField("inputDevices").set(raw,java.lang.reflect.Array.newInstance(inputDevice,0));
        Class<?> parcelConfig=Class.forName("android.system.virtualizationservice.VirtualMachineConfig");
        Object parcel=parcelConfig.getMethod("rawConfig",raw.getClass()).invoke(null,raw);
        Class<?> owner=Class.forName("android.system.virtualmachine.VirtualizationService");
        Method instance=owner.getDeclaredMethod("getInstance");instance.setAccessible(true);serviceHolder=instance.invoke(null);
        Method getBinder=owner.getDeclaredMethod("getBinder");getBinder.setAccessible(true);Object service=getBinder.invoke(serviceHolder);
        progress.println("H:AVF_SERVICE_READY");
        Class<?> serviceApi=Class.forName("android.system.virtualizationservice.IVirtualizationService");
        Class<?> vmApi=Class.forName("android.system.virtualizationservice.IVirtualMachine");
        Method create=serviceApi.getMethod("createVm",parcelConfig,ParcelFileDescriptor.class,ParcelFileDescriptor.class,ParcelFileDescriptor.class,ParcelFileDescriptor.class);
        if(preflight){
            Bundle bundle=new Bundle();bundle.putString("token","preflight-invalid-token");bundle.putBinder("owner",new Binder());
            Bundle result=providerCall("register_owner",bundle);
            if(result==null||result.getBoolean("accepted"))throw new SecurityException("Invalid owner must be rejected");
            Binder probe=new Binder(){
                @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
                    if(code!=1)return super.onTransact(code,data,reply,flags);
                    data.enforceInterface(INTERFACE);
                    if(!"preflight-fd-probe".equals(data.readString())||data.readInt()!=4055)throw new SecurityException("Wrong probe");
                    try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(new File("/dev/null"),ParcelFileDescriptor.MODE_READ_ONLY)){
                        reply.writeNoException();fd.writeToParcel(reply,Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                    }catch(IOException e){reply.writeException(new IllegalStateException(e));}
                    return true;
                }
            };
            bundle=new Bundle();bundle.putString("token","preflight-fd-probe");bundle.putBinder("owner",probe);
            Bundle probeResult=providerCall("probe_fd",bundle);
            if(probeResult==null||!probeResult.getBoolean("fd_roundtrip"))throw new IOException("Descriptor unit test failed");
            progress.println("H:FD_ROUNDTRIP_UNIT_PASS_NO_VSOCK_OR_VIDEO");
            // Duplex IPC unit test only: no VM and no AF_VSOCK connection.
            final int probeUid=context.getPackageManager().getApplicationInfo("com.example.uavfwinpetest",0).uid;
            try {
                final ParcelFileDescriptor[] pair=ParcelFileDescriptor.createSocketPair();
                try(ParcelFileDescriptor local=pair[0];ParcelFileDescriptor remote=pair[1]) {
                    byte[] challenge="UAVF-FD-CHALLENGE".getBytes(StandardCharsets.US_ASCII);
                    android.system.Os.write(local.getFileDescriptor(),challenge,0,challenge.length);
                    Binder duplex=new Binder(){
                        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
                            if(code!=1)return super.onTransact(code,data,reply,flags);
                            if(Binder.getCallingUid()!=probeUid)throw new SecurityException("Wrong probe UID");
                            data.enforceInterface(INTERFACE);
                            if(!"preflight-duplex-probe".equals(data.readString())||data.readInt()!=4055)throw new SecurityException("Wrong duplex probe");
                            reply.writeNoException();remote.writeToParcel(reply,Parcelable.PARCELABLE_WRITE_RETURN_VALUE);return true;
                        }
                    };
                    Bundle request=new Bundle();request.putString("token","preflight-duplex-probe");request.putBinder("owner",duplex);
                    Bundle response=providerCall("probe_duplex",request);
                    if(response==null||!response.getBoolean("duplex_roundtrip"))throw new IOException("Client duplex test failed");
                    android.system.StructPollfd poll=new android.system.StructPollfd();poll.fd=local.getFileDescriptor();poll.events=(short)android.system.OsConstants.POLLIN;
                    if(android.system.Os.poll(new android.system.StructPollfd[]{poll},5000)!=1)throw new IOException("Owner duplex reply timeout");
                    byte[] received=new byte[16];int n=android.system.Os.read(local.getFileDescriptor(),received,0,received.length);
                    if(n!=16||!"UAVF-FD-RESPONSE".equals(new String(received,StandardCharsets.US_ASCII)))throw new IOException("Owner duplex reply differs: "+n);
                    progress.println("H:DUPLEX_BINDER_SOCKET_UNIT_PASS_NO_VM_NO_VSOCK");
                }
            }catch(Exception e){throw new IOException("Duplex Binder socket unit failed",e);}
            progress.println("H:PREFLIGHT_PASS_NO_VM_CREATED_PROVIDER_REJECTS_INVALID_TOKEN");return;
        }
        File ready=new File(ROOT,"owner-display-ready");if(ready.exists()&&!ready.delete())throw new IOException("Stale ready marker");
        File stop=new File(ROOT,"owner-stop-requested");if(stop.exists()&&!stop.delete())throw new IOException("Stale stop marker");
        ParcelFileDescriptor output=ParcelFileDescriptor.open(new File(ROOT,"owner-serial.bin"),ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY);
        ParcelFileDescriptor input=ParcelFileDescriptor.fromFd(0);
        ParcelFileDescriptor log=ParcelFileDescriptor.open(new File(ROOT,"owner-crosvm.log"),ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY);
        final int clientUid=context.getPackageManager().getApplicationInfo("com.example.uavfwinpetest",0).uid;
        try {
            vm=create.invoke(service,parcel,output,input,log,null);
            final Object handle=vm;
            Binder delegate=new Binder(){
                private boolean recordedRequest,recordedFailure;
                @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
                    if(code!=1)return super.onTransact(code,data,reply,flags);
                    if(Binder.getCallingUid()!=clientUid)throw new SecurityException("Wrong viewer UID");
                    data.enforceInterface(INTERFACE);
                    if(!token.equals(data.readString())||data.readInt()!=4055)throw new SecurityException("Wrong channel request");
                    if(!recordedRequest){recordedRequest=true;progress.println("H:AVF_VSOCK_REQUEST client_uid="+Binder.getCallingUid()+" owner_uid="+android.os.Process.myUid());}
                    // Authorized VM-owner delegation: nested AVF operation must
                    // use this owner's identity, not the requesting viewer UID.
                    long identity=Binder.clearCallingIdentity();
                    try {
                        ParcelFileDescriptor fd=(ParcelFileDescriptor)vmApi.getMethod("connectVsock",int.class).invoke(handle,4055);
                        progress.println("H:AVF_VSOCK_FD_READY port=4055");
                        try {reply.writeNoException();fd.writeToParcel(reply,Parcelable.PARCELABLE_WRITE_RETURN_VALUE);}finally{fd.close();}
                    }catch(Exception e){
                        if(!recordedFailure){recordedFailure=true;Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();progress.println("H:AVF_VSOCK_FIRST_FAILURE="+cause);}
                        reply.writeException(new IllegalStateException("AVF owner connect failed",e));
                    }finally{Binder.restoreCallingIdentity(identity);}
                    return true;
                }
            };
            vmApi.getMethod("start").invoke(vm);
            progress.println("H:AVF_OWNER_RUNNING");
            boolean published=false;long end=android.os.SystemClock.elapsedRealtime()+limit*1000L;
            while(android.os.SystemClock.elapsedRealtime()<end&&!stop.exists()) {
                if(!published&&ready.exists()) {
                    Bundle bundle=new Bundle();bundle.putString("token",token);bundle.putBinder("owner",delegate);
                    Bundle result=providerCall("register_owner",bundle);
                    if(result==null||!result.getBoolean("accepted"))throw new SecurityException("Viewer rejected owner");
                    published=true;progress.println("H:AVF_OWNER_DELEGATED");
                }
                Thread.sleep(100);
            }
        } finally {
            if(vm!=null)try{vmApi.getMethod("stop").invoke(vm);}catch(Exception ignored){}
            output.close();input.close();log.close();
        }
    }
}
