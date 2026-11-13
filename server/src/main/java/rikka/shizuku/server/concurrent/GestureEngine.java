package rikka.shizuku.server.concurrent;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Executes complete gestures with the same privileged InputManager path as UiAutomation. */
final class GestureEngine {
    private static Field field(Object object,String name)throws Exception{Field f=object.getClass().getField(name);f.setAccessible(true);return f;}
    static List<GesturePlan.Step> decode(List<?> raw)throws Exception{
        if(raw.size()>2000)throw new IllegalArgumentException("Too many gesture steps");
        List<GesturePlan.Step> result=new ArrayList<>();
        for(Object value:raw){
            long time=field(value,"timeSinceGestureStart").getLong(value);
            int count=field(value,"numTouchPoints").getInt(value);
            Object[] points=(Object[])field(value,"touchPoints").get(value);
            if(count<0||count>16||count>points.length)throw new IllegalArgumentException("Invalid gesture touch count");
            List<GesturePlan.Point> decoded=new ArrayList<>();
            for(int i=0;i<count;i++){Object point=points[i];decoded.add(new GesturePlan.Point(
                    field(point,"mStrokeId").getInt(point),field(point,"mContinuedStrokeId").getInt(point),
                    field(point,"mIsStartOfPath").getBoolean(point),field(point,"mIsEndOfPath").getBoolean(point),
                    field(point,"mX").getFloat(point),field(point,"mY").getFloat(point)));}
            result.add(new GesturePlan.Step(time,decoded));
        }
        return result;
    }
    static boolean execute(List<?> steps,int display,BooleanSupplier alive)throws Exception{
        List<GesturePlan.Frame> plan=GesturePlan.build(decode(steps));
        Class<?> type;
        try{type=Class.forName("android.hardware.input.InputManagerGlobal");}
        catch(ClassNotFoundException old){type=Class.forName("android.hardware.input.InputManager");}
        Object manager=type.getMethod("getInstance").invoke(null);
        Method inject=type.getMethod("injectInputEvent",InputEvent.class,int.class);
        long base=SystemClock.uptimeMillis();GesturePlan.Frame held=null;
        boolean complete=false;
        try{
            for(GesturePlan.Frame frame:plan){
                while(SystemClock.uptimeMillis()<base+frame.time){
                    if(!alive.getAsBoolean()||Thread.currentThread().isInterrupted())return false;
                    Thread.sleep(Math.min(25,Math.max(1,base+frame.time-SystemClock.uptimeMillis())));
                }
                if(!alive.getAsBoolean()||Thread.currentThread().isInterrupted())return false;
                int action=frame.action&255;
                if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_MOVE)held=frame;
                if(!inject(manager,inject,frame,base,display,frame.action))return false;
                if(action==MotionEvent.ACTION_UP)held=null;
                else if(action==MotionEvent.ACTION_POINTER_UP){
                    java.util.LinkedHashMap<Integer,GesturePlan.Contact> remaining=new java.util.LinkedHashMap<>();
                    int lifted=(frame.action>>8)&255;
                    for(int i=0;i<frame.contacts.size();i++)if(i!=lifted)remaining.put(frame.contacts.get(i).id,frame.contacts.get(i));
                    held=new GesturePlan.Frame(frame.time,frame.down,MotionEvent.ACTION_MOVE,remaining);
                }
            }
            complete=true;return true;
        }finally{
            if(!complete&&held!=null){
                try{inject(manager,inject,held,SystemClock.uptimeMillis()-held.time,display,MotionEvent.ACTION_CANCEL);}
                catch(Exception ignored){}
            }
        }
    }
    private static boolean inject(Object manager,Method inject,GesturePlan.Frame frame,long base,int display,int action)throws Exception{
        int count=frame.contacts.size();MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){
            GesturePlan.Contact point=frame.contacts.get(i);
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=point.id;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i]=new MotionEvent.PointerCoords();coordinates[i].x=point.x;coordinates[i].y=point.y;coordinates[i].pressure=1;coordinates[i].size=1;
        }
        MotionEvent event=MotionEvent.obtain(base+frame.down,base+frame.time,action,count,properties,coordinates,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try{
            MotionEvent.class.getMethod("setDisplayId",int.class).invoke(event,display);
            try{return (Boolean)inject.invoke(manager,event,2);}
            catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof Exception)throw(Exception)cause;throw error;}
        }finally{event.recycle();}
    }
}
