/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#pragma once
#include "mavk-viosock.h"

typedef struct {
    ULONGLONG Buffer;
    ULONGLONG Memory;
    ULONGLONG Size;
} MAVK_CODEC_BUFFER;

/* Boxed handles come from the same codec session. The caller supplies core
 * Vulkan usage bits and required Vulkan memory property flags. */
NTSTATUS MavkCodecCreateBuffer(MAVK_VIOSOCK *wire, ULONGLONG deadline,
    ULONGLONG physicalDevice, ULONGLONG device, ULONGLONG size, ULONG usage,
    ULONG requiredProperties, MAVK_CODEC_BUFFER *buffer);
NTSTATUS MavkCodecDestroyBuffer(MAVK_VIOSOCK *wire, ULONGLONG deadline,
    ULONGLONG device, MAVK_CODEC_BUFFER *buffer);
