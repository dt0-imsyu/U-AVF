/* Shell-context feasibility probe, not an APK access or throughput claim. */
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

static double now_ms(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1000.0 + t.tv_nsec / 1000000.0;
}

static void probe_frame(AMediaCodec *codec) {
    ssize_t index = AMediaCodec_dequeueInputBuffer(codec, 500000);
    if (index < 0) { printf(" INPUT_UNAVAILABLE"); return; }
    size_t capacity = 0;
    uint8_t *input = AMediaCodec_getInputBuffer(codec, (size_t)index, &capacity);
    size_t size = 1920 * 1200 * 3 / 2;
    if (!input || capacity < size) { printf(" INPUT_TOO_SMALL=%zu", capacity); return; }
    /* Uniform neutral image is layout-independent for planar/semiplanar YUV. */
    memset(input, 128, size);
    double started = now_ms();
    AMediaCodec_queueInputBuffer(codec, (size_t)index, 0, size, 12345, 0);
    while (now_ms() - started < 2000) {
        AMediaCodecBufferInfo info;
        ssize_t output = AMediaCodec_dequeueOutputBuffer(codec, &info, 10000);
        if (output >= 0) {
            int frame = info.size > 0 && !(info.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG);
            AMediaCodec_releaseOutputBuffer(codec, (size_t)output, false);
            if (frame) { printf(" FIRST_NEUTRAL_FRAME_MS=%.2f BYTES=%d", now_ms()-started, info.size); return; }
        }
    }
    printf(" FIRST_FRAME_TIMEOUT");
}

int main(void) {
    const char *mimes[] = {"video/avc", "video/hevc"};
    for (int i = 0; i < 2; ++i) {
        for (int encoder = 0; encoder < 2; ++encoder) {
            alarm(12); /* Bound vendor calls, not only dequeue polling. */
            AMediaCodec *codec = encoder ? AMediaCodec_createEncoderByType(mimes[i])
                                        : AMediaCodec_createDecoderByType(mimes[i]);
            if (!codec) { printf("%s encoder=%d CREATE=FAIL\n", mimes[i], encoder); continue; }
            char *name = NULL;
            AMediaCodec_getName(codec, &name);
            AMediaFormat *format = AMediaFormat_new();
            AMediaFormat_setString(format, "mime", mimes[i]);
            AMediaFormat_setInt32(format, "width", 1920);
            AMediaFormat_setInt32(format, "height", 1200);
            AMediaFormat_setInt32(format, "frame-rate", 60);
            if (encoder) {
                AMediaFormat_setInt32(format, "bitrate", 20000000);
                AMediaFormat_setInt32(format, "i-frame-interval", 1);
                AMediaFormat_setInt32(format, "color-format", 0x7f420888);
                AMediaFormat_setInt32(format, "max-bframes", 0);
            } else {
                AMediaFormat_setInt32(format, "low-latency", 1);
            }
            media_status_t status = AMediaCodec_configure(codec, format, NULL, NULL,
                                      encoder ? AMEDIACODEC_CONFIGURE_FLAG_ENCODE : 0);
            printf("%s encoder=%d name=%s CONFIGURE=%d", mimes[i], encoder,
                   name ? name : "unknown", status);
            fflush(stdout);
            if (status == AMEDIA_OK) {
                status = AMediaCodec_start(codec);
                printf(" START=%d", status);
                fflush(stdout);
                if (status == AMEDIA_OK) {
                    if (encoder) probe_frame(codec);
                    AMediaCodec_stop(codec);
                }
            }
            printf("\n"); fflush(stdout);
            if (name) AMediaCodec_releaseName(codec, name);
            AMediaFormat_delete(format);
            AMediaCodec_delete(codec);
            alarm(0);
        }
    }
    return 0;
}
