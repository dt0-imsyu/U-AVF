// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static com.example.winavf.WindowsKdWire.littleEndian;

/** Runtime steps from the retained 26100 ARM64 eight-CPU boot. */
final class WindowsKdSmpBootstrap {
    private static final int PMU = 0x49fd3c, ALLOCATE_PMU = 0x49fd70;
    private static final int CPU_ON = 0x4aaf1c, NEXT_CPU = 0x49a7c0, STARTUP_WAIT = 0x481ebc;
    private static final int START_HANDSHAKE = 0xb36084, HANDSHAKE_DONE = 0xb360b0;
    private static final int TIME_IPI = 0x4f895c, TIME_IPI_RETURN = 0x4f8960, START_RETURN = 0xb363bc;
    private static final int PC = 264, SP = 256, PAGE = 4096;
    private static final int BRK = 0xd43e0000;
    private static final byte[] PUBLISH = HexFormat.of().parseHex(
            "307a0bd510820091307a0bd510820091307a0bd510820091307a0bd59f3f03d5df3f03d500003ed4");
    private final WindowsKdSession kd;
    private final Map<Long, Probe> probes = new HashMap<>();
    private long base, physical, runtime;
    private int lastCpu;
    private ByteBuffer cpuOnContext;
    private boolean ipiReturned;
    private boolean online;

    private record Probe(int handle, int rva, byte[] original) {}

    WindowsKdSmpBootstrap(WindowsKdSession kd) { this.kd = kd; }

    boolean online() { return online; }

    void arm(long kernelBase) throws IOException {
        base = kernelBase;
        probe(CPU_ON, "e0013fd6");
        probe(PMU, "133d0b53");
    }

    boolean handle(long pc) throws IOException {
        Probe probe = probes.remove(pc);
        if (probe == null && runtime != 0 && cpuOnContext == null) return false;
        ByteBuffer registers = kd.context();
        require(registers.getLong(PC) == pc, "stop context PC");
        if (probe == null) {
            if (runtime == 0) mapRuntime(registers, pc);
            else publishComplete(registers, pc);
            return true;
        }
        remove(pc, probe);
        switch (probe.rva()) {
            case PMU -> {
                long midr = x(registers, 10);
                int counters = (int) ((x(registers, 8) >>> 11) & 31);
                require(x(registers, 20) == 0 && ((midr == 0x410fd821L && counters == 31)
                        || (midr == 0x410fd811L && counters == 20)), "BSP PMU");
                if (counters == 31) probe(ALLOCATE_PMU, "dc961794");
            }
            case ALLOCATE_PMU -> {
                require(x(registers, 20) == 0 && x(registers, 0) == 31
                        && x(registers, 19) == 31, "BSP counter allocation");
                kd.restoreContext(registers.putLong(8, 20));
            }
            case CPU_ON -> redirectCpuOn(registers);
            case NEXT_CPU -> {
                require(x(registers, 0) == lastCpu + 1 && lastCpu < 7, "next CPU_ON");
                probe(CPU_ON, "e0013fd6");
            }
            case STARTUP_WAIT -> {
                require(x(registers, 19) == 4
                        && readLong(runtime + lastCpu * PAGE + 48) == 1, "secondary CPU reached Windows");
                if (lastCpu == 7) probe(START_HANDSHAKE, "1f0c0071");
            }
            case START_HANDSHAKE -> {
                require(lastCpu == 7 && x(registers, 0) == 4, "all secondary CPUs started");
                probe(HANDSHAKE_DONE, "e00313aa");
            }
            case HANDSHAKE_DONE -> {
                probe(TIME_IPI, "59f5f597");
                probe(START_RETURN, "c0035fd6");
            }
            case TIME_IPI -> configureIpi(registers);
            case TIME_IPI_RETURN -> ipiReturned = true;
            case START_RETURN -> {
                require(ipiReturned && readInt(base + 0xe08a80) == 8
                        && readInt(base + 0xe08c38) == 0, "eight Windows processors online");
                online = true;
                android.util.Log.i("UAVF-Windows-Setup", "WINDOWS_SMP_ONLINE=8");
            }
            default -> throw new IOException("Unknown Windows SMP probe");
        }
        return true;
    }

    private void mapRuntime(ByteBuffer registers, long pc) throws IOException {
        long candidate = x(registers, 19), mapped = x(registers, 20);
        require(candidate >= 0x80000000L && candidate <= 0xffff8000L
                && (candidate & 4095) == 0 && pc == candidate + 308 && kernelAddress(mapped, PAGE),
                "EFI runtime callback");
        var header = littleEndian(kd.read(mapped, 64));
        require(header.getLong(0) == 0x4943535046564155L && header.getLong(8) == 2
                && header.getLong(16) == candidate && header.getLong(24) == 8
                && header.getLong(32) == PAGE && header.getLong(40) == mapped
                && header.getLong(56) == 0 && readInt(mapped + 308) == BRK, "EFI runtime header");
        physical = candidate;
        runtime = mapped;
        kd.restoreContext(registers.putLong(PC, pc + 4));
        android.util.Log.i("UAVF-Windows-Setup", "Windows SMP EFI runtime mapping verified");
    }

    private void redirectCpuOn(ByteBuffer registers) throws IOException {
        long entry = x(registers, 2), context = x(registers, 3);
        int cpu = lastCpu + 1;
        require(runtime != 0 && cpu <= 7 && cpuOnContext == null && x(registers, 0) == 0xc4000003L
                && x(registers, 1) == cpu && x(registers, 15) == base + 0x49a870
                && entry >= 0x80000000L && entry < 0x180000000L && (entry & 3) == 0
                && (cpu == 1 ? context == 0x8000c000L : context >= 0x80000000L && context < 0x180000000L),
                "CPU_ON operands");
        long offset = cpu * PAGE;
        var template = littleEndian(new byte[64]);
        for (int word : new int[]{0x52800020, 0x10000170, 0xf9000200, 0xd5033f9f,
                0x58000080, 0x580000b0, 0xd61f0200, 0xd503201f}) template.putInt(word);
        template.putLong(0).putLong(physical + offset + 56).putLong(0).putInt(0xd503205f).putInt(0x17ffffff);
        require(Arrays.equals(kd.read(runtime + offset, 64), template.array())
                && Arrays.equals(kd.read(runtime + 512, PUBLISH.length), PUBLISH), "owned PSCI trampoline");
        byte[] literals = littleEndian(new byte[16]).putLong(context).putLong(entry).array();
        writeChecked(runtime + offset + 32, literals);
        registers.putLong(24, physical + offset).putLong(32, 0);
        cpuOnContext = littleEndian(registers.array().clone());
        lastCpu = cpu;
        if (cpu < 7) probe(NEXT_CPU, "7f2303d5");
        probe(STARTUP_WAIT, "e003132a");
        kd.restoreContext(registers.putLong(136, runtime + offset).putLong(PC, runtime + 512));
    }

    private void publishComplete(ByteBuffer registers, long pc) throws IOException {
        require(pc == runtime + 548 && x(registers, 16) == runtime + lastCpu * PAGE + 96
                && registers.getLong(SP) == cpuOnContext.getLong(SP)
                && Arrays.equals(kd.read(runtime + 512, PUBLISH.length), PUBLISH), "cache publication return");
        for (int i = 0; i < 31; i++)
            if (i != 16) require(x(registers, i) == x(cpuOnContext, i), "cache publication registers");
        kd.restoreContext(cpuOnContext);
        cpuOnContext = null;
    }

    private void configureIpi(ByteBuffer registers) throws IOException {
        require(lastCpu == 7 && x(registers, 0) == base + 0xa982e0
                && x(registers, 1) == registers.getLong(SP), "native time IPI");
        expect(base + 0x484d98, "09f540b9c9002836");
        expect(base + 0x484dd0, "e1674179284c00b002810291e0c30291");
        expect(base + 0x4aefbc, "28490090133505f9");
        long controller = readLong(base + 0xc100e0), gic = readLong(base + 0xdd2a68);
        require(kernelAddress(controller, 8) && kernelAddress(gic, 8)
                && readInt(controller + 0xf4) == 0x82b, "GIC capability");
        var metadata = littleEndian(kd.read(gic, 0x90 + 8 * 0x48));
        require(metadata.getInt(0x48) == 8 && kernelAddress(metadata.getLong(8), PAGE), "GIC CPU count");
        int affinities = 0, redistributors = 0;
        for (int at = 0x90; at < metadata.capacity(); at += 0x48) {
            long affinity = metadata.getLong(at), address = metadata.getLong(at + 0x18);
            require(affinity >= 0 && affinity < 8 && address >= 0x3fef0000 && address < 0x3fff0000
                    && (address - 0x3fef0000) % 0x20000 == 0
                    && kernelAddress(metadata.getLong(at + 0x20), PAGE), "GIC redistributor");
            affinities |= 1 << (int) affinity;
            redistributors |= 1 << (int) ((address - 0x3fef0000) / 0x20000);
        }
        require(affinities == 255 && redistributors == 255, "unique GIC CPU mappings");
        writeChecked(controller + 0xf4, littleEndian(new byte[4]).putInt(0x80b).array());
        probe(TIME_IPI_RETURN, "20008052");
    }

    private void probe(int rva, String instruction) throws IOException {
        long address = base + rva;
        byte[] original = HexFormat.of().parseHex(instruction);
        for (long other : probes.keySet()) require(Math.abs(other - address) >= 32, "breakpoint overlap");
        require(Arrays.equals(kd.read(address, 4), original), "original instruction " + instruction);
        probes.put(address, new Probe(kd.insertBreakpoint(address), rva, original));
    }

    private void remove(long pc, Probe probe) throws IOException {
        int status = kd.removeBreakpoint(probe.handle());
        require(status == 0 || (status == 0xc0000001
                && readInt(base + 0xdbc420 + (probe.handle() - 1) * 40 + 32) == 0), "breakpoint removal");
        require(Arrays.equals(kd.read(pc, 4), probe.original()), "restored instruction");
    }

    private void expect(long address, String value) throws IOException {
        byte[] bytes = HexFormat.of().parseHex(value);
        require(Arrays.equals(kd.read(address, bytes.length), bytes), "kernel instruction guard");
    }

    private void writeChecked(long address, byte[] bytes) throws IOException {
        kd.write(address, bytes);
        require(Arrays.equals(kd.read(address, bytes.length), bytes), "runtime write readback");
    }

    private int readInt(long address) throws IOException { return littleEndian(kd.read(address, 4)).getInt(); }
    private long readLong(long address) throws IOException { return littleEndian(kd.read(address, 8)).getLong(); }
    private static long x(ByteBuffer context, int register) { return context.getLong(8 + register * 8); }
    private static boolean kernelAddress(long address, int alignment) {
        return address >>> 48 == 0xffff && (address & (alignment - 1)) == 0;
    }
    private static void require(boolean condition, String check) throws IOException {
        if (!condition) throw new IOException("Windows SMP validation failed: " + check);
    }
}
