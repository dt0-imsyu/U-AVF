// SPDX-License-Identifier: LicenseRef-UAVF-1.0
#ifndef UAVF_WINDOWS_SETUP_SOURCE_H
#define UAVF_WINDOWS_SETUP_SOURCE_H

#include <stdint.h>

/* The destination must be a new staging image with a preallocated logical range. */
int windows_setup_create_source(int iso, int destination, int64_t offset, int64_t length);

#endif
