package com.example.taxiapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Objects;

public class DriverSignInActivity extends AppCompatActivity {

    private TextInputLayout textInputEmail;
    private TextInputLayout textInputName;
    private TextInputLayout textInputPassword;
    private TextInputLayout textInputConfirmPassword;

    private Button signUpButton;
    private Button signInButton;
    private TextView toggleSignInTextView;

    private boolean isLoginModeActive;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_sign_in);

        auth = FirebaseAuth.getInstance();

        // If driver is already signed in move to map activity
        if(auth.getCurrentUser() != null){
            startActivity(new Intent(DriverSignInActivity.this, DriverMapsActivity.class));
            finish();
        }

        textInputEmail = findViewById(R.id.activity_driver_sign_in_til_email);
        textInputName = findViewById(R.id.activity_driver_sign_in_til_name);
        textInputPassword = findViewById(R.id.activity_driver_sign_in_til_password);
        textInputConfirmPassword = findViewById(R.id.activity_driver_sign_in_til_confirm_password);

        signUpButton = findViewById(R.id.activity_driver_sign_in_btn_sign_up);
        signInButton = findViewById(R.id.activity_driver_sign_in_btn_sign_in);
        toggleSignInTextView = findViewById(R.id.activity_driver_sign_in_tv_toggle_sign_in);
    }

    private boolean validateEmail(){
        String emailInput = Objects.requireNonNull(textInputEmail.getEditText()).getText().toString().trim();

        if (emailInput.isEmpty()){
            textInputEmail.setError("Please enter your email");
            return false;
            // Checks email using default pattern matcher
        } else if(!android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput).matches()) {
            textInputEmail.setError("Please enter email correctly.");
            return false;
        }else {
            textInputEmail.setError("");
            return true;
        }
    }

    private boolean validateName(){
        String nameInput = Objects.requireNonNull(textInputName.getEditText()).getText().toString().trim();

        if (nameInput.isEmpty()){
            textInputName.setError("Please enter your name");
            return false;
        } else if(nameInput.length() > 16) {
            textInputName.setError("Name length have to be less than 16");
            return false;
        }else {
            textInputName.setError("");
            return true;
        }
    }

    private boolean validatePassword(){
        String passwordInput = Objects.requireNonNull(textInputPassword.getEditText()).getText().toString().trim();
        String confirmPasswordInput = Objects.requireNonNull(textInputConfirmPassword.getEditText()).getText().toString().trim();

        if (passwordInput.isEmpty()){
            textInputPassword.setError("Please enter your password");
            return false;
        } else if(passwordInput.length() < 7) {
            textInputPassword.setError("Password length have to be more than 6");
            return false;
        }else if(!passwordInput.equals(confirmPasswordInput)) {
            textInputPassword.setError("Password doesn't match");
            return false;
        }else {
            textInputPassword.setError("");
            return true;
        }
    }

    public void signUpDriver(View view) {

        // Check if provided inputs from driver is correct
        if(!validateEmail() || !validateName() || !validatePassword()){
            return;
        }

        String name = Objects.requireNonNull(textInputName.getEditText()).getText().toString().trim();
        String email = Objects.requireNonNull(textInputEmail.getEditText()).getText().toString().trim();
        String password = Objects.requireNonNull(textInputPassword.getEditText()).getText().toString().trim();

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign up success, show welcome message to the driver
                        auth.getCurrentUser();
                        Toast.makeText(DriverSignInActivity.this, "Welcome, " + name, Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(DriverSignInActivity.this, DriverMapsActivity.class));
                        finish();
                    } else {
                        // If sign up fails, display a message to the driver
                        Toast.makeText(DriverSignInActivity.this, "Registration failed. Try again", Toast.LENGTH_SHORT).show();
                    }
                });
        }

    public void signInDriver(View view){

        String email = Objects.requireNonNull(textInputEmail.getEditText()).getText().toString().trim();
        String password = Objects.requireNonNull(textInputPassword.getEditText()).getText().toString().trim();

        if(email.isEmpty()){
            textInputEmail.setError("Please enter your email");
        }else if (password.isEmpty()){
            textInputPassword.setError("Please enter your password");
        }else {
            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // Sign in success, show welcome message to the driver
                            auth.getCurrentUser();
                            Toast.makeText(DriverSignInActivity.this, "Welcome", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(DriverSignInActivity.this, DriverMapsActivity.class));
                            finish();
                        } else {
                            // If sign in fails, display a message to the driver
                            Toast.makeText(DriverSignInActivity.this, "Email or Password is incorrect.", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    public void toggleSignIn(View view) {

        // If driver wants to sign up show all objects
        if(isLoginModeActive){
            isLoginModeActive = false;
            toggleSignInTextView.setText(R.string.already_have_an_account);
            textInputConfirmPassword.setVisibility(View.VISIBLE);
            textInputName.setVisibility(View.VISIBLE);
            signUpButton.setVisibility(View.VISIBLE);
            signInButton.setVisibility(View.GONE);
        }else {
            // If driver wants to sign in hide inputs and change buttons
            isLoginModeActive = true;
            toggleSignInTextView.setText(R.string.register_now);
            textInputConfirmPassword.setVisibility(View.GONE);
            textInputName.setVisibility(View.GONE);
            signUpButton.setVisibility(View.GONE);
            signInButton.setVisibility(View.VISIBLE);
        }
    }
}