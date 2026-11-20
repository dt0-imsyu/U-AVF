/* Optional child-process recovery only. No clipboard calls or display state. */
static int clipboard_retry_loop(int (*serve)(void),bool (*allowed)(void),void (*pause)(DWORD)) {
    int result=2;
    while(allowed()) {
        result=serve();
        if(!allowed())break;
        // All sockets/provider references from serve() must be closed first.
        // Avoid spinning if the signed provider is temporarily unavailable.
        pause(1000);
    }
    return result;
}
