// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import android.content.Context;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Android AVF reflection at the boundary of the product runtime. */
final class AvfGuest {
    static final String UBUNTU_VM = "winavf-frame-bridge-test-ubuntu-24045";
    private static final String FRAMEWORK = "android.system.virtualmachine.";

    static Object replaceStopped(Context context, String name, Object config) throws Exception {
        Object manager = manager(context);
        synchronized (AvfGuest.class) {
            Object previous = manager.getClass().getMethod("get", String.class).invoke(manager, name);
            if (previous != null) {
                if (!"STOPPED".equals(state(previous))) {
                    throw new IllegalStateException("Cannot replace AVF workspace " + name + " while it is active.");
                }
                manager.getClass().getMethod("delete", String.class).invoke(manager, name);
            }
            return manager.getClass().getMethod("create", String.class, config.getClass()).invoke(manager, name, config);
        }
    }

    static Object find(Context context, String name) throws Exception {
        Object manager = manager(context);
        return manager.getClass().getMethod("get", String.class).invoke(manager, name);
    }

    static void removeStopped(Context context, String name) throws Exception {
        Object manager = manager(context);
        synchronized (AvfGuest.class) {
            Object vm = manager.getClass().getMethod("get", String.class).invoke(manager, name);
            if (vm == null) return;
            if (!"STOPPED".equals(state(vm))) {
                throw new IllegalStateException("Cannot remove AVF workspace " + name + " while it is active.");
            }
            manager.getClass().getMethod("delete", String.class).invoke(manager, name);
        }
    }

    static void requireStopped(Context context, String name) throws Exception {
        Object vm = find(context, name);
        if (vm != null && !"STOPPED".equals(state(vm))) {
            throw new IllegalStateException("Stop " + name + " before preparing boot media");
        }
    }

    private static Object manager(Context context) throws Exception {
        Object manager = context.getSystemService(Class.forName(FRAMEWORK + "VirtualMachineManager"));
        if (manager == null) throw new IllegalStateException("AVF virtual machine service unavailable");
        return manager;
    }

    static String state(Object vm) throws Exception {
        Class<?> type = vm.getClass();
        int status = ((Number) type.getMethod("getStatus").invoke(vm)).intValue();
        for (String name : new String[]{"RUNNING", "STOPPED", "DELETED"}) {
            if (status == type.getField("STATUS_" + name).getInt(null)) return name;
        }
        throw new IllegalStateException("Unknown AVF status: " + status);
    }

    static InputStream consoleOutput(Object vm) throws Exception {
        return (InputStream) vm.getClass().getMethod("getConsoleOutput").invoke(vm);
    }

    static void run(Object vm) throws Exception {
        vm.getClass().getMethod("run").invoke(vm);
    }

    static void observe(Object vm, Executor executor, Consumer<String> stopped, Consumer<String> error) throws Exception {
        Class<?> callback = Class.forName(FRAMEWORK + "VirtualMachineCallback");
        Object listener = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "onStopped" -> stopped.accept(stopReason(callback, args));
                        case "onError" -> error.accept(Arrays.toString(args));
                        case "hashCode" -> { return System.identityHashCode(proxy); }
                        case "equals" -> { return proxy == args[0]; }
                        case "toString" -> { return "U-AVF virtual machine callback"; }
                    }
                    return null;
                });
        vm.getClass().getMethod("setCallback", Executor.class, callback).invoke(vm, executor, listener);
    }

    private static String stopReason(Class<?> callback, Object[] args) throws IllegalAccessException {
        if (args != null && args.length > 1 && args[1] instanceof Integer reason) {
            for (Field field : callback.getFields()) {
                if (field.getName().startsWith("STOP_REASON_") && field.getType() == int.class
                        && field.getInt(null) == reason) return field.getName();
            }
        }
        return Arrays.toString(args);
    }

    private AvfGuest() {}
}
