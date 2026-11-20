#pragma once
#include <stdint.h>

/* Guest-local IPC only. UVF1/vsock/Android remain unchanged. No OS pointers. */
struct UavfIddWireHeader {
    uint32_t magic, version, header_bytes, pixel_format;
    uint32_t width, height, pitch, pixel_bytes;
    uint64_t sequence, display_qpc, qpc_frequency, dropped;
};
static_assert(sizeof(UavfIddWireHeader)==64,"IDD IPC ABI");
static const uint32_t UAVF_IDD_WIRE_MAGIC=0x31444955; /* UID1 */
static const uint32_t UAVF_IDD_FRAME_BYTES=1920u*1200u*4u;
static inline bool uavf_idd_wire_valid(const UavfIddWireHeader &h) {
    return h.magic==UAVF_IDD_WIRE_MAGIC && h.version==1 && h.header_bytes==64 &&
        h.pixel_format==87 && h.width==1920 && h.height==1200 &&
        h.pitch==7680 && h.pixel_bytes==UAVF_IDD_FRAME_BYTES &&
        h.sequence!=0 && h.qpc_frequency!=0;
}
