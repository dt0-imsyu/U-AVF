/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#include "render-miniport.h"

static DXGKDDI_START_DEVICE MavkDdiStartDevice;
static DXGKDDI_DPC_ROUTINE MavkDdiDpcRoutine;

static NTSTATUS MavkDdiStartDevice(IN_CONST_PVOID context, PDXGK_START_INFO start,
                                 PDXGKRNL_INTERFACE dxgk, PULONG sources, PULONG children)
{
    UNREFERENCED_PARAMETER(start);
    MAVK_SCHEDULER *scheduler = context;
    scheduler->Interface = *dxgk;
    NTSTATUS status = MavkDxgkBindCompletion(&scheduler->Completion, &scheduler->Interface);
    if (!NT_SUCCESS(status)) return status;

    *sources = 0;
    *children = 0;
    DbgPrintEx(DPFLTR_IHVVIDEO_ID, DPFLTR_INFO_LEVEL,
               "UAVF WDDM: StartDevice bound, translated interrupts=%lu\n",
               scheduler->Completion.InterruptResources);
    return STATUS_SUCCESS;
}

static VOID MavkDdiDpcRoutine(IN_CONST_PVOID context)
{
    const MAVK_SCHEDULER *scheduler = context;
    MavkDxgkCompletionDpc(&scheduler->Completion);
}

NTSTATUS MavkRegisterRenderMiniport(PDRIVER_OBJECT driver, PUNICODE_STRING registryPath,
                                   DRIVER_INITIALIZATION_DATA *initialization)
{
    if (!initialization || !initialization->DxgkDdiAddDevice ||
        !initialization->DxgkDdiStopDevice || !initialization->DxgkDdiRemoveDevice ||
        !initialization->DxgkDdiQueryAdapterInfo || !initialization->DxgkDdiInterruptRoutine ||
        !initialization->DxgkDdiCreateDevice || !initialization->DxgkDdiDestroyDevice ||
        !initialization->DxgkDdiCreateContext || !initialization->DxgkDdiDestroyContext ||
        !initialization->DxgkDdiCreateAllocation || !initialization->DxgkDdiDestroyAllocation ||
        !initialization->DxgkDdiOpenAllocation || !initialization->DxgkDdiCloseAllocation ||
        !initialization->DxgkDdiRender || !initialization->DxgkDdiPatch ||
        !initialization->DxgkDdiSubmitCommand || !initialization->DxgkDdiBuildPagingBuffer ||
        !initialization->DxgkDdiPreemptCommand || !initialization->DxgkDdiResetFromTimeout ||
        !initialization->DxgkDdiRestartFromTimeout)
        return STATUS_INVALID_PARAMETER;

    initialization->DxgkDdiStartDevice = MavkDdiStartDevice;
    initialization->DxgkDdiDpcRoutine = MavkDdiDpcRoutine;
    initialization->DxgkDdiQueryCurrentFence = MavkDdiQueryCurrentFence;
    return DxgkInitialize(driver, registryPath, initialization);
}
