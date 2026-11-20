/* Three owned I420 slots, at most one READY. Never overwrite ENCODING.
 * Latest complete frame wins; no unbounded frame FIFO or extra pixel copy. */
enum {FQ_FREE,FQ_WRITING,FQ_READY,FQ_ENCODING};
typedef struct {unsigned char *pixels;int state;uint64_t sequence,pts;
    double capture_ms,convert_ms;uint32_t sampled_color;} FrameSlot;
typedef struct {CRITICAL_SECTION lock;HANDLE ready;FrameSlot slots[3];
    uint64_t published,dropped,consumed;} FrameQueue;
static bool fq_init(FrameQueue *q,size_t bytes) {
    memset(q,0,sizeof(*q));InitializeCriticalSection(&q->lock);
    q->ready=CreateEventW(NULL,FALSE,FALSE,NULL);
    for(int i=0;i<3;i++)q->slots[i].pixels=malloc(bytes);
    return q->ready&&q->slots[0].pixels&&q->slots[1].pixels&&q->slots[2].pixels;
}
static void fq_destroy(FrameQueue *q) {
    for(int i=0;i<3;i++)free(q->slots[i].pixels);
    if(q->ready)CloseHandle(q->ready);DeleteCriticalSection(&q->lock);
}
static int fq_reserve(FrameQueue *q) {
    int result=-1;EnterCriticalSection(&q->lock);
    for(int i=0;i<3;i++)if(q->slots[i].state==FQ_FREE){q->slots[i].state=FQ_WRITING;result=i;break;}
    LeaveCriticalSection(&q->lock);return result;
}
static void fq_cancel(FrameQueue *q,int index) {
    EnterCriticalSection(&q->lock);q->slots[index].state=FQ_FREE;LeaveCriticalSection(&q->lock);
}
static void fq_publish(FrameQueue *q,int index,uint64_t pts,double capture,double convert) {
    EnterCriticalSection(&q->lock);
    for(int i=0;i<3;i++)if(q->slots[i].state==FQ_READY){q->slots[i].state=FQ_FREE;q->dropped++;}
    FrameSlot *slot=&q->slots[index];slot->sequence=++q->published;slot->pts=pts;
    slot->capture_ms=capture;slot->convert_ms=convert;slot->state=FQ_READY;
    LeaveCriticalSection(&q->lock);SetEvent(q->ready);
}
static int fq_borrow(FrameQueue *q,FrameSlot *out) {
    int result=-1;EnterCriticalSection(&q->lock);
    for(int i=0;i<3;i++)if(q->slots[i].state==FQ_READY){q->slots[i].state=FQ_ENCODING;*out=q->slots[i];q->consumed++;result=i;break;}
    LeaveCriticalSection(&q->lock);return result;
}
static void fq_return(FrameQueue *q,int index) {
    EnterCriticalSection(&q->lock);q->slots[index].state=FQ_FREE;LeaveCriticalSection(&q->lock);
}
