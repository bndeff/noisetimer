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
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private BroadcastReceiver receiver;
    private boolean paused;
    private String timeString;
    private double soundlvl;
    private boolean permission;
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

    private void sendParamIntent(double threshold, double multiplier) {
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(Constants.PARAM_ACTION);
        intent.putExtra(Constants.LB_THRESHOLD, threshold);
        intent.putExtra(Constants.LB_MULTIPLIER, multiplier);
        startService(intent);
    }

    private void processMessage(Intent intent) {
        timeString = intent.getStringExtra(Constants.LB_TIME);
        setPaused(intent.getBooleanExtra(Constants.LB_PAUSED, true));
        soundlvl = intent.getDoubleExtra(Constants.LB_LEVEL, 0);
        penaltyThreshold = intent.getDoubleExtra(Constants.LB_THRESHOLD, 0);
        penaltyMultiplier = intent.getDoubleExtra(Constants.LB_MULTIPLIER, 0);
        updateUI();
    }

    private void updateUI() {
        TextView msg = findViewById(R.id.message);
        msg.setText(String.valueOf(timeString));
        int progress = (int) (Math.log(1+soundlvl)/Math.log(2)/15*1000);
        int threshold1 = (int) Math.max(Math.min(penaltyThreshold/15*1000, 998), 1);
        int threshold2 = (int) Math.max(Math.min(((penaltyThreshold+1/penaltyMultiplier)/15*1000),
                999), threshold1);
        setProgress(progress, threshold1, threshold2);
    }

    private void setProgress(int progress, int threshold1, int threshold2) {
        ProgressBar lbar = findViewById(R.id.levelLow);
        ProgressBar mbar = findViewById(R.id.levelMed);
        ProgressBar hbar = findViewById(R.id.levelHigh);
        if(lbar != null && mbar != null && hbar != null) {  // no progress bar in landscape mode
            lbar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, threshold1));
            mbar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, threshold2-threshold1));
            hbar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1000-threshold2));
            lbar.setMax(threshold1);
            lbar.setProgress(Math.min(progress, threshold1));
            mbar.setMax(threshold2-threshold1);
            mbar.setProgress(Math.max(Math.min(progress-threshold1, threshold2-threshold1), 0));
            hbar.setMax(1000-threshold2);
            hbar.setProgress(Math.max(progress-threshold2, 0));
        }
    }

    public void reset(View button) {
        if(!permission)  return;
        sendTimerIntent(Constants.RESET_ACTION);
    }

    public void pause(View button) {
        if(!permission)  return;
        if(paused) {
            sendTimerIntent(Constants.UNPAUSE_ACTION);
            setPaused(false);
        } else {
            sendTimerIntent(Constants.PAUSE_ACTION);
            setPaused(true);
        }
    }

    public void increase(View button) {
        if(!permission)  return;
        sendTimerIntent(Constants.INCREASE_ACTION);
    }

    public void edit(View button) {
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
                    long rem = Integer.valueOf(m.group(1)) * 60 + Integer.valueOf(m.group(2));
                    sendUpdateIntent(rem * 1000);
                } else {
                    p = Pattern.compile("(\\d+),(\\d+)");
                    m = p.matcher(res);
                    if(m.matches()) {
                        // hidden interface to set penalty parameters
                        // use 0 <= threshold1 < threshold2 <= 101
                        // no penalty for sound level < threshold1
                        // double speed at sound level < threshold2
                        int threshold1 = Integer.valueOf(m.group(1));
                        int threshold2 = Integer.valueOf(m.group(2));
                        if((0 <= threshold1) && (threshold1 < threshold2) && (threshold2 <= 101)) {
                            penaltyThreshold = threshold1 * 15.0 / 100.0;
                            penaltyMultiplier = 100.0 / 15.0 / (threshold2-threshold1);
                            if(threshold2 == 101) penaltyMultiplier = 0;
                            sendParamIntent(penaltyThreshold, penaltyMultiplier);
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
        if(pauseButton != null) {  // no buttons in landscape mode
            if (paused) {
                pauseButton.setImageResource(R.drawable.ic_action_play);
            } else {
                pauseButton.setImageResource(R.drawable.ic_action_pause);
            }
        }
    }

}
