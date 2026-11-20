package com.example.winavf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** Explicit shell-owned AVF channel delegation into the diagnostic viewer only. */
public final class WindowsOwnerProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        if(Binder.getCallingUid()!=2000)throw new SecurityException("Diagnostic shell owner only");
        if("mavk_probe".equals(method)){
            long identity=Binder.clearCallingIdentity();try{Bundle result=new Bundle();result.putBoolean("mavk_started",WindowsDiagnosticActivity.diagnosticMavkProbe(arg));return result;}catch(Exception error){throw new IllegalStateException("Mavk probe admission failed",error);}finally{Binder.restoreCallingIdentity(identity);}
        }
        if("clipboard_roundtrip".equals(method)) {
            long identity=Binder.clearCallingIdentity();
            try {
                Bundle result=new Bundle();
                result.putBoolean("guest_clipboard_roundtrip",WindowsDiagnosticActivity.diagnosticClipboardProbe(arg));
                return result;
            } catch(Exception error) {throw new IllegalStateException("Clipboard probe failed",error);}
            finally {Binder.restoreCallingIdentity(identity);}
        }
        if(("input_text".equals(method)||"input_key".equals(method))&&extras!=null) {
            String text=null;
            if("input_text".equals(method)) {
                String encoded=extras.getString("base64");
                if(encoded==null||encoded.length()>11000)throw new IllegalArgumentException("Text payload bound");
                text=new String(android.util.Base64.decode(encoded,android.util.Base64.NO_WRAP),java.nio.charset.StandardCharsets.UTF_8);
            }
            boolean written=WindowsDiagnosticActivity.diagnosticInput(arg,text,
                    extras.getInt("symbol",0),extras.getInt("state",0));
            Bundle result=new Bundle();result.putBoolean("channel_written",written);
            return result;
        }
        if("probe_duplex".equals(method)&&extras!=null){
            long identity=Binder.clearCallingIdentity();
            try(android.os.ParcelFileDescriptor fd=new WindowsDiagnosticActivity.DelegatedAvfChannel(extras.getBinder("owner"),extras.getString("token")).connectVsock(4055)){
                android.system.StructPollfd poll=new android.system.StructPollfd();poll.fd=fd.getFileDescriptor();poll.events=(short)android.system.OsConstants.POLLIN;
                if(android.system.Os.poll(new android.system.StructPollfd[]{poll},5000)!=1)throw new java.io.IOException("Client challenge timeout");
                byte[] challenge=new byte[17];int n=android.system.Os.read(fd.getFileDescriptor(),challenge,0,challenge.length);
                if(n!=17||!"UAVF-FD-CHALLENGE".equals(new String(challenge,java.nio.charset.StandardCharsets.US_ASCII)))throw new java.io.IOException("Client challenge differs: "+n);
                byte[] response="UAVF-FD-RESPONSE".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                android.system.Os.write(fd.getFileDescriptor(),response,0,response.length);
                Bundle result=new Bundle();result.putBoolean("duplex_roundtrip",true);return result;
            }catch(Exception e){throw new IllegalStateException("Diagnostic duplex socket test failed",e);}
            finally{Binder.restoreCallingIdentity(identity);}
        }
        if("probe_fd".equals(method)&&extras!=null){
            long identity=Binder.clearCallingIdentity();
            try(android.os.ParcelFileDescriptor fd=new WindowsDiagnosticActivity.DelegatedAvfChannel(extras.getBinder("owner"),extras.getString("token")).connectVsock(4055)){
                android.system.Os.fstat(fd.getFileDescriptor());
                Bundle result=new Bundle();result.putBoolean("fd_roundtrip",true);return result;
            }catch(Exception e){throw new IllegalStateException("Diagnostic descriptor roundtrip failed",e);}
            finally{Binder.restoreCallingIdentity(identity);}
        }
        if(!"register_owner".equals(method)||extras==null)throw new IllegalArgumentException("Owner registration required");
        boolean accepted=WindowsDiagnosticActivity.attachExternalOwner(extras.getString("token"),extras.getBinder("owner"));
        Bundle result=new Bundle();result.putBoolean("accepted",accepted);return result;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String order){throw new UnsupportedOperationException();}
    @Override public String getType(Uri u){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
