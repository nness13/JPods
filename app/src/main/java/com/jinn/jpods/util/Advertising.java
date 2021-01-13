package com.jinn.jpods.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.gson.Gson;
import com.jinn.jpods.Config;
import com.jinn.jpods.R;
import com.jinn.jpods.Activities.WebActivity;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Advertising {
    Context context;
    int NOTIFICATION_ID = 2;  // Неможе дорівнювати 1
    public Advertising(Context context){
        this.context = context;
        showAdvertising();
    }
    public void showAdvertising(){
       try {
           OkHttpClient client = new OkHttpClient();
           Request request = new Request.Builder()
                   .url("https://subsstudio.tk/api/jpods/ads/notification")
                   .build();

           client.newCall(request).enqueue(new Callback() {
               @Override
               public void onFailure(Call call, IOException e) {
                   e.printStackTrace();
               }

               @Override
               public void onResponse(Call call, Response response) throws IOException {
                   if(response.isSuccessful()){
                       String res = response.body().string();
                       Log.d("obj", res.toString());

//                    HashMap<String, Object> obj = JsonParsing.createHashMapFromJsonString(res.toString());
//                    Log.d("obj", String.valueOf(obj));
                       Map data = new Gson().fromJson(res, Map.class);

                       ArrayList orders = (ArrayList) data.get("data");
                       if(orders.size() == 0) return;
                       Map order = (Map) orders.get(0);

                       ArrayList goods = (ArrayList) order.get("goods");
                       Map good = (Map) goods.get(0);
                       NOTIFICATION_ID = Integer.parseInt(String.valueOf(good.get("id")));
                       new sendNotification(context)
                               .execute(good.get("name").toString(),
                                       good.get("description").toString(),
                                       good.get("img").toString(),
                                       order.get("statisticLink").toString());
                   }
               }
           });
       }catch (Exception e){
           Config.viewAdsNotify = false;
           Config.lastViewAds = System.currentTimeMillis();
           showAdvertising();
           e.printStackTrace();
       }

    }

    private class sendNotification extends AsyncTask<String, Void, Bitmap> {

        Context ctx;
        String title, description, url;

        public sendNotification(Context context) {
            super();
            this.ctx = context;
        }

        @Override
        protected Bitmap doInBackground(String... params) {

            InputStream in;
            title = params[0];
            description = params[1];
            url = params[3];

            try {

                URL url = new URL(params[2]);
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                in = connection.getInputStream();
                Bitmap myBitmap = BitmapFactory.decodeStream(in);
                return myBitmap;
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);

            try {
                NotificationManager notificationManager = (NotificationManager) ctx
                        .getSystemService(Context.NOTIFICATION_SERVICE);
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    int importance = NotificationManager.IMPORTANCE_DEFAULT; //Important for heads-up notification
//                    NotificationChannel channel = new NotificationChannel("1", "notification", importance);
//                    channel.setShowBadge(true);
//                    channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
//                    notificationManager.createNotificationChannel(channel);
//                }

                Intent intent = new Intent(ctx, WebActivity.class);
                intent.putExtra("url", url);
                PendingIntent resultPendingIntent = PendingIntent.getActivity(
                        ctx, 0, intent, 0);

                NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ctx, Config.APP_PREFERENCES)
                        .setContentTitle(title)
                        .setContentText(description)
                        .setContentIntent(resultPendingIntent)
                        .setSmallIcon(R.drawable.ic_skylight_notification)
                        .setColor(16367877)
                        .setDefaults(NotificationCompat.DEFAULT_SOUND) //Important for heads-up notification
                        .setPriority(Notification.PRIORITY_MAX) //Important for heads-up notification
                        .setVibrate(new long[0])
                        .setLargeIcon(result);
                Notification notification = mBuilder.build();

                notificationManager.notify(NOTIFICATION_ID, notification);
                Config.viewAdsNotify = false;
                Config.lastViewAds = System.currentTimeMillis();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}

