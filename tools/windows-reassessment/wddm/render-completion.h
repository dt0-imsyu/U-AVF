/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#pragma once
#include <ntddk.h>
#include <dispmprt.h>
#include "render-queue.h"

/* Interface and interrupt message remain valid until this worker drains. */
typedef struct {
    const DXGKRNL_INTERFACE *Interface;
    ULONG MessageNumber;
    UINT NodeOrdinal;
    UINT EngineOrdinal;
    volatile LONG LastCompletedFence;
    ULONG InterruptResources;
    BOOLEAN RouteVerified;
} MAVK_DXGK_COMPLETION;

/* Call from DxgkDdiStartDevice with an adapter-owned copy of the interface.
 * The render worker uses one scheduler node and one interrupt message. */
NTSTATUS MavkDxgkBindCompletion(MAVK_DXGK_COMPLETION *completion,
                               const DXGKRNL_INTERFACE *interface);
/* The PASSIVE_LEVEL worker calls this after StartDevice has returned. */
NTSTATUS MavkDxgkVerifyCompletionRoute(MAVK_DXGK_COMPLETION *completion);
NTSTATUS MavkDxgkReportRetired(MAVK_DXGK_COMPLETION *completion,
                               const struct mavk_submission *submission,
                               NTSTATUS failureStatus);
VOID MavkDxgkCompletionDpc(const MAVK_DXGK_COMPLETION *completion);
