/* Separate non-LTO translation unit: the caller cannot optimize away the
 * full-frame copy merely because the source-only benchmark later frees it. */
#include <string.h>
#include "idd-system-frame-policy.h"
extern "C" void uavf_idd_proof_copy(unsigned char *destination,
                                    const UavfIddFrame *frame) {
    for(unsigned y=0; y<frame->height; ++y)
        memcpy(destination+y*7680u,frame->pixels+y*frame->pitch,7680);
}
