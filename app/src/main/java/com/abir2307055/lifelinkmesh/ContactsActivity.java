package com.abir2307055.lifelinkmesh;

import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.abir2307055.lifelinkmesh.data.ProfileStore;
import com.google.firebase.firestore.SetOptions;

import androidx.appcompat.app.AppCompatActivity;

import com.abir2307055.lifelinkmesh.data.ContactStore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Emergency contacts: saved on the phone (offline) and backed up in Firestore users/{uid}.
public class ContactsActivity extends AppCompatActivity {

    private static final String USERS = "users";
    private static final String FIELD_EMAILS = "emergencyEmails";

    private EditText editContact1;
    private EditText editContact2;
    private TextView txtStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        editContact1 = findViewById(R.id.editContact1);
        editContact2 = findViewById(R.id.editContact2);
        txtStatus = findViewById(R.id.txtContactsStatus);
        Button btnSave = findViewById(R.id.btnSaveContacts);
        Button btnRestore = findViewById(R.id.btnRestoreContacts);

        // Show what is already saved on this phone.
        List<String> saved = ContactStore.load(this);
        if (saved.size() > 0) editContact1.setText(saved.get(0));
        if (saved.size() > 1) editContact2.setText(saved.get(1));

        btnSave.setOnClickListener(v -> save());
        btnRestore.setOnClickListener(v -> restore());
    }

    private boolean isValid(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void save() {
        String e1 = editContact1.getText().toString().trim();
        String e2 = editContact2.getText().toString().trim();
        if (e1.isEmpty() || !isValid(e1)) {
            txtStatus.setText("Contact 1 must be a valid email address");
            return;
        }
        if (!e2.isEmpty() && !isValid(e2)) {
            txtStatus.setText("Contact 2 is not a valid email address");
            return;
        }

        // 1) Always save on the phone first: this is the copy SOS uses, with no internet.
        ContactStore.save(this, e1, e2);

        // 2) Back up to the cloud only if a verified user is signed in.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            txtStatus.setText("Saved on this phone. Sign in with a verified account "
                    + "(Cloud screen) to back up to the cloud.");
            return;
        }
        txtStatus.setText("Saved on this phone. Backing up...");
        List<String> emails = new ArrayList<>();
        emails.add(e1);
        if (!e2.isEmpty()) emails.add(e2);
        Map<String, Object> doc = new HashMap<>();
        doc.put(FIELD_EMAILS, emails);
        doc.put("updatedAt", System.currentTimeMillis());

        user.getIdToken(true).addOnCompleteListener(this, tokenTask -> {
            if (!tokenTask.isSuccessful()) {
                txtStatus.setText("Saved on this phone. Token refresh failed: "
                        + tokenTask.getException().getMessage());
                return;
            }
            FirebaseFirestore.getInstance().collection(USERS).document(user.getUid())
                    .set(doc, SetOptions.merge())
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            txtStatus.setText("Saved on this phone and backed up to the cloud");
                        } else {
                            txtStatus.setText("Saved on this phone. Backup failed: "
                                    + task.getException().getMessage());
                        }
                    });
        });
    }

    // For a new phone: pull the contacts saved under this account.
    private void restore() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            txtStatus.setText("Sign in with a verified account first (Cloud screen)");
            return;
        }
        txtStatus.setText("Restoring...");
        user.getIdToken(true).addOnCompleteListener(this, tokenTask -> {
            if (!tokenTask.isSuccessful()) {
                txtStatus.setText("Token refresh failed: " + tokenTask.getException().getMessage());
                return;
            }
            FirebaseFirestore.getInstance().collection(USERS).document(user.getUid())
                    .get()
                    .addOnCompleteListener(this, task -> {
                        if (!task.isSuccessful()) {
                            txtStatus.setText("Restore failed: " + task.getException().getMessage());
                            return;
                        }
                        // The name is restored too (the mesh uses it the next time it starts).
                        String cloudName = task.getResult().exists()
                                ? task.getResult().getString("displayName") : null;
                        if (cloudName != null && !cloudName.isEmpty()) {
                            ProfileStore.saveName(this, cloudName);
                        }
                        Object raw = task.getResult().exists()
                                ? task.getResult().get(FIELD_EMAILS) : null;
                        if (!(raw instanceof List)) {
                            txtStatus.setText("Nothing saved in the cloud for this account yet");
                            return;
                        }
                        List<?> list = (List<?>) raw;
                        String e1 = list.size() > 0 ? String.valueOf(list.get(0)) : "";
                        String e2 = list.size() > 1 ? String.valueOf(list.get(1)) : "";
                        ContactStore.save(this, e1, e2);
                        editContact1.setText(e1);
                        editContact2.setText(e2);
                        txtStatus.setText("Restored from the cloud");
                    });
        });
    }
}