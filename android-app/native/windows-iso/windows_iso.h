#ifndef UAVF_WINDOWS_ISO_H
#define UAVF_WINDOWS_ISO_H

#include <stdint.h>

enum windows_iso_status {
    WINDOWS_ISO_OK,
    WINDOWS_ISO_UNSEEKABLE,
    WINDOWS_ISO_INVALID_UDF,
    WINDOWS_ISO_MISSING_ARM64_EFI,
    WINDOWS_ISO_MISSING_BOOT_WIM,
    WINDOWS_ISO_MISSING_INSTALL_IMAGE,
};

struct windows_iso_info {
    int64_t iso_bytes;
    int64_t boot_wim_bytes;
    int64_t install_bytes;
    int install_is_esd;
};

enum windows_iso_status windows_iso_probe(int fd, struct windows_iso_info *info);

struct windows_iso_payload;
struct windows_iso_payload *windows_iso_payload_open(int fd, int boot);
int64_t windows_iso_payload_size(const struct windows_iso_payload *payload);
int windows_iso_payload_read(struct windows_iso_payload *payload, int64_t offset,
                             void *buffer, int count);
void windows_iso_payload_close(struct windows_iso_payload *payload);

#endif
