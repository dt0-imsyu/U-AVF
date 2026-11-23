// SPDX-License-Identifier: LicenseRef-UAVF-1.0
#include "windows_iso.h"
#include "blockinput.h"
#include "udfread.h"

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>

struct iso_input {
    udfread_block_input ops;
    int fd;
    uint32_t blocks;
};

static uint32_t input_size(udfread_block_input *ops) {
    return ((struct iso_input *)ops)->blocks;
}

static int input_read(udfread_block_input *ops, uint32_t lba, void *output,
                      uint32_t blocks, int flags) {
    (void)flags;
    struct iso_input *input = (struct iso_input *)ops;
    if (lba >= input->blocks || blocks > input->blocks - lba || blocks > 1024)
        return -1;
    size_t total = (size_t)blocks * UDF_BLOCK_SIZE;
    size_t done = 0;
    while (done < total) {
        ssize_t count = pread(input->fd, (char *)output + done, total - done,
                              (off_t)lba * UDF_BLOCK_SIZE + (off_t)done);
        if (count < 0 && errno == EINTR)
            continue;
        if (count <= 0)
            return -1;
        done += (size_t)count;
    }
    return (int)blocks;
}

enum { MAX_ISO_COMPONENT = 255, MAX_ISO_PATH = 4096 };

static int find_name(UDFDIR *directory, const char *expected, char name[MAX_ISO_COMPONENT + 1]) {
    struct udfread_dirent entry;
    int found = 0;
    udfread_rewinddir(directory);
    while (udfread_readdir(directory, &entry)) {
        if (strcasecmp(entry.d_name, expected) != 0)
            continue;
        if (found || strlen(entry.d_name) > MAX_ISO_COMPONENT)
            return 0;
        strcpy(name, entry.d_name);
        found = 1;
    }
    return found;
}

static UDFDIR *open_directory(UDFDIR *parent, const char *expected) {
    char name[MAX_ISO_COMPONENT + 1];
    return find_name(parent, expected, name) ? udfread_opendir_at(parent, name) : NULL;
}

static UDFFILE *open_file(UDFDIR *parent, const char *expected) {
    char name[MAX_ISO_COMPONENT + 1];
    return find_name(parent, expected, name) ? udfread_file_openat(parent, name) : NULL;
}

static uint32_t le32(const unsigned char *bytes) {
    return (uint32_t)bytes[0] | (uint32_t)bytes[1] << 8 |
           (uint32_t)bytes[2] << 16 | (uint32_t)bytes[3] << 24;
}

static int arm64_boot_loader(UDFDIR *root) {
    UDFDIR *efi = open_directory(root, "efi");
    if (!efi)
        return 0;
    UDFDIR *boot = open_directory(efi, "boot");
    UDFFILE *loader = boot ? open_file(boot, "bootaa64.efi") : NULL;
    int valid = 0;
    unsigned char dos[64], coff[24];
    if (loader && udfread_file_read(loader, dos, sizeof(dos)) == sizeof(dos) &&
        memcmp(dos, "MZ", 2) == 0) {
        uint32_t offset = le32(dos + 60);
        if (offset >= sizeof(dos) && (int64_t)offset + (int64_t)sizeof(coff) <= udfread_file_size(loader) &&
            udfread_file_seek(loader, offset, SEEK_SET) == offset &&
            udfread_file_read(loader, coff, sizeof(coff)) == sizeof(coff)) {
            valid = memcmp(coff, "PE\0\0", 4) == 0 && coff[4] == 0x64 && coff[5] == 0xaa;
        }
    }
    if (loader) udfread_file_close(loader);
    if (boot) udfread_closedir(boot);
    udfread_closedir(efi);
    return valid;
}

static UDFFILE *open_image(UDFDIR *sources, const char *name) {
    UDFFILE *file = open_file(sources, name);
    if (!file)
        return NULL;
    unsigned char header[48];
    int64_t length = udfread_file_size(file);
    if (length >= 208 && udfread_file_read(file, header, sizeof(header)) == sizeof(header) &&
        memcmp(header, "MSWIM\0\0\0", 8) == 0 &&
        le32(header + 8) >= 208 && le32(header + 8) <= length &&
        le32(header + 40) == 0x00010001 && le32(header + 44) > 0) {
        return file;
    }
    udfread_file_close(file);
    return NULL;
}

static enum windows_iso_status setup_input(int fd, struct iso_input *input) {
    struct stat stat;
    if (fstat(fd, &stat) != 0 || !S_ISREG(stat.st_mode) || stat.st_size <= 0)
        return WINDOWS_ISO_UNSEEKABLE;
    if (stat.st_size % UDF_BLOCK_SIZE != 0 || stat.st_size / UDF_BLOCK_SIZE > UINT32_MAX)
        return WINDOWS_ISO_INVALID_LENGTH;

    *input = (struct iso_input){{NULL, input_read, input_size}, fd, (uint32_t)(stat.st_size / UDF_BLOCK_SIZE)};
    return WINDOWS_ISO_OK;
}

enum windows_iso_status windows_iso_probe(int fd, struct windows_iso_info *info) {
    memset(info, 0, sizeof(*info));
    struct iso_input input;
    enum windows_iso_status status = setup_input(fd, &input);
    if (status != WINDOWS_ISO_OK)
        return status;
    udfread *volume = udfread_init();
    UDFDIR *root = NULL;
    UDFDIR *sources = NULL;
    UDFFILE *boot = NULL;
    UDFFILE *install = NULL;
    status = WINDOWS_ISO_INVALID_UDF;
    if (!volume || udfread_open_input(volume, &input.ops) < 0 || !(root = udfread_opendir(volume, "/")))
        goto finish;
    status = WINDOWS_ISO_MISSING_ARM64_EFI;
    if (!arm64_boot_loader(root))
        goto finish;
    status = WINDOWS_ISO_MISSING_BOOT_WIM;
    sources = open_directory(root, "sources");
    if (!sources || !(boot = open_image(sources, "boot.wim")))
        goto finish;
    info->boot_wim_bytes = udfread_file_size(boot);
    status = WINDOWS_ISO_MISSING_INSTALL_IMAGE;
    install = open_image(sources, "install.wim");
    if (!install) {
        install = open_image(sources, "install.esd");
        info->install_is_esd = 1;
    }
    if (!install)
        goto finish;
    info->install_bytes = udfread_file_size(install);
    info->iso_bytes = (int64_t)input.blocks * UDF_BLOCK_SIZE;
    status = WINDOWS_ISO_OK;

finish:
    if (install) udfread_file_close(install);
    if (boot) udfread_file_close(boot);
    if (sources) udfread_closedir(sources);
    if (root) udfread_closedir(root);
    if (volume) udfread_close(volume);
    return status;
}

struct windows_iso_payload {
    struct iso_input input;
    udfread *volume;
    UDFFILE *file;
};

static struct windows_iso_payload *open_volume(int fd) {
    struct windows_iso_payload *payload = calloc(1, sizeof(*payload));
    if (!payload)
        return NULL;
    payload->input.fd = -1;
    int owned_fd = fcntl(fd, F_DUPFD_CLOEXEC, 0);
    if (owned_fd < 0 || setup_input(owned_fd, &payload->input) != WINDOWS_ISO_OK) {
        if (owned_fd >= 0) close(owned_fd);
        free(payload);
        return NULL;
    }
    payload->volume = udfread_init();
    if (!payload->volume || udfread_open_input(payload->volume, &payload->input.ops) < 0) {
        windows_iso_payload_close(payload);
        return NULL;
    }
    return payload;
}

static int walk_directory(struct windows_iso_payload *source, UDFDIR *directory,
                          char path[MAX_ISO_PATH + 1], size_t used, unsigned depth,
                          windows_iso_visitor visitor, void *state) {
    if (depth > 24)
        return -1;
    struct udfread_dirent entry;
    while (udfread_readdir(directory, &entry)) {
        const char *name = entry.d_name;
        if (!strcmp(name, ".") || !strcmp(name, ".."))
            continue;
        size_t length = strnlen(name, MAX_ISO_COMPONENT + 1);
        if (!length || length > MAX_ISO_COMPONENT || used + length + 1 > MAX_ISO_PATH ||
            strchr(name, '/') || strchr(name, '\\'))
            return -1;
        memcpy(path + used, name, length + 1);
        int result;
        if (entry.d_type == UDF_DT_DIR) {
            result = visitor(path, NULL, state);
            if (!result) {
                UDFDIR *child = udfread_opendir_at(directory, name);
                if (!child)
                    return -1;
                path[used + length] = '/';
                result = walk_directory(source, child, path, used + length + 1, depth + 1,
                                        visitor, state);
                udfread_closedir(child);
            }
        } else if (entry.d_type == UDF_DT_REG) {
            source->file = udfread_file_openat(directory, name);
            if (!source->file)
                return -1;
            result = visitor(path, source, state);
            udfread_file_close(source->file);
            source->file = NULL;
        } else {
            return -1;
        }
        if (result)
            return result;
    }
    return 0;
}

int windows_iso_walk(int fd, windows_iso_visitor visitor, void *state) {
    if (!visitor)
        return -1;
    struct windows_iso_payload *source = open_volume(fd);
    if (!source)
        return -1;
    UDFDIR *root = udfread_opendir(source->volume, "/");
    char path[MAX_ISO_PATH + 1];
    int result = root ? walk_directory(source, root, path, 0, 0, visitor, state) : -1;
    if (root)
        udfread_closedir(root);
    windows_iso_payload_close(source);
    return result;
}

struct windows_iso_payload *windows_iso_payload_open(int fd, int boot) {
    struct windows_iso_payload *payload = open_volume(fd);
    if (!payload)
        return NULL;
    UDFDIR *root = udfread_opendir(payload->volume, "/");
    UDFDIR *sources = root && arm64_boot_loader(root) ? open_directory(root, "sources") : NULL;
    if (sources) {
        payload->file = open_image(sources, boot ? "boot.wim" : "install.wim");
        if (!payload->file && !boot)
            payload->file = open_image(sources, "install.esd");
        udfread_closedir(sources);
    }
    if (root) udfread_closedir(root);
    if (payload->file)
        return payload;
    windows_iso_payload_close(payload);
    return NULL;
}

struct windows_iso_payload *windows_iso_file_open(int fd, const char *path) {
    if (!path || !*path || strnlen(path, MAX_ISO_PATH + 1) > MAX_ISO_PATH)
        return NULL;

    struct windows_iso_payload *payload = open_volume(fd);
    if (!payload)
        return NULL;
    UDFDIR *directory = udfread_opendir(payload->volume, "/");
    while (directory) {
        const char *end = strchr(path, '/');
        size_t length = end ? (size_t)(end - path) : strlen(path);
        if (!length || length > MAX_ISO_COMPONENT || memchr(path, '\\', length)
            || (length == 1 && path[0] == '.')
            || (length == 2 && path[0] == '.' && path[1] == '.'))
            break;
        char component[MAX_ISO_COMPONENT + 1];
        memcpy(component, path, length);
        component[length] = '\0';
        if (!end) {
            payload->file = open_file(directory, component);
            break;
        }
        UDFDIR *child = open_directory(directory, component);
        udfread_closedir(directory);
        directory = child;
        path = end + 1;
    }
    if (directory)
        udfread_closedir(directory);
    if (payload->file)
        return payload;
    windows_iso_payload_close(payload);
    return NULL;
}

int64_t windows_iso_payload_size(const struct windows_iso_payload *payload) {
    return udfread_file_size(payload->file);
}

int windows_iso_payload_read(struct windows_iso_payload *payload, int64_t offset,
                             void *buffer, int count) {
    int64_t size = windows_iso_payload_size(payload);
    if (offset < 0 || count < 0 || offset > size)
        return -1;
    if (size - offset < count)
        count = (int)(size - offset);
    if (count == 0)
        return 0;
    if (udfread_file_seek(payload->file, offset, SEEK_SET) != offset ||
        udfread_file_read(payload->file, buffer, count) != count)
        return -1;
    return count;
}

void windows_iso_payload_close(struct windows_iso_payload *payload) {
    if (payload->file) udfread_file_close(payload->file);
    if (payload->volume) udfread_close(payload->volume);
    close(payload->input.fd);
    free(payload);
}
