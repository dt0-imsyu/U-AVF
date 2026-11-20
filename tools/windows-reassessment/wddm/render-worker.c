/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#include "render-worker.h"

NTSTATUS MavkRenderExecute(MAVK_VIOSOCK *wire, struct mavk_render_queue *pending,
                           MAVK_DXGK_COMPLETION *completion, ULONGLONG deadline,
                           const MAVK_RENDER_BINDING *binding,
                           const MAVK_RENDER_COMMAND *command, NTSTATUS *gpuStatus,
                           struct mavk_submission *retired)
{
    *gpuStatus = STATUS_INVALID_DEVICE_STATE;
    *retired = (struct mavk_submission){0};
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || pending->count ||
        !completion->Interface || !wire->Peer || !wire->Generation ||
        wire->Generation != binding->WireGeneration ||
        wire->Generation != command->WireGeneration ||
        command->ContextId != binding->ContextId ||
        command->ContextId != pending->context_id ||
        !binding->Device || !binding->Queue || !command->Command)
        return STATUS_INVALID_DEVICE_STATE;

    NTSTATUS route = MavkDxgkVerifyCompletionRoute(completion);
    if (!NT_SUCCESS(route)) return route;

    uint64_t serial = mavk_enqueue(pending, command->SubmissionFence, command->AllocationRefs);
    if (!serial) return STATUS_INVALID_DEVICE_STATE;

    ULONGLONG fence;
    NTSTATUS execution = MavkCreateCodecFence(wire, deadline, binding->Device, &fence);
    if (NT_SUCCESS(execution))
        execution = MavkSubmitCodecCommand(wire, deadline, binding->Queue,
                                           command->Command, fence);
    if (NT_SUCCESS(execution))
        execution = MavkWaitCodecFence(wire, deadline, binding->Device, fence);
    if (NT_SUCCESS(execution) && !mavk_gpu_completed(pending, command->ContextId, serial))
        execution = STATUS_DEVICE_PROTOCOL_ERROR;

    /* Only a completed submit can destroy its fence here. On failure the
     * host session owns cleanup, including work the GPU may still be using. */
    NTSTATUS cleanup = execution;
    if (NT_SUCCESS(execution))
        cleanup = MavkDestroyCodecFence(wire, deadline, binding->Device, fence);
    if (!NT_SUCCESS(cleanup)) {
        pending->disconnected = true;
        MavkWireClose(wire);
    }

    if (!mavk_retire(pending, retired)) {
        pending->disconnected = true;
        MavkWireClose(wire);
        return STATUS_DEVICE_PROTOCOL_ERROR;
    }
    *gpuStatus = execution;
    NTSTATUS notification = MavkDxgkReportRetired(completion, retired, execution);
    if (!NT_SUCCESS(notification)) {
        pending->disconnected = true;
        MavkWireClose(wire);
    }
    return NT_SUCCESS(notification) ? cleanup : notification;
}
