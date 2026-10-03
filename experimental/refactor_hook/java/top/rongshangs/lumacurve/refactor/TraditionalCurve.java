package top.rongshangs.lumacurve.refactor;

/** Four editing handles reshape a dense OEM curve without replacing its nodes. */
public final class TraditionalCurve {
    private final float[] lux,nit;
    private final int[] handles;
    public TraditionalCurve(float[] lux,float[] nit){
        if(lux==null||nit==null||lux.length!=nit.length||lux.length<4||lux.length>2048||lux[0]!=0)
            throw new IllegalArgumentException("传统曲线节点不兼容");
        for(int i=0;i<lux.length;i++)if(!Float.isFinite(lux[i])||!Float.isFinite(nit[i])||lux[i]<0||nit[i]<0||
            (i>0&&(lux[i]<=lux[i-1]||nit[i]<nit[i-1])))throw new IllegalArgumentException("设备曲线无效");
        this.lux=lux.clone();this.nit=nit.clone();
        int a=nearest(30,1,lux.length-3),b=nearest(600,a+1,lux.length-2);
        handles=new int[]{0,a,b,lux.length-1};
    }
    private int nearest(float value,int from,int to){int best=from;for(int i=from+1;i<=to;i++)if(Math.abs(lux[i]-value)<Math.abs(lux[best]-value))best=i;return best;}
    public float[] lux(){return lux.clone();}
    public float[] nit(){return nit.clone();}
    public float[] controlsLux(){return select(lux);}
    public float[] controlsNit(){return select(nit);}
    private float[] select(float[] values){float[] result=new float[4];for(int i=0;i<4;i++)result[i]=values[handles[i]];return result;}
    public float[] reshape(float min,float max,float[] factors){
        return reshape(min,max,factors,0);
    }
    public float[] reshape(float min,float max,float[] factors,float floor){
        if(factors==null||factors.length!=4||factors[3]!=1)throw new IllegalArgumentException("高照度末端保持设备原始值");
        float[] desired=new CurvePlan(controlsLux(),controlsNit(),min,max,factors,floor).nits(),result=nit.clone();
        for(int s=0;s<3;s++){
            int a=handles[s],b=handles[s+1];float span=nit[b]-nit[a];
            for(int i=a;i<=b;i++){
                // Exact neutral baseline, including plateaus; no rounded rebuild at 100%.
                if(desired[s]==nit[a]&&desired[s+1]==nit[b])result[i]=nit[i];
                else {float t=span>0?(nit[i]-nit[a])/span:(lux[i]-lux[a])/(lux[b]-lux[a]);result[i]=desired[s]+t*(desired[s+1]-desired[s]);}
            }
            result[a]=desired[s];result[b]=desired[s+1];
        }
        return result;
    }
}
