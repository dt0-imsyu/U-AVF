#include <jni.h>
#include <stdint.h>
#include <string.h>

/* Protocol BGRA bytes interpreted as little-endian jint are exactly AARRGGBB.
 * Copy into the numeric int[] accepted by Bitmap.setPixels(), NOT directly into
 * a Bitmap whose native channel order/premultiplication is implementation-specific.
 */
JNIEXPORT jboolean JNICALL Java_com_example_winavf_WavfNative_copyPixels(
        JNIEnv *env, jclass type, jbyteArray input, jintArray output) {
    (void)type;
    if (!input || !output) return JNI_FALSE;
    int bytes = (*env)->GetArrayLength(env, input);
    int pixels = (*env)->GetArrayLength(env, output);
    if (pixels < 0 || pixels > 4 * 1024 * 1024 || bytes != pixels * 4)
        return JNI_FALSE;
    jbyte *source = (*env)->GetPrimitiveArrayCritical(env, input, NULL);
    if (!source) return JNI_FALSE;
    jint *target = (*env)->GetPrimitiveArrayCritical(env, output, NULL);
    if (!target) {
        (*env)->ReleasePrimitiveArrayCritical(env, input, source, JNI_ABORT);
        return JNI_FALSE;
    }
#if __BYTE_ORDER__ == __ORDER_LITTLE_ENDIAN__
    memcpy(target, source, (size_t)bytes);
#else
    const uint8_t *p = (const uint8_t *)source;
    for (int i = 0; i < pixels; ++i, p += 4)
        target[i] = (jint)((uint32_t)p[0] | ((uint32_t)p[1] << 8) |
                         ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24));
#endif
    (*env)->ReleasePrimitiveArrayCritical(env, output, target, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, input, source, JNI_ABORT);
    return JNI_TRUE;
}

/* Bounded WAVF LZ4 block expansion. No speculative reads or wild copies. */
static int expand(const uint8_t *in, int length, uint8_t *out, int capacity) {
    int cursor = 0, written = 0;
    while (cursor < length) {
        unsigned token = in[cursor++];
        int literal = token >> 4;
        if (literal == 15) {
            unsigned extension;
            do {
                if (cursor == length) return -1;
                extension = in[cursor++];
                if (literal > capacity - (int)extension) return -1;
                literal += (int)extension;
            } while (extension == 255);
        }
        if (literal > length - cursor || literal > capacity - written) return -1;
        memcpy(out + written, in + cursor, (size_t)literal);
        cursor += literal;
        written += literal;
        if (cursor == length) break;
        if (length - cursor < 2) return -1;
        int distance = in[cursor] | ((int)in[cursor + 1] << 8);
        cursor += 2;
        if (distance == 0 || distance > written) return -1;
        int match = (token & 15) + 4;
        if ((token & 15) == 15) {
            unsigned extension;
            do {
                if (cursor == length) return -1;
                extension = in[cursor++];
                if (match > capacity - (int)extension) return -1;
                match += (int)extension;
            } while (extension == 255);
        }
        if (match > capacity - written) return -1;
        int initialized = distance;
        while (match > 0) {
            int count = match < initialized ? match : initialized;
            /* Source range ends at or before destination; memcpy is safe. */
            memcpy(out + written, out + written - initialized, (size_t)count);
            written += count;
            match -= count;
            initialized += count;
        }
    }
    return written == capacity ? written : -1;
}

JNIEXPORT jint JNICALL Java_com_example_winavf_WavfNative_expand(
        JNIEnv *env, jclass type, jbyteArray input, jint offset, jint length,
        jbyteArray output) {
    (void)type;
    if (!input || !output || input == output) return -1;
    int input_size = (*env)->GetArrayLength(env, input);
    int capacity = (*env)->GetArrayLength(env, output);
    if (offset < 0 || length < 0 || offset > input_size - length ||
            capacity > 16 * 1024 * 1024) return -1;
    jbyte *source = (*env)->GetPrimitiveArrayCritical(env, input, NULL);
    if (!source) return -1;
    jbyte *target = (*env)->GetPrimitiveArrayCritical(env, output, NULL);
    if (!target) {
        (*env)->ReleasePrimitiveArrayCritical(env, input, source, JNI_ABORT);
        return -1;
    }
    int result = expand((const uint8_t *)source + offset, length,
                        (uint8_t *)target, capacity);
    (*env)->ReleasePrimitiveArrayCritical(env, output, target, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, input, source, JNI_ABORT);
    return result;
}
