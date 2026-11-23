package com.example.winavf;

import com.example.winavf.diagnostics.CompatibilityStatus;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/** App-owned AVF proof. Attaches only its disposable boot and witness image. */
final class AvfCompatibilityProbe {
    static final String ASSET = "uavf-compat-boot.img";
    private static final String VM_NAME = "uavf-avf-compatibility-v1";
    private static final String WRAPPER_SHA =
            "93EDA7C4BD54C33F85ADA6F05158C74EC6F5C3232F5CBA73846E5B442895F234";
    private static final long REQUESTED_MEMORY = 4L * 1024 * 1024 * 1024;
    private static final long DISK_SIZE = 128L * 1024 * 1024;
    private static final long CHALLENGE_OFFSET = 127L * 1024 * 1024;
    private static final long WITNESS_OFFSET = CHALLENGE_OFFSET + 4096;
    private static final int NONCE_BYTES = 32;
    private static final long PORT = 4069;

    record Result(CompatibilityStatus status, boolean created, boolean booted, boolean memory,
                  boolean storage, boolean vsock, long guestMemoryBytes, String details) {
        String report() {
            return "AVF_STATUS=" + status + "\nVM_CREATED=" + created + "\nKERNEL_BOOTED=" + booted
                    + "\nMEMORY_VERIFIED=" + memory + "\nSTORAGE_VERIFIED=" + storage
                    + "\nVSOCK_VERIFIED=" + vsock + "\nGUEST_MEMORY_BYTES=" + guestMemoryBytes
                    + "\nDETAILS=" + details + "\n";
        }
    }

    private final Context context;
    private final StringBuilder console = new StringBuilder();
    private static final int CONSOLE_LIMIT = 64 * 1024;
    private static final java.util.regex.Pattern MEMORY_LINE = java.util.regex.Pattern.compile(
            "(?m)^UAVF_COMPAT_MEMORY flags=1 bytes=(\\d{1,12})\\r?$");

    AvfCompatibilityProbe(Context context) {
        this.context = context.getApplicationContext();
    }

    Result run() {
        Object manager = null;
        Object vm = null;
        File directory = null;
        InputStream serial = null;
        boolean created = false, booted = false, memory = false, storage = false, vsock = false;
        long bytes = 0;
        String details = "All required AVF checks passed.";
        CompatibilityStatus status = CompatibilityStatus.INSUFFICIENT_DATA;
        try {
            Class<?> managerType = Class.forName("android.system.virtualmachine.VirtualMachineManager");
            manager = context.getSystemService(managerType);
            if (manager == null)
                return new Result(CompatibilityStatus.INSUFFICIENT_DATA, false, false, false, false, false, 0,
                        "Android did not provide a virtualization manager.");
            int capabilities = (int)invoke(manager, "getCapabilities");
            int nonProtected = managerType.getField("CAPABILITY_NON_PROTECTED_VM").getInt(null);
            if ((capabilities & nonProtected) == 0)
                return new Result(CompatibilityStatus.INCOMPATIBLE, false, false, false, false, false, 0,
                        "AVF reports no non-protected VM capability.");

            Object prior = invoke(manager, "get", String.class, VM_NAME);
            if (prior != null) {
                if (running(prior))
                    throw new IOException("A compatibility VM is already running.");
                invoke(manager, "delete", String.class, VM_NAME);
            }
            directory = Files.createTempDirectory(context.getCacheDir().toPath(), "avf-compat-").toFile();
            File wrapper = new File(directory, "wrapper.Image");
            File boot = new File(directory, "boot.img");
            copyAsset("u-boot-wrapper-v24.Image", wrapper, 1430912L, WRAPPER_SHA);
            String expectedHash;
            try (InputStream hash = context.getAssets().open(ASSET + ".sha256")) {
                expectedHash = new String(hash.readNBytes(128), java.nio.charset.StandardCharsets.US_ASCII).strip();
                if (!expectedHash.matches("[0-9a-fA-F]{64}"))
                    throw new IOException("Invalid bundled probe digest.");
            }
            copyAsset(ASSET, boot, DISK_SIZE, expectedHash);
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            try (RandomAccessFile file = new RandomAccessFile(boot, "rw")) {
                file.seek(CHALLENGE_OFFSET);
                file.write(nonce);
                file.getFD().sync();
            }
            Object config = configuration(wrapper, boot);
            vm = manager.getClass().getMethod("create", String.class, config.getClass())
                    .invoke(manager, VM_NAME, config);
            created = true;
            serial = (InputStream)invoke(vm, "getConsoleOutput");
            final InputStream reader = serial;
            Thread thread = new Thread(() -> readConsole(reader), "UAVF-compat-console");
            thread.setDaemon(true);
            thread.start();
            invoke(vm, "run");
            long deadline = SystemClock.elapsedRealtime() + 95_000;
            while (!observed("UAVF_COMPAT_VSOCK_READY") && !observed("UAVF_COMPAT_VSOCK_ERROR")
                    && !observed("UAVF_COMPAT_MODULE_ERROR")
                    && SystemClock.elapsedRealtime() < deadline && running(vm)) {
                Thread.sleep(100);
            }
            booted = observed("UAVF_COMPAT_KERNEL_BOOTED");
            var memoryLine = MEMORY_LINE.matcher(consoleTail());
            if (memoryLine.find()) {
                bytes = Long.parseLong(memoryLine.group(1));
                memory = validMemory(bytes);
            }
            if (!observed("UAVF_COMPAT_VSOCK_READY")) {
                throw new IOException(booted ? "The guest booted but its vsock listener was not ready."
                        : "The compatibility kernel did not reach its init marker.");
            }

            ByteBuffer response = exchange(vm, nonce);
            if (response.getInt() != 0x31434155)
                throw new IOException("Unexpected vsock response signature.");
            int flags = response.getInt();
            bytes = response.getLong();
            byte[] reply = new byte[NONCE_BYTES];
            response.get(reply);
            if (!MessageDigest.isEqual(nonce, reply) || (flags & ~3) != 0)
                throw new IOException("The vsock challenge response did not match.");
            vsock = true;
            booted = true;
            memory = (flags & 1) != 0 && validMemory(bytes);
            invoke(vm, "stop");
            if (running(vm))
                throw new IOException("The probe VM did not stop before disk readback.");
            storage = (flags & 2) != 0 && verifyWitness(boot, nonce);
            status = memory && storage ? CompatibilityStatus.COMPATIBLE : CompatibilityStatus.NEEDS_WORK;
            if (status == CompatibilityStatus.NEEDS_WORK)
                details = !memory ? "Guest memory write/read did not pass." : "Virtio block write/read did not pass.";
        } catch (Exception error) {
            if (error instanceof InterruptedException)
                Thread.currentThread().interrupt();
            booted |= observed("UAVF_COMPAT_KERNEL_BOOTED");
            status = booted && !observed("UAVF_COMPAT_MODULE_ERROR")
                    ? CompatibilityStatus.NEEDS_WORK : CompatibilityStatus.INSUFFICIENT_DATA;
            details = description(error);
        } finally {
            try {
                if (vm != null) {
                    if (running(vm))
                        invoke(vm, "stop");
                    if (running(vm))
                        throw new IOException("The compatibility VM is still running.");
                    invoke(manager, "delete", String.class, VM_NAME);
                }
                if (directory != null) {
                    Files.deleteIfExists(new File(directory, "boot.img").toPath());
                    Files.deleteIfExists(new File(directory, "wrapper.Image").toPath());
                    Files.delete(directory.toPath());
                }
            } catch (Exception error) {
                status = booted ? CompatibilityStatus.NEEDS_WORK : CompatibilityStatus.INSUFFICIENT_DATA;
                details += " Probe cleanup: " + description(error);
            } finally {
                if (serial != null) {
                    try { serial.close(); } catch (IOException ignored) {}
                }
            }
        }
        return new Result(status, created, booted, memory, storage, vsock, bytes, details);
    }

    private static boolean validMemory(long bytes) {
        return bytes >= 64L * 1024 * 1024 && bytes <= REQUESTED_MEMORY;
    }

    private Object configuration(File wrapper, File boot) throws Exception {
        Class<?> image = Class.forName("android.system.virtualmachine.VirtualMachineCustomImageConfig");
        Object custom = Class.forName(image.getName() + "$Builder").getConstructor().newInstance();
        invoke(custom, "setName", String.class, VM_NAME);
        invoke(custom, "setOsName", String.class, VM_NAME);
        invoke(custom, "setKernelPath", String.class, wrapper.getAbsolutePath());
        invoke(custom, "useAutoMemoryBalloon", boolean.class, false);
        Class<?> disk = Class.forName(image.getName() + "$Disk");
        invoke(custom, "addDisk", disk, disk.getMethod("RWDisk", String.class).invoke(null, boot.getPath()));

        Class<?> config = Class.forName("android.system.virtualmachine.VirtualMachineConfig");
        Object builder = Class.forName(config.getName() + "$Builder").getConstructor(Context.class).newInstance(context);
        invoke(builder, "setProtectedVm", boolean.class, false);
        invoke(builder, "setMemoryBytes", long.class, REQUESTED_MEMORY);
        invoke(builder, "setCpuTopology", int.class, config.getField("CPU_TOPOLOGY_ONE_CPU").getInt(null));
        invoke(builder, "setDebugLevel", int.class, 1);
        invoke(builder, "setVmOutputCaptured", boolean.class, true);
        invoke(builder, "setConsoleInputDevice", String.class, "ttyS0");
        invoke(builder, "setCustomImageConfig", image, invoke(custom, "build"));
        return invoke(builder, "build");
    }

    private void copyAsset(String name, File file, long size, String hash) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = context.getAssets().open(name); FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            long count = 0;
            for (int read; (read = input.read(buffer)) != -1;) {
                count += read;
                if (count > size)
                    throw new IOException("Unexpected size for " + name);
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            if (count != size || !HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(hash))
                throw new IOException("Asset verification failed for " + name);
        }
    }

    private static ByteBuffer exchange(Object vm, byte[] nonce) throws Exception {
        try (ParcelFileDescriptor socket = (ParcelFileDescriptor)invoke(vm, "connectVsock", long.class, PORT)) {
            var fd = socket.getFileDescriptor();
            long deadline = SystemClock.elapsedRealtime() + 5000;
            waitForSocket(fd, OsConstants.POLLOUT, deadline);
            new FileOutputStream(fd).write(nonce);
            FileInputStream input = new FileInputStream(fd);
            byte[] reply = new byte[48];
            for (int offset = 0; offset < reply.length;) {
                waitForSocket(fd, OsConstants.POLLIN, deadline);
                int read = input.read(reply, offset, reply.length - offset);
                if (read < 0)
                    throw new IOException("Truncated vsock challenge response.");
                offset += read;
            }
            return ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    private static void waitForSocket(java.io.FileDescriptor fd, int event, long deadline) throws Exception {
        long remaining = deadline - SystemClock.elapsedRealtime();
        var poll = new StructPollfd();
        poll.fd = fd;
        poll.events = (short)event;
        if (remaining <= 0 || Os.poll(new StructPollfd[]{poll}, (int)remaining) == 0)
            throw new IOException("The vsock challenge timed out.");
        if ((poll.revents & event) == 0)
            throw new IOException("The vsock channel closed before completing the challenge.");
    }

    private static boolean verifyWitness(File path, byte[] nonce) throws IOException {
        byte[] expected = nonce.clone();
        for (int i = 0; i < expected.length; i++)
            expected[i] ^= (byte)0xa5;
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            byte[] actual = new byte[nonce.length];
            file.seek(CHALLENGE_OFFSET);
            file.readFully(actual);
            if (!Arrays.equals(actual, nonce))
                return false;
            file.seek(WITNESS_OFFSET);
            file.readFully(actual);
            return Arrays.equals(actual, expected);
        }
    }

    private void readConsole(InputStream stream) {
        try {
            byte[] data = new byte[2048];
            for (int n; (n = stream.read(data)) != -1;) {
                synchronized (console) {
                    console.append(new String(data, 0, n, java.nio.charset.StandardCharsets.US_ASCII));
                    if (console.length() > CONSOLE_LIMIT)
                        console.delete(0, console.length() - CONSOLE_LIMIT);
                }
            }
        } catch (IOException ignored) {
            // AVF closes the console when the test VM stops.
        }
    }

    private boolean observed(String marker) {
        synchronized (console) {
            return console.indexOf(marker) >= 0;
        }
    }

    String consoleTail() {
        synchronized (console) {
            return console.toString();
        }
    }

    private static boolean running(Object vm) throws Exception {
        return (int)invoke(vm, "getStatus") == vm.getClass().getField("STATUS_RUNNING").getInt(null);
    }

    private static Object invoke(Object object, String name) throws Exception {
        return object.getClass().getMethod(name).invoke(object);
    }

    private static Object invoke(Object object, String name, Class<?> type, Object value) throws Exception {
        return object.getClass().getMethod(name, type).invoke(object, value);
    }

    private static String description(Throwable error) {
        while (error instanceof InvocationTargetException && error.getCause() != null)
            error = error.getCause();
        return error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }
}
