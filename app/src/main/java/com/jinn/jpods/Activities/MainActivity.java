package com.jinn.jpods.Activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.jinn.jpods.Config;
import com.jinn.jpods.R;
import com.jinn.jpods.Services.PopUpService;
import com.jinn.jpods.Services.Starter;
import com.jinn.jpods.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private final static int REQUEST_CODE = 10101;
    boolean viewServices;

    RelativeLayout relativeServices, services;
    ImageView apps, trueJinn, studio, mediaJinn;

    SharedPreferences defPrefs;
    SharedPreferences.Editor defPrefsE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        defPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        defPrefsE = defPrefs.edit();

        relativeServices = (RelativeLayout) findViewById(R.id.relative_services);
        services = (RelativeLayout) findViewById(R.id.services);

        relativeServices.setVisibility(View.INVISIBLE);

        apps = (ImageView) findViewById(R.id.apps);
        viewServices = false;
        apps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!viewServices) {
                    viewServices = true;
                    relativeServices.setVisibility(View.VISIBLE);
                    apps.setBackground(getDrawable(R.drawable.silver_circle));
                } else {
                    viewServices = false;
                    relativeServices.setVisibility(View.INVISIBLE);
                    apps.setBackground(null);
                }
            }
        });
        relativeServices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!viewServices) {
                    viewServices = true;
                    relativeServices.setVisibility(View.VISIBLE);
                    apps.setBackground(getDrawable(R.drawable.silver_circle));
                } else {
                    viewServices = false;
                    relativeServices.setVisibility(View.INVISIBLE);
                    apps.setBackground(null);
                }
            }
        });

        trueJinn = (ImageView) findViewById(R.id.item1);
        trueJinn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, WebActivity.class);
                intent.putExtra("url", "https://j-inn.com");
                startActivity(intent);
            }
        });
        studio = (ImageView) findViewById(R.id.item2);
        studio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, WebActivity.class);
                intent.putExtra("url", "https://subsstudio.tk");
                startActivity(intent);
            }
        });
        mediaJinn = (ImageView) findViewById(R.id.item3);
        mediaJinn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, WebActivity.class);
                intent.putExtra("url", "https://media.j-inn.com");
                startActivity(intent);
            }
        });


        //check if Bluetooth LE is available on this device. If not, show an error
        BluetoothAdapter btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        if (btAdapter == null || (btAdapter.isEnabled() && btAdapter.getBluetoothLeScanner() == null) || (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))) {
            Intent i = new Intent(this, NoBTActivity.class);
            startActivity(i);
            finish();
            return;
        }
        if(defPrefs.getBoolean("AutoBluetooth", true)){
            if (!btAdapter.isEnabled()) btAdapter.enable();
        }else{
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
        }

        auth();

        boolean ok = true;
/*        try {
            if (!getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName()))
                ok = false;
        } catch (Throwable t) { }*/
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ok = false;
        if (ok) {
            if(!Config.serviceDisable) Starter.startPodsService(getApplicationContext());
        } else {
            Intent i = new Intent(this, IntroActivity.class);
            startActivity(i);
            finish();
        }
        ((ImageView) (findViewById(R.id.setting))).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { //settings clicked
                Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(i);
            }
        });
        ((ImageView) (findViewById(R.id.centerLamp))).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { //settings clicked
                WebActivity.currentURL = "https://j-inn.com";
                Intent intent = new Intent(MainActivity.this, WebActivity.class);
                startActivity(intent);
            }
        });
//        ((ImageView) (findViewById(R.id.tick))).setImageBitmap(bmp);
        ((Button) (findViewById(R.id.exit))).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { //settings clicked
                finish();
            }
        });
        ((ImageView) (findViewById(R.id.pop_open))).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { //settings clicked
                if(Config.deviceConnect){
                    Intent svc = new Intent(MainActivity.this, PopUpService.class);
                    svc.putExtra("Attach", true);
                    stopService(svc);
                    startService(svc);

                }else{
                    Toast.makeText(getApplicationContext(), "Наушники не підключено", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void auth() {
        if (Util.isOnline()) {
            try {
                String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                OkHttpClient client = new OkHttpClient();

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("deviceID", id)
                        .build();

                Request request = new Request.Builder()
                        .url("https://j-inn.com/api/jpods/access")
                        .post(requestBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            String res = response.body().string();
                            Map responseData = new Gson().fromJson(res, Map.class);
                            Map data = (Map) responseData.get("data");

                            defPrefsE.putString("statusVersion", data.get("status").toString());
                            defPrefsE.commit();
                            Log.d("dsafds", String.valueOf(defPrefs.getAll()));
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            if(defPrefs.getString("statusVersion", "false") == "false"){
                defPrefsE.putString("statusVersion", "0");
                defPrefsE.commit();
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        try {
            getApplicationContext().openFileInput("hidden").close();
            finish();
        } catch (Throwable t) { }
        if (Settings.canDrawOverlays(this)) {}
        else checkDrawOverlayPermission();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
            } else
                Toast.makeText(this, "Sorry. Can't draw overlays without permission...", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkDrawOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE);
        }
    }

}
