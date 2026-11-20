// SPDX-License-Identifier: LicenseRef-UAVF-1.0
#include "ff.h"
#include "diskio.h"
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <linux/fs.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

enum { sector_bytes = 512, max_parts = 9, chunk_bytes = 256 * 1024 };
static pthread_mutex_t volume_lock = PTHREAD_MUTEX_INITIALIZER;
static int disk_fd = -1;
static off_t volume_offset;
static LBA_t volume_sectors;

static int transfer(int fd, off_t offset, void *buffer, size_t size, int writing) {
    size_t done = 0;
    while (done < size) {
        void *position = (char *)buffer + done;
        ssize_t count = writing ? pwrite(fd, position, size - done, offset + (off_t)done)
                                : pread(fd, position, size - done, offset + (off_t)done);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return -1;
        done += (size_t)count;
    }
    return 0;
}

DSTATUS disk_status(BYTE drive) {
    return drive == 0 && disk_fd >= 0 ? 0 : STA_NOINIT;
}

DSTATUS disk_initialize(BYTE drive) {
    return disk_status(drive);
}

static int sector_range(BYTE drive, LBA_t sector, UINT count) {
    return !disk_status(drive) && count && sector < volume_sectors &&
           count <= volume_sectors - sector;
}

DRESULT disk_read(BYTE drive, BYTE *output, LBA_t sector, UINT count) {
    if (!sector_range(drive, sector, count)) return RES_PARERR;
    return transfer(disk_fd, volume_offset + (off_t)sector * sector_bytes,
                    output, (size_t)count * sector_bytes, 0) ? RES_ERROR : RES_OK;
}

DRESULT disk_write(BYTE drive, const BYTE *input, LBA_t sector, UINT count) {
    if (!sector_range(drive, sector, count)) return RES_PARERR;
    return transfer(disk_fd, volume_offset + (off_t)sector * sector_bytes,
                    (void *)input, (size_t)count * sector_bytes, 1) ? RES_ERROR : RES_OK;
}

DRESULT disk_ioctl(BYTE drive, BYTE operation, void *data) {
    if (disk_status(drive)) return RES_NOTRDY;
    switch (operation) {
        case CTRL_SYNC: return fsync(disk_fd) ? RES_ERROR : RES_OK;
        case GET_SECTOR_COUNT: *(LBA_t *)data = volume_sectors; return RES_OK;
        case GET_SECTOR_SIZE: *(WORD *)data = sector_bytes; return RES_OK;
        default: return RES_PARERR;
    }
}

static void part_name(char name[24], int part) {
    if (part == 0) strcpy(name, "/SOURCES/INSTALL.SWM");
    else snprintf(name, 24, "/SOURCES/INSTALL%d.SWM", part + 1);
}

static int valid_part_header(const unsigned char *header, int part, int count) {
    return !memcmp(header, "MSWIM\0\0\0", 8) && (header[16] & 8) &&
           header[40] == part + 1 && !header[41] && header[42] == count && !header[43];
}

static int validate_parts(const jint *fds, int count, off_t *sizes, const struct stat *destination) {
    unsigned char guid[16];
    for (int part = 0; part < count; ++part) {
        struct stat info;
        unsigned char header[48];
        if (fstat(fds[part], &info) || !S_ISREG(info.st_mode) ||
            (info.st_dev == destination->st_dev && info.st_ino == destination->st_ino) ||
            info.st_size < 208 || (uint64_t)info.st_size >= UINT64_C(4294967296) ||
            transfer(fds[part], 0, header, sizeof(header), 0))
            return -1;
        if (!valid_part_header(header, part, count)) return -1;
        if (!part) memcpy(guid, header + 24, sizeof(guid));
        else if (memcmp(guid, header + 24, sizeof(guid))) return -1;
        sizes[part] = info.st_size;
    }
    return 0;
}

static int check_replaceable_source(const char *name, int part) {
    FIL file;
    int result = f_open(&file, name, FA_READ);
    if (result != FR_OK) return result;
    unsigned char actual[48], zero[48] = {0};
    UINT read = 0;
    result = f_read(&file, actual, sizeof(actual), &read);
    int closed = f_close(&file);
    if (result != FR_OK) return result;
    if (closed != FR_OK) return closed;
    if (read != sizeof(actual)) return -2;
    if (!memcmp(actual, zero, sizeof(actual))) return 0;
    // The live Setup template has the retained, exact two-part source set.
    static const unsigned char source_guid[16] = {
        0xb2, 0xed, 0xed, 0xe2, 0x47, 0x2a, 0xe5, 0x80,
        0x75, 0xd0, 0x22, 0xcc, 0x39, 0x88, 0xc7, 0xb0
    };
    return valid_part_header(actual, part, 2) && !memcmp(actual + 24, source_guid, sizeof(source_guid)) ? 0 : -2;
}

static int write_or_verify(int input, off_t size, const char *name, BYTE *buffer, int verify) {
    FIL file;
    int result = f_open(&file, name, verify ? FA_READ : FA_CREATE_NEW | FA_WRITE);
    if (result != FR_OK) return result;
    if (verify && f_size(&file) != (FSIZE_t)size) result = -3;
    for (off_t offset = 0; result == FR_OK && offset < size;) {
        UINT amount = size - offset > chunk_bytes ? chunk_bytes : (UINT)(size - offset);
        UINT done = 0;
        if (transfer(input, offset, buffer, amount, 0)) {
            result = -3;
            break;
        }
        if (verify) {
            result = f_read(&file, buffer + chunk_bytes, amount, &done);
            if (result == FR_OK && (done != amount || memcmp(buffer, buffer + chunk_bytes, amount)))
                result = -3;
        } else {
            result = f_write(&file, buffer, amount, &done);
            if (result == FR_OK && done != amount) result = FR_DENIED;
        }
        offset += amount;
    }
    int closed = f_close(&file);
    return result == FR_OK ? closed : result;
}

static int volume_bounds(int fd, jlong offset, jlong length, struct stat *destination) {
    return offset >= 0 && length > 0 && !(offset % sector_bytes) &&
           !(length % sector_bytes) && length / sector_bytes <= UINT32_MAX &&
           !fstat(fd, destination) && S_ISREG(destination->st_mode) &&
           offset <= destination->st_size && length <= destination->st_size - offset;
}

JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsSetupDisk_writeParts(
        JNIEnv *env, jclass clazz, jint output, jlong offset, jlong length, jintArray parts) {
    (void)clazz;
    struct stat destination;
    if (!parts || !volume_bounds(output, offset, length, &destination))
        return -1;
    int count = (*env)->GetArrayLength(env, parts);
    if (count < 2 || count > max_parts) return -1;
    jint fds[max_parts];
    off_t sizes[max_parts];
    (*env)->GetIntArrayRegion(env, parts, 0, count, fds);
    if ((*env)->ExceptionCheck(env) || validate_parts(fds, count, sizes, &destination)) return -1;
    BYTE *buffer = malloc(2 * chunk_bytes);
    if (!buffer) return FR_NOT_ENOUGH_CORE;

    pthread_mutex_lock(&volume_lock);
    disk_fd = output;
    volume_offset = offset;
    volume_sectors = (LBA_t)(length / sector_bytes);
    FATFS fat;
    int result = f_mount(&fat, "0:", 1);
    if (result == FR_OK && fat.fs_type != FS_FAT32) result = FR_NO_FILESYSTEM;
    char name[24];
    // Only the two known source entries may be replaced. New names use CREATE_NEW.
    for (int part = 0; part < 2 && result == FR_OK; ++part) {
        part_name(name, part);
        result = check_replaceable_source(name, part);
    }
    for (int part = 0; part < 2 && result == FR_OK; ++part) {
        part_name(name, part);
        result = f_unlink(name);
    }
    for (int part = 0; part < count && result == FR_OK; ++part) {
        part_name(name, part);
        result = write_or_verify(fds[part], sizes[part], name, buffer, 0);
    }
    for (int part = 0; part < count && result == FR_OK; ++part) {
        part_name(name, part);
        result = write_or_verify(fds[part], sizes[part], name, buffer, 1);
    }
    f_mount(NULL, "0:", 0);
    disk_fd = -1;
    pthread_mutex_unlock(&volume_lock);
    free(buffer);
    return result;
}

static int digest_file(JNIEnv *env, const char *name, jobject digest, jmethodID update,
                       jbyteArray array, BYTE *buffer) {
    FIL file;
    int result = f_open(&file, name, FA_READ);
    if (result != FR_OK) return result;
    if (f_size(&file) == 0 || f_size(&file) > UINT32_C(2147483648)) result = -4;
    while (result == FR_OK && !f_eof(&file)) {
        UINT count = 0;
        result = f_read(&file, buffer, chunk_bytes, &count);
        if (result != FR_OK) break;
        if (!count) {
            result = -4;
            break;
        }
        (*env)->SetByteArrayRegion(env, array, 0, count, (const jbyte *)buffer);
        if (!(*env)->ExceptionCheck(env)) (*env)->CallVoidMethod(env, digest, update, array, 0, count);
        if ((*env)->ExceptionCheck(env)) result = -4;
    }
    int closed = f_close(&file);
    return result == FR_OK ? closed : result;
}

static int audit_split_headers(void) {
    unsigned char guid[16], header[48];
    int count = 1;
    for (int part = 0; part < count; ++part) {
        FIL file;
        char name[24];
        part_name(name, part);
        int result = f_open(&file, name, FA_READ);
        if (result != FR_OK) return result;
        UINT received = 0;
        result = f_read(&file, header, sizeof(header), &received);
        FSIZE_t length = f_size(&file);
        int closed = f_close(&file);
        if (result != FR_OK) return result;
        if (closed != FR_OK) return closed;
        if (received != sizeof(header) || length < 208) return -5;
        if (part == 0) {
            count = header[42];
            if (count < 2 || count > max_parts) return -5;
            memcpy(guid, header + 24, sizeof(guid));
        }
        if (!valid_part_header(header, part, count) || memcmp(guid, header + 24, sizeof(guid))) return -5;
    }
    return FR_OK;
}

JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsSetupDisk_auditFat(
        JNIEnv *env, jclass clazz, jint input, jlong offset, jlong length,
        jobject efi, jobject wim) {
    (void)clazz;
    struct stat source;
    if (!efi || !wim || !volume_bounds(input, offset, length, &source)) return -1;
    jclass digest_class = (*env)->FindClass(env, "java/security/MessageDigest");
    if (!digest_class) return -4;
    jmethodID update = (*env)->GetMethodID(env, digest_class, "update", "([BII)V");
    if (!update) return -4;
    jbyteArray array = (*env)->NewByteArray(env, chunk_bytes);
    if (!array) return -4;
    BYTE *buffer = malloc(chunk_bytes);
    if (!buffer) return FR_NOT_ENOUGH_CORE;
    pthread_mutex_lock(&volume_lock);
    disk_fd = input;
    volume_offset = offset;
    volume_sectors = (LBA_t)(length / sector_bytes);
    FATFS fat;
    int result = f_mount(&fat, "0:", 1);
    if (result == FR_OK && fat.fs_type != FS_FAT32) result = FR_NO_FILESYSTEM;
    if (result == FR_OK) result = audit_split_headers();
    if (result == FR_OK) result = digest_file(env, "/EFI/BOOT/BOOTAA64.EFI", efi, update, array, buffer);
    if (result == FR_OK) result = digest_file(env, "/SOURCES/BOOT.WIM", wim, update, array, buffer);
    f_mount(NULL, "0:", 0);
    disk_fd = -1;
    pthread_mutex_unlock(&volume_lock);
    free(buffer);
    return result;
}

JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsSetupDisk_checkInstallTools(
        JNIEnv *env, jclass clazz, jint fd, jlong offset, jlong length, jobjectArray tools,
        jboolean write) {
    (void)clazz;
    static const char *names[] = {
        "/UAVF/INSTALL.CMD", "/UAVF/BASE.CMD", "/UAVF/BOOTPREP.CMD",
        "/UAVF/FIRSTBOT.CMD", "/UAVF/DISPLAY.CMD", "/UAVF/BROKER.EXE", "/UAVF/OFFLINE.XML"
    };
    enum { count = sizeof(names) / sizeof(names[0]), max_tool_bytes = 71168 };
    struct stat destination;
    jsize tool_count = tools ? (*env)->GetArrayLength(env, tools) : 0;
    if (tool_count < count - 2 || tool_count > count ||
        !volume_bounds(fd, offset, length, &destination)) return -1;
    BYTE *buffer = malloc(2 * max_tool_bytes);
    if (!buffer) return FR_NOT_ENOUGH_CORE;
    pthread_mutex_lock(&volume_lock);
    disk_fd = fd;
    volume_offset = offset;
    volume_sectors = (LBA_t)(length / sector_bytes);
    FATFS fat;
    int result = f_mount(&fat, "0:", 1);
    if (result == FR_OK && fat.fs_type != FS_FAT32) result = FR_NO_FILESYSTEM;
    if (result == FR_OK && write) result = f_mkdir("/UAVF");
    for (int i = 0; i < tool_count && result == FR_OK; ++i) {
        jbyteArray tool = (*env)->GetObjectArrayElement(env, tools, i);
        jsize size = tool ? (*env)->GetArrayLength(env, tool) : 0;
        if (size <= 0 || size > max_tool_bytes) {
            result = -6;
            break;
        }
        (*env)->GetByteArrayRegion(env, tool, 0, size, (jbyte *)buffer);
        (*env)->DeleteLocalRef(env, tool);
        if ((*env)->ExceptionCheck(env)) {
            result = -6;
            break;
        }
        FIL file;
        if (write) {
            result = f_open(&file, names[i], FA_CREATE_NEW | FA_WRITE);
            if (result != FR_OK) break;
            UINT written = 0;
            result = f_write(&file, buffer, (UINT)size, &written);
            int closed = f_close(&file);
            if (result == FR_OK) result = written != (UINT)size ? FR_DISK_ERR : closed;
            if (result != FR_OK) break;
        }
        result = f_open(&file, names[i], FA_READ);
        if (result != FR_OK) break;
        UINT received = 0;
        result = f_size(&file) == (FSIZE_t)size
                 ? f_read(&file, buffer + max_tool_bytes, (UINT)size, &received) : FR_DISK_ERR;
        int closed = f_close(&file);
        if (result == FR_OK) {
            result = received != (UINT)size || memcmp(buffer, buffer + max_tool_bytes, (size_t)size)
                     ? FR_DISK_ERR : closed;
        }
    }
    if (result == FR_OK && write && fsync(fd)) result = FR_DISK_ERR;
    f_mount(NULL, "0:", 0);
    disk_fd = -1;
    pthread_mutex_unlock(&volume_lock);
    free(buffer);
    return result;
}

static int read_boot_file(const char *path, BYTE *buffer, UINT capacity, UINT *received) {
    FIL file;
    int result = f_open(&file, path, FA_READ);
    if (result != FR_OK) return result;
    FSIZE_t size = f_size(&file);
    result = size && size <= capacity ? f_read(&file, buffer, (UINT)size, received) : FR_INVALID_OBJECT;
    int closed = f_close(&file);
    if (result == FR_OK) result = *received == size ? closed : FR_DISK_ERR;
    return result;
}

JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsSetupDisk_setApproval(
        JNIEnv *env, jclass clazz, jint fd, jlong offset, jlong length, jboolean approve) {
    (void)env;
    (void)clazz;
    static const char path[] = "/UAVF/INSTALL.OK";
    static const BYTE expected[] = "UAVF_WINDOWS_INSTALL_APPROVED_V1\r\n";
    struct stat source;
    if (!volume_bounds(fd, offset, length, &source)) return -1;
    pthread_mutex_lock(&volume_lock);
    disk_fd = fd;
    volume_offset = offset;
    volume_sectors = (LBA_t)(length / sector_bytes);
    FATFS fat;
    int result = f_mount(&fat, "0:", 1);
    if (result == FR_OK && fat.fs_type != FS_FAT32) result = FR_NO_FILESYSTEM;
    if (result == FR_OK && approve) {
        FILINFO info;
        if (f_stat("/UAVF/INPROG.TAG", &info) != FR_NO_FILE
                || f_stat("/UAVF/BOOTRDY.TAG", &info) != FR_NO_FILE) result = FR_EXIST;
        FIL file;
        if (result == FR_OK) result = f_open(&file, path, FA_CREATE_NEW | FA_WRITE);
        if (result == FR_OK) {
            UINT written = 0;
            result = f_write(&file, expected, sizeof(expected) - 1, &written);
            int closed = f_close(&file);
            if (result == FR_OK) result = written == sizeof(expected) - 1 ? closed : FR_DISK_ERR;
        }
    }
    if (result == FR_OK) {
        BYTE actual[sizeof(expected)];
        UINT received = 0;
        result = read_boot_file(path, actual, sizeof(actual), &received);
        if (!approve && (result == FR_NO_FILE || result == FR_NO_PATH)) result = FR_OK;
        else if (result == FR_OK) {
            if (received != sizeof(expected) - 1 || memcmp(actual, expected, received)) result = FR_INVALID_OBJECT;
            else if (!approve) result = f_unlink(path);
        }
    }
    if (result == FR_OK && fsync(fd)) result = FR_DISK_ERR;
    f_mount(NULL, "0:", 0);
    disk_fd = -1;
    pthread_mutex_unlock(&volume_lock);
    return result;
}

JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsSetupDisk_readInstalledBcd(
        JNIEnv *env, jclass clazz, jint fd, jlong offset, jlong length, jbyteArray output) {
    (void)clazz;
    static const char marker[] = "UAVF_WINDOWS_BOOT_PREPARED_V1\r\n";
    struct stat source;
    if (!output || !volume_bounds(fd, offset, length, &source)) return -1;
    jsize capacity = (*env)->GetArrayLength(env, output);
    if (capacity < 8192 || capacity > 1048576) return -1;
    BYTE *buffer = malloc((size_t)capacity);
    if (!buffer) return -FR_NOT_ENOUGH_CORE;
    pthread_mutex_lock(&volume_lock);
    disk_fd = fd;
    volume_offset = offset;
    volume_sectors = (LBA_t)(length / sector_bytes);
    FATFS fat;
    int result = f_mount(&fat, "0:", 1);
    if (result == FR_OK && fat.fs_type != FS_FAT32) result = FR_NO_FILESYSTEM;
    UINT received = 0;
    if (result == FR_OK) {
        result = read_boot_file("/UAVF/BOOTRDY.TAG", buffer, sizeof(marker) - 1, &received);
        if (result == FR_OK && (received != sizeof(marker) - 1 || memcmp(buffer, marker, received))) {
            result = FR_INVALID_OBJECT;
        }
    }
    if (result == FR_OK) result = read_boot_file("/EFI/MICROS~1/BOOT/BCD", buffer, (UINT)capacity, &received);
    f_mount(NULL, "0:", 0);
    disk_fd = -1;
    pthread_mutex_unlock(&volume_lock);
    if (result == FR_OK) (*env)->SetByteArrayRegion(env, output, 0, received, (const jbyte *)buffer);
    free(buffer);
    return result == FR_OK ? (jint)received : -result;
}

JNIEXPORT jint JNICALL Java_com_example_winavf_WindowsSetupDisk_renameNew(
        JNIEnv *env, jclass clazz, jstring from, jstring to) {
    (void)clazz;
    const char *source = (*env)->GetStringUTFChars(env, from, NULL);
    if (!source) return ENOMEM;
    const char *destination = (*env)->GetStringUTFChars(env, to, NULL);
    int error = ENOMEM;
    if (destination) {
        long result = syscall(SYS_renameat2, AT_FDCWD, source, AT_FDCWD, destination, RENAME_NOREPLACE);
        error = result == 0 ? 0 : errno;
        (*env)->ReleaseStringUTFChars(env, to, destination);
    }
    (*env)->ReleaseStringUTFChars(env, from, source);
    return error;
}
