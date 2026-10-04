// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.KeyStore;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import javax.net.ssl.HttpsURLConnection;

/** Opt-in, create-only REST client. Refresh credentials stay encrypted in Keystore. */
final class CompatibilityUpload {
    private static final String KEY="AIzaSyC74CdEnXSWxud-970jt4em5eN7jws4vBc"; // Public client configuration, not admin credential.
    private static final String PROJECT="u-avf-7481c",ALIAS="uavf.compatibility.auth.v1";
    static synchronized String send(Context context,SharedPreferences consent,JSONObject fields) throws Exception {
        if(!consent.getBoolean("share_compatibility_report",false))return "Sharing is off";
        SharedPreferences auth=context.getSharedPreferences("compatibility_auth",Context.MODE_PRIVATE);
        int next=auth.getInt("next",0);if(next>=32)return "Report limit reached";
        String refresh=auth.getString("refresh",null),token,uid;
        if(refresh==null) {
            JSONObject a=post("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key="+KEY,"application/json","{\"returnSecureToken\":true}",null);
            uid=a.getString("localId");token=a.getString("idToken");
            if(!auth.edit().putString("uid",uid).putString("refresh",seal(a.getString("refreshToken"))).commit())throw new IOException("Identity storage failed");
        } else {
            JSONObject a=post("https://securetoken.googleapis.com/v1/token?key="+KEY,"application/x-www-form-urlencoded",
                "grant_type=refresh_token&refresh_token="+URLEncoder.encode(unseal(refresh),StandardCharsets.UTF_8),null);
            uid=a.getString("user_id");token=a.getString("id_token");
            if(!uid.equals(auth.getString("uid","")))throw new IOException("Identity mismatch");
            if(!auth.edit().putString("refresh",seal(a.getString("refresh_token"))).commit())throw new IOException("Identity storage failed");
        }
        if(!consent.getBoolean("share_compatibility_report",false))return "Sharing cancelled";
        String name="projects/"+PROJECT+"/databases/(default)/documents/compatibility_reports/"+uid+"/runs/"+next;
        JSONObject write=new JSONObject().put("update",new JSONObject().put("name",name).put("fields",fields))
            .put("currentDocument",new JSONObject().put("exists",false)).put("updateTransforms",new JSONArray()
            .put(new JSONObject().put("fieldPath","submittedAt").put("setToServerValue","REQUEST_TIME")));
        post("https://firestore.googleapis.com/v1/projects/"+PROJECT+"/databases/(default)/documents:commit","application/json",
            new JSONObject().put("writes",new JSONArray().put(write)).toString(),token);
        auth.edit().putInt("next",next+1).apply();return "Report sent to developers";
    }
    static JSONObject value(String v)throws Exception{return new JSONObject().put("stringValue",v==null?"":v);}
    static JSONObject map(JSONObject v)throws Exception{return new JSONObject().put("mapValue",new JSONObject().put("fields",v));}
    private static SecretKey key()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    private static String seal(String text)throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(text.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    private static String unseal(String text)throws Exception {
        String[] p=text.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    private static JSONObject post(String url,String type,String body,String token)throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type",type);
        if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);
        try {
            byte[] b=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(b.length);
            try(OutputStream out=c.getOutputStream()){out.write(b);}int status=c.getResponseCode();
            if(status<200||status>=300) {
                String detail="";
                try(InputStream error=c.getErrorStream()) {
                    if(error!=null) {
                        byte[] response=error.readNBytes(8192);
                        detail=new JSONObject(new String(response,StandardCharsets.UTF_8)).getJSONObject("error").optString("message","");
                    }
                } catch(Exception ignored) {}
                throw new IOException((url.contains("firestore")?"Firestore":"Auth")+" HTTP "+status+" "+detail.substring(0,Math.min(160,detail.length())));
            }
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                b=new byte[4096];int n;while((n=in.read(b))>=0){if(out.size()+n>65536)throw new IOException("Response too large");out.write(b,0,n);}
                return new JSONObject(out.toString(StandardCharsets.UTF_8));
            }
        } finally {c.disconnect();}
    }
}
