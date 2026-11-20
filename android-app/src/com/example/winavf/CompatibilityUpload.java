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
import java.util.Locale;
import java.io.*;
import javax.net.ssl.HttpsURLConnection;

/** Opt-in, create-only REST client. Refresh credentials stay encrypted in Keystore. */
final class CompatibilityUpload {
    private static final String KEY="AIzaSyC74CdEnXSWxud-970jt4em5eN7jws4vBc"; // Public client configuration, not admin credential.
    private static final String PROJECT="u-avf-7481c",ALIAS="uavf.compatibility.auth.v1";
    private static final String DOCUMENTS="projects/"+PROJECT+"/databases/(default)/documents";
    private static final String FIRESTORE="https://firestore.googleapis.com/v1/"+DOCUMENTS;
    private static final int SLOT_LIMIT=32;

    record Group(String manufacturer, String model, int androidApi, int ramGiB, int cpuCount) {
        static Group forDevice(String manufacturer, String model, int api, long memory, int cpus) {
            long gib=1L<<30;
            int ram=(int)Math.max(0,Math.min(999,(memory+gib-1)/gib));
            int cpuCount=Math.max(1,Math.min(999,cpus));
            return new Group(slug(manufacturer,48),slug(model,64),api,ram,cpuCount);
        }

        String configuration() {
            return "api"+androidApi+"-ram"+ramGiB+"-cpu"+cpuCount;
        }

        JSONObject deviceFields() throws JSONException {
            return new JSONObject().put("androidApi",integer(androidApi))
                    .put("ramGiB",integer(ramGiB)).put("cpuCount",integer(cpuCount));
        }
    }

    private static String slug(String value,int limit) {
        String key=(value==null?"":value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-");
        key=key.replaceAll("^-+|-+$","");
        return key.isEmpty()?"unknown":key.substring(0,Math.min(limit,key.length()));
    }

    static synchronized String send(Context context,boolean consent,String run,Group group,JSONObject fields) throws Exception {
        if(!consent)return "Sharing is off";
        if(!run.matches("[a-f0-9]{32}"))throw new IOException("Missing compatibility run ID");
        SharedPreferences auth=context.getSharedPreferences("compatibility_auth",Context.MODE_PRIVATE);
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
        String owner="/compatibility_reporters/"+uid;
        String receipt=owner+"/receipts/"+run;
        if(getDocument(receipt,token)!=null)return "Compatibility report already sent";
        for(int next=Math.max(0,auth.getInt("next_v2",0));next<SLOT_LIMIT;next++) {
            String slot=Integer.toString(next);
            String slotPath=owner+"/slots/"+slot;
            if(getDocument(slotPath,token)!=null) {
                auth.edit().putInt("next_v2",next+1).apply();
                continue;
            }
            String report="/compatibility_devices/"+group.manufacturer()+"/"+group.model()
                    +"/"+group.configuration()+"/reporters/"+uid+"/tests/"+run;
            JSONObject metadata=new JSONObject().put("manufacturer",value(group.manufacturer()))
                    .put("model",value(group.model())).put("configuration",value(group.configuration()))
                    .put("slot",value(slot)).put("layout",value("direct"));
            JSONObject write=create(report,fields.put("slot",value(slot)));
            write.put("updateTransforms",new JSONArray().put(
                    new JSONObject().put("fieldPath","submittedAt").put("setToServerValue","REQUEST_TIME")));
            JSONArray writes=new JSONArray().put(write).put(create(receipt,metadata))
                    .put(create(slotPath,new JSONObject().put("run",value(run))));
            try {
                post(FIRESTORE+":commit","application/json",new JSONObject().put("writes",writes).toString(),token);
            } catch(IOException error) {
                try {
                    if(getDocument(receipt,token)!=null)return "Compatibility report already sent";
                } catch(Exception confirmationError) {
                    error.addSuppressed(confirmationError);
                }
                throw error;
            }
            auth.edit().putInt("next_v2",next+1).apply();
            return "Report sent to developers";
        }
        return "Report limit reached";
    }
    private static JSONObject create(String path,JSONObject fields)throws JSONException {
        return new JSONObject().put("update",new JSONObject().put("name",DOCUMENTS+path).put("fields",fields))
                .put("currentDocument",new JSONObject().put("exists",false));
    }
    private static JSONObject getDocument(String path,String token)throws Exception {
        return request(FIRESTORE+path,null,null,token,true);
    }
    static JSONObject value(String v)throws Exception{return new JSONObject().put("stringValue",v==null?"":v);}
    static JSONObject integer(long value)throws JSONException{return new JSONObject().put("integerValue",Long.toString(value));}
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
        return request(url,type,body,token,false);
    }
    private static JSONObject request(String url,String type,String body,String token,boolean missingOk)throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);
        c.setRequestMethod(body==null?"GET":"POST");
        if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);
        try {
            if(body!=null) {
                c.setDoOutput(true);c.setRequestProperty("Content-Type",type);
                byte[] data=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(data.length);
                try(OutputStream out=c.getOutputStream()){out.write(data);}
            }
            int status=c.getResponseCode();
            if(missingOk&&status==404)return null;
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
                byte[] b=new byte[4096];int n;while((n=in.read(b))>=0){if(out.size()+n>65536)throw new IOException("Response too large");out.write(b,0,n);}
                return new JSONObject(out.toString(StandardCharsets.UTF_8));
            }
        } finally {c.disconnect();}
    }
}
