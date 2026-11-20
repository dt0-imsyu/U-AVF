/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#include <ntifs.h>
#include "mavk-admission.h"
#include "mavk-viosock.h"

/* ABI from virtio-win viosock/sys/public.h and viosock/inc/vio_sockets.h.
 * Accept is a create with the listener handle in the EA, not a socket IOCTL. */
#define VIO_DEVICE 0x0801
#define VIO_IOCTL(fn) CTL_CODE(VIO_DEVICE, 0x800 | (fn), METHOD_BUFFERED, FILE_ANY_ACCESS)
#define VIO_GET_AF CTL_CODE(VIO_DEVICE, 0xc03, METHOD_BUFFERED, FILE_ANY_ACCESS)
#define VIO_FIONBIO 0x8004667eu
#define MAVK_PORT 4057u
#define HOST_CID 2u

static volatile LONG64 wireGeneration;

typedef struct { USHORT Family, Reserved; ULONG Port, Cid; } VIO_ADDRESS;
typedef struct { ULONGLONG Listener; ULONG Type, Reserved; } VIO_PARAMS;
typedef struct {
    ULONG Next;
    UCHAR Flags, NameLength;
    USHORT ValueLength;
    UCHAR Name[4];
    UCHAR Value[sizeof(VIO_PARAMS)];
} VIO_EA;
typedef struct { ULONG Code, Size; ULONGLONG Address; } VIO_CONTROL;

C_ASSERT(sizeof(VIO_ADDRESS) == 12);
C_ASSERT(FIELD_OFFSET(VIO_EA, Value) == sizeof(FILE_FULL_EA_INFORMATION));
C_ASSERT(sizeof(VIO_PARAMS) == 16);
C_ASSERT(sizeof(VIO_CONTROL) == 16);
C_ASSERT(sizeof(struct mavk_admission) == 312);

/* NT event and cancellation exports are in the ARM64 WDK import library. */
NTSYSAPI NTSTATUS NTAPI ZwCreateEvent(PHANDLE, ACCESS_MASK, POBJECT_ATTRIBUTES, EVENT_TYPE, BOOLEAN);
NTSYSAPI NTSTATUS NTAPI ZwCancelIoFileEx(HANDLE, PIO_STATUS_BLOCK, PIO_STATUS_BLOCK);

static NTSTATUS OpenSocket(HANDLE *file, HANDLE listener, BOOLEAN socket)
{
    UNICODE_STRING name;
    OBJECT_ATTRIBUTES attributes;
    IO_STATUS_BLOCK io;
    HANDLE opened = NULL;
    VIO_EA ea = {0};
    VIO_PARAMS params = {(ULONG_PTR)listener, 1, 0};
    ea.NameLength = sizeof(ea.Name) - 1;
    ea.ValueLength = sizeof(params);
    RtlCopyMemory(ea.Value, &params, sizeof(params));
    RtlInitUnicodeString(&name, L"\\DosDevices\\Viosock");
    InitializeObjectAttributes(&attributes, &name, OBJ_CASE_INSENSITIVE | OBJ_KERNEL_HANDLE, NULL, NULL);
    NTSTATUS status = ZwCreateFile(&opened, FILE_GENERIC_READ | FILE_GENERIC_WRITE, &attributes, &io,
                                   NULL, 0, FILE_SHARE_READ | FILE_SHARE_WRITE, FILE_OPEN,
                                   FILE_NON_DIRECTORY_FILE, socket ? &ea : NULL, socket ? sizeof(ea) : 0);
    if (NT_SUCCESS(status)) *file = opened;
    return status;
}

/* Keep the buffers and status block alive until cancellation is acknowledged. */
static NTSTATUS Await(HANDLE file, HANDLE event, PIO_STATUS_BLOCK io,
                      NTSTATUS issued, ULONGLONG deadline)
{
    if (issued != STATUS_PENDING) return issued;
    ULONGLONG now = KeQueryUnbiasedInterruptTime();
    LARGE_INTEGER timeout;
    timeout.QuadPart = now < deadline ? -(LONGLONG)(deadline - now) : 0;
    NTSTATUS wait = ZwWaitForSingleObject(event, FALSE, &timeout);
    if (wait == STATUS_SUCCESS) return io->Status;
    IO_STATUS_BLOCK cancel;
    ZwCancelIoFileEx(file, io, &cancel);
    ZwWaitForSingleObject(event, FALSE, NULL);
    return wait == STATUS_TIMEOUT ? STATUS_IO_TIMEOUT : wait;
}

static NTSTATUS Control(HANDLE file, HANDLE event, ULONGLONG deadline, ULONG code,
                        PVOID in, ULONG inSize, PVOID out, ULONG outSize)
{
    IO_STATUS_BLOCK io = {0};
    NTSTATUS status = ZwDeviceIoControlFile(file, event, NULL, NULL, &io, code,
                                            in, inSize, out, outSize);
    status = Await(file, event, &io, status, deadline);
    if (NT_SUCCESS(status) && io.Information != outSize) return STATUS_DEVICE_PROTOCOL_ERROR;
    return status;
}

static NTSTATUS Transfer(HANDLE file, HANDLE event, ULONGLONG deadline,
                         PVOID data, ULONG bytes, BOOLEAN write)
{
    UCHAR *p = data;
    /* The Viosock endpoint was opened asynchronously. NT file I/O expects
     * an explicit offset for an asynchronous non-pipe file object. Viosock
     * ignores the offset and consumes/produces a byte stream. */
    LARGE_INTEGER offset = {0};
    while (bytes) {
        if (KeQueryUnbiasedInterruptTime() >= deadline) return STATUS_IO_TIMEOUT;
        IO_STATUS_BLOCK io = {0};
        NTSTATUS status = write ? ZwWriteFile(file, event, NULL, NULL, &io, p, bytes, &offset, NULL)
                                : ZwReadFile(file, event, NULL, NULL, &io, p, bytes, &offset, NULL);
        status = Await(file, event, &io, status, deadline);
        if (status == STATUS_CANT_WAIT) {
            LARGE_INTEGER backoff;
            backoff.QuadPart = -10000; // Viosock nonblocking receive can race the host reply.
            KeDelayExecutionThread(KernelMode, FALSE, &backoff);
            continue;
        }
        if (!NT_SUCCESS(status)) return status;
        if (!io.Information || io.Information > bytes) return STATUS_DEVICE_PROTOCOL_ERROR;
        p += io.Information;
        bytes -= (ULONG)io.Information;
    }
    return STATUS_SUCCESS;
}

/* Windows ARM64 is little-endian. All wire fields are explicit u32 words. */
NTSTATUS MavkWireExchange(MAVK_VIOSOCK *wire, ULONGLONG deadline, ULONG opcode,
                          const VOID *request, ULONG requestBytes,
                          VOID *response, ULONG responseCapacity,
                          ULONG *responseBytes, ULONG *bridgeStatus)
{
    *responseBytes = 0;
    *bridgeStatus = 0;
    if (requestBytes > MAVK_WIRE_MAX_PAYLOAD || responseCapacity > MAVK_WIRE_MAX_PAYLOAD ||
        (requestBytes && !request) || (responseCapacity && !response))
        return STATUS_INVALID_BUFFER_SIZE;
    if (!wire->Peer || !wire->Event || wire->Sequence == MAXULONGLONG)
        return STATUS_INVALID_DEVICE_STATE;

    ULONGLONG sequence = ++wire->Sequence;
    ULONG frame[8] = {0x314b564d, 0x00200001, opcode, 0, (ULONG)sequence,
                      (ULONG)(sequence >> 32), requestBytes, 0};
    NTSTATUS status = Transfer(wire->Peer, wire->Event, deadline, frame, sizeof(frame), TRUE);
    if (!NT_SUCCESS(status)) return status;
    status = Transfer(wire->Peer, wire->Event, deadline, (PVOID)request, requestBytes, TRUE);
    if (!NT_SUCCESS(status)) return status;
    status = Transfer(wire->Peer, wire->Event, deadline, frame, sizeof(frame), FALSE);
    if (!NT_SUCCESS(status)) return status;
    if (frame[0] != 0x314b564d || frame[1] != 0x00200001 || frame[2] != opcode ||
        frame[3] != 1 || frame[4] != (ULONG)sequence || frame[5] != (ULONG)(sequence >> 32) ||
        frame[6] > responseCapacity || (frame[7] && frame[6])) return STATUS_DEVICE_PROTOCOL_ERROR;
    status = Transfer(wire->Peer, wire->Event, deadline, response, frame[6], FALSE);
    if (NT_SUCCESS(status)) {
        *responseBytes = frame[6];
        *bridgeStatus = frame[7];
    }
    return status;
}

static NTSTATUS Exchange(MAVK_VIOSOCK *wire, ULONGLONG deadline, ULONG op,
                         const VOID *request, ULONG requestBytes, PVOID response, ULONG responseBytes)
{
    ULONG actual, bridgeStatus;
    NTSTATUS status = MavkWireExchange(wire, deadline, op, request, requestBytes,
                                       response, responseBytes, &actual, &bridgeStatus);
    if (NT_SUCCESS(status) && (actual != responseBytes || bridgeStatus))
        return STATUS_DEVICE_PROTOCOL_ERROR;
    return status;
}

NTSTATUS MavkCodecExchange(MAVK_VIOSOCK *wire, ULONGLONG deadline, const VOID *packet,
                           ULONG packetBytes, VOID *reply, ULONG replyBytes)
{
    if (KeGetCurrentIrql() != PASSIVE_LEVEL) return STATUS_INVALID_DEVICE_STATE;
    NTSTATUS status = Exchange(wire, deadline, 29, packet, packetBytes, reply, replyBytes);
    if (!NT_SUCCESS(status)) MavkWireClose(wire);
    return status;
}

NTSTATUS MavkCodecResult(LONG result)
{
    if (!result) return STATUS_SUCCESS;
    if (result == -1 || result == -2) return STATUS_INSUFFICIENT_RESOURCES;
    if (result == -4) return STATUS_DEVICE_HARDWARE_ERROR;
    return STATUS_UNSUCCESSFUL;
}

static NTSTATUS VerifyCompletion(MAVK_VIOSOCK *wire, ULONGLONG deadline, uint32_t *checked)
{
    enum { Bytes = 1024, Words = Bytes / sizeof(ULONG) };
    const ULONG size = Bytes, seed = 0x4d41564b;
    ULONG id[2];
    NTSTATUS status = Exchange(wire, deadline, 2, &size, sizeof(size), id, sizeof(id));
    if (!NT_SUCCESS(status)) return status;
    if (!id[0] && !id[1]) return STATUS_DEVICE_PROTOCOL_ERROR;
    const ULONG dispatch[] = {id[0], id[1], seed};
    /* DispatchVerify replies only after the existing host Vulkan fence signals. */
    status = Exchange(wire, deadline, 4, dispatch, sizeof(dispatch), NULL, 0);
    if (!NT_SUCCESS(status)) return status;
    const ULONG read[] = {id[0], id[1], 0, Bytes};
    ULONG words[Words];
    status = Exchange(wire, deadline, 5, read, sizeof(read), words, sizeof(words));
    if (!NT_SUCCESS(status)) return status;
    for (ULONG i = 0; i < Words; ++i) {
        if (words[i] != ((i * 1664525u + 1013904223u) ^ seed)) return STATUS_DATA_ERROR;
        ++*checked;
    }
    return Exchange(wire, deadline, 3, id, sizeof(id), NULL, 0);
}

static NTSTATUS Draw(MAVK_VIOSOCK *wire, ULONGLONG deadline, uint32_t *checked)
{
    enum { Width = 64, Bytes = Width * Width * 4 };
    const ULONG extent[] = {Width, Width};
    UCHAR *pixels = ExAllocatePool2(POOL_FLAG_NON_PAGED, Bytes, 'kvaM');
    if (!pixels) return STATUS_INSUFFICIENT_RESOURCES;
    NTSTATUS status = Exchange(wire, deadline, 6, extent, sizeof(extent), pixels, Bytes);
    if (NT_SUCCESS(status)) {
        /* Same interior/exterior mask as bridge_probe.c, evaluated in fixed
         * point to avoid floating-point/SIMD use in the kernel driver. */
        for (int y = 0; y < Width && NT_SUCCESS(status); ++y) {
            int ny = 2 * y + 1 - Width;
            for (int x = 0; x < Width; ++x) {
                int nx = 2 * x + 1 - Width;
                int absx = nx < 0 ? -nx : nx;
                int side = 48 - ny - 2 * absx;
                BOOLEAN inside = ny * 100 > -67 * Width && ny * 100 < 67 * Width &&
                                 side * 100 > 16 * Width;
                BOOLEAN outside = ny * 100 < -83 * Width || ny * 100 > 83 * Width ||
                                  side * 100 < -16 * Width;
                if (!inside && !outside) continue;
                const UCHAR *p = pixels + (y * Width + x) * 4;
                if (p[2] > 1 || p[3] < 254 ||
                    (inside ? (p[0] < 254 || p[1] < 63 || p[1] > 65) : (p[0] > 1 || p[1] > 1))) {
                    status = STATUS_DATA_ERROR;
                    break;
                }
                ++*checked;
            }
        }
        if (NT_SUCCESS(status) && *checked != 3330) status = STATUS_DATA_ERROR;
    }
    ExFreePoolWithTag(pixels, 'kvaM');
    return status;
}

static NTSTATUS OpenWire(MAVK_VIOSOCK *wire, USHORT port, ULONGLONG deadline,
                         uint32_t *stage, ULONG hello[4])
{
    HANDLE device = NULL, listener = NULL, peer = NULL, event = NULL;
    OBJECT_ATTRIBUTES attributes;
    ULONG family = 0, backlog = 1, nonblocking = 1;
    VIO_ADDRESS address = {0};
    VIO_CONTROL control = {VIO_FIONBIO, sizeof(nonblocking), (ULONG_PTR)&nonblocking};
    LARGE_INTEGER delay;
    delay.QuadPart = -500000;
    NTSTATUS status;

    if (KeGetCurrentIrql() != PASSIVE_LEVEL || !port || wire->Peer || wire->Event ||
        wire->Sequence || wire->Generation)
        return STATUS_INVALID_DEVICE_STATE;
    *stage = MAVK_OPEN_DEVICE;
    InitializeObjectAttributes(&attributes, NULL, OBJ_KERNEL_HANDLE, NULL, NULL);
    status = ZwCreateEvent(&event, EVENT_ALL_ACCESS, &attributes, NotificationEvent, FALSE);
    if (!NT_SUCCESS(status)) goto done;
    status = OpenSocket(&device, NULL, FALSE);
    if (!NT_SUCCESS(status)) goto done;
    status = Control(device, event, deadline, VIO_GET_AF, NULL, 0, &family, sizeof(family));
    if (!NT_SUCCESS(status)) goto done;
    if (family == 0 || family > MAXUSHORT) { status = STATUS_DEVICE_PROTOCOL_ERROR; goto done; }

    *stage = MAVK_OPEN_LISTENER;
    status = OpenSocket(&listener, NULL, TRUE);
    if (!NT_SUCCESS(status)) goto done;
    status = Control(listener, event, deadline, VIO_IOCTL(12), &control, sizeof(control), NULL, 0);
    if (!NT_SUCCESS(status)) goto done;
    *stage = MAVK_BIND;
    address.Family = (USHORT)family;
    address.Port = port;
    address.Cid = MAXULONG;
    status = Control(listener, event, deadline, VIO_IOCTL(1), &address, sizeof(address), NULL, 0);
    if (!NT_SUCCESS(status)) goto done;
    *stage = MAVK_LISTEN;
    status = Control(listener, event, deadline, VIO_IOCTL(5), &backlog, sizeof(backlog), NULL, 0);
    if (!NT_SUCCESS(status)) goto done;

    *stage = MAVK_ACCEPT;
    do {
        status = OpenSocket(&peer, listener, TRUE);
        if (status != STATUS_CANT_WAIT) break;
        KeDelayExecutionThread(KernelMode, FALSE, &delay);
    } while (KeQueryUnbiasedInterruptTime() < deadline);
    if (status == STATUS_CANT_WAIT) status = STATUS_IO_TIMEOUT;
    if (!NT_SUCCESS(status)) goto done;

    *stage = MAVK_CHECK_PEER;
    status = Control(peer, event, deadline, VIO_IOCTL(8), NULL, 0, &address, sizeof(address));
    if (!NT_SUCCESS(status)) goto done;
    if (address.Family != family || address.Cid != HOST_CID) { status = STATUS_ACCESS_DENIED; goto done; }
    wire->Peer = peer;
    wire->Event = event;
    /* Some Viosock versions set SOCK_NON_BLOCK even for FIONBIO=0.
     * Leave the accepted endpoint as delivered and handle CANT_WAIT in I/O. */

    *stage = MAVK_HELLO;
    status = Exchange(wire, deadline, 1, NULL, 0, hello, 4 * sizeof(*hello));
    if (!NT_SUCCESS(status)) goto done;
    if (hello[0] != 1 || hello[1] != 1048576 || !hello[2] || hello[3] < hello[1]) {
        status = STATUS_DEVICE_PROTOCOL_ERROR;
        goto done;
    }
    wire->Generation = (ULONGLONG)InterlockedIncrement64(&wireGeneration);
    peer = event = NULL; // The stream owns both on success.
done:
    if (peer) ZwClose(peer);
    if (listener) ZwClose(listener);
    if (device) ZwClose(device);
    if (event) ZwClose(event);
    if (!NT_SUCCESS(status)) RtlZeroMemory(wire, sizeof(*wire));
    return status;
}

NTSTATUS MavkWireOpen(MAVK_VIOSOCK *wire, USHORT port, ULONGLONG deadline)
{
    uint32_t stage;
    ULONG hello[4];
    return OpenWire(wire, port, deadline, &stage, hello);
}

VOID MavkWireClose(MAVK_VIOSOCK *wire)
{
    if (wire->Peer) ZwClose(wire->Peer);
    if (wire->Event) ZwClose(wire->Event);
    RtlZeroMemory(wire, sizeof(*wire));
}

NTSTATUS MavkWireQueryCodecVersion(MAVK_VIOSOCK *wire, ULONGLONG deadline, ULONG *api)
{
    const ULONG request[] = {3, 20137, 12, 0};
    ULONG reply[2];
    NTSTATUS status = MavkCodecExchange(wire, deadline, request, sizeof(request), reply, sizeof(reply));
    if (!NT_SUCCESS(status)) return status;
    if (reply[1] || ((reply[0] >> 22) & 0x7f) != 1)
        return STATUS_DEVICE_PROTOCOL_ERROR;
    *api = reply[0];
    return STATUS_SUCCESS;
}

VOID MavkAdmit(struct mavk_admission *report, BOOLEAN codec)
{
    ULONG hello[4];
    struct { ULONG Api, Driver, Vendor, Device, Type; char Name[256]; } identity;
    MAVK_VIOSOCK wire = {0};
    ULONGLONG deadline = KeQueryUnbiasedInterruptTime() + 200000000ull;
    NTSTATUS status = OpenWire(&wire, MAVK_PORT, deadline, &report->stage, hello);
    if (!NT_SUCCESS(status)) goto done;
    report->max_payload = hello[1];
    report->max_buffers = hello[2];
    report->max_buffer_bytes = hello[3];
    report->stage = MAVK_DEVICE_IDENTITY;
    status = Exchange(&wire, deadline, 7, NULL, 0, &identity, sizeof(identity));
    if (!NT_SUCCESS(status)) goto done;
    report->api_version = identity.Api;
    report->driver_version = identity.Driver;
    report->vendor = identity.Vendor;
    report->device = identity.Device;
    report->device_type = identity.Type;
    RtlCopyMemory(report->device_name, identity.Name, sizeof(identity.Name) - 1);
    if (codec) {
        ULONG version;
        report->stage = MAVK_CODEC_VERSION;
        status = MavkWireQueryCodecVersion(&wire, deadline, &version);
        if (NT_SUCCESS(status)) {
            report->codec_api_version = version;
            report->stage = MAVK_COMPLETE;
        }
        goto done;
    }
    report->stage = MAVK_GPU_COMPLETION;
    status = VerifyCompletion(&wire, deadline, &report->checked_words);
    if (!NT_SUCCESS(status)) goto done;
    report->stage = MAVK_DRAW;
    status = Draw(&wire, deadline, &report->checked_pixels);
    if (!NT_SUCCESS(status)) goto done;
    report->stage = MAVK_COMPLETE;
done:
    MavkWireClose(&wire);
    report->status = (uint32_t)status;
}
