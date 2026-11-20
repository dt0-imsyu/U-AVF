/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#pragma once
#include "codec-fence.h"
#include "render-completion.h"

typedef struct {
    ULONGLONG ContextId;
    ULONGLONG WireGeneration;
    ULONGLONG PhysicalDevice;
    ULONGLONG Device;
    ULONGLONG Queue;
} MAVK_RENDER_BINDING;

typedef struct {
    ULONGLONG ContextId;
    ULONGLONG WireGeneration;
    ULONGLONG Command;
    UINT SubmissionFence;
    VOID *AllocationRefs;
} MAVK_RENDER_COMMAND;

/* Executed by the one PASSIVE_LEVEL owner of this stream and render queue.
 * The caller keeps packet and allocation storage live across this call.
 * When retired->serial is nonzero, retired returns the allocation reference
 * ownership to the caller, including on cleanup or notification failure.
 * If a submission remains in pending, recovery owns its references.
 * Keep references live through recovery if the notification fails or the
 * submission did not reach confirmed GPU completion. The KMD supplies the
 * binding and resolves Command from its own table; stamp both records when
 * creating the corresponding host objects on this stream. Reopening the
 * stream invalidates the old binding and all previously stamped commands.
 * The worker owns the host fence for this submission. On execution,
 * fence cleanup or notification failure it closes the stream. The caller
 * must recreate its context before processing any further work. */
NTSTATUS MavkRenderExecute(MAVK_VIOSOCK *wire, struct mavk_render_queue *pending,
                           MAVK_DXGK_COMPLETION *completion, ULONGLONG deadline,
                           const MAVK_RENDER_BINDING *binding,
                           const MAVK_RENDER_COMMAND *command, NTSTATUS *gpuStatus,
                           struct mavk_submission *retired);
