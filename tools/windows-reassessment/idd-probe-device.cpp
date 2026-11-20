// Admission helper only. Does not install an INF, import trust or alter modes.
// Explicit disposable-environment opt-in is required to enumerate the device.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <swdevice.h>
#include <cfgmgr32.h>
#include <stdio.h>
#include <wchar.h>

struct Creation {
    HANDLE event;
    HRESULT result;
    WCHAR instance[MAX_DEVICE_ID_LEN];
};
#ifndef UAVF_IDD_HOLD_MS
#define UAVF_IDD_HOLD_MS 60000
#endif
static void WINAPI created(HSWDEVICE, HRESULT hr, void *context, PCWSTR instance) {
    auto *c = static_cast<Creation *>(context);
    c->result = hr;
    if (instance) {
        size_t n = wcslen(instance);
        if (n >= MAX_DEVICE_ID_LEN) c->result = HRESULT_FROM_WIN32(ERROR_INSUFFICIENT_BUFFER);
        else memcpy(c->instance, instance, (n+1)*sizeof(WCHAR));
    }
    SetEvent(c->event);
}
int wmain(int argc, wchar_t **argv) {
    bool working = argc == 2 && wcscmp(argv[1], L"--approved-working-load-probe") == 0;
    if (working) {
        DWORD serial = 0;
        if (!GetVolumeInformationW(L"C:\\", nullptr, 0, &serial, nullptr, nullptr, nullptr, 0)
            || serial != 0x3ef669b7) return 12;
        // Explicit one-off working-guest admission, never infer it from TESTSIGNING.
        HANDLE approval = CreateFileW(L"C:\\UAVF\\IddWorkingProbe\\approved-working-test.txt",
            GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, 0, nullptr);
        if (approval == INVALID_HANDLE_VALUE) return 13;
        char text[64] = {}; DWORD bytes = 0;
        BOOL read = ReadFile(approval, text, sizeof(text)-1, &bytes, nullptr);
        CloseHandle(approval);
        if (!read || strcmp(text, "APPROVED_WORKING_IDD_TEST_3EF669B7") != 0) return 14;
    }
    if (argc != 2 || (!working && wcscmp(argv[1], L"--disposable-load-probe") != 0)) {
        puts("NO_DEVICE_CHANGE. Requires --disposable-load-probe in an approved disposable guest.");
        puts("Working guest requires explicit --approved-working-load-probe and exact approval marker.");
        return 2;
    }
    Creation c = {};
    c.result = E_PENDING;
    c.event = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (!c.event) return 3;
    SW_DEVICE_CREATE_INFO info = {};
    info.cbSize = sizeof(info);
    info.pszInstanceId = L"UavfIddSourceProbe";
    info.pszzHardwareIds = L"UavfIddSourceProbe\0";
    info.pszzCompatibleIds = L"UavfIddSourceProbe\0";
    info.pszDeviceDescription = L"U-AVF isolated source probe";
    info.CapabilityFlags = SWDeviceCapabilitiesRemovable | SWDeviceCapabilitiesDriverRequired;
    HSWDEVICE device = nullptr;
    HRESULT hr = SwDeviceCreate(L"UavfIddSourceProbe", L"HTREE\\ROOT\\0", &info,
                               0, nullptr, created, &c, &device);
    int code = 4;
    if (SUCCEEDED(hr)) {
        DWORD wait = WaitForSingleObject(c.event, 10000);
        if (wait == WAIT_OBJECT_0) {
            printf("ENUMERATION_HRESULT=0x%08lx\n", static_cast<unsigned long>(c.result));
            if (SUCCEEDED(c.result)) {
                DEVINST node = 0;
                CONFIGRET cr = CR_NO_SUCH_DEVNODE;
                ULONG status = 0, problem = 0;
                // The creation callback does not guarantee asynchronous driver
                // start has finished. Give PnP a bounded admission window.
                ULONGLONG deadline = GetTickCount64()+60000;
                do {
                    cr = CM_Locate_DevNodeW(&node, c.instance, CM_LOCATE_DEVNODE_NORMAL);
                    if (cr == CR_SUCCESS) cr = CM_Get_DevNode_Status(&status, &problem, node, 0);
                    if (cr == CR_SUCCESS && !(status & DN_HAS_PROBLEM) && (status & DN_STARTED)) break;
                    Sleep(100);
                } while (GetTickCount64()<deadline);
                printf("PNP_QUERY=0x%lx STATUS=0x%lx PROBLEM=%lu\n", cr, status, problem);
                // Enumeration success alone is NOT a loaded driver or swapchain PASS.
                if (cr == CR_SUCCESS && !(status & DN_HAS_PROBLEM) && (status & DN_STARTED)) {
                    printf("PNP_STARTED_ONLY; NOT_SWAPCHAIN_OR_FPS_PASS. Holding device for%useconds.\n",UAVF_IDD_HOLD_MS/1000);
                    Sleep(UAVF_IDD_HOLD_MS);
                    code = 0;
                }
            }
        } else puts("ENUMERATION_TIMEOUT_OR_WAIT_FAILURE; NOT_ADMITTED");
    } else printf("CREATE_HRESULT=0x%08lx\n", static_cast<unsigned long>(hr));
    // Close guarantees no more creation callbacks, even after the timeout.
    // It initiates unplug, NOT DriverStore/PnP-registry uninstall.
    if (device) SwDeviceClose(device);
    CloseHandle(c.event);
    puts("DEVICE_HANDLE_CLOSED; UNPLUG_ASYNCHRONOUS; PACKAGE_NOT_UNINSTALLED");
    return code;
}
