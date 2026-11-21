// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.File;

/** Builds the Android AVF configuration for an Ubuntu boot. */
final class UbuntuVmConfig {
    private static final String FRAMEWORK = "android.system.virtualmachine.";
    static final String GENERIC_VM = "winavf-generic-ubuntu-24045";
    private static final String[] GFXSTREAM_CONTEXTS = {"gfxstream-gles", "gfxstream-vulkan", "gfxstream-composer"};

    record Media(File kernel, File writableDisk, File iso, File extraDisk) {}

    record Options(String name, long memoryBytes, String cpuTopology, int width, int height, int refreshHz,
                   boolean disableUdmabuf, boolean hostVirgl) {}

    private record Gpu(String backend, boolean vulkan, String[] contexts, String features) {}

    private UbuntuVmConfig() {}

    static Object build(Context context, SharedPreferences prefs, Intent intent, Media media, Options options)
            throws Exception {
        Class<?> imageType = Class.forName(FRAMEWORK + "VirtualMachineCustomImageConfig");
        Object image = builder(imageType, "");
        set(image, "setName", String.class, options.name());
        set(image, "setOsName", String.class, "ubuntu-24.04.5-stock");
        set(image, "setKernelPath", String.class, media.kernel().getAbsolutePath());
        set(image, "useNetwork", boolean.class, prefs.getBoolean("linux_internet_enabled", true));
        configureAudio(imageType, image, prefs);
        set(image, "useAutoMemoryBalloon", boolean.class, false);
        configureDisks(imageType, image, media);
        configureDisplay(imageType, image, options);
        configureGpu(imageType, image, gpu(prefs, intent, options));
        return vmConfig(context, options, imageType, finish(image));
    }

    private static void configureAudio(Class<?> imageType, Object image, SharedPreferences prefs) throws Exception {
        if (!prefs.getBoolean("linux_audio_output_enabled", true)
                || !prefs.getBoolean("linux_audio_avf_native_experimental", false)) return;
        Object audio = builder(imageType, "$AudioConfig");
        set(audio, "setUseSpeaker", boolean.class, true);
        set(audio, "setUseMicrophone", boolean.class, false);
        attach(image, "setAudioConfig", finish(audio));
    }

    private static void configureDisks(Class<?> imageType, Object image, Media media) throws Exception {
        Class<?> diskType = Class.forName(imageType.getName() + "$Disk");
        addDisk(image, diskType, media.writableDisk(), "RWDisk");
        if (media.iso() != null) addDisk(image, diskType, media.iso(), "RODisk");
        if (media.extraDisk() != null) addDisk(image, diskType, media.extraDisk(), "RODisk");
    }

    private static void addDisk(Object image, Class<?> diskType, File path, String factory) throws Exception {
        Object disk = diskType.getMethod(factory, String.class).invoke(null, path.getAbsolutePath());
        set(image, "addDisk", diskType, disk);
    }

    private static void configureDisplay(Class<?> imageType, Object image, Options options) throws Exception {
        Object display = builder(imageType, "$DisplayConfig");
        set(display, "setWidth", int.class, options.width());
        set(display, "setHeight", int.class, options.height());
        set(display, "setHorizontalDpi", int.class, 160);
        set(display, "setVerticalDpi", int.class, 160);
        set(display, "setRefreshRate", int.class, options.refreshHz());
        Log.i("UAVF-Display", "Guest virtual display refresh request=" + options.refreshHz());
        attach(image, "setDisplayConfig", finish(display));
    }

    private static Gpu gpu(SharedPreferences prefs, Intent intent, Options options) {
        if (options.hostVirgl()) {
            // These transient modes are diagnostic overrides of the usual VirGL2 host renderer.
            if (intent.getBooleanExtra("gfxstream_aligned_ring_probe", false)) {
                return new Gpu("gfxstream", true, GFXSTREAM_CONTEXTS, "ExternalBlob:disabled");
            }
            boolean venus = intent.getBooleanExtra("venus_gpu_probe", false);
            return new Gpu("virglrenderer", venus, venus ? new String[]{"virgl2", "venus"}
                    : new String[]{"virgl2"}, null);
        }
        if (prefs.getBoolean("linux_gpu_gfxstream_vk_hostmem_experimental", false)) {
            String features = prefs.getBoolean("linux_gpu_gfxstream_system_blob_experimental", false)
                    ? "SystemBlob:enabled"
                    : options.disableUdmabuf() ? "VulkanAllocateHostVisibleAsUdmabuf:disabled"
                    : "VulkanAllocateHostMemory:enabled";
            return new Gpu("gfxstream", true, GFXSTREAM_CONTEXTS, features);
        }
        if (prefs.getBoolean("linux_gpu_gfxstream_vulkan_experimental", false)) {
            return new Gpu("gfxstream", false, new String[]{"gfxstream-gles"}, null);
        }
        if (prefs.getBoolean("linux_gpu_virgl_experimental", false)) {
            return new Gpu("virglrenderer", false, new String[]{"virgl2"}, null);
        }
        return null;
    }

    private static void configureGpu(Class<?> imageType, Object image, Gpu gpu) throws Exception {
        Object config = builder(imageType, "$GpuConfig");
        if (gpu != null) {
            set(config, "setBackend", String.class, gpu.backend());
            set(config, "setRendererUseEgl", Boolean.class, true);
            set(config, "setRendererUseGles", Boolean.class, true);
            set(config, "setRendererUseGlx", Boolean.class, false);
            set(config, "setRendererUseSurfaceless", Boolean.class, true);
            set(config, "setRendererUseVulkan", Boolean.class, gpu.vulkan());
            set(config, "setContextTypes", String[].class, gpu.contexts());
            if (gpu.features() != null) set(config, "setRendererFeatures", String.class, gpu.features());
        }
        attach(image, "setGpuConfig", finish(config));
    }

    private static Object vmConfig(Context context, Options options, Class<?> imageType, Object image)
            throws Exception {
        Class<?> configType = Class.forName(FRAMEWORK + "VirtualMachineConfig");
        Object vm = Class.forName(configType.getName() + "$Builder").getConstructor(Context.class).newInstance(context);
        set(vm, "setProtectedVm", boolean.class, false);
        set(vm, "setMemoryBytes", long.class, options.memoryBytes());
        set(vm, "setCpuTopology", int.class, configType.getField(options.cpuTopology()).getInt(null));
        set(vm, "setDebugLevel", int.class, 1);
        set(vm, "setConsoleInputDevice", String.class, "ttyS0");
        set(vm, "setVmOutputCaptured", boolean.class, true);
        set(vm, "setVmConsoleInputSupported", boolean.class, true);
        set(vm, "setCustomImageConfig", imageType, image);
        return finish(vm);
    }

    private static Object builder(Class<?> owner, String type) throws Exception {
        return Class.forName(owner.getName() + type + "$Builder").getConstructor().newInstance();
    }

    private static void attach(Object target, String method, Object value) throws Exception {
        set(target, method, value.getClass(), value);
    }

    private static void set(Object target, String method, Class<?> type, Object value) throws Exception {
        target.getClass().getMethod(method, type).invoke(target, value);
    }

    private static Object finish(Object target) throws Exception {
        return target.getClass().getMethod("build").invoke(target);
    }
}
