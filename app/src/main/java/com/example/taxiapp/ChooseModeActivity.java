package com.example.taxiapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class ChooseModeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose_mode);

    }

    // Opens passengers sign up / sign in activity
    public void goToPassengerSignIn(View view) {
        startActivity(new Intent(ChooseModeActivity.this, PassengerSignInActivity.class));
    }

    // Opens drivers sign up / sign in activity
    public void goToDriverSignIn(View view) {
        startActivity(new Intent(ChooseModeActivity.this, DriverSignInActivity.class));
    }
}