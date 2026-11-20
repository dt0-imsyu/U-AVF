/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#pragma once
#include "mavk-viosock.h"

NTSTATUS MavkCreateCodecFence(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                              ULONGLONG device, ULONGLONG *fence);
NTSTATUS MavkDestroyCodecFence(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                               ULONGLONG device, ULONGLONG fence);

/* Codec 3, queue-submit-with-commands framing disabled. Both handles must
 * come from the owner's encoder on this connection, in its device namespace.
 * The worker calls this after submitting the work against this Vulkan fence. */
NTSTATUS MavkWaitCodecFence(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                            ULONGLONG device, ULONGLONG fence);

/* One already recorded primary command buffer. The caller owns these
 * handles and supplies an unsignaled, idle fence on the same device. */
NTSTATUS MavkSubmitCodecCommand(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                                ULONGLONG queue, ULONGLONG command, ULONGLONG fence);
