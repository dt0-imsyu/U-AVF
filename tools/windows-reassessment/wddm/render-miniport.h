/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#pragma once
#include "render-scheduler.h"

/* The caller supplies the remaining hardware-specific DDIs. AddDevice must
 * allocate a nonpaged MAVK_SCHEDULER and initialize it. StopDevice owns
 * worker teardown before freeing the interface or letting RemoveDevice run. */
NTSTATUS MavkRegisterRenderMiniport(PDRIVER_OBJECT driver, PUNICODE_STRING registryPath,
                                   DRIVER_INITIALIZATION_DATA *initialization);
