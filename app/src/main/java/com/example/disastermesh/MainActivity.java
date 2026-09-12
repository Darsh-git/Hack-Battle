package com.example.disastermesh;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bleprototype.R;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText etSender, etDisasterType, etDescription, etLocation;
    private Button btnSubmit;
    private RecyclerView recyclerView;
    private ReportAdapter adapter;
    private PacketRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repository = new PacketRepository(this);

        etSender = findViewById(R.id.etSender);
        etDisasterType = findViewById(R.id.etDisasterType);
        etDescription = findViewById(R.id.etDescription);
        etLocation = findViewById(R.id.etLocation);
        btnSubmit = findViewById(R.id.btnSubmit);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReportAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        btnSubmit.setOnClickListener(v -> saveReport());

        loadReports();
    }

    private void saveReport() {
        String sender = etSender.getText().toString().trim();
        String type = etDisasterType.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();
        String location = etLocation.getText().toString().trim();

        if (sender.isEmpty() || type.isEmpty()) {
            Toast.makeText(this, "Please fill in Name and Disaster Type", Toast.LENGTH_SHORT).show();
            return;
        }

        String rawId = sender.toLowerCase() + "_" + type.toLowerCase() + "_" + location.toLowerCase();
        String packetId = String.valueOf(rawId.hashCode());

        long currentTime = System.currentTimeMillis();

        EmergencyReport report = new EmergencyReport(
                packetId,
                type,
                "MEDIUM",           // severity
                location,
                currentTime,        // createdAt
                currentTime,        // receivedAt
                5,                  // initial TTL
                sender,             // sourceDevice
                0,                  // relayCount
                "PENDING"           // status
        );

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean isNew = repository.saveIfNew(report);

            runOnUiThread(() -> {
                if (isNew) {
                    Toast.makeText(MainActivity.this, "Report Saved Offline!", Toast.LENGTH_SHORT).show();
                    etSender.setText("");
                    etDisasterType.setText("");
                    etDescription.setText("");
                    etLocation.setText("");
                    loadReports();
                } else {
                    Toast.makeText(MainActivity.this, "Duplicate Report! Blocked by Database.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void loadReports() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<EmergencyReport> reports = repository.getAllPackets();
            runOnUiThread(() -> adapter.setReports(reports));
        });
    }
}
