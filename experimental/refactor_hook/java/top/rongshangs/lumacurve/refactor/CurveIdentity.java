package top.rongshangs.lumacurve.refactor;
import java.nio.*;
import java.security.*;
/** Bind saved options to the detected baseline, not just the device model. */
public final class CurveIdentity {
    public static String of(String backend,float[] x,float[] y,float min,float max){
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(backend.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer bytes=ByteBuffer.allocate(12+(x.length+y.length)*4);bytes.putInt(x.length).putFloat(min).putFloat(max);
            for(float n:x)bytes.putFloat(n);for(float n:y)bytes.putFloat(n);byte[] hash=digest.digest(bytes.array());
            StringBuilder out=new StringBuilder();for(byte n:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",n&255));return out.toString();
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
