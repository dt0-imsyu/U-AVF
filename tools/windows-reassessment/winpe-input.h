/* Guest-specific public SendInput adapter for the shared 16-byte UIN1 protocol.
 * No input hook, driver, or desktop security override. */
typedef struct {char magic[4];uint8_t kind,value;uint16_t reserved;int32_t x,y;} UinPacket;
_Static_assert(sizeof(UinPacket)==16,"UIN1 ABI");
typedef struct {SOCKET client;volatile LONG stopping;unsigned accepted,rejected;} UinState;
static WORD virtual_key(uint32_t sym) {
    if(sym>='a' && sym<='z')return (WORD)(sym-'a'+'A');
    if(sym>='A' && sym<='Z')return (WORD)sym;
    if(sym>='0' && sym<='9')return (WORD)sym;
    if(sym>=0xffbe && sym<=0xffc9)return (WORD)(VK_F1+sym-0xffbe);
    if(sym>=0xffb0 && sym<=0xffb9)return (WORD)(VK_NUMPAD0+sym-0xffb0);
    switch(sym) {
        case 0xff08:return VK_BACK;case 0xff09:return VK_TAB;case 0xff0d:case 0xff8d:return VK_RETURN;
        case 0xff1b:return VK_ESCAPE;case 0xffff:return VK_DELETE;
        case 0xff50:return VK_HOME;case 0xff57:return VK_END;case 0xff63:return VK_INSERT;
        case 0xff51:return VK_LEFT;case 0xff52:return VK_UP;case 0xff53:return VK_RIGHT;case 0xff54:return VK_DOWN;
        case 0xff55:return VK_PRIOR;case 0xff56:return VK_NEXT;
        case 0xffe1:return VK_LSHIFT;case 0xffe2:return VK_RSHIFT;
        case 0xffe3:return VK_LCONTROL;case 0xffe4:return VK_RCONTROL;
        case 0xffe9:return VK_LMENU;case 0xffea:return VK_RMENU;
        case 0xffeb:case 0xffe7:return VK_LWIN;case 0xffec:case 0xffe8:return VK_RWIN;
        case 0xffe5:return VK_CAPITAL;case 0xff7f:return VK_NUMLOCK;case 0xff14:return VK_SCROLL;
        case 0xff61:return VK_SNAPSHOT;case 0xff13:return VK_PAUSE;
        case 0xffaf:return VK_DIVIDE;case 0xffaa:return VK_MULTIPLY;case 0xffad:return VK_SUBTRACT;
        case 0xffab:return VK_ADD;case 0xffae:return VK_DECIMAL;
        case 0x1008ff26:return VK_BROWSER_BACK;
        default:return 0;
    }
}
static int inject_packet(const UinPacket *p) {
    if(memcmp(p->magic,"UIN1",4)||p->reserved)return 0;
    INPUT input={0};
    if(p->kind==1) {
        int w=GetSystemMetrics(SM_CXSCREEN),h=GetSystemMetrics(SM_CYSCREEN);
        if(w<2||h<2||p->x<0||p->y<0||p->x>=w||p->y>=h)return 0;
        input.type=INPUT_MOUSE;input.mi.dx=(LONG)((int64_t)p->x*65535/(w-1));
        input.mi.dy=(LONG)((int64_t)p->y*65535/(h-1));input.mi.dwFlags=MOUSEEVENTF_MOVE|MOUSEEVENTF_ABSOLUTE;
    }else if(p->kind==2 && p->value<=1 && p->x>=0 && p->x<=3) {
        input.type=INPUT_MOUSE;
        int button=p->x?p->x:1;
        input.mi.dwFlags=button==1?(p->value?MOUSEEVENTF_LEFTDOWN:MOUSEEVENTF_LEFTUP):
            button==2?(p->value?MOUSEEVENTF_MIDDLEDOWN:MOUSEEVENTF_MIDDLEUP):(p->value?MOUSEEVENTF_RIGHTDOWN:MOUSEEVENTF_RIGHTUP);
    }else if(p->kind==3 && p->value<=1) {
        input.type=INPUT_KEYBOARD;WORD vk=virtual_key((uint32_t)p->x);
        if(vk) {
            input.ki.wVk=vk;input.ki.wScan=(WORD)MapVirtualKeyW(vk,MAPVK_VK_TO_VSC);
            input.ki.dwFlags=p->value?0:KEYEVENTF_KEYUP;
            if(vk==VK_RCONTROL||vk==VK_RMENU||(vk>=VK_PRIOR&&vk<=VK_DELETE)||vk==VK_LWIN||vk==VK_RWIN||vk==VK_DIVIDE)input.ki.dwFlags|=KEYEVENTF_EXTENDEDKEY;
        }else {
            uint32_t c=(uint32_t)p->x;if(c&0x01000000)c&=0x00ffffff;
            if(!c||c>0xffff)return 0;input.ki.wScan=(WORD)c;
            input.ki.dwFlags=KEYEVENTF_UNICODE|(p->value?0:KEYEVENTF_KEYUP);
        }
    }else if(p->kind==4 && p->x>=-32&&p->x<=32&&p->y>=-32&&p->y<=32) {
        if(p->x && p->y)return 0;
        input.type=INPUT_MOUSE;input.mi.dwFlags=p->y?MOUSEEVENTF_WHEEL:MOUSEEVENTF_HWHEEL;
        input.mi.mouseData=(DWORD)((p->y?p->y:p->x)*WHEEL_DELTA);
    }else return 0;
    return SendInput(1,&input,sizeof(input))==1;
}
static DWORD WINAPI input_worker(void *arg) {
    UinState *s=arg;
    while(!InterlockedCompareExchange(&s->stopping,0,0)) {
        UinPacket p;unsigned got=0;
        while(got<sizeof(p) && !InterlockedCompareExchange(&s->stopping,0,0)) {
            int n=recv(s->client,((char*)&p)+got,(int)(sizeof(p)-got),0);
            if(n>0)got+=(unsigned)n;
            else if(n==SOCKET_ERROR&&WSAGetLastError()==WSAETIMEDOUT)continue;
            else return 0;
        }
        if(got!=sizeof(p))break;
        if(inject_packet(&p))s->accepted++;else s->rejected++;
    }
    return 0;
}
