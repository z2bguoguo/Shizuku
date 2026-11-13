package rikka.shizuku.server.concurrent;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class GesturePlanTest {
    private GesturePlan.Point point(int id,boolean start,boolean end,float x){return new GesturePlan.Point(id,-1,start,end,x,30);}
    private GesturePlan.Step step(long time,GesturePlan.Point...points){return new GesturePlan.Step(time,Arrays.asList(points));}
    @Test public void stationaryTapKeepsRealTimingAndReleasesContact(){
        List<GesturePlan.Frame> frames=GesturePlan.build(Arrays.asList(step(0,point(900,true,false,10)),step(80,point(900,false,true,10))));
        assertEquals(2,frames.size());assertEquals(0,frames.get(0).action);assertEquals(1,frames.get(1).action);
        assertEquals(80,frames.get(1).time);assertEquals(0,frames.get(1).down);assertEquals(0,frames.get(0).contacts.get(0).id);
    }
    @Test public void multitouchUsesStablePointerIdsAndCorrectUpIndices(){
        List<GesturePlan.Frame> frames=GesturePlan.build(Arrays.asList(
                step(0,point(21,true,false,10)),
                step(20,point(21,false,false,12),point(84,true,false,50)),
                step(40,point(21,false,true,14),point(84,false,false,55)),
                step(60,point(84,false,true,60))));
        GesturePlan.Frame down=frames.stream().filter(f->(f.action&255)==5).findFirst().get();
        assertEquals(5|(1<<8),down.action);assertEquals(2,down.contacts.size());
        assertEquals(0,down.contacts.get(0).id);assertEquals(1,down.contacts.get(1).id);
        GesturePlan.Frame up=frames.stream().filter(f->(f.action&255)==6).findFirst().get();assertEquals(6,up.action);
        assertEquals(1,frames.get(frames.size()-1).action);assertEquals(1,frames.get(frames.size()-1).contacts.get(0).id);
    }
    @Test public void incompleteAndCrossCallGesturesAreRejectedBeforeInjection(){
        try{GesturePlan.build(Arrays.asList(step(0,point(1,true,false,10))));fail();}catch(IllegalArgumentException expected){}
        try{GesturePlan.build(Arrays.asList(step(0,new GesturePlan.Point(1,0,true,true,10,10))));fail();}catch(IllegalArgumentException expected){}
        try{GesturePlan.build(Arrays.asList(step(0,point(1,true,false,Float.NaN)),step(1,point(1,false,true,1))));fail();}catch(IllegalArgumentException expected){}
        try{GesturePlan.build(Arrays.asList(step(0,point(1,true,false,1)),step(10001,point(1,false,true,1))));fail();}catch(IllegalArgumentException expected){}
    }
}
