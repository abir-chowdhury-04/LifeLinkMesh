package com.abir2307055.lifelinkmesh;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.abir2307055.lifelinkmesh.data.AppDatabase;
import com.abir2307055.lifelinkmesh.data.SosAlert;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.abir2307055.lifelinkmesh.cloud.CloudSync;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


// Cloud screen: Firebase Authentication (with email verification)
// and SOS alerts in the Realtime Database.
public class CloudActivity extends AppCompatActivity {

    private static final String SOS_PATH = "sos";

    private FirebaseAuth auth;
    private TextView txtUser;
    private TextView txtStatus;
    private TextView txtData;
    private EditText editEmail;
    private EditText editPassword;

    // Room must not run on the main thread, so database reads go through this.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud);

        auth = FirebaseAuth.getInstance();

        txtUser = findViewById(R.id.txtCloudUser);
        txtStatus = findViewById(R.id.txtCloudStatus);
        txtData = findViewById(R.id.txtCloudData);
        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        Button btnSignUp = findViewById(R.id.btnSignUp);
        Button btnSignIn = findViewById(R.id.btnSignIn);
        Button btnSignOut = findViewById(R.id.btnSignOut);
        Button btnSendVerify = findViewById(R.id.btnSendVerify);
        Button btnCheckVerified = findViewById(R.id.btnCheckVerified);
        Button btnUploadSos = findViewById(R.id.btnUploadSos);
        Button btnReadSos = findViewById(R.id.btnReadSos);

        btnSignUp.setOnClickListener(v -> signUp());
        btnSignIn.setOnClickListener(v -> signIn());
        btnSignOut.setOnClickListener(v -> {
            auth.signOut();
            txtStatus.setText("Signed out");
            showUser();
        });
        btnSendVerify.setOnClickListener(v -> sendVerification());
        btnCheckVerified.setOnClickListener(v -> checkVerified());
        btnUploadSos.setOnClickListener(v -> uploadSos());
        btnReadSos.setOnClickListener(v -> readSos());

        showUser();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ---------------- Authentication ----------------

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

    // The phone caches the user, so reload() fetches the fresh verified flag.
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

    // ---------------- Realtime Database: SOS ----------------

    // Manual "upload now" uses the same code as the automatic timer.
    private void uploadSos() {
        txtStatus.setText("Uploading...");
        CloudSync.syncNow(this, message -> runOnUiThread(() -> txtStatus.setText(message)));
    }

    private void readSos() {
        if (auth.getCurrentUser() == null) {
            // Still try, to show that the rules deny a signed-out user.
            txtStatus.setText("Reading while signed out (rules should deny this)...");
        } else {
            txtStatus.setText("Reading...");
        }
        FirebaseDatabase.getInstance().getReference(SOS_PATH)
                .orderByChild("timestamp")
                .limitToLast(10)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> lines = new ArrayList<>();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            String category = child.child("category").getValue(String.class);
                            String sender = child.child("senderName").getValue(String.class);
                            String note = child.child("note").getValue(String.class);
                            lines.add(category + " from " + sender + ": " + note);
                        }
                        // The query returns oldest first, so show the newest at the top.
                        StringBuilder sb = new StringBuilder();
                        for (int i = lines.size() - 1; i >= 0; i--) {
                            sb.append(lines.get(i)).append("\n\n");
                        }
                        txtData.setText(sb.length() == 0 ? "(nothing in the cloud yet)" : sb.toString());
                        txtStatus.setText("Read " + lines.size() + " SOS alert(s) from the cloud");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        txtStatus.setText("Read failed: " + error.getMessage());
                    }
                });
    }
}