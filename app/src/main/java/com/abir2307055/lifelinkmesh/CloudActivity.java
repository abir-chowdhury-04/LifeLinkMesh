package com.abir2307055.lifelinkmesh;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.abir2307055.lifelinkmesh.cloud.CloudSync;
import com.abir2307055.lifelinkmesh.databinding.ActivityCloudBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Cloud screen: Firebase Authentication (with email verification)
// and SOS alerts in the Realtime Database.
// Uses View Binding: the generated ActivityCloudBinding class gives typed access to every view.
public class CloudActivity extends AppCompatActivity {

    private static final String SOS_PATH = "sos";

    private ActivityCloudBinding binding;
    private FirebaseAuth auth;

    // Room must not run on the main thread, so database reads go through this.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCloudBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        binding.btnSignUp.setOnClickListener(v -> signUp());
        binding.btnSignIn.setOnClickListener(v -> signIn());
        binding.btnSignOut.setOnClickListener(v -> {
            auth.signOut();
            binding.txtCloudStatus.setText("Signed out");
            showUser();
        });
        binding.btnSendVerify.setOnClickListener(v -> sendVerification());
        binding.btnCheckVerified.setOnClickListener(v -> checkVerified());
        binding.btnUploadSos.setOnClickListener(v -> uploadSos());
        binding.btnOpenBoard.setOnClickListener(v ->
                startActivity(new Intent(this, SosBoardActivity.class)));

        showUser();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ---------------- Authentication ----------------

    private void signUp() {
        String email = binding.editEmail.getText().toString().trim();
        String password = binding.editPassword.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            binding.txtCloudStatus.setText("Enter email and password");
            return;
        }
        binding.txtCloudStatus.setText("Signing up...");
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        binding.txtCloudStatus.setText("Sign up OK. Sending verification email...");
                        sendVerification();
                    } else {
                        binding.txtCloudStatus.setText("Sign up failed: "
                                + task.getException().getMessage());
                    }
                    showUser();
                });
    }

    private void signIn() {
        String email = binding.editEmail.getText().toString().trim();
        String password = binding.editPassword.getText().toString();
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            binding.txtCloudStatus.setText("Enter email and password");
            return;
        }
        binding.txtCloudStatus.setText("Signing in...");
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        binding.txtCloudStatus.setText("Sign in OK");
                    } else {
                        binding.txtCloudStatus.setText("Sign in failed: "
                                + task.getException().getMessage());
                    }
                    showUser();
                });
    }

    private void sendVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            binding.txtCloudStatus.setText("Sign in first");
            return;
        }
        user.sendEmailVerification().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                binding.txtCloudStatus.setText("Verification email sent to " + user.getEmail()
                        + ". Open it (check spam too), click the link, then tap refresh.");
            } else {
                binding.txtCloudStatus.setText("Could not send: "
                        + task.getException().getMessage());
            }
        });
    }

    // The phone caches the user, so reload() fetches the fresh verified flag.
    private void checkVerified() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            binding.txtCloudStatus.setText("Sign in first");
            return;
        }
        user.reload().addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                binding.txtCloudStatus.setText("Refreshed");
            } else {
                binding.txtCloudStatus.setText("Refresh failed: "
                        + task.getException().getMessage());
            }
            showUser();
        });
    }

    private void showUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            binding.txtCloudUser.setText("Not signed in");
        } else if (user.isEmailVerified()) {
            binding.txtCloudUser.setText("Signed in: " + user.getEmail()
                    + "\nEmail verified: YES");
        } else {
            binding.txtCloudUser.setText("Signed in: " + user.getEmail()
                    + "\nEmail verified: NO");
        }
    }

    // ---------------- Realtime Database: SOS ----------------

    // Manual "upload now" uses the same code as the automatic timer.
    private void uploadSos() {
        binding.txtCloudStatus.setText("Uploading...");
        CloudSync.syncNow(this, message ->
                runOnUiThread(() -> binding.txtCloudStatus.setText(message)));
    }
}