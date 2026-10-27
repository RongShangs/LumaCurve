package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.Field;
import java.util.Objects;

/** A temporary field value belongs to this call only while it still equals our value. */
final class FieldOverride implements AutoCloseable {
    final Object target;final Field field;final Object original;Object applied;boolean closed;
    private FieldOverride(Object target,Field field)throws IllegalAccessException{this.target=target;this.field=field;original=field.get(target);}
    static FieldOverride apply(Object target,Field field,Object value)throws IllegalAccessException{
        FieldOverride scope=new FieldOverride(target,field);scope.replace(value);return scope;
    }
    void replace(Object value)throws IllegalAccessException{if(closed)throw new IllegalStateException("Closed field scope");field.set(target,value);applied=value;}
    public void close()throws IllegalAccessException{if(closed)return;if(Objects.equals(field.get(target),applied))field.set(target,original);closed=true;}
}
