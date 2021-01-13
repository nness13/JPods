package com.jinn.jpods.Activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.jinn.jpods.Activities.GetPlayer.AllApps;
import com.jinn.jpods.Config;
import com.jinn.jpods.R;
import com.jinn.jpods.Services.PodsService;
import com.jinn.jpods.Services.Starter;
import com.jinn.jpods.util.Util;

import java.util.regex.Pattern;

public class SettingsActivity extends PreferenceActivity{
    Preference serviceEnable, statusVersion, connectStartPlayer, discovery;
    ListPreference list;
    EditTextPreference nameDevice;
    SharedPreferences prefs, defPrefs;
    SharedPreferences.Editor sEditor, dpEditor;
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.pref_general);

        prefs = getSharedPreferences(Config.APP_PREFERENCES, MODE_PRIVATE);
        sEditor = prefs.edit();
        defPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        dpEditor = defPrefs.edit();

        serviceEnable = (Preference) findPreference("serviceEnable");
        nameDevice = (EditTextPreference) findPreference("nameDevice");
        statusVersion = (Preference) findPreference("statusVersion");
        connectStartPlayer = (Preference) findPreference("connectStartPlayer");
        discovery = (Preference) findPreference("discovery");
        list = (ListPreference) findPreference("scanMode");

        syncStatus();

        list.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int index = list.findIndexOfValue(newValue.toString());

                if (index != -1) {
                    Starter.restartPodsService(getApplicationContext());
                    Toast.makeText(getBaseContext(), list.getEntries()[index], Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });

        discovery.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                BluetoothAdapter ba = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
                if(ba != null){
                    if (ba.isDiscovering()) { ba.cancelDiscovery(); }
                    ba.startDiscovery();
                }
                return true;
            }
        });

        statusVersion.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                appVersion();
                return true;
            }
        });

        nameDevice.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                nameDevice.setTitle(newValue.toString());
                dpEditor.putString("nameDevice", newValue.toString());
                dpEditor.commit();
                Config.name = newValue.toString();
                return false;
            }
        });

        serviceEnable.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if(!Config.serviceDisable) {
                    Config.serviceDisable = true;
                    serviceEnable.setTitle("Запустити сервіс");
                } else {
                    Starter.startPodsService(getApplicationContext());
                    Config.serviceDisable = false;
                    serviceEnable.setTitle("Зупинити сервіс");
                };
                return true;
            }
        });

        connectStartPlayer.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent(getApplicationContext(), AllApps.class);
                startActivity(i);
                return true;
            }
        });

    }

    @Override
    protected void onResume(){
        super.onResume();
        syncStatus();
    }

    private void appVersion(){
        String getStatus = defPrefs.getString("statusVersion", "0");
        if(getStatus.equals("0"))
            statusVersion.setTitle("Отримати Pro");
        if(getStatus.equals("1"))
            statusVersion.setTitle("Pro Version");
    }
    private void statusConPlayer(){
        String nameConnectStartPlayer = defPrefs.getString("connectStartPlayer", "");
        if( nameConnectStartPlayer != "") {
            String[] parts = nameConnectStartPlayer.split(Pattern.quote("|"));
            connectStartPlayer.setSummary("Встановлено: "+parts[0]);
        }else{
            connectStartPlayer.setSummary("Не встановлено");
        }
    }

    private void syncStatus(){
        appVersion();

        nameDevice.setTitle(defPrefs.getString("nameDevice", "AirPods"));

        if(Config.serviceDisable) serviceEnable.setTitle("Запустити сервіс");
        else serviceEnable.setTitle("Зупинити сервіс");

        statusConPlayer();
    }

}

