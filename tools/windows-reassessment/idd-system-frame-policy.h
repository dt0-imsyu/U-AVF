#pragma once
#include <stdint.h>

/* A borrowed frame is valid only until the next successful acquire. A
 * synchronous encoder must finish before returning. Never enqueue this
 * pointer or expose physical RAM/driver buffers to another process. */
struct UavfIddFrame {
    const unsigned char *pixels;
    uint32_t width, height, pitch;
    uint32_t presentation_number;
    uint64_t display_qpc;
};
static inline bool uavf_idd_valid(const UavfIddFrame &frame, unsigned format) {
    return frame.pixels && frame.width == 1920 && frame.height == 1200 &&
        frame.pitch >= 7680 && frame.pitch <= 16384 &&
        (format == 87 || format == 88) &&
        (reinterpret_cast<uintptr_t>(frame.pixels) & 15) == 0;
}
