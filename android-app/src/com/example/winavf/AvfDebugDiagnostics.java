// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Records the framework surface available to the app in diagnostic builds. */
final class AvfDebugDiagnostics {
    private static final String AVF = "android.system.virtualmachine.";
    private static final String MANAGE_PERMISSION = "android.permission.MANAGE_VIRTUAL_MACHINE";
    private static final String CUSTOM_PERMISSION = "android.permission.USE_CUSTOM_VIRTUAL_MACHINE";
    private static final String CONSOLE_VM = "winavf-console-input-probe-20260909";
    private static final Pattern RELEVANT = Pattern.compile(
            "disk|display|graphics|gpu|surface|console|input|keyboard|mouse|touch|virtio|socket|vsock");
    private static final String[] TYPES = {
            "VirtualMachineManager", "VirtualMachine", "VirtualMachineConfig", "VirtualMachineConfig$Builder",
            "VirtualMachineCustomImageConfig", "VirtualMachineCustomImageConfig$Builder",
            "VirtualMachineCustomImageConfig$Disk", "VirtualMachineCustomImageConfig$DisplayConfig",
            "VirtualMachineCustomImageConfig$DisplayConfig$Builder", "VirtualMachineCustomImageConfig$GpuConfig",
            "VirtualMachineCustomImageConfig$GpuConfig$Builder", "VirtualMachineDescriptor", "VirtualMachineCallback"
    };
    private final Context context;
    private final GuestMediaFiles media;
    private final Consumer<String> show;

    AvfDebugDiagnostics(Context context, GuestMediaFiles media, Consumer<String> show) {
        this.context = context;
        this.media = media;
        this.show = show;
    }

    void auditCapabilities() {
        File report = report("avf-capability-audit.txt");
        try (var log = new PrintWriter(new FileOutputStream(report, false))) {
            log.println("fingerprint=" + Build.FINGERPRINT);
            log.println("sdk=" + Build.VERSION.SDK_INT);
            for (String name : TYPES) inspectType(log, AVF + name);
            for (String name : new String[]{MANAGE_PERMISSION, CUSTOM_PERMISSION, "android.permission.VIRTUAL_INPUT_DEVICE"}) {
                var info = context.getPackageManager().getPermissionInfo(name, 0);
                log.println("PERMISSION " + name + " check=" + context.checkSelfPermission(name)
                        + " protection=" + info.protectionLevel);
            }
            show.accept("AVF capability audit saved to " + report.getAbsolutePath());
        } catch (Exception error) {
            show.accept("AVF capability audit failed: " + IoUtil.rootMessage(error));
        }
    }

    private static void inspectType(PrintWriter log, String name) {
        try {
            Class<?> type = Class.forName(name);
            log.println("CLASS " + name + " AVAILABLE");
            writeMembers(log, "CONSTRUCTOR", type.getDeclaredConstructors(), false);
            writeMembers(log, "METHOD", type.getDeclaredMethods(), true);
            writeMembers(log, "FIELD", type.getDeclaredFields(), true);
        } catch (Exception | LinkageError error) {
            log.println("CLASS " + name + " UNAVAILABLE " + IoUtil.rootMessage(error));
        }
    }

    private static void writeMembers(PrintWriter log, String label, Object[] members, boolean filter) {
        Arrays.sort(members, Comparator.comparing(Object::toString));
        for (Object member : members) {
            String description = member.toString();
            if (!filter || RELEVANT.matcher(description.toLowerCase(Locale.ROOT)).find()) {
                log.println("  " + label + " " + description);
            }
        }
    }

    void auditDisplayAccess() {
        File report = report("native-avf-display-access-audit.txt");
        try (var log = new PrintWriter(new FileOutputStream(report, false))) {
            log.println("fingerprint=" + Build.FINGERPRINT);
            log.println("uid=" + Process.myUid());
            log.println("permission.MANAGE_VIRTUAL_MACHINE=" + context.checkSelfPermission(MANAGE_PERMISSION));
            log.println("permission.USE_CUSTOM_VIRTUAL_MACHINE=" + context.checkSelfPermission(CUSTOM_PERMISSION));
            Class<?> services = Class.forName("android.os.ServiceManager");
            log.println("CLASS android.os.ServiceManager=AVAILABLE");
            var getService = services.getMethod("getService", String.class);
            log.println("METHOD ServiceManager.getService=AVAILABLE");
            Object binder = getService.invoke(null, "android.system.virtualizationservice");
            log.println("BINDER android.system.virtualizationservice="
                    + (binder == null ? "NULL" : "AVAILABLE:" + binder.getClass().getName()));
            auditDisplayClasses(log);
            log.println("RESULT=READ_ONLY_HOST_PATH_AUDIT_COMPLETE");
            show.accept("Native AVF display access audit saved to " + report.getAbsolutePath());
        } catch (Exception error) {
            show.accept("Native AVF display access audit failed: " + IoUtil.rootMessage(error));
        }
    }

    private static void auditDisplayClasses(PrintWriter log) {
        String[] types = {
                "android.system.virtualizationservice_internal.IVirtualizationServiceInternal",
                "android.crosvm.ICrosvmAndroidDisplayService"
        };
        for (String type : types) {
            for (String name : new String[]{type, type + "$Stub"}) {
                try {
                    Class.forName(name);
                    log.println("CLASS " + name + "=AVAILABLE");
                } catch (Exception | LinkageError error) {
                    log.println("CLASS " + name + "=UNAVAILABLE:" + IoUtil.rootMessage(error));
                }
            }
        }
    }

    void probeConsoleInput() {
        File report = report("console-input-api-probe.txt");
        boolean created = false;
        try (var log = new PrintWriter(new FileOutputStream(report, false))) {
            log.println("scope=NO_GUEST_RUN_NO_DISK_NO_PRODUCT_MEDIA");
            Object config = consoleConfig();
            boolean enabled = Boolean.TRUE.equals(config.getClass().getMethod("isVmConsoleInputSupported").invoke(config));
            log.println("config.isVmConsoleInputSupported=" + enabled);
            if (!enabled) throw new IllegalStateException("Console-input flag was not persisted in config");
            Object vm = AvfGuest.replaceStopped(context, CONSOLE_VM, config);
            created = true;
            try (var output = (OutputStream) vm.getClass().getMethod("getConsoleInput").invoke(vm)) {
                output.write(new byte[0]);
                output.flush();
            }
            log.println("getConsoleInput=OUTPUT_STREAM_USABLE");
            log.println("result=PASS");
            show.accept("Console input API probe: OutputStream acquired.");
        } catch (Exception error) {
            recordConsoleFailure(report, error);
        } finally {
            if (created) {
                try {
                    AvfGuest.removeStopped(context, CONSOLE_VM);
                } catch (Exception error) {
                    Log.w("U-AVF", "Could not remove disposable console input VM", error);
                }
            }
        }
    }

    private Object consoleConfig() throws Exception {
        File payload = new File(context.getFilesDir(), "payload");
        if (!payload.isDirectory() && !payload.mkdirs()) throw new IOException("Cannot create payload directory");
        String asset = "u-boot-wrapper-v24.Image";
        File kernel = media.copyAsset(asset, new File(payload, asset), -1);
        Class<?> imageType = Class.forName(AVF + "VirtualMachineCustomImageConfig");
        Object image = Class.forName(imageType.getName() + "$Builder").getConstructor().newInstance();
        set(image, "setName", String.class, CONSOLE_VM);
        set(image, "setOsName", String.class, "winavf-console-input-probe");
        set(image, "setKernelPath", String.class, kernel.getAbsolutePath());
        Object vm = Class.forName(AVF + "VirtualMachineConfig$Builder").getConstructor(Context.class).newInstance(context);
        set(vm, "setProtectedVm", boolean.class, false);
        set(vm, "setDebugLevel", int.class, 1);
        set(vm, "setConsoleInputDevice", String.class, "ttyS0");
        set(vm, "setVmConsoleInputSupported", boolean.class, true);
        set(vm, "setCustomImageConfig", imageType, image.getClass().getMethod("build").invoke(image));
        return vm.getClass().getMethod("build").invoke(vm);
    }

    private static void set(Object object, String method, Class<?> type, Object value) throws Exception {
        object.getClass().getMethod(method, type).invoke(object, value);
    }

    private void recordConsoleFailure(File report, Exception failure) {
        String message = IoUtil.rootMessage(failure);
        try (var log = new PrintWriter(new FileOutputStream(report, true))) {
            log.println("result=FAIL");
            log.println("error=" + message);
        } catch (IOException error) {
            Log.w("U-AVF", "Could not save console input probe failure", error);
        }
        show.accept("Console input API probe failed: " + message);
    }

    private File report(String name) { return new File(context.getExternalFilesDir(null), name); }
}
