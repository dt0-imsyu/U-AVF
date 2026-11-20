/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#pragma once
#include "mavk-viosock.h"

typedef struct {
    ULONGLONG Generation;
    ULONGLONG Instance;
    ULONGLONG PhysicalDevice;
    ULONGLONG Device;
    ULONGLONG Queue;
    ULONG Family;
} MAVK_CODEC_DEVICE;

/* Both run on the stream owner. Close after draining work and child objects. */
NTSTATUS MavkCodecOpenDevice(MAVK_VIOSOCK *wire, ULONGLONG deadline, MAVK_CODEC_DEVICE *device);
NTSTATUS MavkCodecCloseDevice(MAVK_VIOSOCK *wire, ULONGLONG deadline, MAVK_CODEC_DEVICE *device);
NTSTATUS MavkCodecCreateCommandPool(MAVK_VIOSOCK *wire, ULONGLONG deadline,
    const MAVK_CODEC_DEVICE *device, ULONGLONG *pool);
NTSTATUS MavkCodecDestroyCommandPool(MAVK_VIOSOCK *wire, ULONGLONG deadline,
    const MAVK_CODEC_DEVICE *device, ULONGLONG pool);
