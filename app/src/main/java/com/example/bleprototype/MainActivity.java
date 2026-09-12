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
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private static BleManager sharedBleManager;

    private BleManager bleManager;
    private PacketRepository packetRepository;
    private PacketAdapter packetAdapter;
    private TextView packetCountView;
    private TextView connectionStatusView;
    private Spinner packetTypeInput;
    private Spinner packetSeverityInput;
    private Button advertisingButton;
    private boolean pendingOnly;
    private boolean scanning;
    private boolean advertising;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        packetCountView = findViewById(R.id.tv_packet_count);
        connectionStatusView = findViewById(R.id.tv_connection_status);
        packetTypeInput = findViewById(R.id.et_packet_type);
        packetSeverityInput = findViewById(R.id.et_packet_severity);
        packetTypeInput.setAdapter(createSpinnerAdapter(
            new String[]{"MEDICAL", "FIRE", "FLOOD", "ACCIDENT", "EARTHQUAKE", "SHELTER", "OTHER"}));
        packetSeverityInput.setAdapter(createSpinnerAdapter(
            new String[]{"LOW", "MEDIUM", "CRITICAL"}));
        packetRepository = new PacketRepository(this);

        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recyclerView);
        packetAdapter = new PacketAdapter(new ArrayList<>(), this::showFullReport);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        recyclerView.setAdapter(packetAdapter);

        advertisingButton = findViewById(R.id.btn_start_advertising);
        Button scanningButton = findViewById(R.id.btn_start_scanning);
        Button allPackets = findViewById(R.id.btn_filter_all);
        Button pendingPackets = findViewById(R.id.btn_filter_pending);
        Button removeExpired = findViewById(R.id.btn_remove_expired);
        advertisingButton.setOnClickListener(v -> toggleAdvertising());
        scanningButton.setOnClickListener(v -> toggleScanning(scanningButton));
        allPackets.setOnClickListener(v -> {
            pendingOnly = false;
            refreshPackets();
        });
        pendingPackets.setOnClickListener(v -> {
            pendingOnly = true;
            refreshPackets();
        });
        removeExpired.setOnClickListener(v -> databaseExecutor.execute(() -> {
            int removed = packetRepository.removeExpiredPackets();
            runOnUiThread(() -> {
                log("Removed " + removed + " expired packet(s).");
                refreshPackets();
            });
        }));

        if (requestNeededPermissions()) {
            initializeBleManager();
        }
        refreshPackets();
    }

    private boolean requestNeededPermissions() {
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
            return false;
        }
        return true;
    }

    private void initializeBleManager() {
        if (bleManager != null) {
            return;
        }
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        bleManager = new BleManager(this, adapter);
        sharedBleManager = bleManager;
        bleManager.setListener(this);
        bleManager.startGattServer();
    }

    private void addIfMissing(List<String> permissions, String permission) {
        if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private ArrayAdapter<String> createSpinnerAdapter(String[] values) {

    return new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            values
    ) {

        @Override
        public View getView(
                int position,
                View convertView,
                ViewGroup parent) {

            TextView view = (TextView) super.getView(
                    position,
                    convertView,
                    parent
            );

            view.setTextColor(0xFF101828);
            view.setTextSize(16);
            view.setGravity(android.view.Gravity.CENTER_VERTICAL);

            return view;
        }

        @Override
        public View getDropDownView(
                int position,
                View convertView,
                ViewGroup parent) {

            TextView view = (TextView) super.getDropDownView(
                    position,
                    convertView,
                    parent
            );

            view.setTextColor(0xFF101828);
            view.setTextSize(16);
            view.setGravity(android.view.Gravity.CENTER_VERTICAL);

            view.setPadding(
                    20,
                    18,
                    20,
                    18
            );

            view.setBackgroundColor(0xFFFFFFFF);

            return view;
        }
    };
}

    private void toggleScanning(Button scanningButton) {
        if (scanning) {
            if (bleManager == null) {
                scanning = false;
                return;
            }
            bleManager.stopScanning();
            scanning = false;
            scanningButton.setText("Start Scanning");
            connectionStatusView.setText("Bluetooth ready • Monitoring idle");
            log("Scanning stopped.");
            return;
        }

        if (bleManager != null && bleManager.isBluetoothReady()) {
            bleManager.startScanning();
            scanning = true;
            scanningButton.setText("Stop Scanning");
            connectionStatusView.setText("Bluetooth ready • Scanning for packets");
            log("Scanning for emergency packets.");
        } else {
            log("Bluetooth is unavailable, disabled, or not permitted.");
        }
    }

    private void toggleAdvertising() {
        openGattReport();
    }

    private void advertiseNewPacket() {
        if (bleManager == null || !bleManager.isBluetoothReady()) {
            log("Bluetooth is unavailable, disabled, or not permitted.");
            return;
        }
        String type = packetTypeInput.getSelectedItem().toString();
        String severity = packetSeverityInput.getSelectedItem().toString();
        if (!packetManager.isSupportedType(type) ||
                !("LOW".equals(severity) || "MEDIUM".equals(severity) || "CRITICAL".equals(severity)) ||
                INITIAL_TTL < 1 || INITIAL_TTL > 255) {
            Toast.makeText(this, "Type: MEDICAL, FIRE, FLOOD, ACCIDENT, EARTHQUAKE, SHELTER, or OTHER", Toast.LENGTH_SHORT).show();
            return;
        }

        long now = System.currentTimeMillis();
        EmergencyPacket packet = new EmergencyPacket(packetManager.generatePacketId(), type, severity,
                "Unknown", now, now, INITIAL_TTL, "local", 0, "PENDING");
        databaseExecutor.execute(() -> {
            boolean isNew = packetRepository.saveIfNew(packet);
            runOnUiThread(() -> {
                if (isNew) {
                    relayManager.markSeen(packet.getPacketId());
                    refreshPackets();
                }
                advertise(packet, isNew ? "Created and advertising" : "Duplicate packet");
            });
        });
    }

    private void advertise(EmergencyPacket packet, String action) {
        try {
            bleManager.startAdvertising(packetManager.encodeForAdvertisement(packet));
            advertising = true;
            advertisingButton.setText("Stop Advertising");
            log(action + " " + packet);
        } catch (IllegalArgumentException exception) {
            log("Could not advertise packet: " + exception.getMessage());
        }
    }

    @Override
    public void onPacketDiscovered(String deviceAddress, byte[] payload) {
        try {
            EmergencyPacket packet = packetManager.decodeTransportPayload(payload, deviceAddress);
            if (!packetManager.validatePacket(packet)) {
                log("Ignored invalid packet from " + deviceAddress);
                return;
            }
            if (relayManager.isDuplicate(packet.getPacketId())) {
                log("Ignored duplicate packet " + packet.getPacketId());
                return;
            }
            databaseExecutor.execute(() -> {
                if (!packetRepository.saveIfNew(packet)) {
                    log("Ignored duplicate packet " + packet.getPacketId());
                    return;
                }
                relayManager.markSeen(packet.getPacketId());
                log("Received and stored " + packet);
                runOnUiThread(() -> refreshPackets());
                scheduleRelay(packet);
            });
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
        relayHandler.postDelayed(() -> {
            bleManager.relayToConnectedPeers(forwardedPacket);
            advertise(forwardedPacket, "Relaying after " + delay + " ms");
        }, delay);
        log("Scheduled relay of " + forwardedPacket.getPacketId() + " with TTL=" + forwardedPacket.getTtl());
    }

    @Override
    public void onScanFailure(String message) {
        scanning = false;
        log(message);
    }

    @Override
    public void onAdvertiseFailure(String message) {
        advertising = false;
        advertisingButton.setText("Send Packet");
        log(message);
    }

    @Override
    public void onAdvertiseSuccess() {
        advertising = true;
        advertisingButton.setText("Stop Advertising");
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
            initializeBleManager();
            log("Bluetooth permissions granted.");
        }
    }

    @Override
    protected void onDestroy() {
        relayHandler.removeCallbacksAndMessages(null);
        if (bleManager != null) {
            bleManager.stopScanning();
            bleManager.stopAdvertising();
            bleManager.stopGattServer();
        }
        if (sharedBleManager == bleManager) {
            sharedBleManager = null;
        }
        databaseExecutor.shutdown();
        packetRepository.close();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (packetAdapter != null) {
            refreshPackets();
        }
    }

    private void openGattReport() {
        if (bleManager == null || !bleManager.isBluetoothReady()) {
            Toast.makeText(this, "Bluetooth is unavailable or not permitted", Toast.LENGTH_LONG).show();
            return;
        }
        if (!scanning) {
            bleManager.startScanning();
            scanning = true;
            connectionStatusView.setText("Bluetooth ready • Discovering GATT peers");
        }
        startActivity(new android.content.Intent(this, GattReportActivity.class));
    }

    private void refreshPackets() {
        databaseExecutor.execute(() -> {
            List<EmergencyPacket> packets = pendingOnly
                    ? packetRepository.getPendingRelays() : packetRepository.getAllPackets();
            runOnUiThread(() -> {
                packetAdapter.setPackets(packets);
                packetCountView.setText((pendingOnly ? "Pending relays: " : "Packets: ") + packets.size());
            });
        });
    }

    private void log(String message) {
        Log.d(TAG, message);
    }

    private void showFullReport(EmergencyPacket packet) {
        StringBuilder report = new StringBuilder();
        appendReportField(report, "Packet ID", packet.getPacketId());
        appendReportField(report, "Type", packet.getType());
        appendReportField(report, "Severity", packet.getSeverity());
        appendReportField(report, "Description", packet.getDescription());
        appendReportField(report, "Location", packet.getLocation());
        appendReportField(report, "Reporter", packet.getReporterName());
        appendReportField(report, "Contact", packet.getContactInfo());
        appendReportField(report, "People affected", packet.getPeopleAffected());
        appendReportField(report, "Assistance needed", packet.getAssistanceNeeded());
        appendReportField(report, "Other information", packet.getNotes());
        appendReportField(report, "Source device", packet.getSourceDevice());
        appendReportField(report, "TTL", String.valueOf(packet.getTtl()));
        appendReportField(report, "Relay count", String.valueOf(packet.getRelayCount()));
        appendReportField(report, "Status", packet.getStatus());
        appendReportField(report, "Created", String.valueOf(packet.getCreatedAt()));
        appendReportField(report, "Received", String.valueOf(packet.getReceivedAt()));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Full report")
                .setMessage(report.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private void appendReportField(StringBuilder report, String label, String value) {
        if (value != null && !value.trim().isEmpty()) {
            report.append(label).append(": ").append(value).append("\n\n");
        }
    }

    public static BleManager getSharedBleManager() {
        return sharedBleManager;
    }
}
