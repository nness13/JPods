package com.jinn.jpods.Services;

import android.app.Service;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
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
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.jinn.jpods.Config;
import com.jinn.jpods.R;

import java.util.List;

import pl.droidsonroids.gif.AnimationListener;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;


public class PopUpConnectService extends Service implements View.OnTouchListener{
  private static final String TAG = PopUpConnectService.class.getSimpleName();

  private WindowManager windowManager;

  private View floatyView;
  TextView title;
  Button button;
  GifImageView gifImageView, loader;

  @Override
  public IBinder onBind(Intent intent) {
      Log.d(TAG, String.valueOf(intent));
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Config.popUpIsOpen = true;
    windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

    addOverlayView();
  }

  private void addOverlayView() {

    final LayoutParams params;
    int layoutParamsType;

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      layoutParamsType = LayoutParams.TYPE_APPLICATION_OVERLAY;
    }
    else {
      layoutParamsType = LayoutParams.TYPE_PHONE;
    }

    params = new LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT,
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

            Log.v(TAG, "BACK Button Pressed");

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
      floatyView = inflater.inflate(R.layout.pop_up_connect, interceptorLayout);
      title = (TextView) floatyView.findViewById(R.id.title);
      button = (Button) floatyView.findViewById(R.id.submit);
      RelativeLayout window = floatyView.findViewById(R.id.pop_up_window);
      Animation anim = AnimationUtils.loadAnimation(this, R.anim.translate_pop_up);
      window.startAnimation(anim);

//      title.setVisibility(View.INVISIBLE);
//      button.setVisibility(View.INVISIBLE);
      title.setText(Config.name);


      gifImageView = (GifImageView) floatyView.findViewById(R.id.frame);
      gifImageView.setImageResource(R.drawable.case_rotate_start_resize);

      loader = (GifImageView) floatyView.findViewById(R.id.loader_in_button);
      loader.setVisibility(View.INVISIBLE);
      loader.setImageResource(R.drawable.loader_btn);

     final GifDrawable gifDrawable = (GifDrawable) gifImageView.getDrawable();
      gifDrawable.addAnimationListener(new AnimationListener() {
        @Override
        public void onAnimationCompleted(int loopNumber) {
          gifDrawable.recycle();
          gifImageView.setImageResource(R.drawable.case_rotate_resize);
          GifDrawable gifD = (GifDrawable) gifImageView.getDrawable();
          gifD.addAnimationListener(new AnimationListener() {
            @Override
            public void onAnimationCompleted(int loopNumber) {
              if(failedConnect){
                gifImageView.setImageResource(R.drawable.failed_connection);
                button.setBackgroundResource(R.drawable.button);
                loader.setVisibility(View.INVISIBLE);
                button.setText("Повторити");
              }
            }
          });
          gifD.start();

          title.setText(Config.name);
          title.setVisibility(View.VISIBLE);
          button.setVisibility(View.VISIBLE);
        }
      });
      button.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) { //settings clicked
            PodsService.device.createBond();
/*
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
              bluetoothGatt = PodsService.device.connectGatt(getApplicationContext(), true, gattCallback, BluetoothDevice.TRANSPORT_LE);
            else
              bluetoothGatt = PodsService.device.connectGatt(getApplicationContext(), true, gattCallback);
*/


            gifImageView.setImageResource(R.drawable.button_case);
            GifDrawable gifDrawable = (GifDrawable) gifImageView.getDrawable();
            gifDrawable.addAnimationListener(new AnimationListener() {
              @Override
              public void onAnimationCompleted(int loopNumber) {
                if(failedConnect){
                  gifImageView.setImageResource(R.drawable.failed_connection);
                  button.setBackgroundResource(R.drawable.button);
                  loader.setVisibility(View.INVISIBLE);
                  button.setText("Повторити");
                }
              }
            });

            button.setBackgroundColor(0x00FFFFFF);
            loader.setVisibility(View.VISIBLE);
            button.setText("Підключення...");
          }
        }
      );

      floatyView.findViewById(R.id.close_btn).setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View view) { //settings clicked
              Config.deviceConnect = false;
              Config.maybeConnected = false;
              onDestroy();
            }
          }
      );
      floatyView.setOnTouchListener(this);
      windowManager.addView(floatyView, params);
    }
    else {
      Log.e("SAW-example", "Layout Inflater Service is null; can't inflate and display R.layout.floating_view");
    }
  }

  public static boolean failedConnect = false;


  @Override
  public void onDestroy() {
    super.onDestroy();

    if (floatyView != null) {

      windowManager.removeView(floatyView);

      floatyView = null;
    }
    Config.popUpIsOpen = false;
//      bluetoothGatt.close();
  }

  BluetoothGatt bluetoothGatt;
  private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
      @Override
      public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
           if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" + bluetoothGatt.discoverServices());
          } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
               Log.i(TAG, "Disconnected from GATT server.");
            }
      }

      @Override
       // New services discovered
      public void onServicesDiscovered(BluetoothGatt gatt, int status) {
          if (status == BluetoothGatt.GATT_SUCCESS) {
              for(BluetoothGattService service : gatt.getServices()){
                  List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                  for (BluetoothGattCharacteristic characteristic : characteristics) {
                      System.out.println("characteristic: " + characteristic.getUuid() );
                      gatt.readCharacteristic(characteristic);
                  }
//                  Log.d("characteristic", String.valueOf(gatt.readCharacteristic(characteristic)));
//                  BluetoothGattService mGattMiFloraService = gatt.getService(service.getUuid());
//                  if(mGattMiFloraService != null){
//                      boolean rs = gatt.readCharacteristic(mGattMiFloraService.getCharacteristic(service.getUuid()));
//                  }
              }
              //Log.d(mTAG, "mGattMiFloraService: " + UUID_MI_FLORA_SERVICE_ID.toString());

          }
          else Log.w(TAG, "onServicesDiscovered received: " + status);
      }

      @Override
      // Result of a characteristic read operation
      public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
          Log.d("status", String.valueOf(status));
          if (status == BluetoothGatt.GATT_SUCCESS)
            Log.d("characteristic", String.valueOf(gatt.readCharacteristic(characteristic)));
      }
  };



  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {
    view.performClick();

    Log.v(TAG, "onTouch...");

    // Kill service
//    onDestroy();

    return true;
  }
}