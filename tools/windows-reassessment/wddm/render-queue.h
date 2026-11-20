/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#ifndef UAVF_WDDM_RENDER_QUEUE_H
#define UAVF_WDDM_RENDER_QUEUE_H

#include <stdbool.h>
#include <stdint.h>

/* Owned by one PASSIVE_LEVEL worker per scheduler queue. The Dxgk callbacks
 * must enqueue requests to that worker before calling this code. */
#define MAVK_MAX_PENDING 32
struct mavk_submission {
    uint64_t serial;
    uint32_t dxgk_fence;
    void *allocation_refs;
    bool gpu_done;
};

struct mavk_render_queue {
    uint64_t context_id;
    uint64_t last_serial;
    unsigned head, count;
    bool disconnected;
    struct mavk_submission pending[MAVK_MAX_PENDING];
};

/* Allocate transport serials independently from potentially wrapping WDDM IDs.
 * Success transfers ownership of the allocation references to the queue. */
static inline uint64_t mavk_enqueue(struct mavk_render_queue *queue,
                                    uint32_t fence, void *allocation_refs)
{
    if (!queue->context_id || queue->disconnected || queue->count == MAVK_MAX_PENDING ||
        queue->last_serial == UINT64_MAX)
        return 0;
    uint64_t serial = ++queue->last_serial;
    unsigned slot = (queue->head + queue->count++) % MAVK_MAX_PENDING;
    queue->pending[slot] = (struct mavk_submission){serial, fence, allocation_refs, false};
    return serial;
}

/* Call only after the host has observed completion of the GPU fence for this
 * submission. A successful transport reply has no corresponding transition. */
static inline bool mavk_gpu_completed(struct mavk_render_queue *queue,
                                      uint64_t context_id, uint64_t serial)
{
    if (!context_id || context_id != queue->context_id || queue->disconnected)
        return false;
    for (unsigned offset = 0; offset < queue->count; offset++) {
        struct mavk_submission *submission =
            &queue->pending[(queue->head + offset) % MAVK_MAX_PENDING];
        if (submission->serial != serial) continue;
        if (submission->gpu_done) return false;
        submission->gpu_done = true;
        return true;
    }
    return false;
}

/* The caller notifies Dxgk and releases the returned references. On connection
 * loss it must use the failure/reset path for work without GPU confirmation.
 * This retirement applies to guest references. The host owns native resources
 * and must independently retain them through GPU completion or device loss. */
static inline bool mavk_retire(struct mavk_render_queue *queue,
                               struct mavk_submission *result)
{
    if (!queue->count) return false;
    struct mavk_submission *submission = &queue->pending[queue->head];
    if (!submission->gpu_done && !queue->disconnected) return false;
    *result = *submission;
    *submission = (struct mavk_submission){0};
    queue->head = (queue->head + 1) % MAVK_MAX_PENDING;
    queue->count--;
    return true;
}

#endif
