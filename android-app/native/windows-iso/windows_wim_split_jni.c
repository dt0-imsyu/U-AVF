// SPDX-License-Identifier: LicenseRef-UAVF-1.0
#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <wimlib.h>

int uavf_wimlib_open_wim_fd(int fd, WIMStruct **wim_ret);

int uavf_wimlib_overlay_boot(const char *image, const char *overlay);

/*
 * A retail Windows ISO ships a boot.wim with exactly two images: index 1 is the
 * plain WinPE and index 2 is "Windows Setup".  The overlay is applied to index
 * 2, so a WIM without that second image is not a Setup medium at all.
 */
#define UAVF_SETUP_IMAGE_INDEX 2
/* PROCESSOR_ARCHITECTURE_ARM64, as stored numerically in the image's ARCH. */
#define UAVF_PROCESSOR_ARCHITECTURE_ARM64 "12"

/*
 * Identifies ARM64 Setup and checks its WinPE build against the boot profile.
 *
 * The checks use architecture, edition, the boot index and build metadata.
 * Localised display names are ignored:
 *
 *   - ARCH is the numeric PROCESSOR_ARCHITECTURE code, where 12 is ARM64;
 *   - EDITIONID "WindowsPE" identifies a WinPE-family image, which excludes a
 *     full Windows image such as install.wim;
 *   - boot_index is a binary field of the WIM header, not XML text, and equals
 *     the Setup index on every retail ISO seen so far.
 */
static int classify_setup_wim(WIMStruct *wim) {
    struct wimlib_wim_info info;
    int status = wimlib_get_wim_info(wim, &info);
    if (status)
        return -status;
    if (info.image_count < UAVF_SETUP_IMAGE_INDEX)
        return 0;

    const wimlib_tchar *architecture =
            wimlib_get_image_property(wim, UAVF_SETUP_IMAGE_INDEX, "WINDOWS/ARCH");
    if (!architecture || strcmp(architecture, UAVF_PROCESSOR_ARCHITECTURE_ARM64) != 0)
        return 0;

    const wimlib_tchar *edition =
            wimlib_get_image_property(wim, UAVF_SETUP_IMAGE_INDEX, "WINDOWS/EDITIONID");
    if (!edition || strcmp(edition, "WindowsPE") != 0)
        return 0;

    if (info.boot_index != UAVF_SETUP_IMAGE_INDEX)
        return 0;

    const wimlib_tchar *build =
            wimlib_get_image_property(wim, UAVF_SETUP_IMAGE_INDEX, "WINDOWS/VERSION/BUILD");
    const wimlib_tchar *revision =
            wimlib_get_image_property(wim, UAVF_SETUP_IMAGE_INDEX, "WINDOWS/VERSION/SPBUILD");
    // The existing KD boot profile covers this WinPE servicing revision.
    return build && revision && strcmp(build, "26100") == 0
        && (strcmp(revision, "6584") == 0 || strcmp(revision, "9457") == 0) ? 1 : 2;
}

JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsWimPayload_splitFd(
        JNIEnv *env, jclass clazz, jint fd, jstring destination, jlong part_bytes) {
    (void)clazz;
    if (!destination || part_bytes <= 0)
        return WIMLIB_ERR_INVALID_PARAM;
    const char *path = (*env)->GetStringUTFChars(env, destination, NULL);
    if (!path)
        return WIMLIB_ERR_NOMEM;
    WIMStruct *wim = NULL;
    int error = uavf_wimlib_open_wim_fd(fd, &wim);
    if (!error) {
        error = wimlib_split(wim, path, (uint64_t)part_bytes, 0);
        wimlib_free(wim);
    }
    (*env)->ReleaseStringUTFChars(env, destination, path);
    return error;
}

JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsWimPayload_overlayBootWim(
        JNIEnv *env, jclass clazz, jstring image, jstring overlay) {
    (void)clazz;
    if (!image || !overlay)
        return WIMLIB_ERR_INVALID_PARAM;
    const char *image_path = (*env)->GetStringUTFChars(env, image, NULL);
    if (!image_path)
        return WIMLIB_ERR_NOMEM;
    const char *overlay_path = (*env)->GetStringUTFChars(env, overlay, NULL);
    int status = WIMLIB_ERR_NOMEM;
    if (overlay_path) {
        status = uavf_wimlib_overlay_boot(image_path, overlay_path);
        (*env)->ReleaseStringUTFChars(env, overlay, overlay_path);
    }
    (*env)->ReleaseStringUTFChars(env, image, image_path);
    return status;
}

/*
 * Reports whether an already-open boot image can receive the overlay.
 *
 * The boot image is the only file that genuinely differs between Windows
 * releases and languages, so it cannot be pinned by digest without rejecting
 * every other release.  Its structure is stable instead, and is inspected
 * straight from the ISO descriptor so a foreign medium is refused before any
 * copy is made.
 *
 * Returns 1 for a supported Setup build, 2 for a Setup build without a boot
 * profile, 0 for foreign media, or a negative wimlib error code.
 */
JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsWimPayload_probeSetupImageFd(
        JNIEnv *env, jclass clazz, jint fd) {
    (void)env;
    (void)clazz;
    WIMStruct *wim = NULL;
    int status = uavf_wimlib_open_wim_fd(fd, &wim);
    if (status)
        return -status;
    int verdict = classify_setup_wim(wim);
    wimlib_free(wim);
    return verdict;
}

/* Same contract as probeSetupImageFd, for the private copy handed to the overlay. */
JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsWimPayload_probeSetupImage(
        JNIEnv *env, jclass clazz, jstring image) {
    (void)clazz;
    if (!image)
        return -WIMLIB_ERR_INVALID_PARAM;
    const char *path = (*env)->GetStringUTFChars(env, image, NULL);
    if (!path)
        return -WIMLIB_ERR_NOMEM;
    WIMStruct *wim = NULL;
    int status = wimlib_open_wim(path, 0, &wim);
    int verdict = status ? -status : classify_setup_wim(wim);
    if (wim)
        wimlib_free(wim);
    (*env)->ReleaseStringUTFChars(env, image, path);
    return verdict;
}

JNIEXPORT jstring JNICALL Java_com_example_winavf_WindowsWimPayload_errorText(
        JNIEnv *env, jclass clazz, jint error) {
    (void)clazz;
    return (*env)->NewStringUTF(env, wimlib_get_error_string(error));
}
