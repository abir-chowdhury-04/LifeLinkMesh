package com.abir2307055.lifelinkmesh.cloud;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

// Backs up the display name to Firestore users/{uid}. The phone keeps its own copy;
// this one is only for restoring the name on another phone.
public class ProfileSync {

    public static void backupName(String name, CloudSync.Listener listener) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            listener.onResult("Name saved on this phone only (sign in with a verified account to back it up)");
            return;
        }
        // Fresh token, so the "email verified" flag the rules check is up to date.
        user.getIdToken(true).addOnCompleteListener(tokenTask -> {
            if (!tokenTask.isSuccessful()) {
                listener.onResult("Name saved on this phone. Backup failed: "
                        + tokenTask.getException().getMessage());
                return;
            }
            Map<String, Object> doc = new HashMap<>();
            doc.put("displayName", name);
            // merge: only this field changes, the emergency contacts stay untouched.
            FirebaseFirestore.getInstance().collection("users").document(user.getUid())
                    .set(doc, SetOptions.merge())
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            listener.onResult("Name saved on this phone and backed up to the cloud");
                        } else {
                            listener.onResult("Name saved on this phone. Backup failed: "
                                    + task.getException().getMessage());
                        }
                    });
        });
    }
}