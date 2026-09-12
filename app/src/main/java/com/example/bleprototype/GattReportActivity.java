package com.example.bleprototype;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.bleprototype.ble.BleManager;
import com.example.bleprototype.model.EmergencyPacket;
import com.example.bleprototype.network.PacketManager;
import com.example.bleprototype.storage.PacketRepository;

public class GattReportActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST_CODE = 7001;
    private static final long LOCATION_TIMEOUT_MS = 10000L;
    private final PacketManager packetManager = new PacketManager();
    private EditText descriptionInput;
    private EditText reporterInput;
    private EditText contactInput;
    private EditText peopleInput;
    private EditText assistanceInput;
    private EditText notesInput;
    private Spinner typeInput;
    private Spinner severityInput;
    private TextView locationView;
    private String location = "Unknown";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gatt_report);

        typeInput = findViewById(R.id.report_type);
        severityInput = findViewById(R.id.report_severity);
        descriptionInput = findViewById(R.id.report_description);
        reporterInput = findViewById(R.id.report_reporter);
        contactInput = findViewById(R.id.report_contact);
        peopleInput = findViewById(R.id.report_people);
        assistanceInput = findViewById(R.id.report_assistance);
        notesInput = findViewById(R.id.report_notes);
        locationView = findViewById(R.id.report_location);

        typeInput.setAdapter(createAdapter(new String[]{
                "MEDICAL", "FIRE", "FLOOD", "ACCIDENT", "EARTHQUAKE", "SHELTER", "OTHER"}));
        severityInput.setAdapter(createAdapter(new String[]{"LOW", "MEDIUM", "CRITICAL"}));

        Button locationButton = findViewById(R.id.report_location_button);
        Button sendButton = findViewById(R.id.report_send_button);
        Button cancelButton = findViewById(R.id.report_cancel_button);
        locationButton.setOnClickListener(v -> captureLocation());
        sendButton.setOnClickListener(v -> sendReport());
        cancelButton.setOnClickListener(v -> finish());
    }

    private ArrayAdapter<String> createAdapter(String[] values) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(0xFF000000);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(0xFFFFFFFF);
                return view;
            }
        };
    }

    private void captureLocation() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST_CODE);
            return;
        }
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            showLocationUnavailable();
            return;
        }

        Location best = lastKnownLocation(locationManager, LocationManager.GPS_PROVIDER);
        Location network = lastKnownLocation(locationManager, LocationManager.NETWORK_PROVIDER);
        if (best == null || (network != null && network.getTime() > best.getTime())) {
            best = network;
        }
        if (best != null) {
            setLocation(best);
            return;
        }

        locationView.setText("Location: getting a current fix...");
        final Location[] current = {null};
        android.location.LocationListener listener = new android.location.LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location newLocation) {
                if (current[0] == null || newLocation.getAccuracy() < current[0].getAccuracy()) {
                    current[0] = newLocation;
                    setLocation(newLocation);
                }
            }
        };
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener);
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    locationManager.removeUpdates(listener);
                } catch (SecurityException ignored) {
                    // Permission may have been revoked while waiting.
                }
                if (current[0] == null) {
                    showLocationUnavailable();
                }
            }, LOCATION_TIMEOUT_MS);
        } catch (SecurityException exception) {
            showLocationUnavailable();
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void setLocation(Location current) {
        location = String.format(java.util.Locale.US, "%.6f, %.6f", current.getLatitude(), current.getLongitude());
        locationView.setText("Location: " + location);
    }

    private void showLocationUnavailable() {
        locationView.setText("Location: unavailable. Enable location services or enter it in Other information.");
    }

    private Location lastKnownLocation(LocationManager manager, String provider) {
        try {
            if (manager.isProviderEnabled(provider)) {
                return manager.getLastKnownLocation(provider);
            }
        } catch (SecurityException ignored) {
            // Permission can change while the activity is open.
        }
        return null;
    }

    private void sendReport() {
        BleManager bleManager = MainActivity.getSharedBleManager();
        if (bleManager == null || !bleManager.isBluetoothReady()) {
            Toast.makeText(this, "Bluetooth is unavailable or not permitted", Toast.LENGTH_LONG).show();
            return;
        }

        long now = System.currentTimeMillis();
        EmergencyPacket packet = new EmergencyPacket(
                packetManager.generatePacketId(),
                typeInput.getSelectedItem().toString(),
                severityInput.getSelectedItem().toString(),
                location,
                now,
                now,
                5,
                "local",
                0,
                "PENDING"
        ).withReportDetails(
                text(descriptionInput), text(reporterInput), text(contactInput),
                text(peopleInput), text(assistanceInput), text(notesInput));

        PacketRepository repository = new PacketRepository(this);
        boolean saved = repository.saveIfNew(packet);
        repository.close();
        if (saved) {
            bleManager.relayToConnectedPeers(packet);
            Toast.makeText(this, "Report queued for nearby GATT peers", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "Could not save report", Toast.LENGTH_LONG).show();
        }
    }

    private String text(EditText input) {
        return input.getText().toString().trim();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE && hasLocationPermission()) {
            captureLocation();
        }
    }
}
