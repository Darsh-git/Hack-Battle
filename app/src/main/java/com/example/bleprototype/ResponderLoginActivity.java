package com.example.bleprototype;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ResponderLoginActivity extends AppCompatActivity {

    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private Button signupButton;
    private TextView toggleButton;
    private boolean isLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_responder_login);

        emailEditText = findViewById(R.id.et_email);
        passwordEditText = findViewById(R.id.et_password);
        loginButton = findViewById(R.id.btn_login);
        signupButton = findViewById(R.id.btn_signup);
        toggleButton = findViewById(R.id.tv_toggle_mode);

        // Login button
        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(ResponderLoginActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            } else {
                // TODO: Add authentication logic here
                Toast.makeText(ResponderLoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();
                navigateToDashboard();
            }
        });

        // Sign up button (only visible in sign up mode)
        signupButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(ResponderLoginActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            } else {
                // TODO: Add sign up logic here
                Toast.makeText(ResponderLoginActivity.this, "Sign up successful", Toast.LENGTH_SHORT).show();
                navigateToDashboard();
            }
        });

        // Toggle between login and sign up
        toggleButton.setOnClickListener(v -> {
            isLoginMode = !isLoginMode;
            updateUI();
        });
    }

    private void updateUI() {
        if (isLoginMode) {
            toggleButton.setText("Don't have an account? Sign up");
            loginButton.setVisibility(android.view.View.VISIBLE);
            signupButton.setVisibility(android.view.View.GONE);
        } else {
            toggleButton.setText("Already have an account? Login");
            loginButton.setVisibility(android.view.View.GONE);
            signupButton.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(ResponderLoginActivity.this, ResponderDashboardActivity.class);
        startActivity(intent);
        finish();
    }
}
