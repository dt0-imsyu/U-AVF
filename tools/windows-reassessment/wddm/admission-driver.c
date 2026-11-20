/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#include <ntifs.h>
#include <wdmsec.h>
#include "mavk-admission.h"

VOID MavkAdmit(struct mavk_admission *report, BOOLEAN codec);
DRIVER_INITIALIZE DriverEntry;

static UNICODE_STRING linkName;
static volatile LONG busy;

static NTSTATUS Dispatch(PDEVICE_OBJECT device, PIRP irp)
{
    UNREFERENCED_PARAMETER(device);
    PIO_STACK_LOCATION stack = IoGetCurrentIrpStackLocation(irp);
    NTSTATUS status = STATUS_INVALID_DEVICE_REQUEST;
    ULONG_PTR bytes = 0;
    switch (stack->MajorFunction) {
    case IRP_MJ_CREATE:
        status = stack->FileObject->FileName.Length ? STATUS_OBJECT_NAME_INVALID : STATUS_SUCCESS;
        break;
    case IRP_MJ_CLOSE:
    case IRP_MJ_CLEANUP:
        status = STATUS_SUCCESS;
        break;
    case IRP_MJ_DEVICE_CONTROL:
        if (stack->Parameters.DeviceIoControl.IoControlCode != MAVK_IOCTL_ADMIT &&
            stack->Parameters.DeviceIoControl.IoControlCode != MAVK_IOCTL_ADMIT_CODEC) break;
        if (stack->Parameters.DeviceIoControl.InputBufferLength ||
            stack->Parameters.DeviceIoControl.OutputBufferLength < sizeof(struct mavk_admission)) {
            status = STATUS_INFO_LENGTH_MISMATCH;
            break;
        }
        if (KeGetCurrentIrql() != PASSIVE_LEVEL || InterlockedCompareExchange(&busy, 1, 0)) {
            status = STATUS_DEVICE_BUSY;
            break;
        }
        struct mavk_admission *report = irp->AssociatedIrp.SystemBuffer;
        RtlZeroMemory(report, sizeof(*report));
        report->version = MAVK_ADMISSION_VERSION;
        MavkAdmit(report, stack->Parameters.DeviceIoControl.IoControlCode == MAVK_IOCTL_ADMIT_CODEC);
        InterlockedExchange(&busy, 0);
        bytes = sizeof(*report);
        status = STATUS_SUCCESS;
        break;
    }
    irp->IoStatus.Status = status;
    irp->IoStatus.Information = bytes;
    IoCompleteRequest(irp, IO_NO_INCREMENT);
    return status;
}

static VOID Unload(PDRIVER_OBJECT driver)
{
    IoDeleteSymbolicLink(&linkName);
    IoDeleteDevice(driver->DeviceObject);
}

NTSTATUS DriverEntry(PDRIVER_OBJECT driver, PUNICODE_STRING registryPath)
{
    UNREFERENCED_PARAMETER(registryPath);
    const GUID deviceClass = {0x6eaad48e, 0xec28, 0x4e9b, {0xa3, 0x41, 0xd9, 0xe5, 0x60, 0x48, 0xf1, 0x0b}};
    UNICODE_STRING name, acl;
    RtlInitUnicodeString(&linkName, L"\\DosDevices\\UavfMavkAdmission");
    RtlInitUnicodeString(&name, L"\\Device\\UavfMavkAdmission");
    RtlInitUnicodeString(&acl, L"D:P(A;;GA;;;SY)(A;;GA;;;BA)");
    PDEVICE_OBJECT device;
    NTSTATUS status = IoCreateDeviceSecure(driver, 0, &name, FILE_DEVICE_UNKNOWN,
                                            FILE_DEVICE_SECURE_OPEN, TRUE, &acl, &deviceClass, &device);
    if (!NT_SUCCESS(status)) return status;
    driver->MajorFunction[IRP_MJ_CREATE] = Dispatch;
    driver->MajorFunction[IRP_MJ_CLOSE] = Dispatch;
    driver->MajorFunction[IRP_MJ_CLEANUP] = Dispatch;
    driver->MajorFunction[IRP_MJ_DEVICE_CONTROL] = Dispatch;
    driver->DriverUnload = Unload;
    status = IoCreateSymbolicLink(&linkName, &name);
    if (!NT_SUCCESS(status)) IoDeleteDevice(device);
    else device->Flags &= ~DO_DEVICE_INITIALIZING;
    return status;
}
