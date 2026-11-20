/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#include "render-queue.h"
#include <assert.h>
#include <stdio.h>

int main(void)
{
    struct mavk_render_queue first = {.context_id = 1}, second = {.context_id = 2};
    struct mavk_submission result;
    int first_refs, second_refs;
    uint64_t old = mavk_enqueue(&first, UINT32_MAX, &first_refs);
    uint64_t next = mavk_enqueue(&first, 0, &second_refs);
    assert(old && next > old && mavk_enqueue(&second, 1, NULL) == old);
    assert(!mavk_retire(&first, &result));
    assert(mavk_gpu_completed(&first, 1, next));
    assert(!mavk_gpu_completed(&first, 1, next));
    assert(!mavk_gpu_completed(&second, 2, next));
    assert(!mavk_gpu_completed(&second, 1, old));
    assert(!mavk_retire(&first, &result) && !mavk_retire(&second, &result));
    assert(mavk_gpu_completed(&first, 1, old));
    assert(mavk_retire(&first, &result) && result.gpu_done &&
           result.dxgk_fence == UINT32_MAX && result.allocation_refs == &first_refs);
    assert(mavk_retire(&first, &result) && result.gpu_done &&
           result.dxgk_fence == 0 && result.allocation_refs == &second_refs);
    assert(!mavk_gpu_completed(&first, 1, old));

    for (unsigned i = 0; i < MAVK_MAX_PENDING; i++)
        assert(mavk_enqueue(&first, i, &first_refs));
    assert(!mavk_enqueue(&first, 32, NULL));
    first.disconnected = true;
    assert(!mavk_enqueue(&first, 32, NULL));
    assert(!mavk_gpu_completed(&first, 1, next + 1));
    for (unsigned i = 0; i < MAVK_MAX_PENDING; i++)
        assert(mavk_retire(&first, &result) && !result.gpu_done &&
               result.dxgk_fence == i && result.allocation_refs == &first_refs);
    assert(!mavk_retire(&first, &result));
    assert(mavk_gpu_completed(&second, 2, old));
    assert(mavk_retire(&second, &result) && result.gpu_done && result.dxgk_fence == 1);
    struct mavk_render_queue reopened = {.context_id = 3};
    assert(mavk_enqueue(&reopened, 1, NULL) == old);
    assert(!mavk_gpu_completed(&reopened, 2, old));
    assert(mavk_gpu_completed(&reopened, 3, old));
    puts("WDDM_RENDER_QUEUE_PASS ordering capacity disconnect isolation");
}
