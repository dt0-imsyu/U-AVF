/* Read-only native display inventory. Does not call ChangeDisplaySettings,
 * change registry values, initialize a VM, or require administrator rights. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>

static void print_mode(const char *kind, const wchar_t *device, DWORD index) {
    DEVMODEW mode = {0};
    mode.dmSize = sizeof(mode);
    if (EnumDisplaySettingsExW(device, index, &mode, 0))
        wprintf(L"%hs device=%ls width=%lu height=%lu bpp=%lu hz=%lu flags=%lu\n",
                kind, device, mode.dmPelsWidth, mode.dmPelsHeight,
                mode.dmBitsPerPel, mode.dmDisplayFrequency, mode.dmDisplayFlags);
    else
        wprintf(L"%hs device=%ls unavailable error=%lu\n", kind, device, GetLastError());
}

int wmain(void) {
    DWORD session = 0;
    ProcessIdToSessionId(GetCurrentProcessId(), &session);
    wchar_t user[256] = {0};
    DWORD user_size = 256;
    GetUserNameW(user, &user_size);
    /* Explicit DPI awareness reports native dimensions, not scaled UI units. */
    SetProcessDPIAware();
    wprintf(L"READ_ONLY=1 pid=%lu session=%lu user=%ls\n", GetCurrentProcessId(), session, user);
    wprintf(L"SCREEN width=%d height=%d monitors=%d\n", GetSystemMetrics(SM_CXSCREEN),
            GetSystemMetrics(SM_CYSCREEN), GetSystemMetrics(SM_CMONITORS));
    unsigned devices = 0, modes = 0;
    for (DWORD i = 0; i < 64; ++i) {
        DISPLAY_DEVICEW device = {0};
        device.cb = sizeof(device);
        if (!EnumDisplayDevicesW(NULL, i, &device, 0)) break;
        ++devices;
        wprintf(L"DEVICE name=%ls description=%ls id=%ls key=%ls state=%08lx\n",
                device.DeviceName, device.DeviceString, device.DeviceID,
                device.DeviceKey, device.StateFlags);
        print_mode("CURRENT", device.DeviceName, ENUM_CURRENT_SETTINGS);
        print_mode("REGISTRY", device.DeviceName, ENUM_REGISTRY_SETTINGS);
        for (DWORD j = 0; j < 2048; ++j) {
            DEVMODEW mode = {0};
            mode.dmSize = sizeof(mode);
            if (!EnumDisplaySettingsExW(device.DeviceName, j, &mode, 0)) break;
            ++modes;
            wprintf(L"MODE device=%ls index=%lu width=%lu height=%lu bpp=%lu hz=%lu\n",
                    device.DeviceName, j, mode.dmPelsWidth, mode.dmPelsHeight,
                    mode.dmBitsPerPel, mode.dmDisplayFrequency);
        }
    }
    wprintf(L"INVENTORY_COMPLETE devices=%u modes=%u\n", devices, modes);
    return devices ? 0 : 2;
}
