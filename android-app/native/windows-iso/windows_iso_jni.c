// SPDX-License-Identifier: LicenseRef-UAVF-1.0
#include "windows_iso.h"
#include <jni.h>

static struct windows_iso_payload *handle(jlong value) {
    return (struct windows_iso_payload *)(uintptr_t)value;
}

JNIEXPORT jlongArray JNICALL Java_com_example_winavf_WindowsInstallIso_probeFd(
        JNIEnv *env, jclass clazz, jint fd) {
    (void)clazz;
    struct windows_iso_info info;
    enum windows_iso_status status = windows_iso_probe(fd, &info);
    jlong result[] = {status, info.iso_bytes, info.boot_wim_bytes, info.install_bytes, info.install_is_esd};
    jlongArray array = (*env)->NewLongArray(env, sizeof(result) / sizeof(result[0]));
    if (array)
        (*env)->SetLongArrayRegion(env, array, 0, sizeof(result) / sizeof(result[0]), result);
    return array;
}

JNIEXPORT jlong JNICALL Java_com_example_winavf_WindowsInstallIso_openPayloadFd(
        JNIEnv *env, jclass clazz, jint fd, jboolean boot) {
    (void)env;
    (void)clazz;
    return (jlong)(uintptr_t)windows_iso_payload_open(fd, boot);
}

JNIEXPORT jlong JNICALL Java_com_example_winavf_WindowsInstallIso_openFileFd(
        JNIEnv *env, jclass clazz, jint fd, jstring path) {
    (void)clazz;
    if (!path)
        return 0;
    const char *name = (*env)->GetStringUTFChars(env, path, NULL);
    if (!name)
        return 0;
    struct windows_iso_payload *file = windows_iso_file_open(fd, name);
    (*env)->ReleaseStringUTFChars(env, path, name);
    return (jlong)(uintptr_t)file;
}

JNIEXPORT jlong JNICALL Java_com_example_winavf_WindowsInstallIso_payloadSize(
        JNIEnv *env, jclass clazz, jlong value) {
    (void)env;
    (void)clazz;
    return windows_iso_payload_size(handle(value));
}

JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsInstallIso_readPayload(
        JNIEnv *env, jclass clazz, jlong value, jlong offset, jint size, jbyteArray output) {
    (void)clazz;
    if (!output || size < 0 || (*env)->GetArrayLength(env, output) < size)
        return -1;
    jbyte *bytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (!bytes)
        return -1;
    int count = windows_iso_payload_read(handle(value), offset, bytes, size);
    (*env)->ReleaseByteArrayElements(env, output, bytes, count < 0 ? JNI_ABORT : 0);
    return count;
}

JNIEXPORT void JNICALL Java_com_example_winavf_WindowsInstallIso_closePayload(
        JNIEnv *env, jclass clazz, jlong value) {
    (void)env;
    (void)clazz;
    windows_iso_payload_close(handle(value));
}
