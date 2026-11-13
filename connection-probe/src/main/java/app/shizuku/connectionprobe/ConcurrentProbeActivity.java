package app.shizuku.connectionprobe;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import rikka.shizuku.Shizuku;

public class ConcurrentProbeActivity extends Activity {
    private final IBinder[] services = new IBinder[2];
    private final Shizuku.UserServiceArgs[] args = new Shizuku.UserServiceArgs[2];
    private final ServiceConnection[] connections = new ServiceConnection[2];
    private final CountDownLatch ready = new CountDownLatch(2);
    private boolean started, running;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        android.widget.TextView view = new android.widget.TextView(this);
        view.setText("Independent application concurrency test");view.setTextSize(24);setContentView(view);
        new File(getFilesDir(), "concurrent-ready.txt").delete();new File(getFilesDir(),"concurrent-result.txt").delete();
        Shizuku.addBinderReceivedListenerSticky(this::bind);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if(ready.getCount()!=0) write("concurrent-ready.txt","FAIL startup timeout");
        },15000);
    }
    private void bind() {
        if(started)return;started=true;
        if(Shizuku.getUid()!=2000){write("concurrent-ready.txt","FAIL ordinary SDK UID query");return;}
        for(int i=0;i<2;i++){
            final int index=i;
            args[i]=new Shizuku.UserServiceArgs(new ComponentName(this,ConcurrentProbeService.class))
                    .tag("concurrent-"+i).processNameSuffix("concurrent-"+i).daemon(false).version(1);
            connections[i]=new ServiceConnection(){
                @Override public void onServiceConnected(ComponentName name,IBinder binder){
                    services[index]=binder;
                    new Thread(()->{
                        try{call(binder,1,false);ready.countDown();if(ready.getCount()==0)write("concurrent-ready.txt","PASS two persistent automation clients ready");}
                        catch(Exception e){write("concurrent-ready.txt","FAIL "+android.util.Log.getStackTraceString(e));}
                    }).start();
                }
                @Override public void onServiceDisconnected(ComponentName name){if(running&&index!=0)write("concurrent-result.txt","FAIL unexpected disconnect");}
            };
            Shizuku.bindUserService(args[i],connections[i]);
        }
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);if(intent.getBooleanExtra("run",false)&&!running){running=true;new Thread(this::exercise,"concurrent-test").start();}}
    private void exercise(){
        ExecutorService workers=Executors.newFixedThreadPool(4);AtomicReference<Throwable> failed=new AtomicReference<>();
        try{
            if(!ready.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Clients not ready");
            CountDownLatch complete=new CountDownLatch(4);
            for(int task=0;task<4;task++){final int index=task%2;workers.execute(()->{
                try{for(int round=0;round<12;round++){call(services[index],2,true);if(round%3==0)call(services[index],3,true);if(round==4)call(services[index],4,true);Thread.sleep(100);}}
                catch(Throwable error){failed.compareAndSet(null,error);}finally{complete.countDown();}
            });}
            if(!complete.await(45,TimeUnit.SECONDS))throw new IllegalStateException("Concurrent calls timed out");
            if(failed.get()!=null)throw new IllegalStateException("Concurrent worker failed",failed.get());
            Shizuku.unbindUserService(args[0],connections[0],true);
            Thread.sleep(200);
            for(int i=0;i<5;i++)call(services[1],2,true);
            String stats = stats(services[1]);
            if (!stats.contains("gesturesFailed=0") || !java.util.regex.Pattern.compile("gesturesCompleted=[1-9][0-9]*").matcher(stats).find()) {
                throw new IllegalStateException("Gesture coordination was not exercised successfully: " + stats);
            }
            write("concurrent-result.txt","PASS independentUid="+android.os.Process.myUid()+" fourThreads=48reads survivingClient=5reads\n"+stats);
        }catch(Throwable error){write("concurrent-result.txt","FAIL "+android.util.Log.getStackTraceString(error));}
        finally{workers.shutdownNow();}
    }
    private static String stats(IBinder binder)throws Exception {
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();
        try{if(!binder.transact(5,request,reply,0))throw new IllegalStateException("Stats unimplemented");reply.readException();return reply.readString();}
        finally{request.recycle();reply.recycle();}
    }
    private static void call(IBinder binder,int code,boolean string)throws Exception{
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();
        try{if(!binder.transact(code,request,reply,0))throw new IllegalStateException("Unimplemented probe transaction");reply.readException();if(string){if(reply.readString()==null)throw new IllegalStateException("Empty probe response");}else if(reply.readInt()<=0)throw new IllegalStateException("Invalid process");}
        finally{request.recycle();reply.recycle();}
    }
    private synchronized void write(String file,String value){
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(getFilesDir(),file))){out.write(value.getBytes(StandardCharsets.UTF_8));}
        catch(Exception error){android.util.Log.e("ConcurrentProbe","write failed",error);}
        android.util.Log.i("ConcurrentProbe",value);
    }
}
