// SPDX-License-Identifier: LicenseRef-UAVF-1.0
#include <wimlib.h>

int uavf_wimlib_overlay_boot(const char *image, const char *overlay) {
    WIMStruct *wim = NULL;
    int status = wimlib_open_wim(image, WIMLIB_OPEN_FLAG_WRITE_ACCESS, &wim);
    if (status)
        return status;

    char root[] = WIMLIB_WIM_ROOT_PATH;
    const struct wimlib_update_command add = {
        .op = WIMLIB_UPDATE_OP_ADD,
        .add = {
            .fs_source_path = (char *)overlay,
            .wim_target_path = root,
            .add_flags = WIMLIB_ADD_FLAG_NO_ACLS,
        },
    };
    status = wimlib_update_image(wim, 2, &add, 1, 0);
    if (!status)
        status = wimlib_overwrite(wim, 0, 0);
    wimlib_free(wim);
    return status;
}
