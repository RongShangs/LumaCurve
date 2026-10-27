package top.rongshangs.lumacurve.refactor;

/** Semantic equivalents for programmatic views and Canvas, following uiMode. */
final class ThemePalette {
    static int color(int light,boolean dark){
        if(!dark)return light;
        switch(light){
            case 0xff24303c:return 0xffe4e8ef;
            case 0xff616d79:case 0xff778390:return 0xffaab5c5;
            case 0xff3265df:return 0xff91b3ff;
            case 0xff78b997:return 0xff9bd3b4;
            case 0xfffafafa:return 0xff11151c;
            case 0xffffffff:return 0xff1c232e;
            case 0xffeef2fa:case 0xffedf1f7:case 0xfff2f4f8:case 0xffeef0f4:case 0xfff4f5f7:return 0xff252e3c;
            case 0xffeef2ff:case 0xffedf3ff:case 0xffeef3ff:case 0xffe5edff:return 0xff263650;
            case 0xffe5eaf2:case 0xffe7ebf2:return 0xff384455;
            case 0xff9db4e2:return 0xff617fad;
            case 0xffbbc5d2:case 0xffa5aab3:return 0xff8290a4;
            case 0xffbd3434:return 0xffff9c9c;
            case 0xffad5426:return 0xffffb27e;
            default:return light;
        }
    }
    /** Background-only fallback; never reinterpret already themed foreground text. */
    static int surface(int light,boolean dark){int mapped=color(light,dark);if(!dark||mapped!=light)return mapped;int r=(light>>>16)&255,g=(light>>>8)&255,b=light&255;if((light>>>24)!=0&&r>=200&&g>=200&&b>=200)return (light&0xff000000)|0x252e3c;return light;}
}
