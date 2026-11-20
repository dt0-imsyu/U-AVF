package com.example.winavf;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.example.winavf.runtime.VmConsole;

/** Keeps the AVF-owning process foreground while its user-started VM runs.
 * Display Surfaces belong to the Activity and can be replaced independently. */
public final class VmRuntimeService extends Service {
    private static volatile Object activeVm;
    private static volatile WindowsSetupRuntime windowsSetup;
    private static final VmConsole ubuntuConsole = new VmConsole();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long started;

    static WindowsSetupRuntime windowsSetup() { return windowsSetup; }
    static VmConsole ubuntuConsole() { return ubuntuConsole; }

    static synchronized boolean startWindowsSetup(Context context, WindowsSetupRuntime.Boot boot) {
        if (activeVm != null || windowsSetupBusy()) return false;
        WindowsSetupRuntime candidate = new WindowsSetupRuntime(context, boot);
        windowsSetup = candidate;
        try {
            context.startForegroundService(new Intent(context, VmRuntimeService.class));
            return true;
        } catch (RuntimeException failure) {
            windowsSetup = null;
            throw failure;
        }
    }

    static boolean windowsSetupBusy() {
        WindowsSetupRuntime setup=windowsSetup;
        return setup!=null && (setup.active() || setup.continueInstalled());
    }

    private void runWindowsSetup(WindowsSetupRuntime setup) {
        setup.start(() -> handler.post(() -> {
            if (windowsSetup != setup || !setup.continueInstalled()) return;
            WindowsSetupRuntime installed = new WindowsSetupRuntime(
                    this, WindowsSetupRuntime.Boot.INSTALLED, setup.restarting());
            windowsSetup = installed;
            runWindowsSetup(installed);
        }));
    }

    /** The running handle belongs to the service, not a recreated Activity's manager. */
    static Object runningVm() {
        Object vm=activeVm;
        if(vm==null)return null;
        try {
            int status=((Number)vm.getClass().getMethod("getStatus").invoke(vm)).intValue();
            return status==vm.getClass().getField("STATUS_RUNNING").getInt(null)?vm:null;
        } catch(Exception error) {
            throw new IllegalStateException("Could not verify retained VM owner",error);
        }
    }

    static synchronized void keepAlive(Context context,Object vm) {
        if (windowsSetupBusy())
            throw new IllegalStateException("Windows Setup already has an active owner");
        Object running=runningVm();
        if(running!=null && running!=vm)throw new IllegalStateException("A workspace already has a running owner");
        activeVm=vm;
        context.startForegroundService(new Intent(context,VmRuntimeService.class));
    }

    static void ended(Context context,Object vm) {
        if(activeVm==vm) {
            activeVm=null;
            context.stopService(new Intent(context,VmRuntimeService.class));
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel("vm_runtime","Running workspace",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,
            new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"vm_runtime")
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("U-AVF workspace active")
            .setContentText("Your workspace keeps running in the background. Tap to return.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build();
        startForeground(4055,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        started=android.os.SystemClock.elapsedRealtime();
        handler.postDelayed(check,15000);
    }

    private final Runnable check=new Runnable() {
        public void run() {
            if (windowsSetupBusy()) {
                handler.postDelayed(this,5000);
                return;
            }
            Object vm=activeVm;
            if(vm==null) {stopSelf();return;}
            try {
                int status=((Number)vm.getClass().getMethod("getStatus").invoke(vm)).intValue();
                int running=vm.getClass().getField("STATUS_RUNNING").getInt(null);
                if(status!=running && android.os.SystemClock.elapsedRealtime()-started>15000) {
                    activeVm=null;stopSelf();return;
                }
            } catch(Exception error) {
                android.util.Log.w("U-AVF-runtime","Could not query VM state",error);
            }
            handler.postDelayed(this,5000);
        }
    };

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        WindowsSetupRuntime setup = windowsSetup;
        if (setup != null && setup.active()) runWindowsSetup(setup);
        // Never resurrect or auto-boot a workspace following process death.
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        WindowsSetupRuntime setup = windowsSetup;
        if (setup != null) setup.stop();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
