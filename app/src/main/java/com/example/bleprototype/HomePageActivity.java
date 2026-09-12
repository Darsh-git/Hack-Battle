package com.example.bleprototype;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class HomePageActivity extends AppCompatActivity {

    private Button userButton;
    private Button responderButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        userButton = findViewById(R.id.btn_user);
        responderButton = findViewById(R.id.btn_responder);

        // User button - goes to the existing MainActivity
        userButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomePageActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // Responder button - goes to responder login
        responderButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomePageActivity.this, ResponderLoginActivity.class);
            startActivity(intent);
        });
    }
}
