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

/** Keeps the AVF-owning process foreground while its user-started VM runs.
 * Display Surfaces belong to the Activity and can be replaced independently. */
public final class VmRuntimeService extends Service {
    private static volatile Object activeVm;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long started;

    static void keepAlive(Context context,Object vm) {
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
            .setContentText("Ubuntu keeps running in the background. Tap to return.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build();
        startForeground(4055,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        started=android.os.SystemClock.elapsedRealtime();
        handler.postDelayed(check,15000);
    }

    private final Runnable check=new Runnable() {
        public void run() {
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
        // Never resurrect or auto-boot a workspace following process death.
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
