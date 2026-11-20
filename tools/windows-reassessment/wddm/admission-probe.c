/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <string.h>
#include "mavk-admission.h"

int main(int argc, char **argv)
{
    BOOL codec = argc == 2 && !strcmp(argv[1], "--codec");
    if (argc > 1 && !codec) {
        fputs("usage: UavfMavkAdmission.exe [--codec]\n", stderr);
        return 1;
    }
    HANDLE driver = CreateFileW(L"\\\\.\\UavfMavkAdmission", GENERIC_READ | GENERIC_WRITE,
                                0, NULL, OPEN_EXISTING, 0, NULL);
    if (driver == INVALID_HANDLE_VALUE) {
        fprintf(stderr, "MAVK_ADMISSION_OPEN_FAILED win32=%lu\n", GetLastError());
        return 1;
    }
    struct mavk_admission report;
    DWORD bytes = 0;
    DWORD control = codec ? MAVK_IOCTL_ADMIT_CODEC : MAVK_IOCTL_ADMIT;
    BOOL ok = DeviceIoControl(driver, control, NULL, 0, &report, sizeof(report), &bytes, NULL);
    DWORD error = ok ? 0 : GetLastError();
    CloseHandle(driver);
    if (!ok || bytes != sizeof(report) || report.version != MAVK_ADMISSION_VERSION) {
        fprintf(stderr, "MAVK_ADMISSION_IOCTL_FAILED win32=%lu bytes=%lu\n", error, bytes);
        return 2;
    }
    if (report.status || report.stage != MAVK_COMPLETE) {
        fprintf(stderr, "MAVK_ADMISSION_FAILED stage=%u ntstatus=0x%08x\n", report.stage, report.status);
        return 3;
    }
    if (codec) {
        printf("MAVK_KERNEL_CODEC_PASS api=0x%08x vendor=0x%04x name=%.*s\n",
               report.codec_api_version, report.vendor,
               (int)sizeof(report.device_name), report.device_name);
        return 0;
    }
    printf("MAVK_KERNEL_BRIDGE_PASS vendor=0x%04x device=0x%04x type=%u api=0x%08x driver=0x%08x name=%.*s payload=%u buffers=%u budget=%u pixels=%u words=%u\n",
           report.vendor, report.device, report.device_type, report.api_version, report.driver_version,
           (int)sizeof(report.device_name), report.device_name, report.max_payload, report.max_buffers,
           report.max_buffer_bytes, report.checked_pixels, report.checked_words);
    return 0;
}
