package com.abir2307055.lifelinkmesh;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.abir2307055.lifelinkmesh.data.AppDatabase;
import com.abir2307055.lifelinkmesh.data.ChatMessage;
import com.abir2307055.lifelinkmesh.data.MessageDao;
import com.abir2307055.lifelinkmesh.mesh.MeshHub;
import com.abir2307055.lifelinkmesh.ui.MessageAdapter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.appcompat.app.AlertDialog;

public class ChatActivity extends AppCompatActivity implements MeshHub.ChatListener {

    private MeshHub hub;
    private MessageAdapter adapter;
    private RecyclerView recyclerMessages;
    private EditText editMessage;
    private MessageDao dao;
    // Background thread used only to load saved messages when the screen opens.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logLifecycle("onCreate");
        setContentView(R.layout.activity_chat);

        hub = MeshHub.getInstance(this);
        dao = AppDatabase.getInstance(this).messageDao();

        recyclerMessages = findViewById(R.id.recyclerMessages);
        editMessage = findViewById(R.id.editMessage);
        Button btnSend = findViewById(R.id.btnSend);

        Button btnClearChat = findViewById(R.id.btnClearChat);
        btnClearChat.setOnClickListener(v -> confirmClear());

        adapter = new MessageAdapter();
        recyclerMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());
        loadMessages();
    }

    private void loadMessages() {
        executor.execute(() -> {
            List<ChatMessage> saved = dao.getAll();
            runOnUiThread(() -> {
                adapter.setMessages(saved);
                scrollToBottom();
                // Start listening only after the saved messages are shown,
                // so no message is shown twice.
                hub.addChatListener(this);
            });
        });
    }

    private void sendMessage() {
        String text = editMessage.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        editMessage.setText("");
        hub.sendChat(text);   // the hub saves it, sends it, and calls onChatMessage
    }

    // Called by the hub for every new message, sent or received.
    @Override
    public void onChatMessage(ChatMessage message) {
        runOnUiThread(() -> {
            adapter.addMessage(message);
            scrollToBottom();
        });
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear chat history?")
                .setMessage("This deletes the saved messages on this phone only.")
                .setPositiveButton("Clear", (dialog, which) -> executor.execute(() -> {
                    dao.deleteAll();
                    runOnUiThread(() -> adapter.clear());
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            recyclerMessages.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    // Lifecycle logging, for the demo (Logcat filter: LifeLinkLifecycle).
    private void logLifecycle(String callback) {
        Log.d("LifeLinkLifecycle", "ChatActivity " + callback);
    }

    @Override protected void onStart() { super.onStart(); logLifecycle("onStart"); }
    @Override protected void onResume() { super.onResume(); logLifecycle("onResume"); }
    @Override protected void onPause() { super.onPause(); logLifecycle("onPause"); }
    @Override protected void onStop() { super.onStop(); logLifecycle("onStop"); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logLifecycle("onDestroy");
        hub.removeChatListener(this);
        executor.shutdown();
    }
}