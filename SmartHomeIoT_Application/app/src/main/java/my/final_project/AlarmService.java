package my.final_project;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

public class AlarmService extends Service {
    private String TAG = "TEST+AlarmService";
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Bind");
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "oncreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG,"startCommand");
        PushWakeLock.acquireCpuWakeLock(this);
        Intent alarmIntent = new Intent(getApplicationContext(), AlarmActivity.class);
        startActivity(alarmIntent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"onDestory");
        PushWakeLock.releaseCpuLock();
    }
}
