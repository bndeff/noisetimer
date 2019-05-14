package app.noisetimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class TimerService extends Service {
    private long lastTime = 0;
    private long remaining = 0;
    private long fulltime = 0;
    private double soundlvl = 0;
    private double penalty = 0;
    private double penaltyThreshold = 0;
    private double penaltyMultiplier = 0;
    private boolean paused = true;
    private boolean running = false;
    private NotificationCompat.Builder nbuilder = null;
    private NotificationManager nm = null;
    private PendingIntent intentBack = null;
    private Timer tm = null;
    private LocalBroadcastManager broadcaster = null;
    private MediaRecorder recorder = null;
    private Timer destroyTimer = null;

    @Override
    public void onCreate() {
        super.onCreate();
        lastTime = System.currentTimeMillis();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        fulltime = sp.getLong("default", 300000);
        remaining = sp.getLong("current", fulltime);
        penaltyThreshold = sp.getFloat("threshold", 8);
        penaltyMultiplier = sp.getFloat("multiplier", 0.3f);
        paused = true;
        Intent intent = new Intent(getApplicationContext(), TimerService.class);
        intent.setAction(Constants.OPEN_ACTION);
        intentBack = PendingIntent.getService(getApplicationContext(), 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        nm = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(
                    Constants.CHANNEL_SERVICE,
                    getResources().getString(R.string.channel_service),
                    NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel(
                    Constants.CHANNEL_ALARM,
                    getResources().getString(R.string.channel_alarm),
                    NotificationManager.IMPORTANCE_HIGH));
        }
        nbuilder = new NotificationCompat.Builder(this, Constants.CHANNEL_SERVICE);
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile("/dev/null");
        try {
            recorder.prepare();
            recorder.start();
        }
        catch (IOException ignored) { }
        broadcaster = LocalBroadcastManager.getInstance(this);
        tm = new Timer();
        tm.schedule(new TimerTask() {
            @Override
            public void run() {
                if(running) {
                    updateTime();
                    sendData();
                    nm.notify(Constants.NOTIF_SERVICE, remNotif());
                }
            }
        }, 0, 100);
    }

    @Override
    public void onDestroy() {
        if(tm != null) {
            tm.cancel();
            tm = null;
        }
        if(recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        super.onDestroy();
    }

    private void calcPenalty() {
        penalty = Math.max(Math.log(1+soundlvl)/Math.log(2)-penaltyThreshold, 0) * penaltyMultiplier + 1;
    }

    private void updateTime() {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastTime;
        lastTime = currentTime;
        if(recorder != null) {
            soundlvl = recorder.getMaxAmplitude();
        } else {
            soundlvl = 0;
        }
        calcPenalty();
        if(!paused) {
            remaining -= penalty * elapsed;
            if(remaining <= 0) {
                remaining = 0;
                paused = true;
                alarm();
            }
        }
        persistTimer();
    }

    private void alarm() {
        Notification noti = new NotificationCompat.Builder(this, Constants.CHANNEL_ALARM)
                .setContentTitle("Time elapsed")
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentIntent(intentBack)
                .setOngoing(false)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();
        nm.notify(Constants.NOTIF_ALARM, noti);
        startMainActivity();
    }

    private String formatTime() {
        long secs = (remaining + 500) / 1000;
        return String.format(Locale.US, "%02d:%02d", secs/60, secs%60);
    }

    private String formatPenalty() {
        return String.format(Locale.US, "%1.2f", penalty);
    }

    private void sendData() {
        Intent intent = new Intent(Constants.LB_MESSAGE);
        intent.putExtra(Constants.LB_TIME, formatTime());
        //intent.putExtra(Constants.LB_RAWTIME, remaining);
        //intent.putExtra(Constants.LB_FULLTIME, fulltime);
        intent.putExtra(Constants.LB_LEVEL, soundlvl);
        //intent.putExtra(Constants.LB_PENALTY, penalty);
        intent.putExtra(Constants.LB_PAUSED, paused);
        intent.putExtra(Constants.LB_THRESHOLD, penaltyThreshold);
        intent.putExtra(Constants.LB_MULTIPLIER, penaltyMultiplier);
        broadcaster.sendBroadcast(intent);
    }

    private Notification remNotif() {
        return nbuilder
                .setContentTitle(formatTime())
                .setContentText(formatPenalty())
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentIntent(intentBack)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setDefaults(0)
                .build();
    }

    private void startService() {
        running = true;
        if(destroyTimer != null) {
            destroyTimer.cancel();
            destroyTimer = null;
        }
        sendData();
        startForeground(Constants.NOTIF_SERVICE, remNotif());
        if(recorder != null) recorder.getMaxAmplitude(); // resets maximum to zero as side effect
    }

    private void stopService() {
        running = false;
        destroyTimer = new Timer();
        destroyTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                stopForeground(true);
                stopSelf();
            }
        }, 500);
    }

    void startMainActivity() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.setAction(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivity(mainIntent);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if(action == null) return START_STICKY;
        if(action.equals(Constants.START_ACTION)) {
            startService();
        }
        else if(action.equals(Constants.STOP_ACTION)) {
            stopService();
        }
        else if(action.equals(Constants.PAUSE_ACTION)) {
            paused = true;
        }
        else if(action.equals(Constants.UNPAUSE_ACTION)) {
            paused = false;
        }
        else if(action.equals(Constants.INCREASE_ACTION)) {
            remaining += 60000;
            persistTimer();
        }
        else if(action.equals(Constants.UPDATE_ACTION)) {
            fulltime = intent.getLongExtra(Constants.LB_RAWTIME, 0);
            remaining = fulltime;
            paused = true;
            persistTimer();
        }
        else if(action.equals(Constants.PARAM_ACTION)) {
            penaltyThreshold = intent.getDoubleExtra(Constants.LB_THRESHOLD, 0);
            penaltyMultiplier = intent.getDoubleExtra(Constants.LB_MULTIPLIER, 0);
            persistParams();
        }
        else if(action.equals(Constants.RESET_ACTION)) {
            nm.cancel(Constants.NOTIF_ALARM);
            remaining = fulltime;
            paused = true;
            persistTimer();
        }
        else if(action.equals(Constants.OPEN_ACTION)) {
            nm.cancel(Constants.NOTIF_ALARM);
            startMainActivity();
        }
        return START_STICKY;
    }

    private void persistTimer() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor spe = sp.edit();
        spe.putLong("default", fulltime);
        spe.putLong("current", remaining);
        spe.apply();
    }

    private void persistParams() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor spe = sp.edit();
        spe.putFloat("threshold", (float) penaltyThreshold);
        spe.putFloat("multiplier", (float) penaltyMultiplier);
        spe.apply();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
