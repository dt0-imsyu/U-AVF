#pragma once
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
/* Encoder-owned buffer; valid only until next read/close. No borrowed OS pointer. */
void *uavf_idd_open(unsigned timeout_ms,unsigned long *error);
int uavf_idd_next(void *source,const unsigned char **bgra,uint64_t *sequence,unsigned timeout_ms);
void uavf_idd_close(void *source);
#ifdef __cplusplus
}
#endif
