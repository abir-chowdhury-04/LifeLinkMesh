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
import com.abir2307055.lifelinkmesh.ui.MessageAdapter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_LOCAL_NAME = "local_name";

    private MessageAdapter adapter;
    private RecyclerView recyclerMessages;
    private EditText editMessage;
    private String localName;
    private MessageDao dao;
    // One background thread for all database work (Room refuses the main thread).
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logLifecycle("onCreate");
        setContentView(R.layout.activity_chat);

        // Value sent by MainActivity inside the Intent.
        localName = getIntent().getStringExtra(EXTRA_LOCAL_NAME);
        if (localName == null) {
            localName = "Me";
        }

        dao = AppDatabase.getInstance(this).messageDao();

        recyclerMessages = findViewById(R.id.recyclerMessages);
        editMessage = findViewById(R.id.editMessage);
        Button btnSend = findViewById(R.id.btnSend);

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
            });
        });
    }

    private void sendMessage() {
        String text = editMessage.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        ChatMessage message = new ChatMessage(
                UUID.randomUUID().toString(), localName, text,
                System.currentTimeMillis(), true);
        editMessage.setText("");

        executor.execute(() -> {
            dao.insert(message);                 // save to Room (background thread)
            runOnUiThread(() -> {                // update the screen (main thread)
                adapter.addMessage(message);
                scrollToBottom();
            });
        });
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
        executor.shutdown();
    }
}