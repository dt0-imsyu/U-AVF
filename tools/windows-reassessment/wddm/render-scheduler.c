/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#include "render-scheduler.h"

#define MAVK_RENDER_POOL_TAG 'rmVU'
/* Matches the initial UMD allocation list contract. */
#define MAVK_RENDER_MAX_ALLOCATIONS DXGK_ALLOCATION_LIST_SIZE_GDICONTEXT

VOID MavkSchedulerInitialize(MAVK_SCHEDULER *scheduler)
{
    RtlZeroMemory(scheduler, sizeof(*scheduler));
    KeInitializeSpinLock(&scheduler->Lock);
    KeInitializeEvent(&scheduler->Wake, NotificationEvent, FALSE);
    InitializeListHead(&scheduler->Pending);
}

NTSTATUS MavkSchedulerStartSession(MAVK_SCHEDULER *scheduler, USHORT port, ULONGLONG deadline)
{
    if (scheduler->Retiring.count || scheduler->Device.Generation ||
        scheduler->ContextCount || scheduler->AllocationCount)
        return STATUS_INVALID_DEVICE_STATE;
    NTSTATUS status = MavkDxgkVerifyCompletionRoute(&scheduler->Completion);
    if (!NT_SUCCESS(status)) return status;
    status = MavkWireOpen(&scheduler->Wire, port, deadline);
    if (!NT_SUCCESS(status)) return status;

    /* Confirm that the endpoint enables codec 3 on the newly opened stream.
     * A legacy MVK1 endpoint also accepts the initial wire handshake. */
    ULONG apiVersion;
    status = MavkWireQueryCodecVersion(&scheduler->Wire, deadline, &apiVersion);
    if (NT_SUCCESS(status) && apiVersion < 0x00401000) status = STATUS_NOT_SUPPORTED;
    if (NT_SUCCESS(status))
        status = MavkCodecOpenDevice(&scheduler->Wire, deadline, &scheduler->Device);
    if (!NT_SUCCESS(status)) MavkWireClose(&scheduler->Wire);
    else scheduler->Retiring.disconnected = false;
    return status;
}

NTSTATUS MavkSchedulerInitializeContext(MAVK_SCHEDULER *scheduler, ULONGLONG deadline,
                                       MAVK_SCHEDULER_CONTEXT *context)
{
    const MAVK_CODEC_DEVICE *device = &scheduler->Device;
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !scheduler->Wire.Peer || !device->Queue ||
        device->Generation != scheduler->Wire.Generation || scheduler->NextContext == MAXULONGLONG ||
        scheduler->ContextCount == MAXULONG)
        return STATUS_INVALID_DEVICE_STATE;

    ULONGLONG pool;
    NTSTATUS status = MavkCodecCreateCommandPool(&scheduler->Wire, deadline, device, &pool);
    if (!NT_SUCCESS(status)) return status;
    *context = (MAVK_SCHEDULER_CONTEXT){
        .Owner = scheduler,
        .CommandPool = pool,
        .Binding = {
            .ContextId = ++scheduler->NextContext,
            .WireGeneration = device->Generation,
            .PhysicalDevice = device->PhysicalDevice,
            .Device = device->Device,
            .Queue = device->Queue,
        },
    };
    ExInitializeRundownProtection(&context->Lifetime);
    ++scheduler->ContextCount;
    return STATUS_SUCCESS;
}

NTSTATUS MavkSchedulerDestroyContext(MAVK_SCHEDULER *scheduler, ULONGLONG deadline,
                                    MAVK_SCHEDULER_CONTEXT *context)
{
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || context->Owner != scheduler ||
        !context->CommandPool || !scheduler->ContextCount)
        return STATUS_INVALID_DEVICE_STATE;

    NTSTATUS status = STATUS_SUCCESS;
    if (scheduler->Wire.Generation == context->Binding.WireGeneration)
        status = MavkCodecDestroyCommandPool(&scheduler->Wire, deadline, &scheduler->Device,
                                            context->CommandPool);
    context->CommandPool = 0;
    context->Owner = NULL;
    --scheduler->ContextCount;
    return status;
}

NTSTATUS MavkSchedulerCloseSession(MAVK_SCHEDULER *scheduler, ULONGLONG deadline)
{
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || scheduler->Retiring.count ||
        scheduler->ContextCount || scheduler->AllocationCount)
        return STATUS_INVALID_DEVICE_STATE;
    NTSTATUS status = MavkCodecCloseDevice(&scheduler->Wire, deadline, &scheduler->Device);
    MavkWireClose(&scheduler->Wire);
    return status;
}

NTSTATUS MavkSchedulerCreateBuffer(MAVK_SCHEDULER_CONTEXT *context, ULONGLONG deadline,
    ULONGLONG size, ULONG usage, ULONG memoryProperties, MAVK_RENDER_ALLOCATION *allocation)
{
    MAVK_SCHEDULER *scheduler = context->Owner;
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !scheduler || !context->CommandPool ||
        scheduler->AllocationCount == MAXULONG)
        return STATUS_INVALID_DEVICE_STATE;
    MAVK_VIOSOCK *wire = &scheduler->Wire;
    const MAVK_RENDER_BINDING *binding = &context->Binding;
    if (!wire->Peer || wire->Generation != binding->WireGeneration)
        return STATUS_DEVICE_NOT_READY;

    MAVK_CODEC_BUFFER buffer = {0};
    NTSTATUS status = MavkCodecCreateBuffer(wire, deadline, binding->PhysicalDevice,
        binding->Device, size, usage, memoryProperties, &buffer);
    if (!NT_SUCCESS(status)) return status;
    *allocation = (MAVK_RENDER_ALLOCATION){
        .Owner = context->Owner,
        .Device = binding->Device,
        .WireGeneration = binding->WireGeneration,
        .Native = buffer,
    };
    ExInitializeRundownProtection(&allocation->Lifetime);
    ++scheduler->AllocationCount;
    return STATUS_SUCCESS;
}

NTSTATUS MavkSchedulerDestroyBuffer(MAVK_SCHEDULER *scheduler, ULONGLONG deadline,
                                   MAVK_RENDER_ALLOCATION *allocation)
{
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || allocation->Owner != scheduler ||
        !scheduler->AllocationCount)
        return STATUS_INVALID_PARAMETER;
    NTSTATUS status = STATUS_SUCCESS;
    if (scheduler->Wire.Generation == allocation->WireGeneration)
        status = MavkCodecDestroyBuffer(&scheduler->Wire, deadline, allocation->Device,
                                       &allocation->Native);
    else
        /* The disconnected decoder owns cleanup of its Vulkan objects. */
        RtlZeroMemory(&allocation->Native, sizeof(allocation->Native));
    allocation->Owner = NULL;
    --scheduler->AllocationCount;
    return status;
}

VOID MavkSchedulerReleaseRender(MAVK_QUEUED_RENDER *work)
{
    for (UINT i = work->AllocationCount; i > 0; --i)
        ExReleaseRundownProtection(&work->Allocations[i - 1]->Lifetime);
    ExReleaseRundownProtection(&work->Command->Lifetime);
    ExReleaseRundownProtection(&work->Context->Lifetime);
    ExFreePoolWithTag(work, MAVK_RENDER_POOL_TAG);
}

NTSTATUS MavkSchedulerPrepareRender(MAVK_SCHEDULER_CONTEXT *context,
                                   MAVK_RECORDED_COMMAND *command, UINT allocationCount,
                                   MAVK_RENDER_ALLOCATION *const *allocations,
                                   MAVK_QUEUED_RENDER **work)
{
    *work = NULL;
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !context || !command ||
        allocationCount > MAVK_RENDER_MAX_ALLOCATIONS || (allocationCount && !allocations))
        return STATUS_INVALID_PARAMETER;

    const MAVK_RENDER_BINDING *binding = &context->Binding;
    if (command->Context != context || !command->Handle || !context->Owner ||
        !binding->ContextId || !binding->Device || !binding->Queue || !binding->WireGeneration ||
        command->WireGeneration != binding->WireGeneration)
        return STATUS_INVALID_HANDLE;

    SIZE_T size = FIELD_OFFSET(MAVK_QUEUED_RENDER, Allocations)
                + sizeof(MAVK_RENDER_ALLOCATION *) * allocationCount;
    MAVK_QUEUED_RENDER *record = ExAllocatePool2(POOL_FLAG_NON_PAGED, size, MAVK_RENDER_POOL_TAG);
    if (!record) return STATUS_NO_MEMORY;

    if (!ExAcquireRundownProtection(&context->Lifetime)) {
        ExFreePoolWithTag(record, MAVK_RENDER_POOL_TAG);
        return STATUS_DEVICE_REMOVED;
    }
    if (!ExAcquireRundownProtection(&command->Lifetime)) {
        ExReleaseRundownProtection(&context->Lifetime);
        ExFreePoolWithTag(record, MAVK_RENDER_POOL_TAG);
        return STATUS_DEVICE_REMOVED;
    }
    record->Context = context;
    record->Command = command;
    record->AllocationCount = 0;
    for (UINT i = 0; i < allocationCount; ++i) {
        MAVK_RENDER_ALLOCATION *allocation = allocations[i];
        if (!allocation || allocation->Owner != context->Owner ||
            allocation->Device != binding->Device ||
            allocation->WireGeneration != binding->WireGeneration ||
            !ExAcquireRundownProtection(&allocation->Lifetime)) {
            MavkSchedulerReleaseRender(record);
            return STATUS_INVALID_HANDLE;
        }
        record->Allocations[record->AllocationCount++] = allocation;
    }
    *work = record;
    return STATUS_SUCCESS;
}

VOID MavkSchedulerEnqueue(MAVK_SCHEDULER *scheduler, MAVK_QUEUED_RENDER *work, UINT fence)
{
    KIRQL level;
    work->Fence = fence;
    KeAcquireSpinLock(&scheduler->Lock, &level);
    InsertTailList(&scheduler->Pending, &work->Link);
    KeSetEvent(&scheduler->Wake, IO_NO_INCREMENT, FALSE);
    KeReleaseSpinLock(&scheduler->Lock, level);
}

NTSTATUS APIENTRY MavkDdiQueryCurrentFence(CONST HANDLE adapter, INOUT_PDXGKARG_QUERYCURRENTFENCE current)
{
    MAVK_SCHEDULER *scheduler = adapter;
    if (current->NodeOrdinal || current->EngineOrdinal) return STATUS_INVALID_PARAMETER;
    current->CurrentFence = (UINT)InterlockedCompareExchange(&scheduler->Completion.LastCompletedFence, 0, 0);
    return STATUS_SUCCESS;
}

NTSTATUS MavkSchedulerRunNext(MAVK_SCHEDULER *scheduler, ULONGLONG deadline,
                             MAVK_QUEUED_RENDER **outstanding)
{
    *outstanding = NULL;
    if (KeGetCurrentIrql() != PASSIVE_LEVEL) return STATUS_INVALID_DEVICE_STATE;
    KIRQL level;
    KeAcquireSpinLock(&scheduler->Lock, &level);
    if (IsListEmpty(&scheduler->Pending)) {
        KeClearEvent(&scheduler->Wake);
        KeReleaseSpinLock(&scheduler->Lock, level);
        return STATUS_NO_MORE_ENTRIES;
    }
    LIST_ENTRY *entry = RemoveHeadList(&scheduler->Pending);
    KeReleaseSpinLock(&scheduler->Lock, level);

    MAVK_QUEUED_RENDER *work = CONTAINING_RECORD(entry, MAVK_QUEUED_RENDER, Link);
    *outstanding = work;
    if (work->Context->Owner != scheduler) return STATUS_INVALID_HANDLE;
    const MAVK_RENDER_BINDING *binding = &work->Context->Binding;
    scheduler->Retiring.context_id = binding->ContextId;
    const MAVK_RENDER_COMMAND command = {
        .ContextId = binding->ContextId,
        .WireGeneration = work->Command->WireGeneration,
        .Command = work->Command->Handle,
        .SubmissionFence = work->Fence,
        .AllocationRefs = work,
    };
    struct mavk_submission retired;
    NTSTATUS gpuStatus;
    return MavkRenderExecute(&scheduler->Wire, &scheduler->Retiring,
        &scheduler->Completion, deadline, binding, &command, &gpuStatus, &retired);
}
