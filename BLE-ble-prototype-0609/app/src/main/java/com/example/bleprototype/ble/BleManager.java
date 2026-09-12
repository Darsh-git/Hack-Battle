package com.example.bleprototype.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleManager {
    private static final String TAG = "BleManager";
    public static final UUID SERVICE_UUID = UUID.fromString("8f8b7d9c-4c2c-4704-b09b-4c7b1b6e82d7");
    public static final UUID PACKET_UUID = UUID.fromString("b178c1d0-9bf7-4bc0-b202-5ba6f7d3f6d1");

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private Listener listener;
    private AdvertiseCallback advertiseCallback;
    private ScanCallback scanCallback;

    public interface Listener {
        void onPacketDiscovered(String deviceAddress, byte[] payload);
        void onScanFailure(String message);
        void onAdvertiseFailure(String message);
        void onAdvertiseSuccess();
    }

    public BleManager(Context context, BluetoothAdapter bluetoothAdapter) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isBluetoothReady() {
        if (bluetoothAdapter == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return bluetoothAdapter.isEnabled();
    }

    public void startAdvertising(byte[] payload) {
        if (!isBluetoothReady()) {
            if (listener != null) {
                listener.onAdvertiseFailure("Bluetooth is disabled");
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            if (listener != null) {
                listener.onAdvertiseFailure("Missing BLUETOOTH_ADVERTISE permission");
            }
            return;
        }

        if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            if (listener != null) {
                listener.onAdvertiseFailure("BLE advertising is not supported");
            }
            return;
        }

        if (payload == null || payload.length > 11) {
            if (listener != null) {
                listener.onAdvertiseFailure("BLE advertisement payload must be at most 11 bytes");
            }
            return;
        }

        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            if (listener != null) {
                listener.onAdvertiseFailure("BluetoothLeAdvertiser is null");
            }
            return;
        }
        stopAdvertising();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .build();

        // Keep only one 128-bit service-data entry: adding another 128-bit
        // service UUID would exceed the 31-byte legacy advertising budget.
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceData(new ParcelUuid(PACKET_UUID), payload)
                .build();

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.d(TAG, "Advertising started");
                if (listener != null) {
                    listener.onAdvertiseSuccess();
                }
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
                Log.e(TAG, "Advertising failed: " + errorCode);
                if (listener != null) {
                    listener.onAdvertiseFailure("Advertising failed. Code=" + errorCode);
                }
            }
        };

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    public void startScanning() {
        if (!isBluetoothReady()) {
            if (listener != null) {
                listener.onScanFailure("Bluetooth is disabled");
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            if (listener != null) {
                listener.onScanFailure("Missing BLUETOOTH_SCAN permission");
            }
            return;
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            if (listener != null) {
                listener.onScanFailure("BluetoothLeScanner is null");
            }
            return;
        }
        stopScanning();

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceData(new ParcelUuid(PACKET_UUID), null)
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                if (device != null) {
                    byte[] payload = null;
                    if (result.getScanRecord() != null && result.getScanRecord().getServiceData() != null) {
                        payload = result.getScanRecord().getServiceData().get(new ParcelUuid(PACKET_UUID));
                    }

                    Log.d(TAG, "Discovered device: " + device.getAddress());
                    if (listener != null && payload != null) {
                        listener.onPacketDiscovered(device.getAddress(), payload);
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                if (listener != null) {
                    listener.onScanFailure("Scan failed. Code=" + errorCode);
                }
            }
        };

        scanner.startScan(filters, settings, scanCallback);
    }

    public void stopScanning() {
        if (scanner != null && scanCallback != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            scanner.stopScan(scanCallback);
            scanCallback = null;
        }
    }

    public void stopAdvertising() {
        if (advertiser != null && advertiseCallback != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            advertiser.stopAdvertising(advertiseCallback);
            advertiseCallback = null;
        }
    }
}
