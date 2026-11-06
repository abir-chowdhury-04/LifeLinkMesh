package com.abir2307055.lifelinkmesh;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// Cloud screen: Firebase Authentication (email + password).
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

        btnSignUp.setOnClickListener(v -> signUp());
        btnSignIn.setOnClickListener(v -> signIn());
        btnSignOut.setOnClickListener(v -> {
            auth.signOut();
            txtStatus.setText("Signed out");
            showUser();
        });

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
                        txtStatus.setText("Sign up OK");
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

    // Shows who is signed in right now (null means nobody).
    private void showUser() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            txtUser.setText("Not signed in");
        } else {
            txtUser.setText("Signed in: " + user.getEmail());
        }
    }
}