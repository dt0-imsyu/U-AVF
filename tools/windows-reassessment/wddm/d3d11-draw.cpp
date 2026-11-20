/* SPDX-License-Identifier: LicenseRef-UAVF-1.0 */
#define WIN32_LEAN_AND_MEAN
#include <d3d11.h>
#include <d3dcompiler.h>
#include <dxgi1_2.h>
#include <wrl/client.h>
#include <cstdio>
#include <cwchar>

using Microsoft::WRL::ComPtr;

static int fail(const char *stage, HRESULT hr)
{
    std::fprintf(stderr, "%s hr=0x%08lx\n", stage, static_cast<unsigned long>(hr));
    return 1;
}

static HRESULT compile(const char *code, size_t size, const char *target, ID3DBlob **out)
{
    ComPtr<ID3DBlob> errors;
    HRESULT hr = D3DCompile(code, size, nullptr, nullptr, nullptr, "main", target, 0, 0, out, &errors);
    if (FAILED(hr) && errors)
        std::fwrite(errors->GetBufferPointer(), 1, errors->GetBufferSize(), stderr);
    return hr;
}

int wmain(int argc, wchar_t **argv)
{
    if (argc > 2 || (argc == 2 && !*argv[1])) return fail("USAGE: d3d11-draw.exe [exact-adapter-name]", E_INVALIDARG);
    ComPtr<IDXGIFactory1> factory;
    HRESULT hr = CreateDXGIFactory1(IID_PPV_ARGS(&factory));
    if (FAILED(hr)) return fail("CreateDXGIFactory1", hr);
    ComPtr<IDXGIAdapter1> selected;
    for (UINT i = 0;; ++i) {
        ComPtr<IDXGIAdapter1> adapter;
        hr = factory->EnumAdapters1(i, &adapter);
        if (hr == DXGI_ERROR_NOT_FOUND) break;
        if (FAILED(hr)) return fail("EnumAdapters1", hr);
        DXGI_ADAPTER_DESC1 desc {};
        if (FAILED(hr = adapter->GetDesc1(&desc))) return fail("GetDesc1", hr);
        std::wprintf(L"DXGI_ADAPTER name=%ls vendor=%04x device=%04x luid=%08lx:%08lx flags=%x\n",
                     desc.Description, desc.VendorId, desc.DeviceId, static_cast<unsigned long>(desc.AdapterLuid.HighPart),
                     static_cast<unsigned long>(desc.AdapterLuid.LowPart), desc.Flags);
        if (argc != 2 || std::wcscmp(desc.Description, argv[1]) != 0) continue;
        if (selected || (desc.Flags & (DXGI_ADAPTER_FLAG_SOFTWARE | DXGI_ADAPTER_FLAG_REMOTE)))
            return fail("DXGI_ADAPTER_AMBIGUOUS_OR_SOFTWARE", E_FAIL);
        selected = adapter;
    }
    if (argc == 1) return 0;
    if (!selected) return fail("DXGI_ADAPTER_MISSING", DXGI_ERROR_NOT_FOUND);

    const D3D_FEATURE_LEVEL levelRequested = D3D_FEATURE_LEVEL_11_0;
    D3D_FEATURE_LEVEL level {};
    ComPtr<ID3D11Device> device;
    ComPtr<ID3D11DeviceContext> context;
    hr = D3D11CreateDevice(selected.Get(), D3D_DRIVER_TYPE_UNKNOWN, nullptr, 0, &levelRequested, 1,
                           D3D11_SDK_VERSION, &device, &level, &context);
    if (FAILED(hr)) return fail("D3D11CreateDevice", hr);
    if (level != levelRequested) return fail("D3D_FEATURE_LEVEL", E_FAIL);

    const char vertex[] = R"(
float4 main(float2 xy : POSITION) : SV_Position { return float4(xy, 0, 1); }
)";
    const char pixel[] = R"(
float4 main() : SV_Target { return float4(1, 0.25, 0, 1); }
)";
    ComPtr<ID3DBlob> vsCode, psCode;
    if (FAILED(hr = compile(vertex, sizeof(vertex) - 1, "vs_5_0", &vsCode))) return fail("CompileVS", hr);
    if (FAILED(hr = compile(pixel, sizeof(pixel) - 1, "ps_5_0", &psCode))) return fail("CompilePS", hr);
    ComPtr<ID3D11VertexShader> vs;
    ComPtr<ID3D11PixelShader> ps;
    if (FAILED(hr = device->CreateVertexShader(vsCode->GetBufferPointer(), vsCode->GetBufferSize(), nullptr, &vs)))
        return fail("CreateVertexShader", hr);
    if (FAILED(hr = device->CreatePixelShader(psCode->GetBufferPointer(), psCode->GetBufferSize(), nullptr, &ps)))
        return fail("CreatePixelShader", hr);
    const D3D11_INPUT_ELEMENT_DESC element {"POSITION", 0, DXGI_FORMAT_R32G32_FLOAT, 0, 0, D3D11_INPUT_PER_VERTEX_DATA, 0};
    ComPtr<ID3D11InputLayout> layout;
    if (FAILED(hr = device->CreateInputLayout(&element, 1, vsCode->GetBufferPointer(), vsCode->GetBufferSize(), &layout)))
        return fail("CreateInputLayout", hr);
    const float vertices[] = {-.75f, -.75f, .75f, -.75f, 0, .75f};
    D3D11_BUFFER_DESC vbDesc {};
    vbDesc.ByteWidth = sizeof(vertices);
    vbDesc.Usage = D3D11_USAGE_IMMUTABLE;
    vbDesc.BindFlags = D3D11_BIND_VERTEX_BUFFER;
    D3D11_SUBRESOURCE_DATA vbData {vertices, 0, 0};
    ComPtr<ID3D11Buffer> vb;
    if (FAILED(hr = device->CreateBuffer(&vbDesc, &vbData, &vb))) return fail("CreateBuffer", hr);

    constexpr UINT width = 64, height = 64;
    D3D11_TEXTURE2D_DESC desc {};
    desc.Width = width;
    desc.Height = height;
    desc.MipLevels = desc.ArraySize = desc.SampleDesc.Count = 1;
    desc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    desc.BindFlags = D3D11_BIND_RENDER_TARGET;
    ComPtr<ID3D11Texture2D> target, staging;
    ComPtr<ID3D11RenderTargetView> view;
    if (FAILED(hr = device->CreateTexture2D(&desc, nullptr, &target))) return fail("CreateTarget", hr);
    if (FAILED(hr = device->CreateRenderTargetView(target.Get(), nullptr, &view))) return fail("CreateTargetView", hr);
    desc.BindFlags = 0;
    desc.Usage = D3D11_USAGE_STAGING;
    desc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;
    if (FAILED(hr = device->CreateTexture2D(&desc, nullptr, &staging))) return fail("CreateStaging", hr);

    D3D11_RASTERIZER_DESC rasterDesc {};
    rasterDesc.FillMode = D3D11_FILL_SOLID;
    rasterDesc.CullMode = D3D11_CULL_NONE;
    rasterDesc.DepthClipEnable = TRUE;
    ComPtr<ID3D11RasterizerState> raster;
    if (FAILED(hr = device->CreateRasterizerState(&rasterDesc, &raster))) return fail("CreateRasterizerState", hr);
    D3D11_VIEWPORT viewport {0, 0, width, height, 0, 1};
    UINT stride = 2 * sizeof(float), offset = 0;
    const float clear[] = {0, 0, 0, 1};
    context->RSSetViewports(1, &viewport);
    context->RSSetState(raster.Get());
    context->IASetInputLayout(layout.Get());
    context->IASetVertexBuffers(0, 1, vb.GetAddressOf(), &stride, &offset);
    context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    context->VSSetShader(vs.Get(), nullptr, 0);
    context->PSSetShader(ps.Get(), nullptr, 0);
    context->OMSetRenderTargets(1, view.GetAddressOf(), nullptr);
    context->ClearRenderTargetView(view.Get(), clear);
    context->Draw(3, 0);
    context->OMSetRenderTargets(0, nullptr, nullptr);
    context->CopyResource(staging.Get(), target.Get());

    D3D11_MAPPED_SUBRESOURCE mapped {};
    if (FAILED(hr = context->Map(staging.Get(), 0, D3D11_MAP_READ, 0, &mapped))) return fail("Map", hr);
    unsigned checked = 0, mismatches = 0;
    if (!mapped.pData || mapped.RowPitch < width * 4) {
        context->Unmap(staging.Get(), 0);
        return fail("MapLayout", E_FAIL);
    }
    for (UINT y = 0; y < height; ++y) {
        const auto *row = static_cast<const unsigned char *>(mapped.pData) + y * mapped.RowPitch;
        for (UINT x = 0; x < width; ++x) {
            float nx = 2 * (x + .5f) / width - 1;
            float ny = 1 - 2 * (y + .5f) / height;
            float side = .375f - .5f * ny - (nx < 0 ? -nx : nx);
            bool inside = ny > -.67f && ny < .67f && side > .08f;
            bool outside = ny < -.83f || ny > .83f || side < -.08f;
            if (!inside && !outside) continue;
            const auto *p = row + 4 * x;
            bool valid = p[2] <= 1 && p[3] >= 254 &&
                         (inside ? (p[0] >= 254 && p[1] >= 63 && p[1] <= 65) : (p[0] <= 1 && p[1] <= 1));
            ++checked;
            if (!valid) ++mismatches;
        }
    }
    context->Unmap(staging.Get(), 0);
    if (mismatches || checked != 3330) {
        std::fprintf(stderr, "D3D11_READBACK_FAILED pixels=%u mismatches=%u\n", checked, mismatches);
        return 1;
    }
    std::wprintf(L"D3D11_DRAW_PASS adapter=%ls feature_level=11_0 pixels=%u\n", argv[1], checked);
    return 0;
}
