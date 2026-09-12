package com.example.bleprototype;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ResponderDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_responder_dashboard);

        TextView dashboardHeading = findViewById(R.id.tv_dashboard_heading);
        dashboardHeading.setText("Dashboard");
    }
}
