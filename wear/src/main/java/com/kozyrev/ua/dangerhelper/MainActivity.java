package com.kozyrev.ua.dangerhelper;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.kozyrev.ua.dangerhelper.fragments.CounterFragment;
import com.kozyrev.ua.dangerhelper.fragments.SettingsFragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity
        implements SensorEventListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener, MessageApi.MessageListener,
        NodeApi.NodeListener {

    public static void LOGD(final String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

    private static final String TAG = "DangerHelper";

    /**
     * How long to keep the screen on when no activity is happening *
     */
    private static final long SCREEN_ON_TIMEOUT_MS = 100000; // in milliseconds

    /**
     * an up-down movement that takes more than this will not be registered as such *
     */
    private static final long TIME_THRESHOLD_NS_TWO = 2000000000; // in nanoseconds (= 2sec)
    private static final long TIME_THRESHOLD_NS_ONE = 1000000000; // in nanoseconds (= 1sec)

    /**
     * was found in empiric method)
     */
    private static final float WRIST_MOVE_THRESHOLD = 7.0f;

    private SensorManager mSensorManager;
    private Sensor mSensorGyro;
    private long mLastTime = 0;
    private boolean mUp = false;
    private int mJumpCounter = 0;
    private ViewPager mPager;
    private CounterFragment mCounterPage;
    private SettingsFragment mSettingPage;
    private ImageView mSecondIndicator;
    private ImageView mFirstIndicator;
    private Timer mTimer;
    private TimerTask mTimerTask;

    private Timer pauseTimer;
    private TimerTask pauseTimerTask;

    private Timer countDownTimer;
    private TimerTask countDownTimerTask;

    private Handler mHandler;

    private long timeTresholdNano;

    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.jj_layout);
        setupViews();
        mHandler = new Handler();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renewTimer();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        timeTresholdNano = TIME_THRESHOLD_NS_ONE;

        //DATA LAYER
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

    }

    private void setupViews() {
        mPager = (ViewPager) findViewById(R.id.pager);
        mFirstIndicator = (ImageView) findViewById(R.id.indicator_0);
        mSecondIndicator = (ImageView) findViewById(R.id.indicator_1);
        final PagerAdapter adapter = new PagerAdapter(getFragmentManager());
        mCounterPage = new CounterFragment();
        mSettingPage = new SettingsFragment();
        adapter.addFragment(mCounterPage);
        adapter.addFragment(mSettingPage);
        setIndicator(0);
        mPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {
            }

            @Override
            public void onPageSelected(int i) {
                setIndicator(i);
                renewTimer();
            }

            @Override
            public void onPageScrollStateChanged(int i) {
            }
        });

        mPager.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSensorManager.registerListener(this, mSensorGyro,
                SensorManager.SENSOR_DELAY_FASTEST)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Successfully registered for the gyro sensor updates");
            }
        }

        //DATA LAYER
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Unregistered for sensor events");
        }

        //DATA LAYER
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        Wearable.NodeApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            detectWristMove(event.values[0], event.timestamp);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void detectWristMove(float xValue, long timestamp) {
        if ((Math.abs(xValue) > WRIST_MOVE_THRESHOLD) && checkingState) {
            Log.i("123", "x action detected");
            if (timestamp - mLastTime < timeTresholdNano) {//&& mUp != (xValue > 0)) {
                onWristMoveDetected(!mUp);

            }
            mUp = xValue > 0;
            mLastTime = timestamp;
        }
    }

    private void onWristMoveDetected(boolean up) {
        if (up) {
            //return;
        }
        renewPauseTimer();
        mJumpCounter++;
        startCountDownTimer(mJumpCounter);
        setCounter(mJumpCounter);
        if (mJumpCounter == 3) {
            timeTresholdNano = TIME_THRESHOLD_NS_TWO;
            Toast.makeText(this, "long", Toast.LENGTH_SHORT).show();
            mCounterPage.setCounter("S");
        } else if (mJumpCounter == 6) {
            Toast.makeText(this, "short", Toast.LENGTH_SHORT).show();
            mCounterPage.setCounter("S O");
            timeTresholdNano = TIME_THRESHOLD_NS_ONE;
        } else if (mJumpCounter == 9) {
            mCounterPage.setCounter("S O S!");
            Toast.makeText(this, "BINGO!!!", Toast.LENGTH_SHORT).show();
            requestSos();
        }

        renewTimer();
    }

    private void startCountDownTimer(final int tempCounter) {

        if (null != countDownTimer) {
            countDownTimer.cancel();
        }
        countDownTimerTask = new TimerTask() {
            @Override
            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (tempCounter == mJumpCounter) {
                            mJumpCounter = 0;
                            setCounter(0);
                        }
                    }
                });


            }
        };
        countDownTimer = new Timer();
        countDownTimer.schedule(countDownTimerTask, 4000);
    }

    /**
     * Updates the counter on UI, saves it to preferences and vibrates the watch when counter
     * reaches a multiple of 10.
     */
    private void setCounter(int i) {
        mCounterPage.setCounter(i);
        Utils.vibrate(this, 0);
    }

    public void resetCounter() {
        setCounter(0);
        mJumpCounter = 0;
        renewTimer();
    }

    private boolean checkingState = true;

    private void renewPauseTimer() {
        checkingState = false;
        Log.i("123", "checking false");
        pauseTimerTask = new TimerTask() {
            @Override
            public void run() {
                checkingState = true;
                Log.i("123", "checking true");

            }
        };
        pauseTimer = new Timer();
        pauseTimer.schedule(pauseTimerTask, 1000);
    }

    /**
     * Starts a timer to clear the flag FLAG_KEEP_SCREEN_ON.
     */
    private void renewTimer() {
        if (null != mTimer) {
            mTimer.cancel();
        }
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG,
                            "Removing the FLAG_KEEP_SCREEN_ON flag to allow going to background");
                }
                resetFlag();
            }
        };
        mTimer = new Timer();
        mTimer.schedule(mTimerTask, SCREEN_ON_TIMEOUT_MS);
    }

    /**
     * Resets the FLAG_KEEP_SCREEN_ON flag so activity can go into background.
     */
    private void resetFlag() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Resetting FLAG_KEEP_SCREEN_ON flag to allow going to background");
                }
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                finish();
            }
        });
    }

    /**
     * Sets the page indicator for the ViewPager.
     */
    private void setIndicator(int i) {
        switch (i) {
            case 0:
                mFirstIndicator.setImageResource(R.drawable.full_10);
                mSecondIndicator.setImageResource(R.drawable.empty_10);
                break;
            case 1:
                mFirstIndicator.setImageResource(R.drawable.empty_10);
                mSecondIndicator.setImageResource(R.drawable.full_10);
                break;
        }
    }

    //DATA LAYER PART

    @Override
    public void onConnected(Bundle connectionHint) {
        LOGD(TAG, "onConnected(): Successfully connected to Google API client");
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.NodeApi.addListener(mGoogleApiClient, this);


        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                setupSosCapability();
                return null;
            }
        }.execute();


    }

    @Override
    public void onConnectionSuspended(int cause) {
        LOGD(TAG, "onConnectionSuspended(): Connection to Google API client was suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(TAG, "onConnectionFailed(): Failed to connect, with result: " + result);
    }

    @Override
    public void onPeerConnected(Node node) {

    }

    @Override
    public void onPeerDisconnected(Node node) {

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        LOGD(TAG, "onDataChanged(): " + dataEvents);

        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                if (DataLayerListenerService.COUNT_PATH.equals(path)) {
                    LOGD(TAG, "Data Changed for COUNT_PATH");
//                    generateEvent("DataItem Changed", event.getDataItem().toString());
                } else {
                    LOGD(TAG, "Unrecognized path: " + path);
                }

            } else if (event.getType() == DataEvent.TYPE_DELETED) {
//                generateEvent("DataItem Deleted", event.getDataItem().toString());
            } else {
//                generateEvent("Unknown data event type", "Type = " + event.getType());
            }
        }
    }

    private void setupSosCapability() {
        nodesIds = new ArrayList<>(getNodes());
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<String>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }
        return results;
    }

    List<String> nodesIds;

    public static final String SOS_MESSAGE_PATH = "/sos_action";

    private void requestSos() {
        Log.i("123", "sending message");
        for (String sosNodeId : nodesIds) {
            Log.i("123", "sending message inside");
            Wearable.MessageApi.sendMessage(mGoogleApiClient, sosNodeId,
                    SOS_MESSAGE_PATH, new byte[0]).setResultCallback(
                    new ResultCallback() {
                        @Override
                        public void onResult(Result result) {
                            if (!result.getStatus().isSuccess()) {
                                // Failed to send message
                                Log.i("123", "failed to send message");
                            }
                        }
                    }
            );
        }
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        LOGD(TAG, "onMessageReceived: " + event);
    }

}