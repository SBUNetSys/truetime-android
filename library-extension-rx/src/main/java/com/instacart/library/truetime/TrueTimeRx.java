package com.instacart.library.truetime;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.FlowableTransformer;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.reactivestreams.Publisher;

public class TrueTimeRx
      extends TrueTime {

    private static final String TAG = TrueTimeRx.class.getSimpleName();

    private int _retryCount = 50;

    public String _ntpPoolAddress = "time.google.com";
    //public String _ntpHost = "216.239.35.8";
    public String _ntpHost = "192.168.1.2";
    public TrueTimeRx withSharedPreferencesCache(Context context) {
        return this;
    }


    public TrueTimeRx withConnectionTimeout(int timeout) {
        super.withConnectionTimeout(timeout);
        return this;
    }

    public TrueTimeRx withRootDelayMax(float rootDelay) {
        super.withRootDelayMax(rootDelay);
        return this;
    }

    public TrueTimeRx withRootDispersionMax(float rootDispersion) {
        super.withRootDispersionMax(rootDispersion);
        return this;
    }

    public TrueTimeRx withServerResponseDelayMax(int serverResponseDelayInMillis) {
        super.withServerResponseDelayMax(serverResponseDelayInMillis);
        return this;
    }

    public TrueTimeRx withLoggingEnabled(boolean isLoggingEnabled) {
        super.withLoggingEnabled(isLoggingEnabled);
        return this;
    }

    public TrueTimeRx withRetryCount(int retryCount) {
        _retryCount = retryCount;
        return this;
    }

    /**
     * @return Date object that returns the current time in the default Timezone
     */
    public Date now() {
//        if (!isInitialized()) {
//            throw new IllegalStateException("You need to call init() on TrueTime at least once.");
//        }
        Date ret = null;
        try {
            //initializeRx(_ntpPoolAddress);
            SNTP_CLIENT.requestTime(_ntpHost,
                                    _rootDelayMax,
                                    _rootDispersionMax,
                                    _serverResponseDelayMax,
                                    _udpSocketTimeoutInMillis);
            long cachedSntpTime = _getCachedSntpTime();
            long cachedDeviceUptime = _getCachedDeviceUptime();
            long deviceUptime = SystemClock.elapsedRealtime();
            long now = cachedSntpTime + (deviceUptime - cachedDeviceUptime);
            ret = new Date(now);
        } catch (Exception e) {
            e.printStackTrace();

        }
        return ret;
    }

    /**
     * Initialize TrueTime
     * See {@link #initializeNtp(String)} for details on working
     *
     * @return accurate NTP Date
     */
    public Single<Date> initializeRx(String ntpPoolAddress) {
        _ntpPoolAddress = ntpPoolAddress;
        Log.d(TAG, "initializeRx: " + ntpPoolAddress);

        return initializeNtp(ntpPoolAddress).map(new Function<long[], Date>() {
                    @Override
                    public Date apply(long[] longs) throws Exception {
                        return now();
                    }
                });
     }

    /**
     * Initialize TrueTime
     * A single NTP pool server is provided.
     * Using DNS we resolve that to multiple IP hosts (See {@link #initializeNtp(List)} for manually resolved IPs)
     *
     * Use this instead of {@link #initializeRx(String)} if you wish to also get additional info for
     * instrumentation/tracking actual NTP response data
     *
     * @param ntpPool NTP pool server e.g. time.apple.com, 0.us.pool.ntp.org
     * @return Observable of detailed long[] containing most important parts of the actual NTP response
     * See RESPONSE_INDEX_ prefixes in {@link SntpClient} for details
     */
    public Single<long[]> initializeNtp(String ntpPool) {
        Log.i(TAG, "initializeNtp......" + ntpPool);
        return Flowable
              .just(ntpPool)
              .compose(resolveNtpPoolToIpAddresses())
              .compose(performNtpAlgorithm())
              .firstOrError();
    }

    /**
     * Initialize TrueTime
     * Use this if you want to resolve the NTP Pool address to individual IPs yourself
     *
     * See https://github.com/instacart/truetime-android/issues/42
     * to understand why you may want to do something like this.
     *
     * @param resolvedNtpAddresses list of resolved IP addresses for an NTP
     * @return Observable of detailed long[] containing most important parts of the actual NTP response
     * See RESPONSE_INDEX_ prefixes in {@link SntpClient} for details
     */
//    public Single<long[]> initializeNtp(List<InetAddress> resolvedNtpAddresses) {
//        return Flowable.fromIterable(resolvedNtpAddresses)
//               .compose(performNtpAlgorithm())
//               .firstOrError();
//    }

    /**
     * Transformer that takes in a pool of NTP addresses
     * Against each IP host we issue a UDP call and retrieve the best response using the NTP algorithm
     */
    private FlowableTransformer<InetAddress, long[]> performNtpAlgorithm() {
        return new FlowableTransformer<InetAddress, long[]>() {
            @Override
            public Flowable<long[]> apply(Flowable<InetAddress> inetAddressObservable) {
                return inetAddressObservable
                      .map(new Function<InetAddress, String>() {
                          @Override
                          public String apply(InetAddress inetAddress) {
                              return inetAddress.getHostAddress();
                          }
                      })
                      .flatMap(bestResponseAgainstSingleIp(5))  // get best response from querying the ip 5 times
                      .take(5)                                  // take 5 of the best results
                      .toList()
                      .toFlowable()
                      .filter(new Predicate<List<long[]>>() {
                          @Override
                          public boolean test(List<long[]> longs) throws Exception {
                              return longs.size() > 0;
                          }
                      })
                      .map(filterMedianResponse())
                      .doOnNext(new Consumer<long[]>() {
                          @Override
                          public void accept(long[] ntpResponse) {
                              cacheTrueTimeInfo(ntpResponse);
                          }
                      });
            }
        };
    }

    private FlowableTransformer<String, InetAddress> resolveNtpPoolToIpAddresses() {
        return new FlowableTransformer<String, InetAddress>() {
            @Override
            public Publisher<InetAddress> apply(Flowable<String> ntpPoolFlowable) {
                return ntpPoolFlowable
                      .observeOn(Schedulers.io())
                      .flatMap(new Function<String, Flowable<InetAddress>>() {
                          @Override
                          public Flowable<InetAddress> apply(String ntpPoolAddress) {
                              try {
                                  Log.d(TAG, "---- resolving ntpHost : " + ntpPoolAddress);
                                  return Flowable.fromArray(InetAddress.getAllByName(ntpPoolAddress));
                              } catch (UnknownHostException e) {
                                  return Flowable.error(e);
                              }
                          }
                      });
            }
        };
    }

    private Function<String, Flowable<long[]>> bestResponseAgainstSingleIp(final int repeatCount) {
        return new Function<String, Flowable<long[]>>() {
            @Override
            public Flowable<long[]> apply(String singleIp) {
                return Flowable
                      .just(singleIp)
                      .repeat(repeatCount)
                      .flatMap(new Function<String, Flowable<long[]>>() {
                          @Override
                          public Flowable<long[]> apply(final String singleIpHostAddress) {
                              return Flowable.create(new FlowableOnSubscribe<long[]>() {
                                      @Override
                                      public void subscribe(@NonNull FlowableEmitter<long[]> o)
                                          throws Exception {

                                          Log.d(TAG,
                                              "---- requestTime from: " + singleIpHostAddress);
                                          try {
                                              o.onNext(requestTime(singleIpHostAddress));
                                              o.onComplete();
                                          } catch (IOException e) {
                                              o.tryOnError(e);
                                          }
                                      }
                                  }, BackpressureStrategy.BUFFER)
                                      .subscribeOn(Schedulers.io())
                                    .doOnError(new Consumer<Throwable>() {
                                        @Override
                                        public void accept(Throwable throwable) {
                                            Log.e(TAG, "---- Error requesting time", throwable);
                                        }
                                    })
                                    .retry(_retryCount);
                          }
                      })
                      .toList()
                      .toFlowable()
                      .map(filterLeastRoundTripDelay()); // pick best response for each ip
            }
        };
    }

    private Function<List<long[]>, long[]> filterLeastRoundTripDelay() {
        return new Function<List<long[]>, long[]>() {
            @Override
            public long[] apply(List<long[]> responseTimeList) {
                Collections.sort(responseTimeList, new Comparator<long[]>() {
                    @Override
                    public int compare(long[] lhsParam, long[] rhsLongParam) {
                        long lhs = SntpClient.getRoundTripDelay(lhsParam);
                        long rhs = SntpClient.getRoundTripDelay(rhsLongParam);
                        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
                    }
                });

                Log.d(TAG, "---- filterLeastRoundTrip: " + responseTimeList);

                return responseTimeList.get(0);
            }
        };
    }

    private Function<List<long[]>, long[]> filterMedianResponse() {
        return new Function<List<long[]>, long[]>() {
            @Override
            public long[] apply(List<long[]> bestResponses) {
                Collections.sort(bestResponses, new Comparator<long[]>() {
                    @Override
                    public int compare(long[] lhsParam, long[] rhsParam) {
                        long lhs = SntpClient.getClockOffset(lhsParam);
                        long rhs = SntpClient.getClockOffset(rhsParam);
                        return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
                    }
                });

                Log.d(TAG, "---- bestResponse: " + Arrays.toString(bestResponses.get(bestResponses.size() / 2)));

                return bestResponses.get(bestResponses.size() / 2);
            }
        };
    }
}
