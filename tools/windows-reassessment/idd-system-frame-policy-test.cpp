#include "idd-system-frame-policy.h"
int main() {
    alignas(16) unsigned char pixel[32] = {};
    UavfIddFrame f = { pixel, 1920, 1200, 7680, 0, 0 };
    if (!uavf_idd_valid(f,87) || !uavf_idd_valid(f,88)) return 1;
    if (uavf_idd_valid(f,28)) return 2;
    f.pixels=nullptr; if(uavf_idd_valid(f,87))return 3;
    f.pixels=pixel+1; if(uavf_idd_valid(f,87))return 4;
    f.pixels=pixel; f.width=1280; if(uavf_idd_valid(f,87))return 5;
    f.width=1920; f.height=800; if(uavf_idd_valid(f,87))return 6;
    f.height=1200; f.pitch=7679; if(uavf_idd_valid(f,87))return 7;
    f.pitch=16385; if(uavf_idd_valid(f,87))return 8;
    f.pitch=8192; if(!uavf_idd_valid(f,87))return 9;
    return 0;
}
