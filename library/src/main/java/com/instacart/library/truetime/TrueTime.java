package com.instacart.library.truetime;

import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;

public class TrueTime {

    private static final String TAG = TrueTime.class.getSimpleName();

    private final static TrueTime INSTANCE = new TrueTime();
    public final SntpClient SNTP_CLIENT = new SntpClient();

    public static float _rootDelayMax = 100;
    public static float _rootDispersionMax = 100;
    public static int _serverResponseDelayMax = 750;
    public static int _udpSocketTimeoutInMillis = 30_000;

    //private String _ntpHost = "1.us.pool.ntp.org";
    private String _ntpHost = "0";

    /**
     * @return Date object that returns the current time in the default Timezone
     */
    public Date now() {
//        if (!isInitialized()) {
//            throw new IllegalStateException("You need to call init() on TrueTime at least once.");
//        }
        long cachedSntpTime = _getCachedSntpTime();
        long cachedDeviceUptime = _getCachedDeviceUptime();
        long deviceUptime = SystemClock.elapsedRealtime();
        long now = cachedSntpTime + (deviceUptime - cachedDeviceUptime);

        return new Date(now);
    }

    public  boolean isInitialized() {
        return SNTP_CLIENT.wasInitialized();
    }

    public TrueTime build() {
        return INSTANCE;
    }

    public void initialize() throws IOException {
        initialize(_ntpHost);
    }

    public synchronized TrueTime withConnectionTimeout(int timeoutInMillis) {
        _udpSocketTimeoutInMillis = timeoutInMillis;
        return INSTANCE;
    }

    public synchronized TrueTime withRootDelayMax(float rootDelayMax) {
        if (rootDelayMax > _rootDelayMax) {
          String log = String.format(Locale.getDefault(),
              "The recommended max rootDelay value is %f. You are setting it at %f",
              _rootDelayMax, rootDelayMax);
          Log.w(TAG, log);
        }

        _rootDelayMax = rootDelayMax;
        return INSTANCE;
    }

    public synchronized TrueTime withRootDispersionMax(float rootDispersionMax) {
      if (rootDispersionMax > _rootDispersionMax) {
        String log = String.format(Locale.getDefault(),
            "The recommended max rootDispersion value is %f. You are setting it at %f",
            _rootDispersionMax, rootDispersionMax);
        Log.w(TAG, log);
      }

      _rootDispersionMax = rootDispersionMax;
      return INSTANCE;
    }

    public synchronized TrueTime withServerResponseDelayMax(int serverResponseDelayInMillis) {
        _serverResponseDelayMax = serverResponseDelayInMillis;
        return INSTANCE;
    }

    public synchronized TrueTime withNtpHost(String ntpHost) {
        _ntpHost = ntpHost;
        return INSTANCE;
    }

    public synchronized TrueTime withLoggingEnabled(boolean isLoggingEnabled) {
        return INSTANCE;
    }

    // -----------------------------------------------------------------------------------

    protected void initialize(String ntpHost) throws IOException {
//        if (isInitialized()) {
//            Log.i(TAG, "---- TrueTime already initialized from previous boot/init");
//            return;
//        }
        Log.d(TAG, "TrueTime::intialize()");
        requestTime(ntpHost);
    }

    long[] requestTime(String ntpHost) throws IOException {
        return SNTP_CLIENT.requestTime(ntpHost,
            _rootDelayMax,
            _rootDispersionMax,
            _serverResponseDelayMax,
            _udpSocketTimeoutInMillis);
    }


    void cacheTrueTimeInfo(long[] response) {
        SNTP_CLIENT.cacheTrueTimeInfo(response);
    }

    public long _getCachedDeviceUptime() {
        long cachedDeviceUptime = SNTP_CLIENT.wasInitialized()
                                  ? SNTP_CLIENT.getCachedDeviceUptime()
                                  : 0;

        if (cachedDeviceUptime == 0L) {
            throw new RuntimeException("expected device time from last boot to be cached. couldn't find it.");
        }

        return cachedDeviceUptime;
    }

    public long _getCachedSntpTime() {
        Log.d(TAG, "_getCachedSntpTime SNTP_CLIENT " + SNTP_CLIENT);
        long cachedSntpTime = SNTP_CLIENT.wasInitialized()
                              ? SNTP_CLIENT.getCachedSntpTime()
                              : 0;

        if (cachedSntpTime == 0L) {
            throw new RuntimeException("expected SNTP time from last boot to be cached. couldn't find it.");
        }

        return cachedSntpTime;
    }

}
