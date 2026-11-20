/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#include "render-completion.h"

typedef struct {
    MAVK_DXGK_COMPLETION *Completion;
    DXGKARGCB_NOTIFY_INTERRUPT_DATA Data;
} MAVK_NOTIFY;

static BOOLEAN CheckInterruptLevel(PVOID unused)
{
    UNREFERENCED_PARAMETER(unused);
    KIRQL level = KeGetCurrentIrql();
    return level > DISPATCH_LEVEL && level < PROFILE_LEVEL;
}

NTSTATUS MavkDxgkBindCompletion(MAVK_DXGK_COMPLETION *completion,
                               const DXGKRNL_INTERFACE *interface)
{
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !interface ||
        !interface->DxgkCbGetDeviceInformation || !interface->DxgkCbSynchronizeExecution ||
        !interface->DxgkCbNotifyInterrupt || !interface->DxgkCbQueueDpc ||
        !interface->DxgkCbNotifyDpc)
        return STATUS_INVALID_DEVICE_STATE;

    DXGK_DEVICE_INFO info = {0};
    NTSTATUS status = interface->DxgkCbGetDeviceInformation(interface->DeviceHandle, &info);
    if (!NT_SUCCESS(status)) return status;

    /* Log resource evidence when Dxgkrnl cannot run our callback at DIRQL. */
    const CM_RESOURCE_LIST *resources = info.TranslatedResourceList;
    ULONG interrupts = 0;
    if (resources && resources->Count == 1) {
        const CM_PARTIAL_RESOURCE_LIST *partial = &resources->List[0].PartialResourceList;
        for (ULONG i = 0; i < partial->Count; ++i) {
            if (partial->PartialDescriptors[i].Type == CmResourceTypeInterrupt)
                ++interrupts;
        }
    }
    *completion = (MAVK_DXGK_COMPLETION){
        .Interface = interface, .InterruptResources = interrupts
    };
    return STATUS_SUCCESS;
}

NTSTATUS MavkDxgkVerifyCompletionRoute(MAVK_DXGK_COMPLETION *completion)
{
    const DXGKRNL_INTERFACE *interface = completion->Interface;
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !interface)
        return STATUS_INVALID_DEVICE_STATE;
    if (completion->RouteVerified) return STATUS_SUCCESS;

    BOOLEAN atInterruptLevel = FALSE;
    NTSTATUS status = interface->DxgkCbSynchronizeExecution(interface->DeviceHandle,
        CheckInterruptLevel, NULL, completion->MessageNumber, &atInterruptLevel);
    if (!NT_SUCCESS(status) || !atInterruptLevel) {
        DbgPrintEx(DPFLTR_IHVVIDEO_ID, DPFLTR_ERROR_LEVEL,
                   "UAVF WDDM: completion synchronization failed 0x%08lx dirql=%lu interrupts=%lu\n",
                   (ULONG)status, (ULONG)atInterruptLevel, completion->InterruptResources);
        return NT_SUCCESS(status) ? STATUS_DEVICE_CONFIGURATION_ERROR : status;
    }

    completion->RouteVerified = TRUE;
    return STATUS_SUCCESS;
}

static BOOLEAN ReportAtInterruptLevel(PVOID data)
{
    if (!CheckInterruptLevel(NULL)) return FALSE;
    MAVK_NOTIFY *notify = data;
    MAVK_DXGK_COMPLETION *completion = notify->Completion;
    const DXGKRNL_INTERFACE *dxgk = completion->Interface;
    if (notify->Data.InterruptType == DXGK_INTERRUPT_DMA_COMPLETED) {
        InterlockedExchange(&completion->LastCompletedFence,
                            (LONG)notify->Data.DmaCompleted.SubmissionFenceId);
    }
    dxgk->DxgkCbNotifyInterrupt(dxgk->DeviceHandle, &notify->Data);
    dxgk->DxgkCbQueueDpc(dxgk->DeviceHandle);
    return TRUE;
}

NTSTATUS MavkDxgkReportRetired(MAVK_DXGK_COMPLETION *completion,
                               const struct mavk_submission *submission,
                               NTSTATUS failureStatus)
{
    if (!completion->Interface || !submission->serial || KeGetCurrentIrql() != PASSIVE_LEVEL)
        return STATUS_INVALID_DEVICE_STATE;
    MAVK_NOTIFY notify = {0};
    notify.Completion = completion;
    if (submission->gpu_done) {
        notify.Data.InterruptType = DXGK_INTERRUPT_DMA_COMPLETED;
        notify.Data.DmaCompleted.SubmissionFenceId = submission->dxgk_fence;
        notify.Data.DmaCompleted.NodeOrdinal = completion->NodeOrdinal;
        notify.Data.DmaCompleted.EngineOrdinal = completion->EngineOrdinal;
    } else {
        if (NT_SUCCESS(failureStatus)) return STATUS_INVALID_PARAMETER;
        notify.Data.InterruptType = DXGK_INTERRUPT_DMA_FAULTED;
        notify.Data.DmaFaulted.FaultedFenceId = submission->dxgk_fence;
        notify.Data.DmaFaulted.Status = failureStatus;
        notify.Data.DmaFaulted.NodeOrdinal = completion->NodeOrdinal;
        notify.Data.DmaFaulted.EngineOrdinal = completion->EngineOrdinal;
    }
    const DXGKRNL_INTERFACE *dxgk = completion->Interface;
    BOOLEAN delivered = FALSE;
    NTSTATUS status = dxgk->DxgkCbSynchronizeExecution(dxgk->DeviceHandle,
        ReportAtInterruptLevel, &notify, completion->MessageNumber, &delivered);
    if (NT_SUCCESS(status) && !delivered) return STATUS_DEVICE_NOT_READY;
    return status; // The caller routes synchronization failure to adapter recovery.
}

VOID MavkDxgkCompletionDpc(const MAVK_DXGK_COMPLETION *completion)
{
    const DXGKRNL_INTERFACE *dxgk = completion->Interface;
    dxgk->DxgkCbNotifyDpc(dxgk->DeviceHandle);
}
