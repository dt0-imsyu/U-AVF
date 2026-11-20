// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.function.IntConsumer;

/** Shared AVF launch path for the verified Windows serial-console configuration. */
final class WindowsAvfOwner {
    // Retain the platform owner for the lifetime of its Binder and VM.
    private final Object serviceHolder;
    private final Object service;
    private final Object parcel;
    private final Method create;
    private final Method start;
    private final Method stop;
    private final Method connectVsock;
    private final Method getState;
    private final int deadState;
    private Binder lifecycleCallback;

    static Object rawConfig(Context context, File wrapper, File disk, long memoryBytes,
                            boolean matchHostCpus) throws ReflectiveOperationException {
        Class<?> custom = Class.forName("android.system.virtualmachine.VirtualMachineCustomImageConfig");
        Object customBuilder = Class.forName(custom.getName() + "$Builder").getConstructor().newInstance();
        set(customBuilder, "setName", String.class, "VmRun");
        set(customBuilder, "setOsName", String.class, "");
        set(customBuilder, "setKernelPath", String.class, wrapper.getAbsolutePath());
        set(customBuilder, "useNetwork", boolean.class, false);
        set(customBuilder, "useAutoMemoryBalloon", boolean.class, true);
        Class<?> diskType = Class.forName(custom.getName() + "$Disk");
        Object writableDisk = diskType.getMethod("RWDisk", String.class).invoke(null, disk.getAbsolutePath());
        set(customBuilder, "addDisk", diskType, writableDisk);
        Object image = customBuilder.getClass().getMethod("build").invoke(customBuilder);

        Class<?> config = Class.forName("android.system.virtualmachine.VirtualMachineConfig");
        Object builder = Class.forName(config.getName() + "$Builder").getConstructor(Context.class)
                .newInstance(context);
        set(builder, "setProtectedVm", boolean.class, false);
        set(builder, "setMemoryBytes", long.class, memoryBytes);
        int topology = config.getField(matchHostCpus ? "CPU_TOPOLOGY_MATCH_HOST" : "CPU_TOPOLOGY_ONE_CPU")
                .getInt(null);
        set(builder, "setCpuTopology", int.class, topology);
        set(builder, "setConsoleInputDevice", String.class, "ttyS0");
        set(builder, "setCustomImageConfig", custom, image);
        Method toRaw = config.getDeclaredMethod("toVsRawConfig");
        toRaw.setAccessible(true);
        Object raw = toRaw.invoke(builder.getClass().getMethod("build").invoke(builder));
        Class<?> inputDevice = Class.forName("android.system.virtualizationservice.InputDevice");
        raw.getClass().getField("inputDevices").set(raw, Array.newInstance(inputDevice, 0));
        return raw;
    }

    WindowsAvfOwner(Object raw) throws ReflectiveOperationException {
        Class<?> parcelType = Class.forName("android.system.virtualizationservice.VirtualMachineConfig");
        parcel = parcelType.getMethod("rawConfig", raw.getClass()).invoke(null, raw);
        Class<?> owner = Class.forName("android.system.virtualmachine.VirtualizationService");
        Method instance = owner.getDeclaredMethod("getInstance");
        instance.setAccessible(true);
        serviceHolder = instance.invoke(null);
        Method binder = owner.getDeclaredMethod("getBinder");
        binder.setAccessible(true);
        service = binder.invoke(serviceHolder);

        Class<?> serviceApi = Class.forName("android.system.virtualizationservice.IVirtualizationService");
        Class<?> vmApi = Class.forName("android.system.virtualizationservice.IVirtualMachine");
        connectVsock = vmApi.getMethod("connectVsock", int.class);
        stop = vmApi.getMethod("stop");
        getState = vmApi.getMethod("getState");
        deadState = Class.forName("android.system.virtualizationservice.VirtualMachineState")
                .getField("DEAD").getInt(null);
        create = serviceApi.getMethod("createVm", parcelType, ParcelFileDescriptor.class,
                ParcelFileDescriptor.class, ParcelFileDescriptor.class, ParcelFileDescriptor.class);
        start = vmApi.getMethod("start");
    }

    Object create(ParcelFileDescriptor serialOut, ParcelFileDescriptor serialIn,
                  ParcelFileDescriptor log) throws ReflectiveOperationException {
        return create.invoke(service, parcel, serialOut, serialIn, log, null);
    }

    void start(Object vm) throws ReflectiveOperationException { start.invoke(vm); }

    void observeDeath(Object vm, IntConsumer callback) throws ReflectiveOperationException {
        Class<?> api = Class.forName("android.system.virtualizationservice.IVirtualMachineCallback");
        Class<?> stub = Class.forName(api.getName() + "$Stub");
        var field = stub.getDeclaredField("TRANSACTION_onDied");
        field.setAccessible(true);
        int onDied = field.getInt(null);
        String descriptor = api.getName();
        lifecycleCallback = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code != onDied) return super.onTransact(code, data, reply, flags);
                data.enforceInterface(descriptor);
                data.readInt(); // CID of this callback's VM.
                int reason = data.readInt();
                data.enforceNoDataAvail();
                callback.accept(reason);
                return true;
            }
        };
        lifecycleCallback.attachInterface(null, descriptor);
        Object proxy = stub.getMethod("asInterface", IBinder.class).invoke(null, lifecycleCallback);
        Class.forName("android.system.virtualizationservice.IVirtualMachine")
                .getMethod("registerCallback", api).invoke(vm, proxy);
    }

    void stop(Object vm) throws ReflectiveOperationException { stop.invoke(vm); }

    boolean hasExited(Object vm) throws ReflectiveOperationException {
        return ((Number) getState.invoke(vm)).intValue() == deadState;
    }

    ParcelFileDescriptor connectVsock(Object vm, int port) throws ReflectiveOperationException {
        return (ParcelFileDescriptor) connectVsock.invoke(vm, port);
    }

    private static void set(Object target, String name, Class<?> type, Object value)
            throws ReflectiveOperationException {
        target.getClass().getMethod(name, type).invoke(target, value);
    }
}
