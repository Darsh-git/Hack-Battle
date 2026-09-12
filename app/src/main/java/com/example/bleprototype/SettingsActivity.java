package com.example.bleprototype;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SettingsActivity extends AppCompatActivity {
    private static final String PREFERENCES_NAME = "app_preferences";
    private static final String DARK_THEME_KEY = "dark_theme";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        SharedPreferences preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        Switch themeSwitch = findViewById(R.id.switch_app_theme);
        themeSwitch.setChecked(preferences.getBoolean(DARK_THEME_KEY, false));
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(DARK_THEME_KEY, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        ImageButton backButton = findViewById(R.id.btn_settings_back);
        backButton.setOnClickListener(v -> finish());
    }
}
