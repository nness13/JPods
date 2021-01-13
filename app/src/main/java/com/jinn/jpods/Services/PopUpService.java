package com.jinn.jpods.Services;

import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.SupportActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jinn.jpods.Config;
import com.jinn.jpods.R;
import com.jinn.jpods.util.Util;

import java.io.File;
import java.util.regex.Pattern;

import pl.droidsonroids.gif.AnimationListener;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;


public class PopUpService extends Service implements View.OnTouchListener{
  private static final String TAG = PopUpService.class.getSimpleName();

  private WindowManager windowManager;

  private SharedPreferences defPrefs;
  private PackageManager packageManager = null;

  Updater updater;

  private View floatyView;
  TextView title, podsText, podsText2, caseText;
  ProgressBar batteryPods, batteryPods2, batteryCase;
  ImageView chargeL, chargeR;
  Button button;
  GifImageView gifImageView;
  GifDrawable gifDrawable;
  private boolean shutdown = false;
  private boolean attach;

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    attach = intent.getBooleanExtra("Attach", false);
    Config.popUpAttach = attach;
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Config.popUpIsOpen = true;
    defPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    packageManager = getPackageManager();

    Log.d("onCreate", String.valueOf(Config.popUpAttach));
    windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

    addOverlayView();
/*    runOnUiThread(new Runnable() {

      @Override
      public void run() {

        // Stuff that updates the UI

      }
    });*/

  }

  private void addOverlayView() {

    final WindowManager.LayoutParams params;
    int layoutParamsType;

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      layoutParamsType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }
    else {
      layoutParamsType = LayoutParams.TYPE_PHONE;
    }

    params = new WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        layoutParamsType,
        0,
        PixelFormat.TRANSLUCENT);

    params.gravity = Gravity.BOTTOM;
    params.x = 0;
    params.y = 0;

    RelativeLayout interceptorLayout = new RelativeLayout(this) {

      @Override
      public boolean dispatchKeyEvent(KeyEvent event) {
        // Only fire on the ACTION_DOWN event, or you'll get two events (one for _DOWN, one for _UP)
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
          // Check if the HOME button is pressed
          if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            // As we've taken action, we'll return true to prevent other apps from consuming the event as well
            return true;
          }
        }
        // Otherwise don't intercept the event
        return super.dispatchKeyEvent(event);
      }
    };

    LayoutInflater inflater = ((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE));


    if (inflater != null) {
      floatyView = inflater.inflate(R.layout.floating_view, interceptorLayout);
      title = (TextView) floatyView.findViewById(R.id.title);
      batteryPods = (ProgressBar) floatyView.findViewById(R.id.batteryPods);
      chargeL = (ImageView) floatyView.findViewById(R.id.chargeL);
      podsText = (TextView) floatyView.findViewById(R.id.podsText);
      batteryPods2 = (ProgressBar) floatyView.findViewById(R.id.batteryPods2);
      chargeR = (ImageView) floatyView.findViewById(R.id.chargeR);
      podsText2 = (TextView) floatyView.findViewById(R.id.podsText2);
      batteryCase = (ProgressBar) floatyView.findViewById(R.id.batteryCase);
      caseText = (TextView) floatyView.findViewById(R.id.caseText);
      button = (Button) floatyView.findViewById(R.id.submit);

      RelativeLayout window = floatyView.findViewById(R.id.pop_up_window);
      Animation anim = AnimationUtils.loadAnimation(this, R.anim.translate_pop_up);
      window.startAnimation(anim);
      anim.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) { }
        @Override
        public void onAnimationEnd(Animation animation) {
          gifImageView.setImageResource(R.drawable.anim_pods);
          gifDrawable = (GifDrawable) gifImageView.getDrawable();
          gifDrawable.addAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationCompleted(int loopNumber) {
              viewEl = true;
              gifDrawable.recycle();
              gifImageView.setImageResource(R.drawable.airpods);
              GifDrawable gifDrawable = (GifDrawable) gifImageView.getDrawable();
              gifDrawable.start();

              title.setVisibility(View.VISIBLE);
              batteryPods.setVisibility(View.VISIBLE);
              podsText.setVisibility(View.VISIBLE);
              batteryPods2.setVisibility(View.VISIBLE);
              podsText2.setVisibility(View.VISIBLE);
              batteryCase.setVisibility(View.VISIBLE);
              caseText.setVisibility(View.VISIBLE);
              button.setVisibility(View.VISIBLE);
            }
          });
          updater = new Updater();
        }

        @Override
        public void onAnimationRepeat(Animation animation) { }
      });

      title.setVisibility(View.INVISIBLE);

      batteryPods.setVisibility(View.INVISIBLE);
      chargeL.setVisibility(View.INVISIBLE);
      podsText.setVisibility(View.INVISIBLE);

      batteryPods2.setVisibility(View.INVISIBLE);
      chargeR.setVisibility(View.INVISIBLE);
      podsText2.setVisibility(View.INVISIBLE);

      batteryCase.setVisibility(View.INVISIBLE);
      caseText.setVisibility(View.INVISIBLE);
      button.setVisibility(View.INVISIBLE);

      gifImageView = (GifImageView) floatyView.findViewById(R.id.frame);


      floatyView.findViewById(R.id.submit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { //settings clicked
                startPlayer();
                onDestroy();
            }}
      );
      floatyView.findViewById(R.id.close_btn).setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) { //settings clicked
                  onDestroy();
            }
          }
      );
      floatyView.setOnTouchListener(this);
      windowManager.addView(floatyView, params);
    }else Log.e("SAW-example", "Layout Inflater Service is null; can't inflate and display R.layout.floating_view");
  }


  @Override
  public void onDestroy() {
    super.onDestroy();
    shutdown = true;
    Config.popUpIsOpen = false;
    Log.d("onDestroy", String.valueOf(Config.popUpAttach));
    Config.popUpAttach = false;
    if (floatyView != null) {

      windowManager.removeView(floatyView);

      floatyView = null;
    }
  }

  public void startPlayer(){
    String nameConnectStartPlayer = defPrefs.getString("connectStartPlayer", "");
    if( nameConnectStartPlayer != "") {
      String[] parts = nameConnectStartPlayer.split(Pattern.quote("|"));
      try{
        Intent intent = packageManager.getLaunchIntentForPackage(parts[1]);

        if(intent != null) {
          startActivity(intent);
        }
      } catch(ActivityNotFoundException e) {
        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
      } catch(Exception e) {
        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
      }
    }
  }

  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {
    view.performClick();

    Log.v(TAG, "onTouch...");

    // Kill service
//    onDestroy();

    return true;
  }

  public boolean viewEl = false;
  class Updater implements Runnable{
    Thread thread;
    Updater() {
      thread = new Thread(this, "Updater");
      thread.start();
    }
    @Override
    public void run() {
      while (!shutdown) {
        floatyView.post(new Runnable() {
          @Override
          public void run() {
            title.setText(Config.name);

            batteryPods.setProgress(Util.minMax(Config.leftStatusF));
            podsText.setText(String.valueOf(Util.minMax(Config.leftStatusF)));
            if(Config.chargeL && viewEl) chargeL.setVisibility(View.VISIBLE);
            else chargeL.setVisibility(View.INVISIBLE);

            batteryPods2.setProgress(Util.minMax(Config.rightStatusF));
            podsText2.setText(String.valueOf(Util.minMax(Config.rightStatusF)));
            if(Config.chargeR && viewEl) chargeR.setVisibility(View.VISIBLE);
            else chargeR.setVisibility(View.INVISIBLE);

            batteryCase.setProgress(Util.minMax(Config.caseStatusF));
            caseText.setText(String.valueOf(Util.minMax(Config.caseStatusF)));
          }
        });
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {}
      }
    }
  }


}
