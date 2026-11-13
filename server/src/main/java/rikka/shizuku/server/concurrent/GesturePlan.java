package rikka.shizuku.server.concurrent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validated complete gesture, independent of Android/IPC and immutable after planning. */
public final class GesturePlan {
    public static final class Point {
        public final int stroke, continued;
        public final boolean start, end;
        public final float x,y;
        public Point(int stroke,int continued,boolean start,boolean end,float x,float y){this.stroke=stroke;this.continued=continued;this.start=start;this.end=end;this.x=x;this.y=y;}
    }
    public static final class Step {
        public final long time;
        public final List<Point> points;
        public Step(long time,List<Point> points){this.time=time;this.points=points;}
    }
    public static final class Contact {
        public final int id;
        public final float x,y;
        Contact(int id,float x,float y){this.id=id;this.x=x;this.y=y;}
    }
    public static final class Frame {
        public final long time,down;
        public final int action;
        public final List<Contact> contacts;
        Frame(long time,long down,int action,Map<Integer,Contact> active){this.time=time;this.down=down;this.action=action;this.contacts=new ArrayList<>(active.values());}
    }
    private GesturePlan(){}
    public static List<Frame> build(List<Step> steps){
        if(steps.isEmpty()||steps.size()>2000)throw new IllegalArgumentException("Gesture must contain 1..2000 steps");
        Map<Integer,Contact> active=new LinkedHashMap<>();List<Frame> frames=new ArrayList<>();
        long previous=-1,down=0;
        for(Step step:steps){
            if(step.time<previous||step.time>10000||step.time<0||step.points.size()>16)throw new IllegalArgumentException("Gesture order, duration or touch count is invalid");
            previous=step.time;Set<Integer> present=new HashSet<>();boolean moved=false;
            for(Point point:step.points){
                if(!present.add(point.stroke)||point.continued!=-1||!Float.isFinite(point.x)||!Float.isFinite(point.y))throw new IllegalArgumentException("Invalid or cross-call continued stroke");
                Contact contact=active.get(point.stroke);
                if(contact!=null){
                    if(point.start)throw new IllegalArgumentException("Stroke started twice");
                    moved|=contact.x!=point.x||contact.y!=point.y;
                    active.put(point.stroke,new Contact(contact.id,point.x,point.y));
                }else if(!point.start)throw new IllegalArgumentException("Stroke has no starting point");
            }
            for(Integer stroke:active.keySet())if(!present.contains(stroke))throw new IllegalArgumentException("Stroke disappeared without ending");
            if(moved&&!active.isEmpty())frames.add(new Frame(step.time,down,2,active));
            for(Point point:step.points)if(point.end&&active.containsKey(point.stroke)){
                int index=new ArrayList<>(active.keySet()).indexOf(point.stroke);
                frames.add(new Frame(step.time,down,active.size()==1?1:6|(index<<8),active));active.remove(point.stroke);
            }
            for(Point point:step.points)if(point.start){
                Set<Integer> used=new HashSet<>();for(Contact contact:active.values())used.add(contact.id);
                int id=0;while(used.contains(id))id++;
                if(active.isEmpty())down=step.time;
                active.put(point.stroke,new Contact(id,point.x,point.y));
                if(active.size()>16)throw new IllegalArgumentException("Too many active touches");
                frames.add(new Frame(step.time,down,active.size()==1?0:5|((active.size()-1)<<8),active));
                if(point.end){frames.add(new Frame(step.time,down,active.size()==1?1:6|((active.size()-1)<<8),active));active.remove(point.stroke);}
            }
        }
        if(!active.isEmpty()||frames.isEmpty())throw new IllegalArgumentException("A complete gesture must release all touches");
        return frames;
    }
}
