package com.example.bleprototype;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;

import com.example.bleprototype.ble.BleManager;
import com.example.bleprototype.model.EmergencyPacket;
import com.example.bleprototype.network.PacketManager;
import com.example.bleprototype.network.RelayManager;
import com.example.bleprototype.storage.PacketRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final String NOTIFICATION_CHANNEL_ID = "critical_packets";

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
    private TextView networkHealthView;
    private TextView emptyPacketsView;
    private EditText packetSearchInput;
    private Button advertisingButton;
    private boolean scanning;
    private boolean advertising;
    private String selectedFilter = "ALL";
    private String packetSearch = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences preferences = getSharedPreferences("app_preferences", MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(preferences.getBoolean("dark_theme", false)
            ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        packetCountView = findViewById(R.id.tv_packet_count);
        connectionStatusView = findViewById(R.id.tv_connection_status);
        networkHealthView = findViewById(R.id.tv_network_health);
        emptyPacketsView = findViewById(R.id.tv_empty_packets);
        packetSearchInput = findViewById(R.id.et_packet_search);
        packetRepository = new PacketRepository(this);
        createNotificationChannel();

        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recyclerView);
        packetAdapter = new PacketAdapter(new ArrayList<>(), this::showFullReport);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        recyclerView.setAdapter(packetAdapter);

        advertisingButton = findViewById(R.id.btn_start_advertising);
        findViewById(R.id.btn_settings).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        Button scanningButton = findViewById(R.id.btn_start_scanning);
        Spinner packetFilter = findViewById(R.id.spinner_packet_filter);
        packetFilter.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"All packets", "Pending relays", "Relayed", "Critical"}));
        packetFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedFilter = position == 1 ? "PENDING" : position == 2 ? "RELAYED" :
                        position == 3 ? "CRITICAL" : "ALL";
                refreshPackets();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        packetSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                packetSearch = s.toString().trim().toLowerCase(Locale.US);
                refreshPackets();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        advertisingButton.setOnClickListener(v -> toggleAdvertising());
        scanningButton.setOnClickListener(v -> toggleScanning(scanningButton));

        if (requestNeededPermissions()) {
            initializeBleManager();
        }
        cleanupExpiredPackets();
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
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
                if ("CRITICAL".equalsIgnoreCase(packet.getSeverity())) {
                    notifyCriticalPacket(packet);
                }
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
            databaseExecutor.execute(() -> {
                packetRepository.markRelayed(receivedPacket.getPacketId(), forwardedPacket.getTtl());
                runOnUiThread(this::refreshPackets);
            });
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
                    log("A requested permission was denied.");
                }
            }
            if (hasRequiredBluetoothPermissions()) {
                initializeBleManager();
                log("Bluetooth permissions granted.");
            }
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
            cleanupExpiredPackets();
            refreshPackets();
        }
    }

    private void cleanupExpiredPackets() {
        databaseExecutor.execute(() -> {
            int removed = packetRepository.removeExpiredPackets();
            if (removed > 0) {
                log("Removed " + removed + " expired packet(s).");
            }
        });
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
            List<EmergencyPacket> packets = packetRepository.getAllPackets();
            List<EmergencyPacket> filtered = new ArrayList<>();
            int pendingCount = 0;
            long latestReceivedAt = packets.isEmpty() ? 0L : packets.get(0).getReceivedAt();
            for (EmergencyPacket packet : packets) {
                if ("PENDING".equalsIgnoreCase(packet.getStatus()) && packet.getTtl() > 0) {
                    pendingCount++;
                }
                boolean matchesFilter = "ALL".equals(selectedFilter)
                        || "PENDING".equals(selectedFilter) && "PENDING".equalsIgnoreCase(packet.getStatus())
                        || "RELAYED".equals(selectedFilter) && "RELAYED".equalsIgnoreCase(packet.getStatus())
                        || "CRITICAL".equals(selectedFilter) && "CRITICAL".equalsIgnoreCase(packet.getSeverity());
                String searchable = (packet.getPacketId() + " " + packet.getType() + " "
                        + packet.getSeverity() + " " + packet.getLocation()).toLowerCase(Locale.US);
                if (matchesFilter && (packetSearch.isEmpty() || searchable.contains(packetSearch))) {
                    filtered.add(packet);
                }
            }
            final int pendingPacketCount = pendingCount;
            final long latestPacketTime = latestReceivedAt;
            runOnUiThread(() -> {
                packetAdapter.setPackets(filtered);
                packetCountView.setText(("ALL".equals(selectedFilter) ? "Packets: " : "Matches: ")
                        + filtered.size());
                emptyPacketsView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                emptyPacketsView.setText(packets.isEmpty() ? "No packets received yet"
                    : packetSearch.isEmpty() ? "No packets match this filter"
                    : "No packets match your search");
                updateNetworkHealth(pendingPacketCount, latestPacketTime);
            });
        });
    }

            private void updateNetworkHealth(int pendingCount, long latestReceivedAt) {
        String bluetooth = bleManager != null && bleManager.isBluetoothReady() ? "ready" : "unavailable";
        String activity = scanning ? "scanning" : advertising ? "relaying" : "idle";
        int peers = bleManager == null ? 0 : bleManager.getConnectedPeerCount();
            String lastPacket = latestReceivedAt <= 0 ? "none"
                : android.text.format.DateFormat.format("HH:mm", latestReceivedAt).toString();
        networkHealthView.setText("Network: Bluetooth " + bluetooth + " • " + activity
                + " • Peers: " + peers + " • Pending: " + pendingCount
                + " • Last packet: " + lastPacket);
    }

    private boolean hasRequiredBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                    == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, "Critical packets", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts for critical emergency packets");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void notifyCriticalPacket(EmergencyPacket packet) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Critical emergency packet")
                .setContentText(packet.getType() + " • " + packet.getLocation())
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(packet.getPacketId().hashCode(), builder.build());
        }
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
                .setTitle("CRITICAL".equalsIgnoreCase(packet.getSeverity())
                        ? "Critical report" : "Full report")
                .setMessage(report.toString())
                .setNeutralButton("Copy ID", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Packet ID", packet.getPacketId()));
                        Toast.makeText(this, "Packet ID copied", Toast.LENGTH_SHORT).show();
                    }
                })
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
