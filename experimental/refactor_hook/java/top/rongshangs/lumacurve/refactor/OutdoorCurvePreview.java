package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Read-only framework brightness / physical-nit coordinates for the outdoor editor. */
final class OutdoorCurvePreview {
    final double[] brightness,nit;final double normal,nativeMax;
    OutdoorCurvePreview(JSONObject j)throws JSONException{
        JSONArray x=j.getJSONArray("brightness"),y=j.getJSONArray("nit");int n=x.length();
        if(n<2||n>131||y.length()!=n)throw new IllegalArgumentException("Invalid outdoor mapping");
        brightness=new double[n];nit=new double[n];
        for(int i=0;i<n;i++){if(!(x.opt(i) instanceof Number)||!(y.opt(i) instanceof Number))throw new IllegalArgumentException("Invalid outdoor mapping type");brightness[i]=x.getDouble(i);nit[i]=y.getDouble(i);if(!Double.isFinite(brightness[i])||brightness[i]<0||brightness[i]>1||!Double.isFinite(nit[i])||nit[i]<0||i>0&&(brightness[i]<=brightness[i-1]||nit[i]<nit[i-1]))throw new IllegalArgumentException("Invalid outdoor mapping");}
        if(!(j.opt("normal") instanceof Number)||!(j.opt("native_max") instanceof Number))throw new IllegalArgumentException("Invalid outdoor bounds type");normal=j.getDouble("normal");nativeMax=j.getDouble("native_max");
        if(!Double.isFinite(normal)||normal<brightness[0]||normal>brightness[n-1]||!Double.isFinite(nativeMax)||nativeMax<normal||nativeMax>brightness[n-1]||nit[n-1]<=nit[0])throw new IllegalArgumentException("Invalid outdoor bounds");
    }
    static OutdoorCurvePreview read(JSONObject state){try{return state==null?null:new OutdoorCurvePreview(state.getJSONObject("preview_mapping"));}catch(Exception unknown){return null;}}
    double toNit(double value){if(!Double.isFinite(value)||value<brightness[0]||value>brightness[brightness.length-1])return Double.NaN;for(int i=1;i<brightness.length;i++)if(value<=brightness[i])return nit[i-1]+(nit[i]-nit[i-1])*(value-brightness[i-1])/(brightness[i]-brightness[i-1]);return Double.NaN;}
    double toBrightness(double value){if(!Double.isFinite(value)||value<nit[0]||value>nit[nit.length-1])return Double.NaN;for(int i=1;i<nit.length;i++)if(value<=nit[i])return nit[i]==nit[i-1]?brightness[i-1]:brightness[i-1]+(brightness[i]-brightness[i-1])*(value-nit[i-1])/(nit[i]-nit[i-1]);return Double.NaN;}
    double maximum(OutdoorOptions o){return o.flags[2]?brightness[brightness.length-1]:nativeMax;}
    double target(OutdoorOptions o,double lux){return toNit(OutdoorPolicy.target(o,(float)lux,(float)normal,(float)normal,(float)maximum(o)));}
    double strength(OutdoorOptions o,double value){double b=toBrightness(value),span=maximum(o)-normal;return !Double.isFinite(b)||span<=0?Double.NaN:Math.max(OutdoorOptions.MIN[5],Math.min(OutdoorOptions.MAX[5],(b-normal)/span));}
    boolean strengthEditable(OutdoorOptions o){return maximum(o)>normal;}
}
