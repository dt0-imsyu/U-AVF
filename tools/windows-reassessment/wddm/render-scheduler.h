/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#pragma once
#include "codec-buffer.h"
#include "codec-device.h"
#include "render-worker.h"

typedef struct MAVK_SCHEDULER MAVK_SCHEDULER;

typedef struct {
    MAVK_SCHEDULER *Owner;
    MAVK_RENDER_BINDING Binding;
    ULONGLONG CommandPool;
    EX_RUNDOWN_REF Lifetime;
} MAVK_SCHEDULER_CONTEXT;

/* Created by the KMD only after opening this allocation on the Vulkan device.
 * The allocation stays pinned until GPU retirement or adapter recovery. */
typedef struct {
    MAVK_SCHEDULER *Owner;
    ULONGLONG Device;
    ULONGLONG WireGeneration;
    EX_RUNDOWN_REF Lifetime;
    MAVK_CODEC_BUFFER Native;
} MAVK_RENDER_ALLOCATION;

typedef struct {
    MAVK_SCHEDULER_CONTEXT *Context;
    ULONGLONG WireGeneration;
    ULONGLONG Handle;
    EX_RUNDOWN_REF Lifetime;
} MAVK_RECORDED_COMMAND;

typedef struct {
    LIST_ENTRY Link;
    MAVK_SCHEDULER_CONTEXT *Context;
    MAVK_RECORDED_COMMAND *Command;
    UINT Fence;
    UINT AllocationCount;
    MAVK_RENDER_ALLOCATION *Allocations[];
} MAVK_QUEUED_RENDER;

struct MAVK_SCHEDULER {
    KSPIN_LOCK Lock;
    KEVENT Wake;
    LIST_ENTRY Pending;
    DXGKRNL_INTERFACE Interface;
    MAVK_DXGK_COMPLETION Completion;
    /* The PASSIVE_LEVEL owner alone reads, writes or closes the wire. */
    MAVK_VIOSOCK Wire;
    MAVK_CODEC_DEVICE Device;
    ULONGLONG NextContext;
    ULONG ContextCount;
    ULONG AllocationCount;
    struct mavk_render_queue Retiring;
};

VOID MavkSchedulerInitialize(MAVK_SCHEDULER *scheduler);
NTSTATUS MavkSchedulerStartSession(MAVK_SCHEDULER *scheduler, USHORT port, ULONGLONG deadline);
/* Move to the next generation after draining any old session resources.
 * These calls share the same PASSIVE_LEVEL owner as the transport. */
NTSTATUS MavkSchedulerInitializeContext(MAVK_SCHEDULER *scheduler, ULONGLONG deadline,
                                       MAVK_SCHEDULER_CONTEXT *context);
/* Unpublish the context and drain pinned submissions before requesting
 * destruction on the wire owner. The owner must remain free to retire work. */
NTSTATUS MavkSchedulerDestroyContext(MAVK_SCHEDULER *scheduler, ULONGLONG deadline,
                                    MAVK_SCHEDULER_CONTEXT *context);
NTSTATUS MavkSchedulerCloseSession(MAVK_SCHEDULER *scheduler, ULONGLONG deadline);
DXGKDDI_QUERYCURRENTFENCE MavkDdiQueryCurrentFence;
NTSTATUS MavkSchedulerCreateBuffer(MAVK_SCHEDULER_CONTEXT *context, ULONGLONG deadline,
    ULONGLONG size, ULONG usage, ULONG memoryProperties, MAVK_RENDER_ALLOCATION *allocation);
/* Remove the allocation from handle lookup and drain Lifetime outside the
 * wire-owning worker before sending the destruction request on that worker. */
NTSTATUS MavkSchedulerDestroyBuffer(MAVK_SCHEDULER *scheduler, ULONGLONG deadline,
                                   MAVK_RENDER_ALLOCATION *allocation);

/* Called by Render on already resolved KMD handles, with the source objects
 * stabilized by VidMm or by references taken during handle-table lookup. One reference
 * per allocation entry is held in the resulting nonpaged work record.
 * Context and resource owners initialize their Lifetime when exposed and
 * wait for rundown before destroying or changing native handles. */
NTSTATUS MavkSchedulerPrepareRender(MAVK_SCHEDULER_CONTEXT *context,
                                   MAVK_RECORDED_COMMAND *command, UINT allocationCount,
                                   MAVK_RENDER_ALLOCATION *const *allocations,
                                   MAVK_QUEUED_RENDER **work);
VOID MavkSchedulerReleaseRender(MAVK_QUEUED_RENDER *work);

/* The Submit DDI queues the nonpaged record prepared by Render directly.
 * No allocation, buffer quota or network I/O is required on enqueue. */
VOID MavkSchedulerEnqueue(MAVK_SCHEDULER *scheduler, MAVK_QUEUED_RENDER *work, UINT fence);

/* Worker runs only after the completion route and codec are initialized.
 * Ownership of the record returns to the caller after dequeue. Keep its context
 * and allocations pinned through recovery when this function reports failure. */
NTSTATUS MavkSchedulerRunNext(MAVK_SCHEDULER *scheduler, ULONGLONG deadline,
                             MAVK_QUEUED_RENDER **outstanding);
