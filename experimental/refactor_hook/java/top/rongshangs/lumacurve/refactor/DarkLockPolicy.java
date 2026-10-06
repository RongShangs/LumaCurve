package top.rongshangs.lumacurve.refactor;

/** Deterministic deadlines. No brightness outputs, sensor writes or mode writes here. */
final class DarkLockPolicy {
    static final int NONE=0,ENTER=1,EXIT=2;
    long darkSince=-1,brightSince=-1;boolean locked;
    void reset(){darkSince=brightSince=-1;locked=false;}
    static boolean valid(float lux){return Float.isFinite(lux)&&lux>=0;}
    int step(BrightnessControlOptions o,boolean eligible,boolean auto,float main,float assist,boolean dual,long now){
        if(!o.darkLock||!eligible){boolean had=locked;reset();return had?EXIT:NONE;}
        if(locked){
            if(auto){reset();return NONE;} // User re-enabled auto: yield without another write.
            if(!valid(main)||dual&&!valid(assist)){reset();return EXIT;}
            if(main>=o.exitLux||dual&&assist>=o.exitLux){if(brightSince<0)brightSince=now;if(now-brightSince>=o.exitSeconds*1000L){reset();return EXIT;}}
            else brightSince=-1;
            return NONE;
        }
        if(!auto||!valid(main)||dual&&!valid(assist)||main>o.enterLux||dual&&assist>o.enterLux){darkSince=-1;return NONE;}
        if(darkSince<0)darkSince=now;
        if(now-darkSince>=o.minutes*60000L){locked=true;darkSince=brightSince=-1;return ENTER;}
        return NONE;
    }
    long next(BrightnessControlOptions o){return locked?(brightSince<0?-1:brightSince+o.exitSeconds*1000L):(darkSince<0?-1:darkSince+o.minutes*60000L);}
}
