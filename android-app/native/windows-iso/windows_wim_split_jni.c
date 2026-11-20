// SPDX-License-Identifier: LicenseRef-UAVF-1.0
#include <jni.h>
#include <stdint.h>
#include <wimlib.h>

int uavf_wimlib_open_wim_fd(int fd, WIMStruct **wim_ret);

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

JNIEXPORT jstring JNICALL Java_com_example_winavf_WindowsWimPayload_errorText(
        JNIEnv *env, jclass clazz, jint error) {
    (void)clazz;
    return (*env)->NewStringUTF(env, wimlib_get_error_string(error));
}
