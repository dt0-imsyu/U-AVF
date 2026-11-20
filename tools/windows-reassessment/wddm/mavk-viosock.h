/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#pragma once
#include <ntifs.h>

#define MAVK_WIRE_MAX_PAYLOAD (1024u * 1024u)

/* One owner at PASSIVE_LEVEL. Each call receives an absolute deadline in
 * unbiased interrupt-time units. The owner must close after any wire failure. */
typedef struct {
    HANDLE Peer;
    HANDLE Event;
    ULONGLONG Sequence;
    ULONGLONG Generation;
} MAVK_VIOSOCK;

/* Open/close on the same PASSIVE_LEVEL worker. A successful open accepts one
 * app-owned host endpoint and negotiates the existing MVK1 stream. */
NTSTATUS MavkWireOpen(MAVK_VIOSOCK *wire, USHORT port, ULONGLONG deadline);
VOID MavkWireClose(MAVK_VIOSOCK *wire);
NTSTATUS MavkWireQueryCodecVersion(MAVK_VIOSOCK *wire, ULONGLONG deadline, ULONG *api);
NTSTATUS MavkCodecExchange(MAVK_VIOSOCK *wire, ULONGLONG deadline, const VOID *packet,
                           ULONG packetBytes, VOID *reply, ULONG replyBytes);
NTSTATUS MavkCodecResult(LONG result);

NTSTATUS MavkWireExchange(MAVK_VIOSOCK *wire, ULONGLONG deadline,
                          ULONG opcode, const VOID *request, ULONG requestBytes,
                          VOID *response, ULONG responseCapacity,
                          ULONG *responseBytes, ULONG *bridgeStatus);
