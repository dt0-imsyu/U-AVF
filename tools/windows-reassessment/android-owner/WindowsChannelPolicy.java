package com.example.winavf;

/** Own Binder admission only; never a system/private AVF transaction. */
final class WindowsChannelPolicy {
    private static final long GIB = 1024L * 1024L * 1024L;
    private static final long RETAINED_WDDM_TEST_VOLUME = 0xc63ef6813ef669b7L;

    static boolean allowed(long port, boolean ready, boolean clipboardEnabled) {
        return ready && (port == 4055L || (port == 4054L && clipboardEnabled));
    }
    static boolean mavkAllowed(long port,boolean ready,boolean enabled){return ready&&enabled&&port==4057L;}

    static long windowsResumeReserve(boolean wddmAdmission, boolean mavkEnabled,
            long volumeSerial) {
        if (wddmAdmission) {
            if (!mavkEnabled || volumeSerial != RETAINED_WDDM_TEST_VOLUME)
                throw new SecurityException("WDDM admission requires the retained diagnostic NTFS volume");
            return 8 * GIB; // Existing diagnostic NTFS resume only, with room for sparse growth.
        }
        return 52 * GIB;
    }
}
