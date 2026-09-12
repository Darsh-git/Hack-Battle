package com.example.bleprototype.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
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

import com.example.bleprototype.model.EmergencyPacket;
import com.example.bleprototype.network.PacketManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BleManager {
    private static final String TAG = "BleManager";
    public static final UUID SERVICE_UUID = UUID.fromString("8f8b7d9c-4c2c-4704-b09b-4c7b1b6e82d7");
    public static final UUID PACKET_UUID = UUID.fromString("b178c1d0-9bf7-4bc0-b202-5ba6f7d3f6d1");
    private static final UUID CLIENT_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothManager bluetoothManager;
    private final List<BluetoothDevice> connectedDevices = new ArrayList<>();
    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private BluetoothGattServer gattServer;
    private BluetoothGattCharacteristic packetCharacteristic;
    private final Map<String, BluetoothGatt> clientGatts = new HashMap<>();
    private final Map<String, BluetoothGattCharacteristic> clientCharacteristics = new HashMap<>();
    private final Map<String, List<byte[]>> pendingWrites = new HashMap<>();
    private final Set<String> connectingAddresses = new HashSet<>();
    private final Set<String> clientWritesInProgress = new HashSet<>();
    private byte[] latestGattPayload;
    private BluetoothLeAdvertiser gattAdvertiser;
    private Listener listener;
    private AdvertiseCallback advertiseCallback;
    private AdvertiseCallback gattAdvertiseCallback;
    private ScanCallback scanCallback;
    private ScanCallback gattScanCallback;

    public interface Listener {
        void onPacketDiscovered(String deviceAddress, byte[] payload);
        void onScanFailure(String message);
        void onAdvertiseFailure(String message);
        void onAdvertiseSuccess();
    }

    public BleManager(Context context, BluetoothAdapter bluetoothAdapter) {
        this.context = context.getApplicationContext();
        this.bluetoothAdapter = bluetoothAdapter;
        this.bluetoothManager = (BluetoothManager) context.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
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
        startGattClientScan(settings);
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
        stopGattClientScan();
    }

    private void startGattClientScan(ScanSettings settings) {
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);

        gattScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (device != null) {
                    connectToGattPeer(device);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "GATT scan failed: " + errorCode);
            }
        };
        scanner.startScan(filters, settings, gattScanCallback);
    }

    private void stopGattClientScan() {
        if (scanner != null && gattScanCallback != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            scanner.stopScan(gattScanCallback);
            gattScanCallback = null;
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

    public void startGattServer() {
        if (!isBluetoothReady() || bluetoothManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (gattServer != null) {
            return;
        }

        BluetoothGattService packetService = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        packetCharacteristic = new BluetoothGattCharacteristic(
                PACKET_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_WRITE
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
                        | BluetoothGattCharacteristic.PERMISSION_WRITE
        );
        packetCharacteristic.addDescriptor(new BluetoothGattDescriptor(
                    CLIENT_CONFIG_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE
                ));
        packetService.addCharacteristic(packetCharacteristic);

        gattServer = bluetoothManager.openGattServer(context, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    synchronized (connectedDevices) {
                        if (!connectedDevices.contains(device)) {
                            connectedDevices.add(device);
                        }
                    }
                    Log.d(TAG, "GATT server connected: " + device.getAddress());
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    synchronized (connectedDevices) {
                        connectedDevices.remove(device);
                    }
                    Log.d(TAG, "GATT server disconnected: " + device.getAddress());
                }
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                    BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                if (packetCharacteristic.equals(characteristic)) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                            latestGattPayload);
                }
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                     BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                                                     boolean responseNeeded, int offset, byte[] value) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                        responseNeeded, offset, value);
                if (packetCharacteristic.equals(characteristic) && value != null) {
                    latestGattPayload = value.clone();
                    if (responseNeeded) {
                        gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                    }
                    if (listener != null) {
                        listener.onPacketDiscovered(device.getAddress(), value);
                    }
                }
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattDescriptor descriptor, boolean preparedWrite,
                                                 boolean responseNeeded, int offset, byte[] value) {
                if (responseNeeded) {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
            }
        });

        if (gattServer != null) {
            gattServer.addService(packetService);
            startGattServiceAdvertising();
            Log.d(TAG, "GATT server started");
        }
    }

    private void startGattServiceAdvertising() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isMultipleAdvertisementSupported()) {
            return;
        }
        gattAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (gattAdvertiser == null) {
            return;
        }
        stopGattServiceAdvertising();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build();
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();
        gattAdvertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "GATT service advertising failed: " + errorCode);
            }
        };
        gattAdvertiser.startAdvertising(settings, data, gattAdvertiseCallback);
    }

    private void stopGattServiceAdvertising() {
        if (gattAdvertiser != null && gattAdvertiseCallback != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            gattAdvertiser.stopAdvertising(gattAdvertiseCallback);
            gattAdvertiseCallback = null;
        }
    }

    private void connectToGattPeer(BluetoothDevice device) {
        String address = device.getAddress();
        synchronized (clientGatts) {
            if (clientGatts.containsKey(address) || !connectingAddresses.add(address)) {
                return;
            }
        }
        try {
            BluetoothGatt gatt = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt callbackGatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        synchronized (clientGatts) {
                            connectingAddresses.remove(address);
                            clientGatts.put(address, callbackGatt);
                        }
                        Log.d(TAG, "GATT client connected: " + address);
                        if (!callbackGatt.requestMtu(512)) {
                            callbackGatt.discoverServices();
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        removeGattClient(address, callbackGatt);
                        Log.d(TAG, "GATT client disconnected: " + address);
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt callbackGatt, int status) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        return;
                    }
                    BluetoothGattService service = callbackGatt.getService(SERVICE_UUID);
                    BluetoothGattCharacteristic characteristic = service == null
                            ? null : service.getCharacteristic(PACKET_UUID);
                    if (characteristic == null) {
                        return;
                    }
                    synchronized (clientGatts) {
                        clientCharacteristics.put(address, characteristic);
                    }
                    callbackGatt.setCharacteristicNotification(characteristic, true);
                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID);
                    if (descriptor != null) {
                        enableNotifications(callbackGatt, descriptor);
                    } else {
                        flushNextWrite(address);
                    }
                }

                @Override
                public void onMtuChanged(BluetoothGatt callbackGatt, int mtu, int status) {
                    callbackGatt.discoverServices();
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt callbackGatt, BluetoothGattDescriptor descriptor, int status) {
                    if (CLIENT_CONFIG_UUID.equals(descriptor.getUuid())) {
                        flushNextWrite(address);
                    }
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt callbackGatt,
                                                   BluetoothGattCharacteristic characteristic, int status) {
                    if (PACKET_UUID.equals(characteristic.getUuid())) {
                        synchronized (clientGatts) {
                            clientWritesInProgress.remove(address);
                            List<byte[]> queue = pendingWrites.get(address);
                            if (queue != null && !queue.isEmpty()) {
                                queue.remove(0);
                            }
                        }
                        flushNextWrite(address);
                    }
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt callbackGatt,
                                                    BluetoothGattCharacteristic characteristic,
                                                    byte[] value) {
                    if (PACKET_UUID.equals(characteristic.getUuid())) {
                        handleGattPayload(address, value);
                    }
                }

                @Override
                @SuppressWarnings("deprecation")
                public void onCharacteristicChanged(BluetoothGatt callbackGatt,
                                                    BluetoothGattCharacteristic characteristic) {
                    if (PACKET_UUID.equals(characteristic.getUuid())) {
                        handleGattPayload(address, characteristic.getValue());
                    }
                }
            });
            if (gatt == null) {
                synchronized (clientGatts) {
                    connectingAddresses.remove(address);
                }
            }
        } catch (SecurityException exception) {
            synchronized (clientGatts) {
                connectingAddresses.remove(address);
            }
            Log.e(TAG, "Unable to connect to GATT peer: " + address, exception);
        }
    }

    private void handleGattPayload(String deviceAddress, byte[] payload) {
        if (payload != null && payload.length > 0 && listener != null) {
            listener.onPacketDiscovered(deviceAddress, payload);
        }
    }

    private void enableNotifications(BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            enableNotificationsLegacy(gatt, descriptor);
        }
    }

    @SuppressWarnings("deprecation")
    private void enableNotificationsLegacy(BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    private void removeGattClient(String address, BluetoothGatt gatt) {
        synchronized (clientGatts) {
            connectingAddresses.remove(address);
            clientGatts.remove(address);
            clientCharacteristics.remove(address);
            pendingWrites.remove(address);
            clientWritesInProgress.remove(address);
        }
        if (gatt != null) {
            gatt.close();
        }
    }

    private void enqueueClientWrite(String address, byte[] payload) {
        synchronized (clientGatts) {
            List<byte[]> queue = pendingWrites.get(address);
            if (queue == null) {
                queue = new ArrayList<>();
                pendingWrites.put(address, queue);
            }
            queue.add(payload.clone());
        }
        flushNextWrite(address);
    }

    private void flushNextWrite(String address) {
        BluetoothGatt gatt;
        BluetoothGattCharacteristic characteristic;
        byte[] payload;
        synchronized (clientGatts) {
            gatt = clientGatts.get(address);
            characteristic = clientCharacteristics.get(address);
            List<byte[]> queue = pendingWrites.get(address);
            if (gatt == null || characteristic == null || queue == null || queue.isEmpty()
                    || clientWritesInProgress.contains(address)) {
                return;
            }
            payload = queue.get(0);
            clientWritesInProgress.add(address);
        }

        boolean started = writeCharacteristic(gatt, characteristic, payload);
        if (!started) {
            synchronized (clientGatts) {
                clientWritesInProgress.remove(address);
            }
        }
    }

    private boolean writeCharacteristic(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic,
                                        byte[] payload) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return gatt.writeCharacteristic(characteristic, payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS;
        }
        return writeCharacteristicLegacy(gatt, characteristic, payload);
    }

    @SuppressWarnings("deprecation")
    private boolean writeCharacteristicLegacy(BluetoothGatt gatt,
                                              BluetoothGattCharacteristic characteristic,
                                              byte[] payload) {
        characteristic.setValue(payload);
        return gatt.writeCharacteristic(characteristic);
    }

    public void stopGattServer() {
        stopGattServiceAdvertising();
        stopGattClientScan();
        List<BluetoothGatt> gatts;
        synchronized (clientGatts) {
            gatts = new ArrayList<>(clientGatts.values());
            clientGatts.clear();
            clientCharacteristics.clear();
            pendingWrites.clear();
            connectingAddresses.clear();
            clientWritesInProgress.clear();
        }
        for (BluetoothGatt gatt : gatts) {
            gatt.close();
        }
        if (gattServer != null) {
            gattServer.close();
            gattServer = null;
        }
        synchronized (connectedDevices) {
            connectedDevices.clear();
        }
        packetCharacteristic = null;
        latestGattPayload = null;
    }

    public void relayToConnectedPeers(EmergencyPacket packet) {
        if (packet == null) {
            return;
        }
        try {
            PacketManager packetManager = new PacketManager();
            byte[] payload = packetManager.encodeGattPayload(packet);
            latestGattPayload = payload.clone();
            if (gattServer != null && packetCharacteristic != null) {
                synchronized (connectedDevices) {
                    for (BluetoothDevice device : connectedDevices) {
                        notifyCharacteristicChanged(device, payload);
                    }
                }
            }
            int clientCount;
            synchronized (clientGatts) {
                clientCount = clientCharacteristics.size();
                for (String address : clientCharacteristics.keySet()) {
                    enqueueClientWrite(address, payload);
                }
            }
            Log.d(TAG, "Relayed packet to " + (connectedDevices.size() + clientCount)
                    + " connected peer(s): " + packet.getPacketId());
        } catch (Exception exception) {
            Log.e(TAG, "Failed to relay packet via GATT", exception);
        }
    }

    private void notifyCharacteristicChanged(BluetoothDevice device, byte[] payload) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gattServer.notifyCharacteristicChanged(device, packetCharacteristic, false, payload);
        } else {
            notifyCharacteristicChangedLegacy(device, payload);
        }
    }

    @SuppressWarnings("deprecation")
    private void notifyCharacteristicChangedLegacy(BluetoothDevice device, byte[] payload) {
        packetCharacteristic.setValue(payload);
        gattServer.notifyCharacteristicChanged(device, packetCharacteristic, false);
    }
}
