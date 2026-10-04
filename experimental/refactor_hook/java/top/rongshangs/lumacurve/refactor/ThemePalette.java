package top.rongshangs.lumacurve.refactor;

/** Semantic equivalents for programmatic views and Canvas, following uiMode. */
final class ThemePalette {
    static int color(int light,boolean dark){
        if(!dark)return light;
        switch(light){
            case 0xff24303c:return 0xffe4e8ef;
            case 0xff616d79:case 0xff778390:return 0xffaab5c5;
            case 0xff3265df:return 0xff91b3ff;
            case 0xfffafafa:return 0xff11151c;
            case 0xffffffff:return 0xff1c232e;
            case 0xffeef2fa:case 0xffedf1f7:case 0xfff2f4f8:case 0xffeef0f4:return 0xff252e3c;
            case 0xffeef2ff:case 0xffedf3ff:case 0xffeef3ff:return 0xff263650;
            case 0xffe5eaf2:case 0xffe7ebf2:return 0xff384455;
            case 0xff9db4e2:return 0xff617fad;
            case 0xffbbc5d2:case 0xffa5aab3:return 0xff8290a4;
            case 0xffbd3434:return 0xffff9c9c;
            case 0xffad5426:return 0xffffb27e;
            default:return light;
        }
    }
}
