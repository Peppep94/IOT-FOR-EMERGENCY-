package univpm.iot_for_emergency.View.Funzionali;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.lang.reflect.Method;

import static android.content.ContentValues.TAG;


/*Service che si occupa di connettere e leggere i dati dal Beacon.*/
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)

public class BluetoothLeService extends Service {
    private static String LOG_TAG = "BluetoothLeService";
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private int mConnectionState = STATE_DISCONNECTED;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;



    private static final byte[] ENABLE_SENSOR = {0x01};




    @Override
    public void onCreate() {
        Log.i("ScanService", "sono partito");
        super.onCreate();
        getBluetoothAdapterAndLeScanner();
        mHandler = new Handler();
        mBluetoothDeviceAddress="";
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void getBluetoothAdapterAndLeScanner() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("ScanService", "OnStartCommand ");
        scanLeDevice(true);
        //TODO do something useful
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        //TODO for communication return IBinder implementation
        return null;
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            Log.i("ScanService", "scanledevice dentro");
            // Lo scan si ferma dopo un tempo predefinito.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeScanner.stopScan(scanCallback);
                    final Intent intent1 = new Intent("action");
                    intent1.putExtra("nome","pausa");
                    sendBroadcast(intent1);
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            scanLeDevice(true);
                        }
                    }, 5000);
                    Log.e("ble scanner","Tempo scaduto");
                }
            }, SCAN_PERIOD);
            mBluetoothLeScanner.startScan(scanCallback);
            final Intent intent1 = new Intent("action");
            intent1.putExtra("nome","scanning");
            sendBroadcast(intent1);
        } else {
            mBluetoothLeScanner.stopScan(scanCallback);
        }
    }



    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("ScanService", "callback dentro");
            Log.e("Errore", String.valueOf(callbackType));
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            final String deviceName = device.getName();
            mBluetoothDeviceAddress=device.getAddress();
            if (deviceName != null && deviceName.length() > 0) {
                if (deviceName.contains("SensorTag")) {
                    Log.e("callback",deviceName);
                    connect();
                    scanLeDevice(false);
                    final Intent intent = new Intent("univpm.iot_for_emergency.View.Funzionali.Trovato");
                    intent.putExtra("device",mBluetoothDeviceAddress);
                    sendBroadcast(intent);

                }
            } else {
                Log.e("Errore", String.valueOf(callbackType));
            }
        }
    };

    private boolean refreshDeviceCache(BluetoothGatt gatt){
        try {
            BluetoothGatt localBluetoothGatt = gatt;
            Method localMethod = localBluetoothGatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                boolean bool = ((Boolean) localMethod.invoke(localBluetoothGatt, new Object[0])).booleanValue();
                return bool;
            }
        }
        catch (Exception localException) {
            Log.e(TAG, "An exception occured while refreshing device");
        }
        return false;
    }



    /* Connessione a dispositivo*/
    public boolean connect() {
        if (mBluetoothAdapter == null || mBluetoothDeviceAddress == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mBluetoothDeviceAddress);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
        refreshDeviceCache(mBluetoothGatt);
        Log.d(TAG, "Trying to create a new connection.");
        mConnectionState = STATE_CONNECTING;
        return true;
    }


    /*Messaggio di risposta ottenuto dopo le interazioni col dispositivo*/
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        /*Metodo che viene richiamato quando cambia lo stato della connessione (da non connesso passo a connesso e viceversa) */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                broadcasUpdate("univpm.iot_for_emergency.View.Funzionali.Connesso",gatt.getDevice().getAddress());
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
            }
            gatt.discoverServices();
        }


        /*Metodo che viene richiamato una volta che sono stati scoperti i servizi offerti dal dispositivo */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            /* Mi associo al service che mi interessa*/
            BluetoothGattService humidityService = gatt.getService(SensorTagGatt.UUID_HUM_SERV);

            /* Mi associo ala caratteristica che mi interessa*/
            BluetoothGattCharacteristic enableHum = humidityService.getCharacteristic(SensorTagGatt.UUID_HUM_CONF);

            /*Assegno alla caratteristica desiderata il valore che accende il sensore*/
            enableHum.setValue(ENABLE_SENSOR);

            /*Invio il dato al sensore*/
            gatt.writeCharacteristic(enableHum);

        }

        /*Metodo che viene richiamato una volta che sono stati iviati dati al dispositivo */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            BluetoothGattService humidityService = gatt.getService(SensorTagGatt.UUID_HUM_SERV);

            BluetoothGattCharacteristic humidityCharacteristic = humidityService.getCharacteristic(SensorTagGatt.UUID_HUM_DATA);

            /*richiedo di leggere il valore della caratteristica*/
            gatt.readCharacteristic(humidityCharacteristic);

        }
        /*Metodo che viene richiamato una volta che sono stati richiesti i dati al dispositivo*/
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            /*Controllo se il valore è uguale a 0, se è così vuol dire che abbiamo preso il valore prima che il sensore si accendesse quindi provo a leggere di nuovo */
            byte []value=characteristic.getValue();
            int t = shortUnsignedAtOffset(value, 0);
            if (t==0)
            {
                gatt.readCharacteristic(characteristic);
            }else {
                /*se il valore ricevuto è diverso da 0 abbiamo letto i dati e li inviamo alla home */
                broadcastUpdate("univpm.iot_for_emergency.View.Funzionali.Ricevuti",characteristic);
            }
        }

    };

    private static Integer shortUnsignedAtOffset(byte[] c, int offset) {
        Integer lowerByte = (int) c[offset] & 0xFF;
        Integer upperByte = (int) c[offset+1] & 0xFF;
        return (upperByte << 8) + lowerByte;
    }


    /*Tutte le funzioni broadcast inviano un intent caratterizzato da un'azione e da dari dati, il receiver della home si comporta in modo diverso in base all'azione dell'intent */
    private void broadcastUpdate(final String action,BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        double humidity = SensorTagData.extractHumidity(characteristic);
        double temperature = SensorTagData.extractHumAmbientTemperature(characteristic);
        intent.putExtra("device",mBluetoothDeviceAddress);
        intent.putExtra("hum",humidity);
        intent.putExtra("temp",temperature);
        sendBroadcast(intent);
        if(!disconnect()){
            stopSelf();
        }
        close();
    }

    private void broadcasUpdate(final String action,String device){
        final Intent intent =new Intent(action);
        intent.putExtra("device",device);
        sendBroadcast(intent);

    }

    public void broadcastUpdate(final String action){
        Intent intent=new Intent(action);
        sendBroadcast(intent);
    }

    public boolean disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        mBluetoothGatt.disconnect();
        return true;
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }


}
