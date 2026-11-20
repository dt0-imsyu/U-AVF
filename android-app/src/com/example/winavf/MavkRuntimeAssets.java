package com.example.winavf;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.zip.ZipFile;

/** Immutable, signed-APK description of the opt-in Live-only Mavk prefix. */
final class MavkRuntimeAssets {
    static final String MANIFEST="uavf-mavk-runtime.json";
    static final String PREFIX="p7-platform-prefix-mavk-live.img";
    static final String BASE="p7-platform-prefix-virgl-gbm-linux-first-cu120-xdamage-composite-overlay.img";
    static final String BASE_SHA="FE451CA13834DC5EDE3096EDC3AF1EF2894BFDECAA7801CED9E69EDB07447858";
    static final long PREFIX_BYTES=134217728L;
    final String sha256;
    private MavkRuntimeAssets(String sha256){this.sha256=sha256;}
    static MavkRuntimeAssets load(Context context)throws Exception {
        String text;
        try(InputStream in=context.getAssets().open(MANIFEST)){
            byte[] bytes=in.readNBytes(8193);
            if(bytes.length>8192)throw new java.io.IOException("Mavk manifest bound");
            text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        }
        JSONObject json=new JSONObject(text);
        String sha=json.getString("prefix_sha256").toUpperCase(java.util.Locale.ROOT);
        if(json.getInt("codec_version")!=3||json.getLong("prefix_bytes")!=PREFIX_BYTES
                ||!PREFIX.equals(json.getString("prefix_asset"))||!BASE_SHA.equalsIgnoreCase(json.getString("base_sha256"))
                ||!sha.matches("[0-9A-F]{64}"))throw new java.io.IOException("Mavk guest/host contract mismatch");
        try(ZipFile apk=new ZipFile(context.getApplicationInfo().sourceDir)){
            if(apk.getEntry("lib/arm64-v8a/libmavk_runtime_probe.so")==null)throw new java.io.IOException("Mavk renderer library missing");
        }
        return new MavkRuntimeAssets(sha);
    }
    static boolean isBundled(Context context){try{load(context);return true;}catch(Exception absent){return false;}}
}
