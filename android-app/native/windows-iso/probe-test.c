// SPDX-License-Identifier: LicenseRef-UAVF-1.0
#include "windows_iso.h"
#include <fcntl.h>
#include <inttypes.h>
#include <stdio.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc != 2)
        return 64;
    int fd = open(argv[1], O_RDONLY);
    if (fd < 0)
        return 66;
    struct windows_iso_info info;
    enum windows_iso_status status = windows_iso_probe(fd, &info);
    close(fd);
    printf("status=%d iso=%" PRId64 " boot=%" PRId64 " install=%" PRId64 " esd=%d\n",
           status, info.iso_bytes, info.boot_wim_bytes, info.install_bytes, info.install_is_esd);
    return status;
}
