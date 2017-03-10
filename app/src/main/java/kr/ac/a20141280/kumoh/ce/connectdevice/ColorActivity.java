package kr.ac.a20141280.kumoh.ce.connectdevice;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;


import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.ColorPicker.OnColorChangedListener;
import com.larswerkman.holocolorpicker.SVBar;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * Created by BA2K on 2017-02-19.
 */
public class ColorActivity extends AppCompatActivity implements OnColorChangedListener{
    ColorPicker picker;

    private final static String TAG = "TAG";

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";


    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private boolean mGottenChracteristics = false;
    private ArrayList<HashMap<String, String>> gattServiceData;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    private String receivedData;
    private BluetoothGattCharacteristic characteristic;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();

            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
            Log.i("seung", mDeviceAddress + "  device connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i("seung", "in broadcast receiver");
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                receivedData = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);            // BluetoothLeService에서 넘어온 값(=라즈베리파이로부터 넘어온 값) 처리

                String[] arr = receivedData.split(",");

                for(int i=0; i<arr.length; i++)           // string형의 color값들에 0x붙여서 16진수화
                    arr[i] = "0x" + arr[i];

                int oldColor = Color.argb(Integer.decode("0xff"), Integer.decode(arr[0]), Integer.decode(arr[1]), Integer.decode(arr[2]));      // 16진수->10진수로 변환

                picker.setColor(oldColor);
                picker.setOldCenterColor(oldColor);            // 이전 컬러값을 UI에 반영
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color);

        ConnectingTask task = new ConnectingTask();
        task.execute();

        picker = (ColorPicker)findViewById(R.id.picker);
        picker.getColor();
        SVBar svBar = (SVBar) findViewById(R.id.svbar);
        picker.addSVBar(svBar);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        setTitle(mDeviceName);

        picker.setOnColorChangedListener(this);         // 컬러 선택시 컬러값 전송을 위한 리스너 --seung
    }

    private class ConnectingTask extends AsyncTask<Void, Void, Void> {          // 프로그레스 다이얼로그 사용
        ProgressDialog asyncDialog = new ProgressDialog(ColorActivity.this);

        @Override
        protected void onPreExecute() {
            asyncDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            asyncDialog.setMessage("디바이스를 연결 중 입니다.");

            asyncDialog.setCanceledOnTouchOutside(false);
            asyncDialog.show();
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            try {
                Intent gattServiceIntent = new Intent(ColorActivity.this, BluetoothLeService.class);
                bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);           // 블루투스 서비스 시작 --seung

                while(true) {
                    if(mGottenChracteristics == true) {
                        mGottenChracteristics = false;
                        break;
                    }
                }
            } catch (Exception e) {
                Log.i("seung", "connection error " +  e.toString());
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            characteristic = mGattCharacteristics.get(2).get(1);            // 하드코딩, read의 characteristic을 가져옴 --seung
            mBluetoothLeService.readCharacteristic(characteristic);

            characteristic = mGattCharacteristics.get(2).get(0);            // read 했으니 이제 write의 characteristic을 가져옴 --seung

            asyncDialog.dismiss();
            super.onPostExecute(result);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public void onColorChanged(int color) {
        characteristic.setValue(Integer.toHexString(color));
        mBluetoothLeService.writeCharacteristic(characteristic);
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME, "Service");
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(LIST_NAME, "Characteristic");
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        mGottenChracteristics = true;           // characteristic을 다 받아온 경우
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
