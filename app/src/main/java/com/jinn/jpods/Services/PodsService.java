package com.jinn.jpods.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanFilter.Builder;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jinn.jpods.util.Advertising;
import com.jinn.jpods.BuildConfig;
import com.jinn.jpods.Config;
import com.jinn.jpods.util.HashDevice;
import com.jinn.jpods.Activities.MainActivity;
import com.jinn.jpods.R;
import com.jinn.jpods.util.SimulateBattery;
import com.jinn.jpods.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is the class that does most of the work. It has 3 functions:
 * - Detect when AirPods are detected
 * - Receive beacons from AirPods and decode them (easier said than done thanks to google's autism)
 * - Display the notification with the status
 */
public class PodsService extends Service {
    private static final boolean ENABLE_LOGGING = BuildConfig.DEBUG; //Log is only displayed if this is a debug build, not release
    private static final String TAG = "AirPods";

    public static BluetoothAdapter ba;
    public static BluetoothDevice device;
    private static BluetoothLeScanner btScanner;
    public static final String MODEL_AIRPODS_NORMAL = "airpods", MODEL_AIRPODS_PRO = "airpodspro";

    private static ArrayList<ScanResult> recentBeacons = new ArrayList<>();
    private static final long RECENT_BEACONS_MAX_T_NS = 10000000000L; //10s
    private ScanCallback leScanCallback;

    private BroadcastReceiver btReceiver = null, screenReceiver = null, firstConnectReceiver = null, bondReceiver= null;

    private static SharedPreferences prefs, defPrefs;
    private static SharedPreferences.Editor sEditor, dpEditor;

    public PodsService() { }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(Config.APP_PREFERENCES, MODE_PRIVATE);
        sEditor = prefs.edit();
        defPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        dpEditor = defPrefs.edit();

        receiverFirstConnectedPods();
        receiverRunAppCheckConnectedPods();
        runScreenReceiver();
        pairingReceiver();

        //this BT Profile Proxy allows us to know if airpods are already connected when the app is started. It also fires an event when BT is turned off, in case the BroadcastReceiver doesn't do its job
        ba = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (!ba.isEnabled()) ba.enable();
        ba.getProfileProxy(
                getApplicationContext(),
                new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int i, BluetoothProfile bluetoothProfile) {
                        if (i == BluetoothProfile.HEADSET) {
                            if (ENABLE_LOGGING) Log.d("432", "BT PROXY SERVICE CONNECTED");
                            BluetoothHeadset h = (BluetoothHeadset) bluetoothProfile;
                            for (BluetoothDevice d : h.getConnectedDevices()) {
                                if (checkUUID(d)) {
                                    if (ENABLE_LOGGING) Log.d("436", "BT PROXY: AIRPODS ALREADY CONNECTED");
                                    ConnectPods(d);
                                    break;
                                }
                            }
                        }
                    }
                    @Override
                    public void onServiceDisconnected(int i) {
                        if (i == BluetoothProfile.HEADSET) {
                            if (ENABLE_LOGGING) Log.d("448", "BT PROXY SERVICE DISCONNECTED ");
                            Config.deviceConnect = false;
                        }
                    }
                },
                BluetoothProfile.HEADSET
        );
        if (ba.isDiscovering()) ba.cancelDiscovery();
        if (ba.isEnabled()) startAirPodsScanner(); //if BT is already on when the app is started, start the scanner without waiting for an event to happen
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override // Запускається
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationThread n = null;
        if (n == null || !n.isAlive()) {
            n = new NotificationThread();
            n.start();
        }
        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (firstConnectReceiver != null) unregisterReceiver(firstConnectReceiver);
        if (btReceiver != null) unregisterReceiver(btReceiver);
        if (bondReceiver != null) unregisterReceiver(bondReceiver);
        if (screenReceiver != null) unregisterReceiver(screenReceiver);
        if(Config.deviceConnect) saveDataDisconnected();

        if(defPrefs.getBoolean("AutoBluetooth", true) && ba.isEnabled()) ba.disable();
    }

    public static ArrayList<String> lastData = new ArrayList<String>();
    private void startAirPodsScanner() {
        try {
            if (ENABLE_LOGGING) Log.d("92", "START SCANNER");
            BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter btAdapter = btManager.getAdapter();

            btScanner = btAdapter.getBluetoothLeScanner();
            if (btAdapter == null) throw new Exception("No BT");
            if (!btAdapter.isEnabled()) throw new Exception("BT Off");

            leScanCallback = new ScanCallback() {
                @Override
                public void onBatchScanResults(List<ScanResult> scanResults) {
                    if (ENABLE_LOGGING) Log.d(TAG, String.valueOf("dsa"));
                    for (ScanResult result : scanResults) onScanResult(-1, result);
                    super.onBatchScanResults(scanResults);
                }
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    try {
                        byte[] data = result.getScanRecord().getManufacturerSpecificData(76);
                        if (ENABLE_LOGGING) Log.d(TAG, String.valueOf(data.length));

                        if(data.length == 17) {
                            Config.maybeConnected = false;
                            Config.deviceFound = false;
                            closePop_Up();
                            closePop_Up_Connected();
                        }
                        if (data == null||data.length!=27) return;

                        recentBeacons.add(result);
                        ScanResult strongestBeacon = null;
                        for (int i = 0; i < recentBeacons.size(); i++) {
                            if (SystemClock.elapsedRealtimeNanos() - recentBeacons.get(i).getTimestampNanos() > RECENT_BEACONS_MAX_T_NS) {
                                recentBeacons.remove(i--);
                                continue;
                            }
                            if (strongestBeacon == null || strongestBeacon.getRssi() < recentBeacons.get(i).getRssi())
                                strongestBeacon = recentBeacons.get(i);
                        }
                        if (strongestBeacon != null && strongestBeacon.getDevice().getAddress().equals(result.getDevice().getAddress()))
                            strongestBeacon = result;
                        result = strongestBeacon;
//                        if (result.getRssi() < -40) return;

                        String a = decodeHex(result.getScanRecord().getManufacturerSpecificData(76));
                        ArrayList<String> byteStrTwo = new ArrayList<String>();
                        for(int i=0;i<a.length()-1;i=i+2) byteStrTwo.add(a.substring(i, i+2));

                        String listString = "";
                        for (String s : byteStrTwo) {
                            listString += s + "\t";
                        }
                        listString = "\n" + listString + "|\tleft:"+ hex_to_decimal(byteStrTwo.get(12))
                                + "\tright:"+ hex_to_decimal(byteStrTwo.get(13))
                                + "\tcase:"+ hex_to_decimal(byteStrTwo.get(14));

                        boolean write = true;
                        for(int i = 0; i < lastData.size(); i++){
                            if(lastData.get(i).equals(listString)) write = false;
                        }
                        if(write) lastData.add(listString);
                        if (ENABLE_LOGGING) Log.d("lastData", String.valueOf(lastData));

                        String str = byteStrTwo.get(13);
                        Config.leftStatus = Integer.parseInt(str, 16);
                        if( Integer.parseInt(str, 16) > 100 ){
                            Config.leftStatus = Integer.parseInt(str, 16) - 128;
                            Config.chargeL = true;
                        }else Config.chargeL = false;
                        String str2 = byteStrTwo.get(12);
                        Config.rightStatus = Integer.parseInt(str2, 16);
                        if( Integer.parseInt(str2, 16) > 100 ){
                            Config.rightStatus = Integer.parseInt(str2, 16) - 128;
                            Config.chargeR = true;
                        }else Config.chargeR = false;

                        String str3 = byteStrTwo.get(14); //case (0-10 batt; 15=disconnected)
                        if(Integer.parseInt(str3, 16) <= 100){
                            Config.caseStatus = Integer.parseInt(str3, 16);
                        }

                        String str4 = byteStrTwo.get(14); //charge status (bit 0=left; bit 1=right; bit 2=case)
//                        if (ENABLE_LOGGING) Log.d("15-s", Integer.toBinaryString(hex_to_decimal(str4)));

                        if (byteStrTwo.get(3).charAt(1) == 'E') Config.model = MODEL_AIRPODS_PRO;
                        else Config.model = MODEL_AIRPODS_NORMAL; //detect if these are AirPods pro or regular ones

//                        if (ENABLE_LOGGING)  Log.d(TAG, String.valueOf(Config.deviceFound));
                        Config.lastBLEdataAds = System.currentTimeMillis();
                        if(!Config.deviceFound){
                            Config.deviceFound = true;
                            maybeConnectPods(result.getDevice());
                            maybeConnectedPods(result.getDevice());
                        }
                    } catch (Throwable t) {
                        if (ENABLE_LOGGING) Log.d("176", "" + t);
                    }
                }
                @Override
                public void onScanFailed(int errorCode) {
                    if (ENABLE_LOGGING) Log.d("130", "onScanFailed");
                }

            };

            List<ScanFilter> filters = null;
            String scanMode = defPrefs.getString("scanMode", "1");
            int ScanModeInt = Integer.parseInt(scanMode);
            if (ENABLE_LOGGING) Log.d("ScanMode", String.valueOf(ScanModeInt));
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanModeInt)
                    .setReportDelay(0)
                    .build();

            btScanner.startScan(filters, settings, leScanCallback);
        } catch (Throwable t) {
            if (ENABLE_LOGGING) Log.d("181", "" + t);
        }
    }
    private void stopAirPodsScanner() {
        try {
            if (btScanner != null) {
                if (ENABLE_LOGGING) Log.d("203", "STOP SCANNER");
                btScanner.stopScan(leScanCallback);
            }
            Config.leftStatus = 0; Config.rightStatus = 0; Config.caseStatus = 0;
        } catch (Throwable t) { }
    }

    public void pairingReceiver() {
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(bondReceiver,intentFilter);
        bondReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if(BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                    BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    closePop_Up_Connected();
                    if (ENABLE_LOGGING) Log.e(TAG,"pin entered and request sent...");
                }
            }
        };
        try { registerReceiver(bondReceiver, intentFilter); } catch (Throwable t) { }
    }
    public void receiverFirstConnectedPods() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.bluetooth.device.action.FOUND"); // BluetoothDevice.ACTION_FOUND
        intentFilter.addAction("android.bluetooth.adapter.action.DISCOVERY_FINISHED"); // BluetoothAdapter.ACTION_DISCOVERY_FINISHED
        firstConnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(BluetoothDevice.ACTION_FOUND)){
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    maybeConnectedPods(device);
                    if (ENABLE_LOGGING) Log.d(TAG, device.getName() + " - " + device.getAddress());
                }
            }
        };

        try { registerReceiver(firstConnectReceiver, intentFilter); } catch (Throwable t) { }
    }
    public void receiverRunAppCheckConnectedPods() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        intentFilter.addAction("android.bluetooth.device.action.ACL_DISCONNECTED");
        intentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED");
        intentFilter.addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED");
        intentFilter.addAction("android.bluetooth.device.action.NAME_CHANGED");
        intentFilter.addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED");
        intentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        intentFilter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        intentFilter.addAction("android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT");
        intentFilter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        intentFilter.addAction("android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED");
        intentFilter.addCategory("android.bluetooth.headset.intent.category.companyid.76");
        btReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                BluetoothDevice bluetoothDevice = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
                String action = intent.getAction();
//                if (ENABLE_LOGGING) Log.d("411", String.valueOf(action));
//                if (ENABLE_LOGGING) Log.d("411", String.valueOf(bluetoothDevice));
                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) { //bluetooth turned off, stop scanner and remove notification
                        if (ENABLE_LOGGING) Log.d("399", "BT OFF");
                        Config.deviceConnect = false;
                        closePop_Up();
                        stopAirPodsScanner();
                        recentBeacons.clear();
                    }
                    if (state == BluetoothAdapter.STATE_ON) { //bluetooth turned on, start/restart scanner
                        if (ENABLE_LOGGING) Log.d("405", "BT ON");
                        startAirPodsScanner();
                    }
                }

                if (bluetoothDevice != null && action != null && !action.isEmpty() && checkUUID(bluetoothDevice)) { //airpods filter
                    if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) { //airpods connected, show notification
                        if (ENABLE_LOGGING) Log.d("411", "ACL CONNECTED");
                        ConnectPods(bluetoothDevice);
                    }
                    if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED) || action.equals(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)) { //airpods disconnected, remove notification but leave the scanner going
                        if (ENABLE_LOGGING) Log.d("416", "ACL DISCONNECTED");
                        closePop_Up();
                        Config.popUpIsOpen = false;
                        Config.deviceConnect = false;
                        recentBeacons.clear();
                    }
                    if(action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)){
                        if(Config.maybeConnected) ConnectPods(bluetoothDevice);
                    }
                }

                if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                    if (ENABLE_LOGGING) Log.d(TAG, String.valueOf(bluetoothDevice.getBondState()));
                    if(bluetoothDevice.getBondState() == 10){
                        PopUpConnectService.failedConnect = true;
                        Config.deviceConnect = false;
                        Config.maybeConnected = false;
                    }else if(bluetoothDevice.getBondState() == 12){
                        closePop_Up_Connected();
                        setSubNameDevice(device.getName());
                    }
                }

                 if (action.equals(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)) {
                    int state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_NOT_PLAYING);
                    if (ENABLE_LOGGING) Log.d("STATE_PLAYING", String.valueOf(state));
                    if (state == BluetoothA2dp.STATE_PLAYING) Config.audioPlaying = true;
                    else Config.audioPlaying = false;
                }
//                 if (action.equals(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)) {
//                    int state = intent.getIntExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS,
//                            );
//
//                }
            }
        };

        try { registerReceiver(btReceiver, intentFilter); } catch (Throwable t) { }
    }
    public void runScreenReceiver() {
        if (prefs.getBoolean("batterySaver", false)) {
            IntentFilter screenIntentFilter = new IntentFilter();
            screenIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
            screenIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
            screenReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() == Intent.ACTION_SCREEN_OFF) {
                        if (ENABLE_LOGGING) Log.d("468", "SCREEN OFF");
                        stopAirPodsScanner();
                    } else if (intent.getAction() == Intent.ACTION_SCREEN_ON) {
                        if (ENABLE_LOGGING) Log.d("471", "SCREEN ON");
                        BluetoothAdapter ba = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                        if (ba.isEnabled()) startAirPodsScanner();
                    }
                }
            };
            try { registerReceiver(screenReceiver, screenIntentFilter); } catch (Throwable t) { }
        }
    }

    private void maybeConnectedPods(BluetoothDevice bluetoothDevice) {
        if(bluetoothDevice.getBondState() == 10 && !Config.maybeConnected && !Config.deviceConnect){
            device = bluetoothDevice;
            Config.name = "Ваші AirPods?";
            openPop_Up_Connected();
            Config.maybeConnected = true;
        }
    }
    private void maybeConnectPods(BluetoothDevice bluetoothDevice) {
        if(bluetoothDevice.getBondState() == 12){
            device = bluetoothDevice;
            getSubNameDevice();
            openPop_Up();
        }
    }

    private void getSubNameDevice(){
            Config.name = defPrefs.getString("nameDevice", "AirPods");
    }
    private void setSubNameDevice(String name){
        if(name == null) name = "AirPods";
        dpEditor.putString("nameDevice", name);
        Config.name = name;
        dpEditor.commit();
    }

    private void ConnectPods(BluetoothDevice bluetoothDevice) {
        if(!Config.deviceConnect){
            device = bluetoothDevice;
            HashDevice.list.put(device.getAddress(), new HashDevice.Device());

            getSubNameDevice();

            Config.deviceConnect = true;

            HashDevice.list.get(device.getAddress()).setLastSeenConnected(System.currentTimeMillis());
            loadDataDisconnected();

            SimulateBattery.correctionWhenConnecting = true;

            ba.cancelDiscovery();

            openPop_Up();
        }
    }

    private void saveDataDisconnected() {
        long lastDisConnected = System.currentTimeMillis();
        HashDevice.list.get(device.getAddress()).setLastDisConnected(lastDisConnected);
        HashDevice.list.get(device.getAddress()).setLastK(Config.lastK);

        Gson gson = new Gson();
        String json = gson.toJson(HashDevice.list);
        sEditor.putString(Config.APP_PREFERENCES_DEVICES, json);
        sEditor.commit();

        Config.maybeConnected = false;
    }
    private void loadDataDisconnected() {
        if (prefs.contains(Config.APP_PREFERENCES_DEVICES)){
            String json = prefs.getString(Config.APP_PREFERENCES_DEVICES, "");
            Gson gson = new Gson();
            java.lang.reflect.Type type = new TypeToken<HashMap<String, HashDevice.Device>>(){}.getType();
            HashMap<String, HashDevice.Device> HashMap2 = gson.fromJson(json, type);
            for(Map.Entry<String, HashDevice.Device> entry : HashMap2.entrySet()){
                if(entry.getKey().equals(device.getAddress())){
                    HashDevice.list.get(device.getAddress())
                        .setLastDisConnected(entry.getValue().getLastDisConnected());
                    HashDevice.list.get(device.getAddress())
                            .setLastK(entry.getValue().getLastK());
                }
            }
        }
    }

    private void openPop_Up_Connected() {
        if(!Config.popUpIsOpen) {
            Intent svc = new Intent(this, PopUpConnectService.class);
            startService(svc);
        }
    }
    private void closePop_Up_Connected() {
        if(Config.popUpIsOpen) {
            Intent svc = new Intent(this, PopUpConnectService.class);
            stopService(svc);
        }
    }

    private void openPop_Up() {
        if (ENABLE_LOGGING)  Log.d("openPop_UpIsOpen", String.valueOf(Config.popUpIsOpen));
        if(!Config.popUpIsOpen) {
            Intent svc = new Intent(this, PopUpService.class);
            stopService(svc);
            startService(svc);
        }
    }
    private void closePop_Up() {
        if(Config.popUpIsOpen && !Config.popUpAttach) {
            Intent svc = new Intent(this, PopUpService.class);
            stopService(svc);
        }
    }

/*    public static Bitmap drawTextToBitmap(Context mContext,  int resourceId,  String mText) {
        try {
            Resources resources = mContext.getResources();
            float scale = resources.getDisplayMetrics().density;
            Bitmap bitmap = BitmapFactory.decodeResource(resources, resourceId);
            android.graphics.Bitmap.Config bitmapConfig =   bitmap.getConfig();
            // set default bitmap config if none
            if(bitmapConfig == null) {
                bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;
            }
            // resource bitmaps are imutable,
            // so we need to convert it to mutable one
            bitmap = bitmap.copy(bitmapConfig, true);

            Canvas canvas = new Canvas(bitmap);
            // new antialised Paint
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            // text color - #3D3D3D
            paint.setColor(Color.rgb(110,110, 110));
            // text size in pixels
            paint.setTextSize((int) (12 * scale));
            // text shadow
            paint.setShadowLayer(1f, 0f, 1f, Color.DKGRAY);

            // draw text to the Canvas center
            Rect bounds = new Rect();
            paint.getTextBounds(mText, 0, mText.length(), bounds);
            int x = (bitmap.getWidth() - bounds.width())/6;
            int y = (bitmap.getHeight() + bounds.height())/5;

            canvas.drawText(mText, x * scale, y * scale, paint);

            return bitmap;
        } catch (Exception e) { return null; }
    }*/

    private class NotificationThread extends Thread {
        private int timeSleep = 10000;

        private boolean isLocationEnabled() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                LocationManager service = (LocationManager) getSystemService(getApplicationContext().LOCATION_SERVICE);
                return service != null && service.isLocationEnabled();
            } else {
                try {
                    return Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE) != Settings.Secure.LOCATION_MODE_OFF;
                } catch (Throwable t) {
                    return true;
                }
            }
        }

        private NotificationManager mNotifyManager;
        public NotificationThread() {
            mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { //on oreo and newer, create a notification channel
                NotificationChannel channel = new NotificationChannel(Config.APP_PREFERENCES, Config.APP_PREFERENCES, NotificationManager.IMPORTANCE_DEFAULT);
                channel.setVibrationPattern(new long[]{ 0 });
                channel.enableVibration(true);
                channel.enableLights(false);
                channel.setShowBadge(true);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                mNotifyManager.createNotificationChannel(channel);
            }
        }

        public void run() {
            boolean notificationShowing = false;
            for(;;) {
//                logToState();
                if(!Config.viewAdsNotify && System.currentTimeMillis() - Config.lastViewAds > Config.periodViewAds){
                    Config.viewAdsNotify = true;
                    new Advertising(getApplicationContext());
                }
                if(System.currentTimeMillis() - Config.lastBLEdataAds > Config.periodBLENo){
                    Config.maybeConnected = false;
                    Config.deviceFound = false;
                    if(!Config.deviceConnect){
                        closePop_Up();
                    }
                    closePop_Up_Connected();
                }

                RemoteViews notificationBig = new RemoteViews(getPackageName(), R.layout.status_big);
                RemoteViews notificationSmall = new RemoteViews(getPackageName(), R.layout.status_small);
                RemoteViews locationDisabledBig = new RemoteViews(getPackageName(), R.layout.location_disabled_big);
                RemoteViews locationDisabledSmall = new RemoteViews(getPackageName(), R.layout.location_disabled_small);
                PendingIntent resultPendingIntent = PendingIntent.getActivity(
                        PodsService.this, 0,
                        new Intent(PodsService.this, MainActivity.class), 0);

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(PodsService.this, Config.APP_PREFERENCES)
                        .setShowWhen(false)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentIntent(resultPendingIntent);
                mBuilder.setPublicVersion(mBuilder.build());


                if (isLocationEnabled() || Build.VERSION.SDK_INT >= 29) { //apparently this restriction was removed in android Q
                    mBuilder.setCustomContentView(notificationSmall);
                    mBuilder.setCustomBigContentView(notificationBig);
                } else {
                    mBuilder.setCustomContentView(locationDisabledSmall);
                    mBuilder.setCustomBigContentView(locationDisabledBig);
                }

                notificationBig.setViewVisibility(R.id.leftPodText, View.VISIBLE);
                notificationBig.setViewVisibility(R.id.rightPodText, View.VISIBLE);
                notificationBig.setViewVisibility(R.id.podCaseText, View.VISIBLE);
                notificationBig.setViewVisibility(R.id.leftPodUpdating, View.INVISIBLE);
                notificationBig.setViewVisibility(R.id.rightPodUpdating, View.INVISIBLE);
                notificationBig.setViewVisibility(R.id.podCaseUpdating, View.INVISIBLE);

                notificationSmall.setViewVisibility(R.id.leftPodText, View.VISIBLE);
                notificationSmall.setViewVisibility(R.id.rightPodText, View.VISIBLE);
                notificationSmall.setViewVisibility(R.id.podCaseText, View.VISIBLE);
                notificationSmall.setViewVisibility(R.id.leftPodUpdating, View.INVISIBLE);
                notificationSmall.setViewVisibility(R.id.rightPodUpdating, View.INVISIBLE);
                notificationSmall.setViewVisibility(R.id.podCaseUpdating, View.INVISIBLE);

                Config.leftStatusF = Config.leftStatus;
                Config.rightStatusF = Config.rightStatus;
                Config.caseStatusF = Config.caseStatus;
                if (Config.deviceConnect) {
//                    Bitmap bmp = drawTextToBitmap(getApplicationContext(),R.drawable.battery_dead_o,"100");
//                    Icon newIcon = Icon.createWithBitmap(bmp);
                    mBuilder.setSmallIcon(R.mipmap.notification_icon);

                    if(Config.leftStatus == 0 && Config.rightStatus == 0 && Config.caseStatus == 0){
                        HashDevice.list.get(device.getAddress()).setK(SimulateBattery.getStatusFull(timeSleep));
                        Config.lastK = HashDevice.list.get(device.getAddress()).getK();
                        Config.leftStatusF = Util.minMax(100 - Config.lastK);
                        Config.rightStatusF = Util.minMax(100 - Config.lastK);
                        Config.caseStatusF = 100;
                    }else{
                        HashDevice.list.get(device.getAddress()).setK(SimulateBattery.getStatus(timeSleep));
                        Config.lastK = HashDevice.list.get(device.getAddress()).getK();
                        Config.leftStatusF = Util.minMax(Util.minMax(Config.leftStatus + 5) - Config.lastK);
                        Config.rightStatusF = Util.minMax(Util.minMax(Config.rightStatus + 5) - Config.lastK);
                        Config.caseStatusF = Util.minMax(Config.caseStatus + 5);
                    }
                    notificationBig.setTextViewText(R.id.leftPodText, Config.leftStatusF + "%");
                    notificationBig.setTextViewText(R.id.rightPodText, Config.rightStatusF + "%");
                    notificationBig.setTextViewText(R.id.podCaseText, Config.caseStatusF + "%");
                    notificationSmall.setTextViewText(R.id.leftPodText, Config.leftStatusF + "%");
                    notificationSmall.setTextViewText(R.id.rightPodText, Config.rightStatusF + "%");
                    notificationSmall.setTextViewText(R.id.podCaseText, Config.caseStatusF + "%");

                    notificationBig.setViewVisibility(R.id.status_small, View.VISIBLE);
                    notificationBig.setViewVisibility(R.id.status_big, View.VISIBLE);
                } else {
                    mBuilder.setSmallIcon(R.drawable.ic_skylight_notification);

                    notificationBig.setTextViewText(R.id.leftPodText, "");
                    notificationBig.setTextViewText(R.id.rightPodText, "");
                    notificationBig.setTextViewText(R.id.podCaseText, "");
                    notificationSmall.setTextViewText(R.id.leftPodText, "");
                    notificationSmall.setTextViewText(R.id.rightPodText, "");
                    notificationSmall.setTextViewText(R.id.podCaseText, "");
                }

                if (Config.model.equals(MODEL_AIRPODS_NORMAL)) {
                    notificationBig.setImageViewResource(R.id.leftPodImg, Config.leftStatusF <= 100 ? R.drawable.left_pod : R.drawable.left_pod_disconnected);
                    notificationBig.setImageViewResource(R.id.rightPodImg, Config.rightStatusF <= 100 ? R.drawable.right_pod : R.drawable.right_pod_disconnected);
                    notificationBig.setImageViewResource(R.id.podCaseImg, Config.caseStatusF <= 100 ? R.drawable.pod_case : R.drawable.pod_case_disconnected);
                    notificationSmall.setImageViewResource(R.id.leftPodImg, Config.leftStatusF <= 100 ? R.drawable.left_pod : R.drawable.left_pod_disconnected);
                    notificationSmall.setImageViewResource(R.id.rightPodImg, Config.rightStatusF <= 100 ? R.drawable.right_pod : R.drawable.right_pod_disconnected);
                    notificationSmall.setImageViewResource(R.id.podCaseImg, Config.caseStatusF <= 100 ? R.drawable.pod_case : R.drawable.pod_case_disconnected);
                }
                else if (Config.model.equals(MODEL_AIRPODS_PRO)) {
                    notificationBig.setImageViewResource(R.id.leftPodImg, Config.leftStatusF <= 100 ? R.drawable.left_podpro : R.drawable.left_podpro_disconnected);
                    notificationBig.setImageViewResource(R.id.rightPodImg, Config.rightStatusF <= 100 ? R.drawable.right_podpro : R.drawable.right_podpro_disconnected);
                    notificationBig.setImageViewResource(R.id.podCaseImg, Config.caseStatusF <= 100 ? R.drawable.podpro_case : R.drawable.podpro_case_disconnected);
                    notificationSmall.setImageViewResource(R.id.leftPodImg, Config.leftStatusF <= 100 ? R.drawable.left_podpro : R.drawable.left_podpro_disconnected);
                    notificationSmall.setImageViewResource(R.id.rightPodImg, Config.rightStatusF <= 100 ? R.drawable.right_podpro : R.drawable.right_podpro_disconnected);
                    notificationSmall.setImageViewResource(R.id.podCaseImg, Config.caseStatusF <= 100 ? R.drawable.podpro_case : R.drawable.podpro_case_disconnected);
                }
                startForeground(1, mBuilder.build());
//                mNotifyManager.notify(1, mBuilder.build());
                if(Config.serviceDisable){
                    if (ENABLE_LOGGING) Log.d(TAG, "Stop");
                    if (ba.isDiscovering()) { ba.cancelDiscovery(); }
                    stopForeground(false);
                    stopSelf();
                    stopAirPodsScanner();
                    return;
                }
                try { Thread.sleep(timeSleep); } catch (InterruptedException e) { }
            }

        }
    }

    // Util private
    private List<ScanFilter> getScanFilters() {
        byte[] manufacturerData = new byte[27];
        byte[] manufacturerDataMask = new byte[27];

        manufacturerData[0] = 7;
        manufacturerData[1] = 25;

        manufacturerDataMask[0] = -1;
        manufacturerDataMask[1] = -1;

        Builder builder = new Builder();
        builder.setManufacturerData(76, manufacturerData, manufacturerDataMask);
        return Collections.singletonList(builder.build());
    }
    private static int hex_to_decimal(String s) {
        String digits = "0123456789ABCDEF";
        s = s.toUpperCase();
        int val = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int d = digits.indexOf(c);
            val = 16*val + d;
        }
        return val;
    }
    private String decodeHex(byte[] bArr) {
        final char[] hexCharset = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] ret = new char[bArr.length * 2];
        for (int i = 0; i < bArr.length; i++) {
            int b = bArr[i] & 0xFF;
            ret[i * 2] = hexCharset[b >>> 4];
            ret[i * 2 + 1] = hexCharset[b & 0x0F];
        }
        return new String(ret);
    }
    private boolean checkUUID(BluetoothDevice bluetoothDevice) {
        ParcelUuid[] AIRPODS_UUIDS = {
                ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a"),
                ParcelUuid.fromString("2a72e02b-7b99-778f-014d-ad0b7221ec74"),
                ParcelUuid.fromString("0000111e-0000-1000-8000-00805f9b34fb"),
        };
        ParcelUuid[] uuids = bluetoothDevice.getUuids();
        if (uuids == null) return false;
        for (ParcelUuid u : uuids) {
            for (ParcelUuid v : AIRPODS_UUIDS) {
                if (u.equals(v)) return true;
            }
        }
        return false;
    }
    private void logToState() {
        Log.d("State",
                "maybeConnected: " + Config.maybeConnected +
                " maybeConnected: " + Config.maybeConnected +
                " deviceConnect: " + Config.deviceConnect +
                " deviceFound: " + Config.deviceFound +
                " audioPlaying: " + Config.audioPlaying +
                " serviceDisable: " + Config.serviceDisable +
                " popUpIsOpen: " + Config.popUpIsOpen +
                " lastBLEdataAds: " + Config.lastBLEdataAds

        );
    }

}
