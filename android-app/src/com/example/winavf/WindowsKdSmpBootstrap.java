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
    private record Instruction(int rva, String bytes) {}

    private enum Stop {
        PMU(new Instruction(0x49fd3c, "133d0b53"), new Instruction(0x49ba5c, "133d0b53")),
        ALLOCATE_PMU(new Instruction(0x49fd70, "dc961794"), new Instruction(0x49ba90, "90131894")),
        CPU_ON(new Instruction(0x4aaf1c, "e0013fd6"), new Instruction(0x4a6df4, "e0013fd6")),
        NEXT_CPU(new Instruction(0x49a7c0, "7f2303d5"), new Instruction(0x4968e0, "7f2303d5")),
        STARTUP_WAIT(new Instruction(0x481ebc, "e003132a"), new Instruction(0x47db0c, "e003132a")),
        START_HANDSHAKE(new Instruction(0xb36084, "1f0c0071"), new Instruction(0xb54140, "1f0c0071")),
        HANDSHAKE_DONE(new Instruction(0xb360b0, "e00313aa"), new Instruction(0xb5416c, "e00313aa")),
        TIME_IPI(new Instruction(0x4f895c, "59f5f597"), new Instruction(0x4f3ea4, "f300f697")),
        TIME_IPI_RETURN(new Instruction(0x4f8960, "20008052"), new Instruction(0x4f3ea8, "20008052")),
        START_RETURN(new Instruction(0xb363bc, "c0035fd6"), new Instruction(0xb54484, "c0035fd6"));

        final Instruction winpe6584;
        final Instruction winpe9457;

        Stop(Instruction winpe6584, Instruction winpe9457) {
            this.winpe6584 = winpe6584;
            this.winpe9457 = winpe9457;
        }
    }

    private record KernelSymbols(int numberProcessors, int barrierWait, int psciHvcHandler,
                                 int calibrateTime, int ipiCheck, Instruction ipiSetup,
                                 Instruction gicRegistration, int interruptController, int gic,
                                 int breakpointTable) {}

    private static final KernelSymbols WINPE_6584 = new KernelSymbols(
            0xe08a80, 0xe08c38, 0x49a870, 0xa982e0, 0x484d98,
            new Instruction(0x484dd0, "e1674179284c00b002810291e0c30291"),
            new Instruction(0x4aefbc, "28490090133505f9"),
            0xc100e0, 0xdd2a68, 0xdbc420);
    private static final KernelSymbols WINPE_9457 = new KernelSymbols(
            0xe09a80, 0xe09c48, 0x496990, 0xab3b60, 0x4809e8,
            new Instruction(0x480a20, "e1674179484c00d002810291e0c30291"),
            new Instruction(0x4aaebc, "484900b013dd02f9"),
            0xc10510, 0xdd35b8, 0xdbe040);

    private static final int PC = 264, SP = 256, PAGE = 4096;
    private static final int BRK = 0xd43e0000;
    private static final byte[] PUBLISH = HexFormat.of().parseHex(
            "307a0bd510820091307a0bd510820091307a0bd510820091307a0bd59f3f03d5df3f03d500003ed4");
    private final WindowsKdSession kd;
    private final Map<Long, Probe> probes = new HashMap<>();
    private KernelSymbols symbols;
    private long base, physical, runtime;
    private int lastCpu;
    private int pendingEntryMarkers;
    private ByteBuffer cpuOnContext;
    private boolean ipiReturned;
    private boolean online;

    private record Probe(int handle, Stop stop, byte[] original) {}

    WindowsKdSmpBootstrap(WindowsKdSession kd) { this.kd = kd; }

    boolean online() { return online; }

    void arm(long kernelBase, WindowsKdBootProfile.Build build) throws IOException {
        base = kernelBase;
        symbols = switch (build) {
            case WINPE_6584 -> WINPE_6584;
            case WINPE_9457 -> WINPE_9457;
        };
        probe(Stop.CPU_ON);
        probe(Stop.PMU);
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
        switch (probe.stop()) {
            case PMU -> {
                long midr = x(registers, 10);
                int counters = (int) ((x(registers, 8) >>> 11) & 31);
                require(x(registers, 20) == 0 && ((midr == 0x410fd821L && counters == 31)
                        || (midr == 0x410fd811L && counters == 20)), "BSP PMU");
                if (counters == 31) probe(Stop.ALLOCATE_PMU);
            }
            case ALLOCATE_PMU -> {
                require(x(registers, 20) == 0 && x(registers, 0) == 31
                        && x(registers, 19) == 31, "BSP counter allocation");
                kd.restoreContext(registers.putLong(8, 20));
            }
            case CPU_ON -> redirectCpuOn(registers);
            case NEXT_CPU -> {
                require(x(registers, 0) == lastCpu + 1 && lastCpu < 7, "next CPU_ON");
                probe(Stop.CPU_ON);
            }
            case STARTUP_WAIT -> {
                require(x(registers, 19) == 4, "Windows startup wait for CPU " + lastCpu);
                long marker = readLong(runtime + lastCpu * PAGE + 48);
                require(marker == 0 || marker == 1, "invalid entry marker for CPU " + lastCpu);
                // The native startup flag can become visible before our marker.
                // Recheck a zero after the final handshake, before reporting SMP online.
                if (marker == 0) pendingEntryMarkers |= 1 << lastCpu;
                if (lastCpu == 7) probe(Stop.START_HANDSHAKE);
            }
            case START_HANDSHAKE -> {
                require(lastCpu == 7 && x(registers, 0) == 4, "all secondary CPUs started");
                probe(Stop.HANDSHAKE_DONE);
            }
            case HANDSHAKE_DONE -> {
                probe(Stop.TIME_IPI);
                probe(Stop.START_RETURN);
            }
            case TIME_IPI -> configureIpi(registers);
            case TIME_IPI_RETURN -> ipiReturned = true;
            case START_RETURN -> {
                require(ipiReturned && readInt(base + symbols.numberProcessors()) == 8
                        && readInt(base + symbols.barrierWait()) == 0, "eight Windows processors online");
                for (int pending = pendingEntryMarkers; pending != 0; pending &= pending - 1) {
                    int cpu = Integer.numberOfTrailingZeros(pending);
                    require(readLong(runtime + cpu * PAGE + 48) == 1, "entry marker for CPU " + cpu);
                }
                online = true;
                android.util.Log.i("UAVF-Windows-Setup", "WINDOWS_SMP_ONLINE=8");
            }
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
                && x(registers, 1) == cpu && x(registers, 15) == base + symbols.psciHvcHandler()
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
        if (cpu < 7) probe(Stop.NEXT_CPU);
        probe(Stop.STARTUP_WAIT);
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
        require(lastCpu == 7 && x(registers, 0) == base + symbols.calibrateTime()
                && x(registers, 1) == registers.getLong(SP), "native time IPI");
        expect(base + symbols.ipiCheck(), "09f540b9c9002836");
        expect(base + symbols.ipiSetup().rva(), symbols.ipiSetup().bytes());
        expect(base + symbols.gicRegistration().rva(), symbols.gicRegistration().bytes());
        long controller = readLong(base + symbols.interruptController()), gic = readLong(base + symbols.gic());
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
        probe(Stop.TIME_IPI_RETURN);
    }

    private void probe(Stop stop) throws IOException {
        Instruction instruction = symbols == WINPE_9457 ? stop.winpe9457 : stop.winpe6584;
        long address = base + instruction.rva();
        byte[] original = HexFormat.of().parseHex(instruction.bytes());
        for (long other : probes.keySet()) require(Math.abs(other - address) >= 32, "breakpoint overlap");
        require(Arrays.equals(kd.read(address, 4), original), "original instruction " + instruction.bytes());
        probes.put(address, new Probe(kd.insertBreakpoint(address), stop, original));
    }

    private void remove(long pc, Probe probe) throws IOException {
        int status = kd.removeBreakpoint(probe.handle());
        require(status == 0 || (status == 0xc0000001
                && readInt(base + symbols.breakpointTable() + (probe.handle() - 1) * 40 + 32) == 0), "breakpoint removal");
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
