// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.runtime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/** One serial reader per managed VM. A replacement viewport only replaces the observer. */
public final class VmConsole {
    @FunctionalInterface public interface Source { InputStream open() throws Exception; }
    public interface Observer {
        void onBytes(byte[] data, int length);
        void onFailure(Exception failure);
    }

    private Session session;

    public synchronized void attach(Object owner, Source source, File log, Observer observer)
            throws Exception {
        if (session != null && session.owner == owner) {
            session.observe(observer);
            return;
        }
        if (session != null) session.close();
        session = new Session(owner, source.open(), log, observer);
        session.start();
    }

    public synchronized void detach(Observer observer) {
        if (session != null) session.detach(observer);
    }

    private static final class Session {
        final Object owner;
        private final InputStream stream;
        private final File log;
        private Observer observer;
        private volatile boolean closed;

        Session(Object owner, InputStream stream, File log, Observer observer) {
            this.owner = owner;
            this.stream = stream;
            this.log = log;
            this.observer = observer;
        }

        void start() {
            Thread worker = new Thread(this::read, "U-AVF-managed-console");
            worker.setDaemon(true);
            worker.start();
        }

        synchronized void observe(Observer next) { observer = next; }

        synchronized void detach(Observer old) {
            if (observer == old) observer = null;
        }

        void close() {
            synchronized (this) {
                closed = true;
                observer = null;
            }
            try { stream.close(); } catch (IOException ignored) {}
        }

        private synchronized FileOutputStream openLog() throws IOException {
            return closed ? null : new FileOutputStream(log);
        }

        private void read() {
            try (InputStream input = stream; FileOutputStream output = openLog()) {
                if (output == null) return;
                byte[] buffer = new byte[4096];
                while (!closed) {
                    int count = input.read(buffer);
                    if (count < 0) return;
                    if (count == 0) continue;
                    Observer current;
                    synchronized (this) {
                        if (closed) return;
                        output.write(buffer, 0, count);
                        current = observer;
                    }
                    if (current != null) current.onBytes(buffer, count);
                }
            } catch (Exception failure) {
                notifyObserver(current -> current.onFailure(failure));
            }
        }

        private void notifyObserver(Consumer<Observer> callback) {
            Observer current;
            synchronized (this) { current = closed ? null : observer; }
            if (current != null) callback.accept(current);
        }
    }
}
