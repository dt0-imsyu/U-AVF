package com.example.winavf;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;

/** Replay both directions of the app-owned WinPE PASS capture using short UART reads. */
public final class WindowsKdWireTest {
    private static final long KERNEL_BASE = 0xfffff80219200000L;
    private static final int GET_VERSION = 0x3146;
    private static final int READ_VIRTUAL = 0x3130;
    private static final int WRITE_VIRTUAL = 0x3131;

    public static void main(String[] args) throws Exception {
        Path capture = Path.of(args[0]);
        checkFramingRecovery();
        var writes = new LinkedHashMap<Long, byte[]>();
        int requests = 0;
        int continues = 0;
        int[] tx;
        try (InputStream input = fragmented(Files.newInputStream(capture.resolve("tx.bin")))) {
            var reader = new WindowsKdWire.Reader(input);
            tx = new int[12];
            for (WindowsKdWire.Packet packet; (packet = reader.next()) != null; ) {
                tx[packet.type()]++;
                if (packet.type() != WindowsKdWire.MANIPULATE) continue;
                ByteBuffer body = WindowsKdWire.littleEndian(packet.body());
                switch (body.getInt(0)) {
                    case 0x3136 -> continues++;
                    case WRITE_VIRTUAL -> {
                        requests++;
                        long address = body.getLong(16);
                        int size = body.getInt(24);
                        check(size == packet.body().length - WindowsKdWire.MANIPULATE_BYTES, "write size");
                        byte[] bytes = Arrays.copyOfRange(packet.body(),
                                WindowsKdWire.MANIPULATE_BYTES, packet.body().length);
                        byte[] earlier = writes.putIfAbsent(address, bytes);
                        check(earlier == null || Arrays.equals(earlier, bytes), "exact retransmission");
                    }
                }
            }
        }
        check(Arrays.equals(tx, new int[]{0, 0, 51, 0, 306, 0, 3, 0, 0, 0, 0, 278}), "TX counts");
        check(requests == 12 && writes.size() == 11 && continues == 7, "boot commands");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        writes.forEach((address, bytes) -> {
            digest.update(WindowsKdWire.littleEndian(new byte[8])
                    .putInt(Math.toIntExact(address - KERNEL_BASE)).putInt(bytes.length).array());
            digest.update(bytes);
        });
        check(HexFormat.of().formatHex(digest.digest()).equals(
                "6c5c05736150cf0e7f1da06391e20fca97e66d3a092f4bb699e0b5054c51b960"), "ordered writes");

        int[] rx = new int[12];
        int versionResponses = 0;
        byte[] versionFrame = null;
        var confirmed = new LinkedHashMap<Long, Integer>();
        var readBack = new HashSet<Long>();
        try (InputStream input = fragmented(Files.newInputStream(capture.resolve("rx.bin")))) {
            var reader = new WindowsKdWire.Reader(input);
            reader.awaitLoader();
            for (WindowsKdWire.Packet packet; (packet = reader.next()) != null; ) {
                rx[packet.type()]++;
                if (packet.type() != WindowsKdWire.MANIPULATE) continue;
                ByteBuffer body = WindowsKdWire.littleEndian(packet.body());
                switch (body.getInt(0)) {
                    case GET_VERSION -> {
                        check(body.getInt(8) == 0 && body.getLong(32) == KERNEL_BASE, "kernel identity");
                        versionResponses++;
                        versionFrame = WindowsKdWire.data(packet.type(), packet.id(), packet.body());
                    }
                    case WRITE_VIRTUAL -> {
                        long address = body.getLong(16);
                        int size = body.getInt(28);
                        check(body.getInt(8) == 0 && writes.get(address).length == size, "write response");
                        confirmed.put(address, size);
                    }
                    case READ_VIRTUAL -> {
                        long address = body.getLong(16);
                        byte[] written = writes.get(address);
                        if (confirmed.containsKey(address) && body.getInt(8) == 0 && written != null
                                && body.getInt(28) == written.length
                                && Arrays.equals(written, Arrays.copyOfRange(packet.body(),
                                        WindowsKdWire.MANIPULATE_BYTES, packet.body().length))) {
                            readBack.add(address);
                        }
                    }
                }
            }
        }
        check(Arrays.equals(rx, new int[]{0, 0, 44, 9, 293, 32, 3, 7, 0, 0, 0, 248}), "RX counts");
        check(versionResponses == 1 && confirmed.keySet().equals(writes.keySet()), "confirmed kernel writes");
        check(readBack.equals(writes.keySet()), "all recorded writes verified by guest readback");
        byte[] rxBytes = Files.readAllBytes(capture.resolve("rx.bin"));
        byte[] bootPrefix = Arrays.copyOf(rxBytes, endOfFrame(rxBytes, versionFrame));
        checkBootProfile(Path.of(args[1]), writes, bootPrefix);
        System.out.println("WINDOWS_KD_CAPTURE_PASS rx=636 tx=638 kernelWrites=11 boundedWrites=18");
    }

    private static int endOfFrame(byte[] stream, byte[] frame) {
        for (int i = 0; i <= stream.length - frame.length; i++) {
            if (Arrays.equals(stream, i, i + frame.length, frame, 0, frame.length)) return i + frame.length;
        }
        throw new AssertionError("missing frame in capture");
    }

    private static void checkBootProfile(Path kernel, LinkedHashMap<Long, byte[]> writes,
                                         byte[] bootPrefix) throws IOException {
        byte[] file = Files.readAllBytes(kernel);
        var header = WindowsKdWire.littleEndian(file);
        int pe = header.getInt(60);
        int sections = Short.toUnsignedInt(header.getShort(pe + 6));
        int section = pe + 24 + Short.toUnsignedInt(header.getShort(pe + 20));
        byte[] image = new byte[header.getInt(pe + 80)];
        System.arraycopy(file, 0, image, 0, header.getInt(pe + 84));
        for (int i = 0; i < sections; i++, section += 40) {
            int size = header.getInt(section + 16);
            if (size > 0) System.arraycopy(file, header.getInt(section + 20), image,
                    header.getInt(section + 12), size);
        }
        long kernelPc = 0xfffff802194013c8L;
        var memory = new KernelMemory(image.clone());
        byte[] expected = image.clone();
        int offset = 0;
        long[] addresses = new long[18];
        for (var write : writes.entrySet()) {
            byte[] bytes = write.getValue();
            System.arraycopy(bytes, 0, expected, (int) (write.getKey() - KERNEL_BASE), bytes.length);
            for (int i = 0; i < bytes.length; i += 32) addresses[offset++] = write.getKey() + i;
        }
        WindowsKdBootProfile.apply(memory, KERNEL_BASE, kernelPc);
        check(offset == addresses.length && Arrays.equals(addresses, memory.addresses), "patch ordering");
        check(Arrays.equals(memory.image, expected), "no writes outside capture ranges");

        var success = new BootTarget(bootPrefix, image.clone());
        new WindowsKdSession(fragmented(success), success.hostOutput).serve(() -> {
            check(success.kernelContinues == 1, "notification follows continue");
            success.notified = true;
        });
        check(success.notified && success.fileDeclines > 100, "captured loader traffic handled");
        check(Arrays.equals(success.memory.addresses, addresses), "wire patch order");
        check(Arrays.equals(success.memory.image, expected), "wire patch contents");

        var repeatedStop = new BootTarget(withVersionReset(bootPrefix), image.clone());
        new WindowsKdSession(fragmented(repeatedStop), repeatedStop.hostOutput)
                .serve(() -> repeatedStop.notified = true);
        check(repeatedStop.versionQueries == 2 && repeatedStop.notified, "read-only query survives same-stop reset");
        check(Arrays.equals(repeatedStop.memory.image, expected), "reset did not change patch data");

        var failed = new BootTarget(bootPrefix, image.clone());
        failed.memory.dropWrites = true;
        expectSessionFailure(failed, "memory mismatch");

        var reset = new BootTarget(bootPrefix, image.clone());
        reset.resetAfterWrite = true;
        expectSessionFailure(reset, "reset during a memory transaction");

        memory = new KernelMemory(image.clone());
        memory.image[0x2fbfa0] ^= 1;
        expectRejected(memory, kernelPc, 0);
        memory = new KernelMemory(image.clone());
        expectRejected(memory, 0xa78982f8L, 0);
        memory = new KernelMemory(image.clone());
        memory.dropWrites = true;
        expectRejected(memory, kernelPc, 1);
    }

    private static byte[] withVersionReset(byte[] bootPrefix) throws IOException {
        var reader = new WindowsKdWire.Reader(new ByteArrayInputStream(bootPrefix));
        reader.awaitLoader();
        byte[] lastStop = null, version = null;
        for (WindowsKdWire.Packet packet; (packet = reader.next()) != null; ) {
            if (packet.type() == WindowsKdWire.STATE_CHANGE && (packet.id() & WindowsKdWire.SYNC_BIT) == 0)
                lastStop = WindowsKdWire.data(packet.type(), packet.id(), packet.body());
            if (packet.type() == WindowsKdWire.MANIPULATE
                    && WindowsKdWire.littleEndian(packet.body()).getInt(0) == GET_VERSION)
                version = WindowsKdWire.data(packet.type(), packet.id(), packet.body());
        }
        check(lastStop != null && version != null, "known kernel stop and version response");
        byte[] reset = WindowsKdWire.control(WindowsKdWire.RESET, 0);
        return ByteBuffer.allocate(bootPrefix.length + lastStop.length + reset.length)
                .put(bootPrefix, 0, bootPrefix.length - version.length).put(reset).put(lastStop).put(version).array();
    }

    private static void expectSessionFailure(BootTarget target, String reason) {
        try {
            new WindowsKdSession(fragmented(target), target.hostOutput)
                    .serve(() -> { throw new AssertionError("reported failed boot as resumed"); });
            throw new AssertionError("failure did not abort session");
        } catch (IOException expectedError) {
            check(expectedError.getMessage().contains(reason), "precise boot failure");
            check(target.memory.writeCount == 1 && target.kernelContinues == 0, "failed guest remains stopped");
        }
    }

    /** Recorded loader exchange followed by a target that answers the new bounded RAM transactions. */
    private static final class BootTarget extends InputStream {
        private ByteArrayInputStream data;
        private int responseId;
        private boolean kernel;
        final KernelMemory memory;
        int kernelContinues;
        int fileDeclines;
        int versionQueries;
        boolean notified;
        boolean resetAfterWrite;

        BootTarget(byte[] prefix, byte[] kernelImage) {
            data = new ByteArrayInputStream(prefix);
            memory = new KernelMemory(kernelImage);
            // The recorded GET_VERSION reply used 0x80800001.
            responseId = WindowsKdWire.INITIAL_ID;
        }

        @Override
        public int read() { return data.read(); }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return data.read(buffer, offset, length);
        }

        final OutputStream hostOutput = new OutputStream() {
            @Override
            public void write(int value) { throw new AssertionError("expected a whole KD packet"); }

            @Override
            public void write(byte[] bytes) throws IOException {
                var reader = new WindowsKdWire.Reader(new ByteArrayInputStream(bytes));
                var packet = reader.next();
                check(packet != null, "valid outgoing packet");
                var next = reader.next();
                if (next != null) {
                    check(packet.control() && packet.type() == WindowsKdWire.ACK && !next.control(),
                            "ACK precedes a grouped reply");
                    packet = next;
                }
                check(reader.next() == null, "at most one ACK and one data packet");
                if (packet.control()) return;
                var request = WindowsKdWire.littleEndian(packet.body());
                if (packet.type() == WindowsKdWire.FILE_IO) {
                    check(next != null, "file replies include the acknowledgement in the same write");
                    check(request.getInt(0) == 0x3430 && request.getInt(4) == 0xc000000f, "declined file");
                    fileDeclines++;
                    return;
                }
                int api = request.getInt(0);
                if (api == GET_VERSION) {
                    versionQueries++;
                    kernel = true;
                    return;
                }
                if (api == 0x3136) {
                    check(request.getInt(16) == 0x10002, "continue status");
                    if (kernel) kernelContinues++;
                    return;
                }
                check(kernel && data.available() == 0, "stopped request after captured version");
                int size = request.getInt(24);
                long address = request.getLong(16);
                byte[] reply = packet.body().clone();
                if (api == WRITE_VIRTUAL) {
                    memory.write(address, Arrays.copyOfRange(reply, 56, reply.length));
                    if (resetAfterWrite) {
                        data = new ByteArrayInputStream(WindowsKdWire.control(WindowsKdWire.RESET, 0));
                        return;
                    }
                    reply = Arrays.copyOf(reply, 56);
                } else {
                    check(api == READ_VIRTUAL, "only scoped KD memory RPCs");
                    reply = Arrays.copyOf(reply, 56 + size);
                    System.arraycopy(memory.read(address, size), 0, reply, 56, size);
                }
                WindowsKdWire.littleEndian(reply).putInt(28, size);
                data = new ByteArrayInputStream(WindowsKdWire.data(WindowsKdWire.MANIPULATE, responseId, reply));
                responseId ^= 1;
            }
        };
    }

    private static void expectRejected(KernelMemory memory, long pc, int attemptedWrites) {
        try {
            WindowsKdBootProfile.apply(memory, KERNEL_BASE, pc);
            throw new AssertionError("unexpected patch success");
        } catch (IOException expected) {
            check(memory.writeCount == attemptedWrites, "writes before rejection");
        }
    }

    private static final class KernelMemory implements WindowsKdBootProfile.StoppedMemory {
        final byte[] image;
        final long[] addresses = new long[18];
        int writeCount;
        boolean dropWrites;

        KernelMemory(byte[] image) { this.image = image; }

        @Override
        public byte[] read(long address, int size) {
            int offset = Math.toIntExact(address - KERNEL_BASE);
            return Arrays.copyOfRange(image, offset, offset + size);
        }

        @Override
        public void write(long address, byte[] data) {
            check(data.length <= 32, "write chunk size");
            addresses[writeCount++] = address;
            if (!dropWrites) System.arraycopy(data, 0, image,
                    Math.toIntExact(address - KERNEL_BASE), data.length);
        }
    }

    private static void checkFramingRecovery() throws IOException {
        byte[] body = new byte[WindowsKdWire.MANIPULATE_BYTES];
        body[0] = (byte) 0xff;
        byte[] valid = WindowsKdWire.data(WindowsKdWire.MANIPULATE, WindowsKdWire.INITIAL_ID, body);
        byte[] badChecksum = valid.clone();
        badChecksum[16] ^= 1;
        byte[] badTrailer = valid.clone();
        badTrailer[badTrailer.length - 1] = 0;
        byte[] reset = WindowsKdWire.control(WindowsKdWire.RESET, 0);
        byte[] marker = "WG:LOAD=PASS,START_WG:LOAD=PASS,START_WINDOWS".getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = ByteBuffer.allocate(marker.length + badChecksum.length + badTrailer.length
                + valid.length + reset.length + 3)
                .put(marker).put(badChecksum).put(badTrailer).put(reset).put(valid)
                .put(new byte[]{'0', '0', '0'}).array();
        try (InputStream input = fragmented(new ByteArrayInputStream(bytes))) {
            var reader = new WindowsKdWire.Reader(input);
            reader.awaitLoader();
            check(reader.next().type() == WindowsKdWire.RESET, "resync to reset");
            check(Arrays.equals(reader.next().body(), body), "data payload");
            check(reader.next() == null, "partial trailing leader");
        }
    }

    private static InputStream fragmented(InputStream input) {
        return new FilterInputStream(input) {
            @Override
            public int read(byte[] data, int offset, int size) throws IOException {
                return super.read(data, offset, Math.min(size, 7));
            }
        };
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
