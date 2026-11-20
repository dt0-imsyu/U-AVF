/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#pragma once
#include <stdint.h>

/* Fixed pointer-free result. The IOCTL accepts no caller-provided commands. */
#define MAVK_IOCTL_ADMIT 0x00226004u
#define MAVK_IOCTL_ADMIT_CODEC 0x00226008u
#define MAVK_ADMISSION_VERSION 3u

enum mavk_admission_stage {
    MAVK_OPEN_DEVICE = 1,
    MAVK_OPEN_LISTENER,
    MAVK_BIND,
    MAVK_LISTEN,
    MAVK_ACCEPT,
    MAVK_CHECK_PEER,
    MAVK_HELLO,
    MAVK_DEVICE_IDENTITY,
    MAVK_GPU_COMPLETION,
    MAVK_DRAW,
    MAVK_COMPLETE,
    MAVK_CODEC_VERSION
};

struct mavk_admission {
    uint32_t version, stage, status;
    uint32_t max_payload, max_buffers, max_buffer_bytes;
    uint32_t api_version, driver_version, vendor, device, device_type;
    uint32_t checked_pixels, checked_words;
    char device_name[256];
    uint32_t codec_api_version;
};
