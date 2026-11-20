/* Bounded public-API feasibility only; never switches the capture backend.
 * A protected Winlogon desktop can reject duplication even when Default works. */
#define COBJMACROS
#include <dxgi1_2.h>
#include <d3d11.h>
static void dxgi_probe(HANDLE report) {
    IDXGIFactory1 *factory=NULL;
    HRESULT result=CreateDXGIFactory1(&IID_IDXGIFactory1,(void**)&factory);
    char line[512];int n=snprintf(line,sizeof(line),"DXGI_FACTORY hr=%08lx\r\n",(unsigned long)result);
    write_all(report,line,(DWORD)n);
    if(FAILED(result))return;
    for(UINT i=0;i<2;i++) {
        IDXGIAdapter1 *adapter=NULL;
        if(FAILED(IDXGIFactory1_EnumAdapters1(factory,i,&adapter)))break;
        DXGI_ADAPTER_DESC1 info={0};IDXGIAdapter1_GetDesc1(adapter,&info);
        n=snprintf(line,sizeof(line),"DXGI_ADAPTER index=%u description=%ls vendor=%04x device=%04x flags=%u\r\n",
            i,info.Description,info.VendorId,info.DeviceId,info.Flags);
        if(n>0&&n<(int)sizeof(line))write_all(report,line,(DWORD)n);
        ID3D11Device *device=NULL;ID3D11DeviceContext *context=NULL;D3D_FEATURE_LEVEL feature=0;
        result=D3D11CreateDevice((IDXGIAdapter*)adapter,D3D_DRIVER_TYPE_UNKNOWN,NULL,
            D3D11_CREATE_DEVICE_BGRA_SUPPORT,NULL,0,D3D11_SDK_VERSION,&device,&feature,&context);
        n=snprintf(line,sizeof(line),"DXGI_DEVICE adapter=%u hr=%08lx feature=%x\r\n",i,(unsigned long)result,(unsigned)feature);
        write_all(report,line,(DWORD)n);
        if(SUCCEEDED(result))for(UINT j=0;j<4;j++) {
            IDXGIOutput *output=NULL;IDXGIOutput1 *output1=NULL;IDXGIOutputDuplication *dup=NULL;
            if(FAILED(IDXGIAdapter1_EnumOutputs(adapter,j,&output)))break;
            DXGI_OUTPUT_DESC desc={0};IDXGIOutput_GetDesc(output,&desc);
            n=snprintf(line,sizeof(line),"DXGI_OUTPUT adapter=%u output=%u attached=%d width=%ld height=%ld\r\n",
                i,j,(int)desc.AttachedToDesktop,desc.DesktopCoordinates.right-desc.DesktopCoordinates.left,
                desc.DesktopCoordinates.bottom-desc.DesktopCoordinates.top);
            write_all(report,line,(DWORD)n);
            result=IDXGIOutput_QueryInterface(output,&IID_IDXGIOutput1,(void**)&output1);
            if(SUCCEEDED(result))result=IDXGIOutput1_DuplicateOutput(output1,(IUnknown*)device,&dup);
            n=snprintf(line,sizeof(line),"DXGI_DUPLICATE adapter=%u output=%u hr=%08lx\r\n",i,j,(unsigned long)result);
            write_all(report,line,(DWORD)n);
            if(SUCCEEDED(result)) {
                DXGI_OUTDUPL_DESC d={0};IDXGIOutputDuplication_GetDesc(dup,&d);
                DXGI_OUTDUPL_FRAME_INFO frame={0};IDXGIResource *resource=NULL;
                result=IDXGIOutputDuplication_AcquireNextFrame(dup,1,&frame,&resource);
                n=snprintf(line,sizeof(line),"DXGI_ACQUIRE hr=%08lx mode=%ux%u system_memory=%d frames=%u\r\n",
                    (unsigned long)result,d.ModeDesc.Width,d.ModeDesc.Height,(int)d.DesktopImageInSystemMemory,frame.AccumulatedFrames);
                write_all(report,line,(DWORD)n);
                if(SUCCEEDED(result)) {
                    if(resource)IDXGIResource_Release(resource);
                    IDXGIOutputDuplication_ReleaseFrame(dup);
                }
                IDXGIOutputDuplication_Release(dup);
            }
            if(output1)IDXGIOutput1_Release(output1);
            IDXGIOutput_Release(output);
        }
        if(context)ID3D11DeviceContext_Release(context);
        if(device)ID3D11Device_Release(device);
        IDXGIAdapter1_Release(adapter);
    }
    IDXGIFactory1_Release(factory);
    FlushFileBuffers(report);
}
