/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#include "codec-fence.h"

NTSTATUS MavkCreateCodecFence(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                              ULONGLONG device, ULONGLONG *fence)
{
    *fence = 0;
    if (!device || KeGetCurrentIrql() != PASSIVE_LEVEL) return STATUS_INVALID_PARAMETER;

    /* VkFenceCreateInfo (sType=8), null pNext, flags=0, null allocator
     * and the generated output-handle placeholder. */
    const ULONG packet[] = {3, 20035, 44, (ULONG)device, (ULONG)(device >> 32),
                            8, 0, 0, 0, 0, 0, 0};
    ULONG reply[3];
    NTSTATUS status = MavkCodecExchange(wire, deadline, packet, sizeof(packet), reply, sizeof(reply));
    if (!NT_SUCCESS(status)) return status;
    if (reply[2]) return MavkCodecResult((LONG)reply[2]);
    *fence = ((ULONGLONG)reply[1] << 32) | reply[0];
    return *fence ? STATUS_SUCCESS : STATUS_DEVICE_PROTOCOL_ERROR;
}

NTSTATUS MavkDestroyCodecFence(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                               ULONGLONG device, ULONGLONG fence)
{
    if (!device || !fence || KeGetCurrentIrql() != PASSIVE_LEVEL)
        return STATUS_INVALID_PARAMETER;
    const ULONG packet[] = {3, 20036, 32, (ULONG)device, (ULONG)(device >> 32),
                            (ULONG)fence, (ULONG)(fence >> 32), 0, 0};
    return MavkCodecExchange(wire, deadline, packet, sizeof(packet), NULL, 0);
}

NTSTATUS MavkSubmitCodecCommand(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                                ULONGLONG queue, ULONGLONG command, ULONGLONG fence)
{
    if (!queue || !command || !fence || KeGetCurrentIrql() != PASSIVE_LEVEL)
        return STATUS_INVALID_PARAMETER;

    /* Generated VkSubmitInfo v3 layout, with no pNext or semaphore arrays. */
    const ULONG packet[] = {
        3, 20018, 56, (ULONG)queue, (ULONG)(queue >> 32), 1,
        4, 0, 0, 1, (ULONG)command, (ULONG)(command >> 32), 0,
        (ULONG)fence, (ULONG)(fence >> 32)
    };
    _Static_assert(sizeof(packet) == 60, "Codec 3 single-command submit size");
    LONG result;
    NTSTATUS status = MavkCodecExchange(wire, deadline, packet, sizeof(packet), &result, sizeof(result));
    if (!NT_SUCCESS(status)) return status;
    return MavkCodecResult(result);
}

NTSTATUS MavkWaitCodecFence(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                            ULONGLONG device, ULONGLONG fence)
{
    if (!device || !fence || KeGetCurrentIrql() != PASSIVE_LEVEL)
        return STATUS_INVALID_PARAMETER;

    enum { CodecVersion = 3, WaitForFences = 20039 };
    /* One fence, waitAll, 2 ms timeout per call. The existing decoder owns
     * unboxing and observes the real VkFence. The transport ACK is separate. */
    const ULONG packet[] = {
        CodecVersion, WaitForFences, 40,
        (ULONG)device, (ULONG)(device >> 32), 1,
        (ULONG)fence, (ULONG)(fence >> 32), 1, 2000000, 0
    };
    _Static_assert(sizeof(packet) == 44, "Codec 3 single-fence wire size");

    while (KeQueryUnbiasedInterruptTime() < deadline) {
        LONG vkResult = 0;
        NTSTATUS status = MavkCodecExchange(wire, deadline, packet, sizeof(packet), &vkResult, sizeof(vkResult));
        if (!NT_SUCCESS(status)) return status;
        if (vkResult == 0) return STATUS_SUCCESS;                 // VK_SUCCESS
        if (vkResult != 2) return MavkCodecResult(vkResult);     // VK_TIMEOUT
    }
    return STATUS_IO_TIMEOUT;
}
