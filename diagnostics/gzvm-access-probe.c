/* Read-only access check: no VM creation, ioctl or memory registration. */
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
int main(void) {
    int fd = open("/dev/gzvm", O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        printf("GZVM_OPEN=FAIL errno=%d message=%s\n", errno, strerror(errno));
        return 1;
    }
    printf("GZVM_OPEN=PASS (no ioctl performed)\n");
    close(fd);
    return 0;
}
