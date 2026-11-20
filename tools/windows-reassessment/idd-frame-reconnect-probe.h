/* Finite SYSTEM IPC test. Does not authenticate, send input, change desktops
 * or claim that the test drawable is a user Desktop/performance workload. */
static uint32_t idd_probe_crc32(const unsigned char *pixels,size_t bytes) {
    uint32_t table[256];
    for(unsigned i=0;i<256;i++){uint32_t c=i;for(unsigned k=0;k<8;k++)c=(c>>1)^((c&1)?0xedb88320u:0);table[i]=c;}
    uint32_t crc=0xffffffffu;
    for(size_t i=0;i<bytes;i++)crc=(crc>>8)^table[(crc^pixels[i])&255];
    return ~crc;
}
static int idd_frame_receiver_probe() {
    HANDLE token=nullptr;unsigned char user[256];DWORD bytes=0;
    if(!OpenProcessToken(GetCurrentProcess(),TOKEN_QUERY,&token))return 20;
    bool system=GetTokenInformation(token,TokenUser,user,sizeof(user),&bytes)&&
        IsWellKnownSid(reinterpret_cast<TOKEN_USER *>(user)->User.Sid,WinLocalSystemSid);
    CloseHandle(token);if(!system)return 21;
    auto *pixels=static_cast<unsigned char *>(HeapAlloc(GetProcessHeap(),0,UAVF_IDD_FRAME_BYTES));
    if(!pixels)return 23;
    DWORD server=0;uint64_t previous_sequence=0;int result=0;
    for(unsigned connection=0;connection<3&&!result;connection++) {
        UavfIddFrameReader reader;ULONGLONG deadline=GetTickCount64()+(connection?5000:50000);
        bool connected=false;
        do {
            DWORD pid=idd_probe_admitted_host();
            if(pid && (!server||pid==server) && reader.open(L"\\\\.\\pipe\\UAVF-IddSourceProbe-3EF669B7",pid,L"C:\\Windows\\System32\\WUDFHost.exe")) {
                server=pid;connected=true;break;
            }
            Sleep(100);
        }while(GetTickCount64()<deadline);
        if(!connected){printf("RECONNECT_ADMISSION_FAILED connection=%u expected_same_pid=%lu\n",connection,server);result=24;break;}
        for(unsigned sample=0;sample<4;sample++) {
            UavfIddWireHeader header={};
            if(!reader.read(&header,pixels,UAVF_IDD_FRAME_BYTES,5000)){result=25;break;}
            if(header.sequence<=previous_sequence){result=26;break;}
            previous_sequence=header.sequence;
            printf("IPC_FRAME connection=%u sample=%u pid=%lu sequence=%llu bytes=%u crc32=%08lx display_qpc=%llu\n",
                connection,sample,server,(unsigned long long)header.sequence,header.pixel_bytes,
                (unsigned long)idd_probe_crc32(pixels,UAVF_IDD_FRAME_BYTES),(unsigned long long)header.display_qpc);
            fflush(stdout);
        }
        reader.close(); // The SAME UMDF worker must accept the next encoder.
        if(!result)printf("IPC_CONNECTION_COMPLETE connection=%u same_pid=%lu\n",connection,server);
    }
    HeapFree(GetProcessHeap(),0,pixels);
    printf("GUEST_PIPE_RECONNECT_RESULT=%d CONNECTIONS=3 SAMPLES_PER_CONNECTION=4; NOT_USER_DESKTOP_OR_FPS_PASS\n",result);
    return result;
}
