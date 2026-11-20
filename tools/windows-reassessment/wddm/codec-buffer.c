/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#include "codec-buffer.h"

/* Pinned gfxstream codec 3. Sizes exclude the leading codec-version word.
 * Vulkan handles are serialized as two little-endian 32-bit words. */
#define WIRE_U64(value) (ULONG)(value), (ULONG)((ULONGLONG)(value) >> 32)

static ULONGLONG DecodeU64(const ULONG *words)
{
    return ((ULONGLONG)words[1] << 32) | words[0];
}

static NTSTATUS ReadHandle(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                           const ULONG *request, ULONG bytes, ULONGLONG *handle)
{
    ULONG reply[3];
    NTSTATUS status = MavkCodecExchange(wire, deadline, request, bytes, reply, sizeof(reply));
    if (!NT_SUCCESS(status)) return status;
    status = MavkCodecResult((LONG)reply[2]);
    if (!NT_SUCCESS(status)) return status;
    *handle = DecodeU64(reply);
    if (*handle) return STATUS_SUCCESS;
    MavkWireClose(wire);
    return STATUS_DEVICE_PROTOCOL_ERROR;
}

NTSTATUS MavkCodecDestroyBuffer(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                                ULONGLONG device, MAVK_CODEC_BUFFER *buffer)
{
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !device)
        return STATUS_INVALID_PARAMETER;

    NTSTATUS status = STATUS_SUCCESS;
    if (buffer->Buffer) {
        const ULONG destroy[] = {3, 20051, 32, WIRE_U64(device), WIRE_U64(buffer->Buffer), 0, 0};
        status = MavkCodecExchange(wire, deadline, destroy, sizeof(destroy), NULL, 0);
    }
    if (NT_SUCCESS(status) && buffer->Memory) {
        const ULONG release[] = {3, 20022, 32, WIRE_U64(device), WIRE_U64(buffer->Memory), 0, 0};
        status = MavkCodecExchange(wire, deadline, release, sizeof(release), NULL, 0);
    }
    RtlZeroMemory(buffer, sizeof(*buffer));
    return status;
}

NTSTATUS MavkCodecCreateBuffer(MAVK_VIOSOCK *wire, ULONGLONG deadline,
    ULONGLONG physicalDevice, ULONGLONG device, ULONGLONG size, ULONG usage,
    ULONG requiredProperties, MAVK_CODEC_BUFFER *buffer)
{
    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !physicalDevice || !device ||
        !size || !usage || (usage & ~0x1ffu) || buffer->Buffer || buffer->Memory)
        return STATUS_INVALID_PARAMETER;

    enum {
        CreateBuffer = 20050, GetMemoryRequirements2 = 20145, GetMemoryProperties = 20008,
        AllocateMemory = 20021, BindBufferMemory = 20028,
        BufferInfo = 12, AllocateInfo = 5, BufferRequirementsInfo2 = 1000146000,
        MemoryRequirements2 = 1000146003, DedicatedRequirements = 1000127000,
        WireDedicatedSize = 0x18000000, MemoryTypes = 32, MemoryHeaps = 16,
        PropertyWords = 2 + MemoryTypes * 2 + MemoryHeaps * 3
    };

    /* Size, usage, exclusive sharing, no queue array or allocator. */
    const ULONG create[] = {3, CreateBuffer, 72, WIRE_U64(device),
        BufferInfo, 0, 0, WIRE_U64(size), usage, 0, 0, 0, 0, 0, 0, 0, 0};
    _Static_assert(sizeof(create) == 76, "vkCreateBuffer v3");
    NTSTATUS status = ReadHandle(wire, deadline, create, sizeof(create), &buffer->Buffer);
    if (!NT_SUCCESS(status)) return status;

    const ULONG query[] = {3, GetMemoryRequirements2, 80, WIRE_U64(device),
        BufferRequirementsInfo2, 0, WIRE_U64(buffer->Buffer),
        MemoryRequirements2, WireDedicatedSize, DedicatedRequirements, DedicatedRequirements,
        0, 0, 0, 0, 0, 0, 0, 0};
    _Static_assert(sizeof(query) == 84, "vkGetBufferMemoryRequirements2 v3");
    ULONG requirements[12];
    status = MavkCodecExchange(wire, deadline, query, sizeof(query), requirements, sizeof(requirements));
    if (!NT_SUCCESS(status)) goto failure;
    if (requirements[0] != MemoryRequirements2 || requirements[1] != WireDedicatedSize ||
        requirements[2] != DedicatedRequirements || requirements[3] != DedicatedRequirements ||
        requirements[4] != 0 || requirements[5] > 1 || requirements[6] > 1 ||
        DecodeU64(requirements + 7) < size || DecodeU64(requirements + 7) > MAXULONGLONG - 65536 ||
        !DecodeU64(requirements + 9)) {
        status = STATUS_DEVICE_PROTOCOL_ERROR;
        goto failure;
    }
    if (requirements[6]) {
        status = STATUS_NOT_SUPPORTED;  // Dedicated binding needs a different allocate chain.
        goto failure;
    }

    ULONG propertyQuery[4 + PropertyWords] = {3, GetMemoryProperties,
        sizeof(propertyQuery) - sizeof(ULONG), WIRE_U64(physicalDevice)};
    ULONG properties[PropertyWords];
    status = MavkCodecExchange(wire, deadline, propertyQuery, sizeof(propertyQuery), properties, sizeof(properties));
    if (!NT_SUCCESS(status)) goto failure;
    if (!properties[0] || properties[0] > MemoryTypes ||
        !properties[1 + 2 * MemoryTypes] || properties[1 + 2 * MemoryTypes] > MemoryHeaps) {
        status = STATUS_DEVICE_PROTOCOL_ERROR;
        goto failure;
    }
    ULONG memoryType = 0;
    for (; memoryType < properties[0]; ++memoryType) {
        if ((requirements[11] & (1u << memoryType)) &&
            (properties[1 + 2 * memoryType] & requiredProperties) == requiredProperties)
            break;
    }
    if (memoryType == properties[0]) {
        status = STATUS_NOT_SUPPORTED;
        goto failure;
    }

    ULONGLONG allocationSize = DecodeU64(requirements + 7);
    const ULONG allocate[] = {3, AllocateMemory, 52, WIRE_U64(device),
        AllocateInfo, 0, WIRE_U64(allocationSize), memoryType, 0, 0, 0, 0};
    _Static_assert(sizeof(allocate) == 56, "vkAllocateMemory v3");
    status = ReadHandle(wire, deadline, allocate, sizeof(allocate), &buffer->Memory);
    if (!NT_SUCCESS(status)) goto failure;

    const ULONG bind[] = {3, BindBufferMemory, 40, WIRE_U64(device),
        WIRE_U64(buffer->Buffer), WIRE_U64(buffer->Memory), 0, 0};
    LONG bindResult;
    status = MavkCodecExchange(wire, deadline, bind, sizeof(bind), &bindResult, sizeof(bindResult));
    if (NT_SUCCESS(status)) status = MavkCodecResult(bindResult);
    if (!NT_SUCCESS(status)) goto failure;

    buffer->Size = size;
    return STATUS_SUCCESS;

failure:
    if (wire->Peer) MavkCodecDestroyBuffer(wire, deadline, device, buffer);
    else RtlZeroMemory(buffer, sizeof(*buffer));
    return status;
}
