package app.noisetimer;

import android.Manifest;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private BroadcastReceiver receiver;
    private boolean paused;
    private String timeString;
    private double soundlvl;
    private boolean permission;
    private double cutoff;
    private double penaltyThreshold;
    private double penaltyMultiplier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        paused = true;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                processMessage(intent);
            }
        };
        permission = true;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(this.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permission = false;
                this.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode != 0) return;
        if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if(!permission) {
                permission = true;
                sendTimerIntent(Constants.START_ACTION);
            }
        } else {
            permission = false;
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogStyle);
            builder.setMessage("Required permission missing");
            builder.setPositiveButton("Close", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    finish();
                }
            });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    finish();
                }
            });
            builder.show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver,
                new IntentFilter(Constants.LB_MESSAGE));
        if(permission) sendTimerIntent(Constants.START_ACTION);
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        if(paused) {
            sendTimerIntent(Constants.STOP_ACTION);
        }
        super.onStop();
    }

    private void sendTimerIntent(String action) {
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void sendUpdateIntent(Long value) {
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(Constants.UPDATE_ACTION);
        intent.putExtra(Constants.LB_RAWTIME, value);
        startService(intent);
    }

    private void sendParamIntent(double cutoff, double threshold, double multiplier) {
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(Constants.PARAM_ACTION);
        intent.putExtra(Constants.LB_CUTOFF, cutoff);
        intent.putExtra(Constants.LB_THRESHOLD, threshold);
        intent.putExtra(Constants.LB_MULTIPLIER, multiplier);
        startService(intent);
    }

    private void processMessage(Intent intent) {
        timeString = intent.getStringExtra(Constants.LB_TIME);
        setPaused(intent.getBooleanExtra(Constants.LB_PAUSED, true));
        soundlvl = intent.getDoubleExtra(Constants.LB_LEVEL, 0);
        cutoff = intent.getDoubleExtra(Constants.LB_CUTOFF, 0);
        penaltyThreshold = intent.getDoubleExtra(Constants.LB_THRESHOLD, 0);
        penaltyMultiplier = intent.getDoubleExtra(Constants.LB_MULTIPLIER, 0);
        updateUI();
    }

    private void updateUI() {
        TextView msg = findViewById(R.id.message);
        msg.setText(String.valueOf(timeString));
        double prg = (Math.log(1+soundlvl)/Math.log(2)/15*1000);
        double th1 = Math.max(Math.min(penaltyThreshold/15*1000, 998), 1);
        double th2 = Math.max(Math.min(((penaltyThreshold+1/penaltyMultiplier)/15*1000), 999), th1);
        int progress = (int) ((prg-cutoff*1000)/(1-cutoff));
        int threshold1 = (int) ((th1-cutoff*1000)/(1-cutoff));
        int threshold2 = (int) ((th2-cutoff*1000)/(1-cutoff));
        if(progress < 0) progress = 0;
        if(threshold1 < 0) threshold1 = 0;
        if(threshold2 < 0) threshold2 = 0;
        setProgress(progress, threshold1, threshold2);
    }

    private void setProgress(int progress, int threshold1, int threshold2) {
        int lw = Math.min(progress, threshold1);
        int mw = Math.max(Math.min(progress, threshold2)-threshold1, 0);
        int hw = Math.max(progress-threshold2, 0);
        int ew = 1000-progress;
        View lbar = findViewById(R.id.levelLow);
        View mbar = findViewById(R.id.levelMed);
        View hbar = findViewById(R.id.levelHigh);
        View ebar = findViewById(R.id.levelEmpty);
        LinearLayout.LayoutParams vp;
        vp = (LinearLayout.LayoutParams) lbar.getLayoutParams();
        vp.weight = lw;
        lbar.setLayoutParams(vp);
        vp = (LinearLayout.LayoutParams) mbar.getLayoutParams();
        vp.weight = mw;
        mbar.setLayoutParams(vp);
        vp = (LinearLayout.LayoutParams) hbar.getLayoutParams();
        vp.weight = hw;
        hbar.setLayoutParams(vp);
        vp = (LinearLayout.LayoutParams) ebar.getLayoutParams();
        vp.weight = ew;
        ebar.setLayoutParams(vp);
    }

    public void reset(@SuppressWarnings("unused") View button) {
        if(!permission)  return;
        sendTimerIntent(Constants.RESET_ACTION);
    }

    public void pause(@SuppressWarnings("unused") View button) {
        if(!permission)  return;
        if(paused) {
            sendTimerIntent(Constants.UNPAUSE_ACTION);
            setPaused(false);
        } else {
            sendTimerIntent(Constants.PAUSE_ACTION);
            setPaused(true);
        }
    }

    public void increase(@SuppressWarnings("unused") View button) {
        if(!permission)  return;
        sendTimerIntent(Constants.INCREASE_ACTION);
    }

    public void edit(@SuppressWarnings("unused") View button) {
        if(!permission)  return;
        sendTimerIntent(Constants.PAUSE_ACTION);
        setPaused(true);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_DATETIME_VARIATION_TIME);
        input.setText(timeString);
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogStyle);
        builder.setView(input);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                String res = input.getText().toString();
                Pattern p = Pattern.compile("(\\d+):(\\d+)");
                Matcher m = p.matcher(res);
                if(m.matches()) {
                    long rem = Integer.valueOf(Objects.requireNonNull(m.group(1))) * 60 +
                               Integer.valueOf(Objects.requireNonNull(m.group(2)));
                    sendUpdateIntent(rem * 1000);
                } else {
                    p = Pattern.compile("(\\d+),(\\d+),(\\d+)");
                    m = p.matcher(res);
                    if(m.matches()) {
                        // hidden interface to set penalty parameters
                        // use 0 <= threshold1 < threshold2 <= 101
                        // no penalty for sound level < threshold1
                        // double speed at sound level < threshold2
                        int cutoffperc = Integer.valueOf(Objects.requireNonNull(m.group(1)));
                        int threshold1 = Integer.valueOf(Objects.requireNonNull(m.group(2)));
                        int threshold2 = Integer.valueOf(Objects.requireNonNull(m.group(3)));
                        if((0 <= threshold1) && (threshold1 < threshold2) && (threshold2 <= 101) &&
                           (0 <= cutoffperc) && (cutoffperc < 100)) {
                            cutoff = cutoffperc / 100.0;
                            penaltyThreshold = threshold1 * 15.0 / 100.0;
                            penaltyMultiplier = 100.0 / 15.0 / (threshold2-threshold1);
                            if(threshold2 == 101) penaltyMultiplier = 0;
                            sendParamIntent(cutoff, penaltyThreshold, penaltyMultiplier);
                        } else {
                            Dialog d = (Dialog) dialogInterface;
                            Toast.makeText(d.getContext(), "Invalid parameters", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Dialog d = (Dialog) dialogInterface;
                        Toast.makeText(d.getContext(), "Invalid time", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        builder.show();
    }

    private void setPaused(boolean newPaused) {
        paused = newPaused;
        ImageButton pauseButton = findViewById(R.id.pause);
        if (paused) {
            pauseButton.setImageResource(R.drawable.ic_action_play);
        } else {
            pauseButton.setImageResource(R.drawable.ic_action_pause);
        }
    }

}
