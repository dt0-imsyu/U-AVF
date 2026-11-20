/* First-party U-AVF guest probe. No OS installation or network access. */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/fs.h>
#include <linux/vm_sockets.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/mount.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysinfo.h>
#include <unistd.h>

enum { PORT = 4069, NONCE_BYTES = 32, BLOCK_BYTES = 128 * 1024 * 1024 };
/* The builder reserves the gap after the ESP and before the backup GPT. */
enum { CHALLENGE_OFFSET = 127 * 1024 * 1024, WITNESS_OFFSET = CHALLENGE_OFFSET + 4096 };
enum { MEMORY_OK = 1, DISK_OK = 2 };

struct report {
    uint8_t magic[4];
    uint32_t flags;
    uint64_t ram_bytes;
    uint8_t nonce[NONCE_BYTES];
};
_Static_assert(sizeof(struct report) == 48, "Unexpected wire layout");

static int exercise_memory(void)
{
    const size_t bytes = 64 * 1024 * 1024;
    const size_t page = (size_t)sysconf(_SC_PAGESIZE);
    volatile uint8_t *data = mmap(NULL, bytes, PROT_READ | PROT_WRITE,
                                 MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (data == MAP_FAILED)
        return 0;
    for (size_t i = 0; i < bytes / page; ++i)
        data[i * page] = (uint8_t)(i * 37 + 1);
    int ok = 1;
    for (size_t i = 0; i < bytes / page; ++i)
        ok &= data[i * page] == (uint8_t)(i * 37 + 1);
    munmap((void *)data, bytes);
    return ok;
}

static int exercise_disk(const uint8_t nonce[NONCE_BYTES])
{
    int fd = open("/dev/vda", O_RDWR | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0)
        return 0;
    uint64_t bytes = 0;
    uint8_t actual[NONCE_BYTES], expected[NONCE_BYTES];
    int ok = ioctl(fd, BLKGETSIZE64, &bytes) == 0 && bytes == BLOCK_BYTES
        && pread(fd, actual, sizeof(actual), CHALLENGE_OFFSET) == sizeof(actual)
        && memcmp(actual, nonce, sizeof(actual)) == 0;
    if (ok) {
        for (size_t i = 0; i < sizeof(expected); ++i)
            expected[i] = nonce[i] ^ 0xa5;
        ok = pwrite(fd, expected, sizeof(expected), WITNESS_OFFSET) == sizeof(expected)
            && fdatasync(fd) == 0
            && pread(fd, actual, sizeof(actual), WITNESS_OFFSET) == sizeof(actual)
            && memcmp(actual, expected, sizeof(actual)) == 0;
    }
    close(fd);
    return ok;
}

static int transfer(int fd, void *buffer, size_t count, int sending)
{
    uint8_t *cursor = buffer;
    while (count) {
        ssize_t bytes = sending ? send(fd, cursor, count, MSG_NOSIGNAL)
                                : recv(fd, cursor, count, 0);
        if (bytes < 0 && errno == EINTR)
            continue;
        if (bytes <= 0)
            return 0;
        cursor += bytes;
        count -= (size_t)bytes;
    }
    return 1;
}

static int load_vsock_modules(void)
{
    static const char *const modules[] = {
        "/modules/vsock.ko",
        "/modules/vmw_vsock_virtio_transport_common.ko",
        "/modules/vmw_vsock_virtio_transport.ko"
    };
    for (size_t i = 0; i < sizeof(modules) / sizeof(modules[0]); ++i) {
        int fd = open(modules[i], O_RDONLY | O_CLOEXEC);
        int result = fd < 0 ? -1 : syscall(SYS_finit_module, fd, "", 0);
        int error = errno;
        if (fd >= 0)
            close(fd);
        if (result < 0 && error != EEXIST) {
            dprintf(STDOUT_FILENO, "UAVF_COMPAT_MODULE_ERROR path=%s errno=%d\n", modules[i], error);
            return 0;
        }
    }
    return 1;
}

int main(void)
{
    mount("devtmpfs", "/dev", "devtmpfs", 0, NULL);
    int console = open("/dev/console", O_WRONLY | O_CLOEXEC);
    if (console >= 0) {
        dup2(console, STDOUT_FILENO);
        dup2(console, STDERR_FILENO);
        if (console > STDERR_FILENO)
            close(console);
    }
    dprintf(STDOUT_FILENO, "UAVF_COMPAT_KERNEL_BOOTED\n");
    struct sysinfo memory;
    struct report result = {.magic = {'U', 'A', 'C', '1'}};
    if (sysinfo(&memory) == 0) {
        result.ram_bytes = (uint64_t)memory.totalram * memory.mem_unit;
        if (exercise_memory())
            result.flags |= MEMORY_OK;
    }
    dprintf(STDOUT_FILENO, "UAVF_COMPAT_MEMORY flags=%u bytes=%llu\n",
            result.flags, (unsigned long long)result.ram_bytes);

    if (!load_vsock_modules()) {
        for (;;)
            pause();
    }
    const struct sockaddr_vm address = {
        .svm_family = AF_VSOCK, .svm_port = PORT, .svm_cid = VMADDR_CID_ANY
    };
    int listener = socket(AF_VSOCK, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (listener < 0 || bind(listener, (const void *)&address, sizeof(address)) != 0
            || listen(listener, 1) != 0) {
        dprintf(STDOUT_FILENO, "UAVF_COMPAT_VSOCK_ERROR=%d\n", errno);
        for (;;)
            pause();
    }
    dprintf(STDOUT_FILENO, "UAVF_COMPAT_VSOCK_READY\n");
    for (;;) {
        struct sockaddr_vm peer = {0};
        socklen_t size = sizeof(peer);
        int client = accept(listener, (void *)&peer, &size);
        if (client < 0)
            continue;
        const struct timeval timeout = {.tv_sec = 5};
        setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(client, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
        if (peer.svm_cid == VMADDR_CID_HOST
                && transfer(client, result.nonce, sizeof(result.nonce), 0)) {
            result.flags &= ~DISK_OK;
            if (exercise_disk(result.nonce))
                result.flags |= DISK_OK;
            transfer(client, &result, sizeof(result), 1);
        }
        close(client);
    }
}
