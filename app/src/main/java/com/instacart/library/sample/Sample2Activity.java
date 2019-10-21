package com.instacart.library.sample;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.instacart.library.truetime.Shell;
import com.instacart.library.truetime.TrueTime;
import com.instacart.library.truetime.TrueTimeRx;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class Sample2Activity
      extends AppCompatActivity {

    @BindView(R.id.tt_btn_refresh) Button refreshBtn;
    @BindView(R.id.tt_time_pst) TextView timeEST;
    @BindView(R.id.tt_time_offset) TextView timeOffset;
    @BindView(R.id.tt_time_device) TextView timeDeviceTime;

    Calendar calendar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);

        getSupportActionBar().setTitle("TrueTimeRx");

        ButterKnife.bind(this);
        calendar = Calendar.getInstance();
        refreshBtn.setEnabled(TrueTimeRx.isInitialized());
    }

    @OnClick(R.id.tt_btn_refresh)
    public void onBtnRefresh() {
        updateTime();
    }

    private void updateTime() {
        if (!TrueTimeRx.isInitialized()) {
            Toast.makeText(this, "Sorry TrueTime not yet initialized.", Toast.LENGTH_SHORT).show();
            return;
        }
        refreshBtn.setEnabled(true);
        Date trueTime = TrueTimeRx.now();
        Date deviceTime = Calendar.getInstance().getTime(); //java.lang.System.currentTimeMillis();

        Log.d("kg",
              String.format(" [trueTime: %d] [devicetime: %d] [drift_sec: %f]",
                            trueTime.getTime(),
                            deviceTime.getTime(),
                            (trueTime.getTime() - deviceTime.getTime()) / 1000F));

        SystemClock.setCurrentTimeMillis
                (TrueTimeRx.now().getTime());  // require setting with root: chmod 644 /dev/alarm
        timeEST.setText(getString(R.string.tt_time_pst,
                _formatDate(trueTime, "yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("GMT-04:00"))));
        timeDeviceTime.setText(getString(R.string.tt_time_device,
                _formatDate(deviceTime,"yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("GMT-04:00"))));
        timeOffset.setText("drift milli-sec: " + (trueTime.getTime() - deviceTime.getTime()));

        //AlarmManager am = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        //am.setTime(TrueTimeRx.now().getTime());

    }

    private String _formatDate(Date date, String pattern, TimeZone timeZone) {
        DateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
        format.setTimeZone(timeZone);
        return format.format(date);
    }

    public void setTime(long time) {
        if (Shell.isSuAvailable()) {
            Shell.runCommand("chmod 666 /dev/alarm");
            SystemClock.setCurrentTimeMillis(time);
            Shell.runCommand("chmod 664 /dev/alarm");
        }
    }
}
