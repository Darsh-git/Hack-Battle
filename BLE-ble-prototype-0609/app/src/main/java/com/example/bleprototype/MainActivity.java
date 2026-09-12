package com.example.bleprototype;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.bleprototype.ble.BleManager;
import com.example.bleprototype.model.EmergencyPacket;
import com.example.bleprototype.network.PacketManager;
import com.example.bleprototype.network.RelayManager;
import com.example.bleprototype.storage.PacketRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A foreground-only demonstration of BLE store-and-forward relaying. */
public class MainActivity extends AppCompatActivity implements BleManager.Listener {
    private static final String TAG = "BLEPrototype";
    private static final int REQUEST_CODE = 1001;
    private static final int INITIAL_TTL = 5;
    private static final long RELAY_MIN_DELAY_MS = 700;
    private static final long RELAY_DELAY_JITTER_MS = 800;

    private final PacketManager packetManager = new PacketManager();
    private final RelayManager relayManager = new RelayManager();
    private final Handler relayHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private BleManager bleManager;
    private PacketRepository packetRepository;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logView = findViewById(R.id.logView);
        packetRepository = new PacketRepository(this);

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        bleManager = new BleManager(this, adapter);
        bleManager.setListener(this);

        Button startAdvertising = findViewById(R.id.btn_start_advertising);
        Button startScanning = findViewById(R.id.btn_start_scanning);
        Button sendPacket = findViewById(R.id.btn_send_packet);
        startAdvertising.setOnClickListener(v -> advertiseNewPacket());
        startScanning.setOnClickListener(v -> startScanning());
        sendPacket.setOnClickListener(v -> advertiseNewPacket());

        requestNeededPermissions();
    }

    private void requestNeededPermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(missing, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_ADVERTISE);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQUEST_CODE);
        }
    }

    private void addIfMissing(List<String> permissions, String permission) {
        if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private void startScanning() {
        if (bleManager.isBluetoothReady()) {
            bleManager.startScanning();
            log("Scanning for emergency packets.");
        } else {
            log("Bluetooth is unavailable, disabled, or not permitted.");
        }
    }

    private void advertiseNewPacket() {
        if (!bleManager.isBluetoothReady()) {
            log("Bluetooth is unavailable, disabled, or not permitted.");
            return;
        }
        EmergencyPacket packet = new EmergencyPacket(packetManager.generatePacketId(), "MEDICAL", INITIAL_TTL,
                System.currentTimeMillis(), 0.0, 0.0, "local");
        packetRepository.savePacket(packet);
        relayManager.markSeen(packet.getPacketId());
        advertise(packet, "Created and advertising");
    }

    private void advertise(EmergencyPacket packet, String action) {
        try {
            bleManager.startAdvertising(packetManager.encodeForAdvertisement(packet));
            log(action + " " + packet);
        } catch (IllegalArgumentException exception) {
            log("Could not advertise packet: " + exception.getMessage());
        }
    }

    @Override
    public void onPacketDiscovered(String deviceAddress, byte[] payload) {
        try {
            EmergencyPacket packet = packetManager.decodeAdvertisement(payload, deviceAddress);
            if (!packetManager.validatePacket(packet)) {
                log("Ignored invalid packet from " + deviceAddress);
                return;
            }
            if (packetRepository.hasPacket(packet.getPacketId()) || relayManager.isDuplicate(packet.getPacketId())) {
                log("Ignored duplicate packet " + packet.getPacketId());
                return;
            }
            if (relayManager.receivePacket(packet) == null) {
                return;
            }
            packetRepository.savePacket(packet);
            log("Received and stored " + packet);
            scheduleRelay(packet);
        } catch (IllegalArgumentException exception) {
            log("Ignored malformed BLE packet from " + deviceAddress + ": " + exception.getMessage());
        }
    }

    private void scheduleRelay(EmergencyPacket receivedPacket) {
        EmergencyPacket forwardedPacket = relayManager.decrementTtl(receivedPacket);
        if (!relayManager.shouldRelay(forwardedPacket)) {
            log("Packet " + receivedPacket.getPacketId() + " reached TTL 0; not relaying.");
            return;
        }
        long delay = RELAY_MIN_DELAY_MS + random.nextInt((int) RELAY_DELAY_JITTER_MS + 1);
        relayHandler.postDelayed(() -> advertise(forwardedPacket, "Relaying after " + delay + " ms"), delay);
        log("Scheduled relay of " + forwardedPacket.getPacketId() + " with TTL=" + forwardedPacket.getTtl());
    }

    @Override
    public void onScanFailure(String message) {
        log(message);
    }

    @Override
    public void onAdvertiseFailure(String message) {
        log(message);
    }

    @Override
    public void onAdvertiseSuccess() {
        log("BLE advertising started.");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    log("Bluetooth permission was denied.");
                    return;
                }
            }
            log("Bluetooth permissions granted.");
        }
    }

    @Override
    protected void onDestroy() {
        relayHandler.removeCallbacksAndMessages(null);
        bleManager.stopScanning();
        bleManager.stopAdvertising();
        packetRepository.close();
        super.onDestroy();
    }

    private void log(String message) {
        Log.d(TAG, message);
        runOnUiThread(() -> {
            String existing = logView.getText() == null ? "" : logView.getText().toString();
            logView.setText(existing + "\n" + message);
        });
    }
}
