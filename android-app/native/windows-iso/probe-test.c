// SPDX-License-Identifier: LicenseRef-UAVF-1.0
#include "windows_iso.h"
#include <fcntl.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static int check_file_reader(int fd) {
    const char *invalid[] = {"/efi/boot/bootaa64.efi", "efi//boot/bootaa64.efi",
                             "efi/../sources/boot.wim", "efi/./boot/bootaa64.efi",
                             "efi/boot/", "efi/boot/missing.efi"};
    for (size_t i = 0; i < sizeof(invalid) / sizeof(invalid[0]); i++) {
        struct windows_iso_payload *unexpected = windows_iso_file_open(fd, invalid[i]);
        if (unexpected) {
            windows_iso_payload_close(unexpected);
            return 1;
        }
    }
    struct windows_iso_payload *loader = windows_iso_file_open(fd, "EFI/boot/bootaa64.efi");
    if (!loader)
        return 1;
    unsigned char dos[64], header[4];
    int read_dos = windows_iso_payload_read(loader, 0, dos, sizeof(dos)) == sizeof(dos);
    uint32_t pe = read_dos ? (uint32_t)dos[60] | (uint32_t)dos[61] << 8
                              | (uint32_t)dos[62] << 16 | (uint32_t)dos[63] << 24 : 0;
    int passed = read_dos && memcmp(dos, "MZ", 2) == 0 && pe >= sizeof(dos)
        && windows_iso_payload_read(loader, pe, header, sizeof(header)) == sizeof(header)
        && memcmp(header, "PE\0\0", sizeof(header)) == 0
        && windows_iso_payload_read(loader, windows_iso_payload_size(loader), header, sizeof(header)) == 0;
    windows_iso_payload_close(loader);
    return !passed;
}

int main(int argc, char **argv) {
    if (argc != 2 && (argc != 3 || strcmp(argv[2], "--check-files") != 0))
        return 64;
    int fd = open(argv[1], O_RDONLY);
    if (fd < 0)
        return 66;
    struct windows_iso_info info;
    enum windows_iso_status status = windows_iso_probe(fd, &info);
    int file_error = argc == 3 && status == WINDOWS_ISO_OK && check_file_reader(fd);
    close(fd);
    printf("status=%d iso=%" PRId64 " boot=%" PRId64 " install=%" PRId64 " esd=%d\n",
           status, info.iso_bytes, info.boot_wim_bytes, info.install_bytes, info.install_is_esd);
    if (argc == 3 && status == WINDOWS_ISO_OK)
        printf("file_reader=%s\n", file_error ? "FAIL" : "PASS");
    return file_error ? 65 : status;
}
