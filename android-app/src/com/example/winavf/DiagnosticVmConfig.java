// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import java.io.File;

/** Builds the AVF configuration used by the historical single-disk boot diagnostics. */
final class DiagnosticVmConfig {
    private static final String AVF = "android.system.virtualmachine.";
    static final String WINDOWS_VM = "winavf-gop-ebs-r1";

    private DiagnosticVmConfig() {}

    static Object build(Context context, File kernel, File disk, boolean keyboard) throws Exception {
        return build(context, WINDOWS_VM, kernel, disk, keyboard, null);
    }

    static Object build(Context context, String name, File kernel, File disk, boolean keyboard,
                        String kernelParameters) throws Exception {
        Class<?> imageType = Class.forName(AVF + "VirtualMachineCustomImageConfig");
        Object image = imageConfig(imageType, name, kernel, disk, keyboard, kernelParameters);
        Class<?> vmType = Class.forName(AVF + "VirtualMachineConfig");
        Object vm = Class.forName(vmType.getName() + "$Builder").getConstructor(Context.class).newInstance(context);
        set(vm, "setProtectedVm", boolean.class, false);
        set(vm, "setMemoryBytes", long.class, 4L << 30);
        set(vm, "setCpuTopology", int.class, vmType.getField("CPU_TOPOLOGY_ONE_CPU").getInt(null));
        set(vm, "setDebugLevel", int.class, 1);
        set(vm, "setConsoleInputDevice", String.class, "ttyS0");
        set(vm, "setVmOutputCaptured", boolean.class, true);
        set(vm, "setVmConsoleInputSupported", boolean.class, true);
        set(vm, "setCustomImageConfig", imageType, image);
        return build(vm);
    }

    private static Object imageConfig(Class<?> type, String name, File kernel, File disk, boolean keyboard,
                                      String kernelParameters) throws Exception {
        String prefix = type.getName();
        Object image = newBuilder(prefix);
        set(image, "setName", String.class, name);
        set(image, "setOsName", String.class, "winavf");
        set(image, "setKernelPath", String.class, kernel.getAbsolutePath());
        if (kernelParameters != null && !kernelParameters.isEmpty()) {
            set(image, "addParam", String.class, kernelParameters);
        }
        set(image, "useNetwork", boolean.class, false);
        set(image, "useAutoMemoryBalloon", boolean.class, true);
        Class<?> diskType = Class.forName(prefix + "$Disk");
        Object volume = diskType.getMethod("RWDisk", String.class).invoke(null, disk.getAbsolutePath());
        set(image, "addDisk", diskType, volume);
        Object display = displayConfig(prefix);
        set(image, "setDisplayConfig", display.getClass(), display);
        if (keyboard) set(image, "useKeyboard", boolean.class, true);
        Object gpu = build(newBuilder(prefix + "$GpuConfig"));
        set(image, "setGpuConfig", gpu.getClass(), gpu);
        return build(image);
    }

    private static Object displayConfig(String prefix) throws Exception {
        Object display = newBuilder(prefix + "$DisplayConfig");
        set(display, "setWidth", int.class, 1280);
        set(display, "setHeight", int.class, 800);
        set(display, "setHorizontalDpi", int.class, 160);
        set(display, "setVerticalDpi", int.class, 160);
        set(display, "setRefreshRate", int.class, 60);
        return build(display);
    }

    private static Object newBuilder(String type) throws Exception {
        return Class.forName(type + "$Builder").getConstructor().newInstance();
    }

    private static void set(Object builder, String property, Class<?> type, Object value) throws Exception {
        builder.getClass().getMethod(property, type).invoke(builder, value);
    }

    private static Object build(Object builder) throws Exception {
        return builder.getClass().getMethod("build").invoke(builder);
    }
}
