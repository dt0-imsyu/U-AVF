/* Frame deliveries advance the cap, never acquisition/poll attempts. */
typedef struct {int64_t last_sent;uint64_t frames;} WindowsFramePacer;
static int64_t frame_pacer_deadline(const WindowsFramePacer *p,int64_t now,int64_t frequency,unsigned fps) {
    if(!p->frames||now<p->last_sent)return now;
    return p->last_sent+(frequency+fps-1)/fps;
}
static void frame_pacer_sent(WindowsFramePacer *p,int64_t now) {
    p->last_sent=now;p->frames++;
}
