package com.example.winavf;

import static com.example.winavf.WindowsKdWire.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/** Single-owner KD session for the pinned, isolated WinPE boot profile. */
final class WindowsKdSession implements WindowsKdBootProfile.StoppedMemory {
    private static final int LOAD_SYMBOLS = 0x3031;
    private static final int EXCEPTION = 0x3030;
    private static final int GET_CONTEXT_EX = 0x315f;
    private static final int SET_CONTEXT = 0x3133;
    private static final int READ_VIRTUAL = 0x3130;
    private static final int WRITE_VIRTUAL = 0x3131;
    private static final int CONTINUE = 0x3136;
    private static final int GET_VERSION = 0x3146;
    private static final int FILE_OPEN = 0x3430;
    private static final int FILE_NOT_FOUND = 0xc000000f;
    private static final int DBG_CONTINUE = 0x10002;
    private static final int PINNED_BUILD = 26100;
    // The retained loader capture needed up to six explicit retransmissions.
    private static final int RETRY_LIMIT = 8;

    private final Reader reader;
    private final OutputStream output;
    private int sendId = INITIAL_ID;
    private int lastReceivedId;
    private int lastSentType;
    private int retries;
    private int processor;
    private byte[] lastSent;
    private byte[] lastAck;
    private boolean awaitingAck;
    private long sentAt;
    private int continueResends;
    private boolean unloadRecoveryUsed;
    private int activeApi;
    private long rpcStartedAt;
    private int writeRecoveries;
    private byte[] currentStop;
    private boolean stopped;
    private boolean started;
    private final WindowsKdSmpBootstrap smp;
    private long loaderBase;
    private boolean loaderBreakHandled;

    WindowsKdSession(InputStream input, OutputStream output) {
        this(input, output, false);
    }

    WindowsKdSession(InputStream input, OutputStream output, boolean matchHostCpus) {
        this.reader = new Reader(input);
        this.output = output;
        this.smp = matchHostCpus ? new WindowsKdSmpBootstrap(this) : null;
    }

    /**
     * The owner must bound I/O and close the transport to end the worker. It should
     * retain the serial worker through first display, since the guest can stop again.
     */
    void serve(Runnable kernelResumed) throws IOException {
        if (started) throw new IllegalStateException("Windows KD session already used");
        started = true;
        reader.awaitLoader();
        send(control(RESET, 0));
        boolean patched = false;
        for (;;) {
            Packet packet = next();
            if (packet == null && patched) return;
            if (packet == null || packet.type() != STATE_CHANGE) {
                throw new IOException("Expected a Windows KD boot stop");
            }
            var state = littleEndian(packet.body());
            processor = Short.toUnsignedInt(state.getShort(6));
            stopped = true;
            currentStop = packet.body();
            long pc = state.getLong(24);
            if (smp != null && !patched && state.getInt(0) == EXCEPTION
                    && processor == 0 && state.getInt(32) == 0x80000003 && resumeLoaderBreak(pc)) {
                resume();
                continue;
            }
            if (smp != null && state.getInt(0) == EXCEPTION
                    && processor == 0 && state.getInt(32) == 0x80000003 && smp.handle(pc)) {
                resume();
                continue;
            }
            if (state.getInt(0) != LOAD_SYMBOLS)
                throw new IOException("Unexpected Windows KD stop 0x" + Integer.toHexString(state.getInt(0))
                        + " cpu=" + processor + " pc=0x" + Long.toUnsignedString(pc, 16)
                        + " code=0x" + Integer.toHexString(state.getInt(32)));
            if (processor != 0 && !(patched && smp != null && smp.online() && processor < 8))
                throw new IOException("Unexpected Windows KD stopped processor " + processor);
            boolean kernelStop = (pc >>> 48) == 0xffff;
            if (smp != null && !patched && !kernelStop && state.get(64) == 0
                    && state.getInt(32) == 12 && state.getInt(60) == 0x333000
                    && Arrays.equals(Arrays.copyOfRange(packet.body(), packet.body().length - 12,
                            packet.body().length), "winload.efi\0".getBytes(StandardCharsets.US_ASCII))) {
                loaderBase = state.getLong(40);
            }
            if (!patched && kernelStop) {
                long base = verifyKernelStop(state, packet.body());
                WindowsKdBootProfile.Build build = WindowsKdBootProfile.apply(this, base, pc);
                if (smp != null) smp.arm(base, build);
                resume();
                patched = true;
                kernelResumed.run();
            } else {
                resume();
            }
        }
    }

    private boolean resumeLoaderBreak(long pc) throws IOException {
        if (loaderBreakHandled || loaderBase < 0x80000000L || loaderBase >= 0x100000000L
                || (loaderBase & 4095) != 0 || pc != loaderBase + 0x1a6198) return false;
        byte[] expected = HexFormat.of().parseHex("00003ed4fd7bc1a8f76342a9f55b41a9");
        if (!Arrays.equals(read(pc, expected.length), expected)) return false;
        ByteBuffer context = context();
        if (context.getLong(264) != pc || (context.getInt(4) & 15) != 5) return false;
        restoreContext(context.putLong(264, pc + 4));
        loaderBreakHandled = true;
        return true;
    }

    private long verifyKernelStop(ByteBuffer state, byte[] bytes) throws IOException {
        int length = state.getInt(32);
        if (state.get(64) != 0 || length < 1 || length > bytes.length - 80) {
            throw new IOException("Missing Windows KD kernel module notification");
        }
        String path = new String(bytes, bytes.length - length, length, StandardCharsets.US_ASCII);
        if (path.endsWith("\0")) path = path.substring(0, path.length() - 1);
        if (!path.toLowerCase(Locale.ROOT).endsWith("\\ntoskrnl.exe")) {
            throw new IOException("Unexpected first Windows KD kernel module");
        }
        ByteBuffer version = rpc(GET_VERSION, 0, 0, null);
        long base = version.getLong(32);
        if (base != state.getLong(40) || Short.toUnsignedInt(version.getShort(18)) != PINNED_BUILD
                || Short.toUnsignedInt(version.getShort(24)) != 0xaa64) {
            throw new IOException("Unexpected Windows KD version or kernel base");
        }
        return base;
    }

    @Override
    public byte[] read(long address, int size) throws IOException {
        var response = rpc(READ_VIRTUAL, address, size, null);
        if (response.capacity() != MANIPULATE_BYTES + size) throw new IOException("Short Windows KD read");
        return Arrays.copyOfRange(response.array(), MANIPULATE_BYTES, response.capacity());
    }

    @Override
    public void write(long address, byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > 32) throw new IOException("Invalid Windows KD write size");
        rpc(WRITE_VIRTUAL, address, bytes.length, bytes);
    }

    @Override
    public void recoverWrite(long address, byte[] before, byte[] after,
                             SocketTimeoutException failure) throws IOException {
        if (!stopped || writeRecoveries++ >= 2) throw failure;
        reset();
        send(control(RESET, 0));
        byte[] actual = read(address, after.length);
        if (Arrays.equals(actual, after)) return;
        if (!Arrays.equals(actual, before)) throw failure;
        write(address, after);
    }

    private ByteBuffer rpc(int api, long address, int size, byte[] extra) throws IOException {
        byte[] body = request(api, extra == null ? 0 : extra.length);
        var fields = littleEndian(body);
        fields.putLong(16, address).putInt(24, size);
        if (extra != null) System.arraycopy(extra, 0, body, MANIPULATE_BYTES, extra.length);
        ByteBuffer response = exchange(api, body);
        if (response.getInt(8) != 0 || (api != GET_VERSION && (response.getLong(16) != address
                || response.getInt(24) != size || response.getInt(28) != size)))
            throw new IOException("Unexpected Windows KD memory response for 0x" + Integer.toHexString(api));
        return response;
    }

    ByteBuffer context() throws IOException {
        byte[] body = request(GET_CONTEXT_EX, 0);
        littleEndian(body).putInt(20, 912);
        ByteBuffer reply = exchange(GET_CONTEXT_EX, body);
        if (reply.getInt(8) != 0 || reply.capacity() != MANIPULATE_BYTES + 912)
            throw new IOException("Unexpected Windows ARM64 context response");
        return littleEndian(Arrays.copyOfRange(reply.array(), MANIPULATE_BYTES, reply.capacity()));
    }

    void restoreContext(ByteBuffer context) throws IOException {
        byte[] body = request(SET_CONTEXT, context.capacity());
        littleEndian(body).putInt(16, context.getInt(0));
        System.arraycopy(context.array(), 0, body, MANIPULATE_BYTES, context.capacity());
        if (exchange(SET_CONTEXT, body).getInt(8) != 0)
            throw new IOException("Windows ARM64 context update rejected");
    }

    int insertBreakpoint(long address) throws IOException {
        byte[] body = request(0x3134, 0);
        littleEndian(body).putLong(16, address);
        ByteBuffer reply = exchange(0x3134, body);
        int handle = reply.getInt(24);
        if (reply.getInt(8) != 0 || handle < 1 || handle > 32)
            throw new IOException("Windows KD breakpoint rejected");
        return handle;
    }

    int removeBreakpoint(int handle) throws IOException {
        byte[] body = request(0x3135, 0);
        littleEndian(body).putInt(16, handle);
        return exchange(0x3135, body).getInt(8);
    }

    private ByteBuffer exchange(int api, byte[] body) throws IOException {
        if (!stopped) throw new IOException("Windows KD RPC requires a stopped guest");
        activeApi = api;
        try {
            boolean recoveredRead = false;
            for (int attempt = 0; attempt < 3; attempt++) {
                rpcStartedAt = System.nanoTime();
                sendData(MANIPULATE, body);
                Packet packet;
                try {
                    packet = next();
                } catch (SocketTimeoutException idle) {
                    if (!readOnlyRpc() || recoveredRead || attempt == 2) throw idle;
                    recoveredRead = true;
                    reset();
                    send(control(RESET, 0));
                    continue;
                }
                if (packet != null && packet.type() == STATE_CHANGE && readOnlyRpc()
                        && Arrays.equals(packet.body(), currentStop)) {
                    // A new epoch can replay the same kernel stop while dropping a query.
                    // Only retry an idempotent query at exactly the same stop.
                    continue;
                }
                if (packet == null || packet.type() != MANIPULATE) {
                    throw new IOException("Missing Windows KD RPC response");
                }
                var response = littleEndian(packet.body());
                if (response.getInt(0) != api) {
                    throw new IOException("Unexpected Windows KD RPC response for 0x" + Integer.toHexString(api));
                }
                return response;
            }
            throw new IOException("Windows KD repeated the same stop without a query response");
        } finally {
            activeApi = 0;
        }
    }

    private byte[] request(int api, int extra) {
        return littleEndian(new byte[MANIPULATE_BYTES + extra])
                .putInt(api).putShort((short) 0).putShort((short) processor).array();
    }

    private void resume() throws IOException {
        byte[] body = request(CONTINUE, 0);
        littleEndian(body).putInt(16, DBG_CONTINUE);
        sendData(MANIPULATE, body);
        stopped = false;
    }

    private Packet next() throws IOException {
        for (;;) {
            Packet packet;
            try {
                packet = reader.next();
            } catch (SocketTimeoutException idle) {
                if (recoverIdleUnload()) continue;
                if (activeApi != 0 && System.nanoTime() - rpcStartedAt >= 5_000_000_000L) throw idle;
                if (awaitingAck && retries < 3 && System.nanoTime() - sentAt >= 1_000_000_000L)
                    retry(lastSentType == FILE_IO ? lastAck : null);
                continue;
            }
            if (packet == null) return null;
            if (packet.control()) {
                if (packet.type() == RESET) reset();
                if (packet.type() == ACK && awaitingAck && packet.id() == (sendId ^ 1)) {
                    awaitingAck = false;
                }
                if (packet.type() == RESEND) {
                    if (isLastContinue()) continueResends++;
                    if (awaitingAck) retry(lastAck);
                    else if (lastAck != null) send(lastAck);
                }
                continue;
            }
            if ((packet.id() & SYNC_BIT) != 0) {
                reset();
                send(control(RESET, 0));
                continue;
            }
            lastAck = control(ACK, packet.id());
            if (packet.id() == lastReceivedId) {
                if (packet.type() == FILE_IO && lastSentType == FILE_IO) retry(lastAck);
                else send(lastAck);
                continue;
            }
            lastReceivedId = packet.id();
            awaitingAck = false;
            lastSent = null;
            lastSentType = 0;
            if (packet.type() != FILE_IO) {
                send(lastAck);
                if (packet.type() == 3) continue; // Debug print
                return packet;
            }
            if (activeApi != 0 || littleEndian(packet.body()).getInt(0) != FILE_OPEN) {
                throw new IOException("Unexpected Windows KD file request");
            }
            byte[] reply = Arrays.copyOf(packet.body(), 64);
            littleEndian(reply).putInt(4, FILE_NOT_FOUND);
            // Preserve both protocol frames and their order, with no separate
            // ingress record boundary between the ACK and immediate reply.
            sendData(FILE_IO, reply, lastAck);
        }
    }

    private void reset() throws IOException {
        if (activeApi != 0 && !readOnlyRpc()) {
            throw new IOException("Windows KD reset during a memory transaction");
        }
        sendId = INITIAL_ID;
        lastReceivedId = 0;
        lastSentType = 0;
        lastSent = null;
        lastAck = null;
        awaitingAck = false;
        retries = 0;
        continueResends = 0;
    }

    private boolean readOnlyRpc() {
        return activeApi == GET_VERSION || activeApi == READ_VIRTUAL || activeApi == GET_CONTEXT_EX;
    }

    private boolean isLastContinue() {
        return lastSentType == MANIPULATE && lastSent != null && littleEndian(lastSent).getInt(16) == CONTINUE;
    }

    private boolean recoverIdleUnload() throws IOException {
        if (unloadRecoveryUsed || !isLastContinue()
                || currentStop == null || littleEndian(currentStop).getInt(0) != LOAD_SYMBOLS || currentStop[64] == 0
                || System.nanoTime() - sentAt < 5_000_000_000L) return false;
        int length = littleEndian(currentStop).getInt(32);
        boolean smpLoaderUnload = smp != null && loaderBreakHandled && length == 0
                && littleEndian(currentStop).getLong(40) == -1;
        if (!smpLoaderUnload) {
            if (continueResends == 0 || length < 1 || length > currentStop.length - 80) return false;
            String path = new String(currentStop, currentStop.length - length, length, StandardCharsets.US_ASCII);
            if (path.endsWith("\0")) path = path.substring(0, path.length() - 1);
            if (!path.equalsIgnoreCase("uiomap.sys")) return false;
        }
        // The loader and uiomap unloads can lose the debugger epoch.
        // Reset the wire once without repeating CONTINUE or a mutation.
        unloadRecoveryUsed = true;
        reset();
        send(control(RESET, 0));
        return true;
    }

    private void retry(byte[] acknowledgement) throws IOException {
        // The ingress helper drains each record through the small UART pipe.
        // Duplicate FILE_IO/RESEND bursts can arrive while it is still draining
        // the queued copy. Do not enqueue another complete reply for each one.
        if (lastSentType == FILE_IO && System.nanoTime() - sentAt < 100_000_000L) return;
        if (retries++ >= RETRY_LIMIT) {
            if (activeApi != 0) throw new SocketTimeoutException("Windows KD RPC retry limit exceeded");
            throw new IOException("Windows KD retry limit exceeded");
        }
        sendTogether(acknowledgement, lastSent);
        sentAt = System.nanoTime();
    }

    private void sendData(int type, byte[] body) throws IOException {
        sendData(type, body, null);
    }

    private void sendData(int type, byte[] body, byte[] acknowledgement) throws IOException {
        lastSent = data(type, sendId, body);
        lastSentType = type;
        retries = 0;
        continueResends = 0;
        awaitingAck = true;
        sendId ^= 1;
        sendTogether(acknowledgement, lastSent);
        sentAt = System.nanoTime();
    }

    private void sendTogether(byte[] acknowledgement, byte[] frame) throws IOException {
        if (acknowledgement == null) {
            send(frame);
            return;
        }
        byte[] bytes = Arrays.copyOf(acknowledgement, acknowledgement.length + frame.length);
        System.arraycopy(frame, 0, bytes, acknowledgement.length, frame.length);
        send(bytes);
    }

    private void send(byte[] bytes) throws IOException {
        output.write(bytes);
        output.flush();
    }
}
