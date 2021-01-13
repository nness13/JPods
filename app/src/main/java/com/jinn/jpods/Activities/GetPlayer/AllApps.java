package com.jinn.jpods.Activities.GetPlayer;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import com.jinn.jpods.Activities.SettingsActivity;
import com.jinn.jpods.R;

import java.util.ArrayList;
import java.util.List;


public class AllApps extends ListActivity {

    private PackageManager packageManager = null;
    private List applist = null;
    private AppAdapter listadapter = null;
    private SharedPreferences defPrefs;
    private SharedPreferences.Editor defPrefsE;
    private Button reset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_app);

        packageManager = getPackageManager();
        defPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        defPrefsE = defPrefs.edit();

        reset = findViewById(R.id.reset);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                defPrefsE.putString("connectStartPlayer", "");
                defPrefsE.apply();

                finish();
            }
        });

        new LoadApplications().execute();
    }


    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        ApplicationInfo app = (ApplicationInfo) applist.get(position);
        defPrefsE.putString("connectStartPlayer", app.loadLabel(packageManager) + "|" + app.packageName);
        defPrefsE.apply();
//        Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
//        startActivity(i);

        finish();
    }

    private List checkForLaunchIntent(List<ApplicationInfo> list) {

        ArrayList<ApplicationInfo> appList = new ArrayList();

        for(ApplicationInfo info : list) {
            try{
                if(packageManager.getLaunchIntentForPackage(info.packageName) != null) {
                    appList.add(info);
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        return appList;
    }

    private class LoadApplications extends AsyncTask<Void, Void, Void> {

        private ProgressDialog progress = null;

        @Override
        protected Void doInBackground(Void... params) {

            applist = checkForLaunchIntent(packageManager.getInstalledApplications(PackageManager.GET_META_DATA));

            listadapter = new AppAdapter(AllApps.this, R.layout.list_item, applist);

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            setListAdapter(listadapter);
            progress.dismiss();
            super.onPostExecute(result);
        }

        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(AllApps.this, null, "Loading apps info...");
            super.onPreExecute();
        }
    }
}