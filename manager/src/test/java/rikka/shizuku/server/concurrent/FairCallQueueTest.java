package rikka.shizuku.server.concurrent;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class FairCallQueueTest {
    @Test public void mutationsAreExclusiveAndIndependentResourcesCanProgress() throws Exception {
        FairCallQueue queue = new FairCallQueue();
        ExecutorService workers = Executors.newFixedThreadPool(6);
        AtomicInteger running = new AtomicInteger(), peak = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            var first = workers.submit(() -> queue.call("input", "A", 3000, () -> { entered.countDown(); release.await(); return 1; }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(2, (int) queue.call("package", "B", 1000, () -> 2));
            List<java.util.concurrent.Future<Integer>> jobs = new ArrayList<>();
            for (int i=0;i<5;i++) { final int owner=i; jobs.add(workers.submit(() -> queue.call("input", owner, 3000, () -> {
                int count=running.incrementAndGet();peak.accumulateAndGet(count,Math::max);Thread.sleep(10);running.decrementAndGet();return count;
            }))); }
            release.countDown();first.get(2,TimeUnit.SECONDS);
            for (var job:jobs) job.get(2,TimeUnit.SECONDS);
            assertEquals(1,peak.get());
        } finally { release.countDown();workers.shutdownNow(); }
    }
    @Test public void timedOutRequestsNeverExecuteLaterAndFailureReleasesTheLane() throws Exception {
        FairCallQueue queue = new FairCallQueue();
        ExecutorService worker=Executors.newSingleThreadExecutor();
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        AtomicInteger effects=new AtomicInteger();
        try {
            var first=worker.submit(()->queue.call("ui", "first",2000,()->{entered.countDown();release.await();return 0;}));
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            try {queue.call("ui","expired",30,()->effects.incrementAndGet());fail();}catch(TimeoutException expected){}
            release.countDown();first.get(2,TimeUnit.SECONDS);
            try {queue.call("ui","broken",1000,()->{throw new IllegalArgumentException("test");});fail();}catch(IllegalArgumentException expected){}
            assertEquals(0,effects.get());assertEquals(7,(int)queue.call("ui","next",1000,()->7));
        }finally{release.countDown();worker.shutdownNow();}
    }
    @Test public void aBusyCallerCannotHoldTheNextTurnAgainstAnotherCaller() throws Exception {
        FairCallQueue queue=new FairCallQueue();ExecutorService workers=Executors.newFixedThreadPool(5);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        List<String> order=Collections.synchronizedList(new ArrayList<>());
        try{
            var first=workers.submit(()->queue.call("ui","holder",3000,()->{entered.countDown();release.await();return 0;}));
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            List<java.util.concurrent.Future<Integer>> jobs=new ArrayList<>();
            for(String name:new String[]{"A1","A2","A3","B1"}){
                String owner=name.substring(0,1);int wanted=jobs.size()+1;
                jobs.add(workers.submit(()->queue.call("ui",owner,3000,()->{order.add(name);return 0;})));
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
                while(!queue.diagnostics().contains("waiting="+wanted+" ")&&System.nanoTime()<until)Thread.yield();
                assertTrue(queue.diagnostics(),queue.diagnostics().contains("waiting="+wanted+" "));
            }
            release.countDown();first.get(2,TimeUnit.SECONDS);for(var job:jobs)job.get(2,TimeUnit.SECONDS);
            assertEquals(java.util.Arrays.asList("A1","B1","A2","A3"),order);
        }finally{release.countDown();workers.shutdownNow();}
    }
    @Test public void disconnectedCallerLosesItsQueuedWork() throws Exception {
        FairCallQueue queue=new FairCallQueue();ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger effects=new AtomicInteger();
        try {
            var first=workers.submit(()->queue.call("ui","holder",2000,()->{entered.countDown();release.await();return 0;}));
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            var cancelled=workers.submit(()->queue.call("ui","gone",2000,()->effects.incrementAndGet()));
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
            while(!queue.diagnostics().contains("waiting=1 ")&&System.nanoTime()<until)Thread.yield();
            assertTrue(queue.diagnostics(),queue.diagnostics().contains("waiting=1 "));
            queue.cancel("gone");release.countDown();first.get(2,TimeUnit.SECONDS);
            try{cancelled.get(2,TimeUnit.SECONDS);fail();}catch(java.util.concurrent.ExecutionException expected){assertTrue(expected.getCause() instanceof IllegalStateException);}
            assertEquals(0,effects.get());
        } finally {release.countDown();workers.shutdownNow();}
    }

}
