package com.example.jchat.activities;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.jchat.R;
import com.example.jchat.adapters.ChatAdapter;
import com.example.jchat.databinding.ActivityChatBinding;
import com.example.jchat.models.ChatMessage;
import com.example.jchat.models.User;
import com.example.jchat.network.ApiClient;
import com.example.jchat.network.ApiService;
import com.example.jchat.utilities.Constants;
import com.example.jchat.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversationId = null;
    private Boolean isReceiverAvailable = false;
    private Boolean isMessageSeen = false;
    private String encodedImage;
    int notification = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
        init();
        listenMessages();
    }


    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages, getBitmapFromEncodedString(receiverUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID));
        binding.chatRecylerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void sendMessage() {
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constants.KEY_TIMESTAMP, new Date());
        message.put(Constants.KEY_SEEN,isMessageSeen);
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if (conversationId != null) {
            updateConversation(binding.inputMessage.getText().toString());
        } else {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constants.KEY_LAST_MESSAGE, binding.inputMessage.getText().toString());
            conversion.put(Constants.KEY_TIMESTAMP, new Date());
            addConversation(conversion);
        }
        if (!isReceiverAvailable) {
            try {
                JSONArray tokens = new JSONArray();
                tokens.put(receiverUser.token);

                JSONObject data = new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());

                JSONObject body = new JSONObject();
                body.put(Constants.REMOTE_MSG_DATA, data);
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);
                sendNotification(body.toString());
            } catch (Exception exception) {
                showToast(exception.getMessage());
            }
        }
        binding.inputMessage.setText(null);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void sendNotification(String messageBody) {
        ApiClient.getClient().create(ApiService.class).sendMessage(
                Constants.getRemoteMsgHeaders(), messageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    notification = 1;
                    try {
                        if (response.body() != null) {
                            JSONObject responseJson = new JSONObject(response.body());
                            JSONArray results = responseJson.getJSONArray("results");
                            if (responseJson.getInt("failure") == 1) {
                                JSONObject error = (JSONObject) results.get(0);
                                showToast(error.getString("error"));
                                return;
                            }

                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    showToast("Notification sent successfully");
                } else {
                    notification = 0;
                    showToast("Error: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                showToast(t.getMessage());
            }
        });
    }

    private void listenAvailabilityOfReceiver() {
        database.collection(Constants.KEY_COLLECTION_USERS).document(
                receiverUser.id).addSnapshotListener(ChatActivity.this, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                if (error != null) {
                    return;
                }
                if (value != null) {
                    if (value.getLong(Constants.KEY_AVAILABILITY) != null) {
                        int availability = Objects.requireNonNull(value.getLong(Constants.KEY_AVAILABILITY)).intValue();
                        isReceiverAvailable = availability == 1;
                    }
                    receiverUser.token = value.getString(Constants.KEY_FCM_TOKEN);
                    if (receiverUser.image == null) {
                        receiverUser.image = value.getString(Constants.KEY_IMAGE);
                        chatAdapter.setReceiverProfileImage(ChatActivity.this.getBitmapFromEncodedString(receiverUser.image));
                        chatAdapter.notifyItemRangeChanged(0, chatMessages.size());
                    }
                }
                if (isReceiverAvailable) {
                    binding.textAvailability.setVisibility(View.VISIBLE);
                } else {
                    binding.textAvailability.setVisibility(View.GONE);
                }
            }
        });
    }

    private void listenMessages() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = (new EventListener<QuerySnapshot>() {
        @Override
        public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
            if (error != null) {
                return;
            }
            if (value != null) {
                int count = chatMessages.size();
                for (DocumentChange documentChange : value.getDocumentChanges()) {
                    ChatMessage chatMessage = new ChatMessage();
                    if (documentChange.getType() == DocumentChange.Type.ADDED) {
                        chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                        chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                        chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                        chatMessage.dateTime = ChatActivity.this.getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                        chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                        chatMessage.chatId = documentChange.getDocument().getId();
                        preferenceManager.putString(Constants.KEY_SENDER_IDD,documentChange.getDocument().getString(Constants.KEY_SENDER_ID));
                        preferenceManager.putString(Constants.KEY_CHAT_ID, documentChange.getDocument().getId());
                        chatMessage.image = documentChange.getDocument().getString(Constants.KEY_IMAGE);
                        chatMessage.seen = documentChange.getDocument().getBoolean(Constants.KEY_SEEN);
                        chatMessages.add(chatMessage);
                    }
                    if (documentChange.getType() == DocumentChange.Type.MODIFIED){
                        chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                        chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                        chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                        chatMessage.dateTime = ChatActivity.this.getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                        chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                        chatMessage.chatId = documentChange.getDocument().getId();
                        chatMessage.seen = documentChange.getDocument().getBoolean(Constants.KEY_SEEN);
                        chatMessage.image = documentChange.getDocument().getString(Constants.KEY_IMAGE);
                        chatMessage.seen = documentChange.getDocument().getBoolean(Constants.KEY_SEEN);
                        chatMessages.set(count-1, chatMessage);
                        Log.e("seen",chatMessage.message+" : activity seen : "+chatMessage.seen);
                        chatAdapter.notifyItemInserted(documentChange.getDocument().hashCode());
                        chatAdapter.notifyDataSetChanged();
                    }
                }
                Collections.sort(chatMessages, new Comparator<ChatMessage>() {
                    @Override
                    public int compare(ChatMessage obj1, ChatMessage obj2) {
                        return obj1.dateObject.compareTo(obj2.dateObject);
                    }
                });
                chatAdapter.notifyItemInserted(chatAdapter.getItemCount());
                if (count == 0) {
                    chatAdapter.notifyDataSetChanged();
                } else {
                    chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                    binding.chatRecylerView.smoothScrollToPosition(chatMessages.size() - 1);
                }
                binding.chatRecylerView.setVisibility(View.VISIBLE);
            }
            binding.progressBar.setVisibility(View.GONE);
            if (conversationId == null) {
                ChatActivity.this.checkForConversation();
            }
        }
    });

    private Bitmap getBitmapFromEncodedString(String encodedImage) {
        if (encodedImage != null) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else {
            return null;
        }

    }

    private void loadReceiverDetails() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receiverUser.name);
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> sendMessage());
        binding.iv1.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pickImage.launch(intent);
        });
    }

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            InputStream inputStream = getContentResolver().openInputStream(imageUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            encodedImage = encodedImage(bitmap);
                            sendImage(encodedImage);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
    );

    private void sendImage(String encodedImage) {
        HashMap<String, Object> image = new HashMap<>();
        image.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        image.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        image.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        image.put(Constants.KEY_TIMESTAMP, new Date());
        image.put(Constants.KEY_IMAGE, encodedImage);
        image.put(Constants.KEY_SEEN,isMessageSeen);
        database.collection(Constants.KEY_COLLECTION_CHAT).add(image);
    }

    private String encodedImage(Bitmap bitmap) {
        int previewWidth = 150;
        int previewHeight = bitmap.getHeight() * previewWidth / bitmap.getWidth();
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void addConversation(HashMap<String, Object> conversion) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        conversationId = documentReference.getId();
                    }
                });

    }

    private void updateConversation(String message) {
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversationId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, new Date());
    }

    private void checkForConversation() {
        if (chatMessages.size() != 0) {
            checkForConversationRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.id);
            checkForConversationRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID));
        }
    }

    private void checkForConversationRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversationOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversationOnCompleteListener = new OnCompleteListener<QuerySnapshot>() {
        @Override
        public void onComplete(@androidx.annotation.NonNull Task<QuerySnapshot> task) {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
                DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
                conversationId = documentSnapshot.getId();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
        try {
            String s1 = preferenceManager.getString(Constants.KEY_SENDER_IDD);
            String r1 = receiverUser.id;

            DocumentReference documentReference1 = database.collection(Constants.KEY_COLLECTION_CHAT)
                    .document(preferenceManager.getString(Constants.KEY_CHAT_ID));

            if (s1.equals(r1)){
                documentReference1.update(Constants.KEY_SEEN,true);
            }
        }catch (NullPointerException e){
            Log.e("n", e.getMessage());
        }

    }
}