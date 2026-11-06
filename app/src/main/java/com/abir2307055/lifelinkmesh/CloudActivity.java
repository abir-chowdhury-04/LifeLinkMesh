package com.abir2307055.lifelinkmesh;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Cloud screen: Firebase Authentication (email + password) with email verification.
public class CloudActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private TextView txtUser;
    private TextView txtStatus;
    private EditText editEmail;
    private EditText editPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud);

        auth = FirebaseAuth.getInstance();

        txtUser = findViewById(R.id.txtCloudUser);
        txtStatus = findViewById(R.id.txtCloudStatus);
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        Button btnSignUp = findViewById(R.id.btnSignUp);
        Button btnSignIn = findViewById(R.id.btnSignIn);
        Button btnSignOut = findViewById(R.id.btnSignOut);
        Button btnSendVerify = findViewById(R.id.btnSendVerify);
        Button btnCheckVerified = findViewById(R.id.btnCheckVerified);

        btnSignUp.setOnClickListener(v -> signUp());
        btnSignIn.setOnClickListener(v -> signIn());
        btnSignOut.setOnClickListener(v -> {
            auth.signOut();
            txtStatus.setText("Signed out");
            showUser();
        });
        btnSendVerify.setOnClickListener(v -> sendVerification());
        btnCheckVerified.setOnClickListener(v -> checkVerified());

        showUser();
    }

    private void signUp() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            txtStatus.setText("Enter email and password");
            return;
        }
        txtStatus.setText("Signing up...");
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        txtStatus.setText("Sign up OK. Sending verification email...");
                        // A new account is signed in automatically, so we can send the link now.
                        sendVerification();
                    } else {
                        txtStatus.setText("Sign up failed: " + task.getException().getMessage());
                    }
                    showUser();
                });
    }

    private void signIn() {
        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            txtStatus.setText("Enter email and password");
            return;
        }
        txtStatus.setText("Signing in...");
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        txtStatus.setText("Sign in OK");
                    } else {
                        txtStatus.setText("Sign in failed: " + task.getException().getMessage());
                    }
                    showUser();
                });
    }

    // Asks Firebase to email a verification link to the signed-in user.
    private void sendVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            txtStatus.setText("Sign in first");
            return;
        }
        user.sendEmailVerification().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                txtStatus.setText("Verification email sent to " + user.getEmail()
                        + ". Open it (check spam too), click the link, then tap refresh.");
            } else {
                txtStatus.setText("Could not send: " + task.getException().getMessage());
            }
        });
    }

    // The phone caches the user, so reload() fetches the fresh verified flag from Firebase.
    private void checkVerified() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            txtStatus.setText("Sign in first");
            return;
        }
        user.reload().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                txtStatus.setText("Refreshed");
            } else {
                txtStatus.setText("Refresh failed: " + task.getException().getMessage());
            }
            showUser();
        });
    }

    // Shows who is signed in and whether the email is verified.
    private void showUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            txtUser.setText("Not signed in");
        } else if (user.isEmailVerified()) {
            txtUser.setText("Signed in: " + user.getEmail() + "\nEmail verified: YES");
        } else {
            txtUser.setText("Signed in: " + user.getEmail() + "\nEmail verified: NO");
        }
    }
}