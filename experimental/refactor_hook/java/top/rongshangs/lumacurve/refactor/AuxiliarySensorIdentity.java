package top.rongshangs.lumacurve.refactor;

import android.hardware.Sensor;
import java.util.List;

/** Explicit scalar-lux identities, never guess from vendor numeric types or device names. */
final class AuxiliarySensorIdentity {
    static boolean secondary(Sensor sensor){
        if(sensor==null)return false;
        return sensor.getType()>=Sensor.TYPE_DEVICE_PRIVATE_BASE&&"zte.sensor.light_second".equals(sensor.getStringType())
            &&!sensor.isWakeUpSensor()&&sensor.getReportingMode()==Sensor.REPORTING_MODE_ON_CHANGE
            &&permissionFree(sensor)
            &&Float.isFinite(sensor.getMaximumRange())&&sensor.getMaximumRange()>0
            &&Float.isFinite(sensor.getResolution())&&sensor.getResolution()>=0;
    }
    static boolean permissionFree(Sensor sensor){
        try{java.lang.reflect.Method m=Sensor.class.getDeclaredMethod("getRequiredPermission");if(m.getReturnType()!=String.class)return false;m.setAccessible(true);Object value=m.invoke(sensor);return value==null||((String)value).isEmpty();}
        catch(Throwable unavailable){return false;}
    }
    static boolean accepted(Sensor sensor){return sensor!=null&&(sensor.getType()==Sensor.TYPE_LIGHT||sensor.getType()==33171055||secondary(sensor));}
    static Sensor unique(List<Sensor> sensors){
        if(sensors==null)return null;Sensor result=null;
        for(Sensor sensor:sensors)if(secondary(sensor)){if(result!=null&&result!=sensor)throw new IllegalArgumentException("存在多个辅助光感候选，保留系统选择");result=sensor;}
        return result;
    }
}
