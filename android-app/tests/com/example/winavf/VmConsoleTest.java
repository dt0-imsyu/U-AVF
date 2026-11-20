// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.runtime.VmConsole;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class VmConsoleTest {
    public static void main(String[] args) throws Exception {
        var log = Files.createTempFile("uavf-vm-console-", ".log");
        var closed = new CountDownLatch(1);
        var input = new PipedInputStream() {
            @Override public void close() throws java.io.IOException {
                try { super.close(); } finally { closed.countDown(); }
            }
        };
        AtomicInteger opened = new AtomicInteger();
        AtomicReference<Exception> error = new AtomicReference<>();
        Object vm = new Object();
        var console = new VmConsole();
        VmConsole.Source source = () -> {
            opened.incrementAndGet();
            return input;
        };
        var before = new Delivery(error);
        var after = new Delivery(error);
        try (var write = new PipedOutputStream(input)) {
            console.attach(vm, source, log.toFile(), before);
            write.write("first\n".getBytes(StandardCharsets.US_ASCII));
            before.await();
            console.attach(vm, source, log.toFile(), after);
            console.detach(before);
            write.write("second\n".getBytes(StandardCharsets.US_ASCII));
            after.await();
            console.detach(after);
        } finally {
            if (!closed.await(5, TimeUnit.SECONDS)) throw new AssertionError("Console did not drain at EOF");
            String written = Files.readString(log);
            Files.delete(log);
            if (!written.equals("first\nsecond\n")) throw new AssertionError("Serial log was lost or rewritten");
        }
        if (opened.get() != 1 || before.count.get() != 1 || after.count.get() != 1 || error.get() != null)
            throw new AssertionError("Console reader or observer was not retained", error.get());
        System.out.println("VM_CONSOLE_TEST=PASS");
    }

    private static final class Delivery implements VmConsole.Observer {
        private final CountDownLatch ready = new CountDownLatch(1);
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicReference<Exception> failure;

        Delivery(AtomicReference<Exception> failure) { this.failure = failure; }
        @Override public void onBytes(byte[] data, int length) {
            count.incrementAndGet();
            ready.countDown();
        }
        @Override public void onFailure(Exception error) {
            failure.set(error);
            ready.countDown();
        }
        void await() throws InterruptedException {
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("No console data");
        }
    }
}
