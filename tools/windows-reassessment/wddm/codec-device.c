/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#include "codec-device.h"

#define U64_WORDS(value) (ULONG)(value), (ULONG)((ULONGLONG)(value) >> 32)

enum {
    CreateInstance = 20000, DestroyInstance = 20001, EnumeratePhysicalDevices = 20002,
    QueueFamilyProperties = 20007, CreateDevice = 20011, DestroyDevice = 20012,
    GetDeviceQueue = 20017, CreateCommandPool = 20085, DestroyCommandPool = 20086,
    PresentPointerBE = 0x01000000, Api11 = 0x00401000
};

static ULONGLONG ReadU64(const ULONG *words)
{
    return ((ULONGLONG)words[1] << 32) | words[0];
}

static NTSTATUS CreateHandle(MAVK_VIOSOCK *wire, ULONGLONG deadline, const ULONG *packet,
                             ULONG bytes, ULONGLONG *handle)
{
    ULONG reply[3];
    NTSTATUS status = MavkCodecExchange(wire, deadline, packet, bytes, reply, sizeof(reply));
    if (!NT_SUCCESS(status)) return status;
    if (reply[2]) return MavkCodecResult((LONG)reply[2]);
    *handle = ReadU64(reply);
    if (*handle) return STATUS_SUCCESS;
    MavkWireClose(wire);
    return STATUS_DEVICE_PROTOCOL_ERROR;
}

NTSTATUS MavkCodecCreateCommandPool(MAVK_VIOSOCK *wire, ULONGLONG deadline,
    const MAVK_CODEC_DEVICE *device, ULONGLONG *pool)
{
    *pool = 0;
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !device->Device || !wire->Generation ||
        wire->Generation != device->Generation)
        return STATUS_INVALID_DEVICE_STATE;

    /* VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO, RESET_COMMAND_BUFFER_BIT. */
    const ULONG packet[] = {3, CreateCommandPool, 48, U64_WORDS(device->Device),
                            39, 0, 2, device->Family, 0, 0, 0, 0};
    _Static_assert(sizeof(packet) == 52, "vkCreateCommandPool codec packet");
    return CreateHandle(wire, deadline, packet, sizeof(packet), pool);
}

NTSTATUS MavkCodecDestroyCommandPool(MAVK_VIOSOCK *wire, ULONGLONG deadline,
    const MAVK_CODEC_DEVICE *device, ULONGLONG pool)
{
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !device->Device || !pool ||
        !wire->Generation || wire->Generation != device->Generation)
        return STATUS_INVALID_DEVICE_STATE;

    const ULONG packet[] = {3, DestroyCommandPool, 32, U64_WORDS(device->Device),
                            U64_WORDS(pool), 0, 0};
    return MavkCodecExchange(wire, deadline, packet, sizeof(packet), NULL, 0);
}

static NTSTATUS FindPhysicalDevice(MAVK_VIOSOCK *wire, ULONGLONG deadline, MAVK_CODEC_DEVICE *device)
{
    const ULONG count[] = {3, EnumeratePhysicalDevices, 36, U64_WORDS(device->Instance),
                           0, PresentPointerBE, 0, 0, 0};
    ULONG countReply[6];
    NTSTATUS status = MavkCodecExchange(wire, deadline, count, sizeof(count), countReply, sizeof(countReply));
    if (!NT_SUCCESS(status)) return status;
    if (countReply[5]) return MavkCodecResult((LONG)countReply[5]);
    /* The app-owned Mali endpoint exposes one Vulkan physical device. */
    if (countReply[2] != 1) return STATUS_NOT_SUPPORTED;

    const ULONG enumerate[] = {3, EnumeratePhysicalDevices, 44, U64_WORDS(device->Instance),
                               0, PresentPointerBE, 1, 0, PresentPointerBE, 0, 0};
    ULONG reply[8];
    status = MavkCodecExchange(wire, deadline, enumerate, sizeof(enumerate), reply, sizeof(reply));
    if (!NT_SUCCESS(status)) return status;
    if (reply[7]) return MavkCodecResult((LONG)reply[7]);
    if (reply[2] != 1) return STATUS_DEVICE_PROTOCOL_ERROR;
    device->PhysicalDevice = ReadU64(reply + 5);
    return device->PhysicalDevice ? STATUS_SUCCESS : STATUS_DEVICE_PROTOCOL_ERROR;
}

static NTSTATUS FindGraphicsFamily(MAVK_VIOSOCK *wire, ULONGLONG deadline, MAVK_CODEC_DEVICE *device)
{
    enum { HeaderWords = 5, FamilyWords = 6, MaxFamilies = 32 };
    const ULONG count[] = {3, QueueFamilyProperties, 36, U64_WORDS(device->PhysicalDevice),
                           0, PresentPointerBE, 0, 0, 0};
    ULONG reply[HeaderWords + MaxFamilies * FamilyWords];
    NTSTATUS status = MavkCodecExchange(wire, deadline, count, sizeof(count), reply, HeaderWords * sizeof(ULONG));
    if (!NT_SUCCESS(status)) return status;
    ULONG families = reply[2];
    if (!families || families > MaxFamilies) return STATUS_NOT_SUPPORTED;

    ULONG query[RTL_NUMBER_OF(count) + MaxFamilies * FamilyWords] = {
        3, QueueFamilyProperties, 36 + families * FamilyWords * sizeof(ULONG),
        U64_WORDS(device->PhysicalDevice), 0, PresentPointerBE, families, 0, PresentPointerBE
    };
    status = MavkCodecExchange(wire, deadline, query, (query[2] + sizeof(ULONG)),
                               reply, (HeaderWords + families * FamilyWords) * sizeof(ULONG));
    if (!NT_SUCCESS(status)) return status;
    if (reply[2] != families) return STATUS_DEVICE_PROTOCOL_ERROR;
    for (ULONG i = 0; i < families; ++i) {
        const ULONG *properties = reply + HeaderWords + i * FamilyWords;
        if ((properties[0] & 1u) && properties[1]) {
            device->Family = i;
            return STATUS_SUCCESS;
        }
    }
    return STATUS_NOT_SUPPORTED;
}

NTSTATUS MavkCodecCloseDevice(MAVK_VIOSOCK *wire, ULONGLONG deadline, MAVK_CODEC_DEVICE *device)
{
    if (KeGetCurrentIrql() != PASSIVE_LEVEL) return STATUS_INVALID_DEVICE_STATE;
    NTSTATUS status = STATUS_SUCCESS;
    if (wire->Generation && wire->Generation == device->Generation) {
        if (device->Device) {
            const ULONG destroy[] = {3, DestroyDevice, 24, U64_WORDS(device->Device), 0, 0};
            status = MavkCodecExchange(wire, deadline, destroy, sizeof(destroy), NULL, 0);
        }
        if (NT_SUCCESS(status) && device->Instance) {
            const ULONG destroy[] = {3, DestroyInstance, 24, U64_WORDS(device->Instance), 0, 0};
            status = MavkCodecExchange(wire, deadline, destroy, sizeof(destroy), NULL, 0);
        }
    }
    RtlZeroMemory(device, sizeof(*device));
    return status;
}

NTSTATUS MavkCodecOpenDevice(MAVK_VIOSOCK *wire, ULONGLONG deadline, MAVK_CODEC_DEVICE *device)
{
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !wire->Generation || device->Generation)
        return STATUS_INVALID_DEVICE_STATE;

    /* Vulkan 1.1, no optional layers/extensions. Codec 3 has pointer tags
     * for nullable application and engine names. */
    const ULONG instance[] = {3, CreateInstance, 96,
        1, 0, 0, 0, PresentPointerBE,
        0, 0, 0, 0, 0, 0, 0, 0, Api11,
        0, 0, 0, 0, 0, 0, 0, 0};
    _Static_assert(sizeof(instance) == 100, "vkCreateInstance codec packet");
    device->Generation = wire->Generation;
    NTSTATUS status = CreateHandle(wire, deadline, instance, sizeof(instance), &device->Instance);
    if (!NT_SUCCESS(status)) goto done;
    status = FindPhysicalDevice(wire, deadline, device);
    if (!NT_SUCCESS(status)) goto done;
    status = FindGraphicsFamily(wire, deadline, device);
    if (!NT_SUCCESS(status)) goto done;

    /* Single graphics queue at priority 1.0 with default device features. */
    const ULONG create[] = {3, CreateDevice, 96, U64_WORDS(device->PhysicalDevice),
        3, 0, 0, 1, 2, 0, 0, device->Family, 1, 0x3f800000,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    _Static_assert(sizeof(create) == 100, "vkCreateDevice codec packet");
    status = CreateHandle(wire, deadline, create, sizeof(create), &device->Device);
    if (!NT_SUCCESS(status)) goto done;

    const ULONG getQueue[] = {3, GetDeviceQueue, 32, U64_WORDS(device->Device), device->Family, 0, 0, 0};
    ULONG queue[2];
    status = MavkCodecExchange(wire, deadline, getQueue, sizeof(getQueue), queue, sizeof(queue));
    if (NT_SUCCESS(status)) {
        device->Queue = ReadU64(queue);
        if (device->Queue) return STATUS_SUCCESS;
        status = STATUS_DEVICE_PROTOCOL_ERROR;
    }
done:
    MavkCodecCloseDevice(wire, deadline, device);
    return status;
}
