/* Rendering stimulus only: no desktop capture or encoder. Software WARP.
   Restriction to the admitted secondary monitor lives in the shared caller. */
#define COBJMACROS
#include <d3d11.h>
#include <dxgi1_2.h>
/* Exact public SDK IIDs, kept local to avoid linking the monolithic GUID lib. */
static const GUID uavf_factory2={0x50c83a1c,0xe072,0x4c48,{0x87,0xb0,0x36,0x30,0xfa,0x36,0xa6,0xd0}};
static const GUID uavf_texture2d={0x6f15aaf2,0xd208,0x4e89,{0x9a,0xb4,0x48,0x95,0x35,0xd3,0x4f,0x9c}};
static HRESULT idd_flip_render(HWND window){
    ID3D11Device *device=0;ID3D11DeviceContext *context=0;
    IDXGIFactory2 *factory=0;IDXGISwapChain1 *swap=0;
    ID3D11Texture2D *buffer=0;ID3D11RenderTargetView *target=0;
    HRESULT hr=D3D11CreateDevice(0,D3D_DRIVER_TYPE_WARP,0,D3D11_CREATE_DEVICE_BGRA_SUPPORT,
        0,0,D3D11_SDK_VERSION,&device,0,&context);
    if(FAILED(hr))goto done;
    hr=CreateDXGIFactory1(&uavf_factory2,(void**)&factory);if(FAILED(hr))goto done;
    DXGI_SWAP_CHAIN_DESC1 desc={0};desc.Width=1920;desc.Height=1200;
    desc.Format=DXGI_FORMAT_B8G8R8A8_UNORM;desc.SampleDesc.Count=1;
    desc.BufferUsage=DXGI_USAGE_RENDER_TARGET_OUTPUT;desc.BufferCount=2;
    desc.Scaling=DXGI_SCALING_STRETCH;desc.SwapEffect=DXGI_SWAP_EFFECT_FLIP_DISCARD;
    desc.AlphaMode=DXGI_ALPHA_MODE_IGNORE;
    hr=IDXGIFactory2_CreateSwapChainForHwnd(factory,(IUnknown*)device,window,&desc,0,0,&swap);
    if(FAILED(hr))goto done;
    hr=IDXGISwapChain1_GetBuffer(swap,0,&uavf_texture2d,(void**)&buffer);if(FAILED(hr))goto done;
    hr=ID3D11Device_CreateRenderTargetView(device,(ID3D11Resource*)buffer,0,&target);
    if(FAILED(hr))goto done;
    started=GetTickCount64();
    while(GetTickCount64()-started<30000){
        MSG message;
        while(PeekMessageW(&message,0,0,0,PM_REMOVE)){
            if(message.message==WM_QUIT)goto done;
            TranslateMessage(&message);DispatchMessageW(&message);
        }
        FLOAT colour[4]={(paints%256)/255.0f,0.5f,1.0f-(paints%256)/255.0f,1.0f};
        ID3D11DeviceContext_ClearRenderTargetView(context,target,colour);
        LARGE_INTEGER a,b;QueryPerformanceCounter(&a);
        hr=IDXGISwapChain1_Present(swap,1,0);QueryPerformanceCounter(&b);
        flush_ticks+=b.QuadPart-a.QuadPart;
        if(hr!=S_OK)goto done; /* No occluded Presents counted as rendered. */
        ++paints;
    }
done:
    if(target)ID3D11RenderTargetView_Release(target);
    if(buffer)ID3D11Texture2D_Release(buffer);
    if(swap)IDXGISwapChain1_Release(swap);
    if(factory)IDXGIFactory2_Release(factory);
    if(context)ID3D11DeviceContext_Release(context);
    if(device)ID3D11Device_Release(device);
    DestroyWindow(window);
    return hr;
}
