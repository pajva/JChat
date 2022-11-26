package com.example.jchat.adapters;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.jchat.R;
import com.example.jchat.databinding.ItemContainerReceivedMessageBinding;
import com.example.jchat.databinding.ItemContainerSenndMessageBinding;
import com.example.jchat.models.ChatMessage;
import com.example.jchat.utilities.Constants;
import com.example.jchat.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.net.URL;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> chatMessages;
    private Bitmap receiverProfileImage;
    private final String senderId;

    public static final int VIEW_TYPE_SENT = 1;
    public static final int VIEW_TYPE_RECEIVED = 2;

    public void setReceiverProfileImage(Bitmap bitmap) {
        receiverProfileImage = bitmap;
    }

    public ChatAdapter(List<ChatMessage> chatMessages, Bitmap receiverProfileImage, String senderId) {
        this.chatMessages = chatMessages;
        this.receiverProfileImage = receiverProfileImage;
        this.senderId = senderId;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            return new SentMessageViewHolder(ItemContainerSenndMessageBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        } else {
            return new ReceiverMessageViewHolder(ItemContainerReceivedMessageBinding
                    .inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_SENT) {
            ((SentMessageViewHolder) holder).setData(chatMessages.get(position));
        } else {
            ((ReceiverMessageViewHolder) holder).setData(chatMessages.get(position), receiverProfileImage);
        }
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (chatMessages.get(position).senderId.equals(senderId)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {

        private final ItemContainerSenndMessageBinding binding;

        SentMessageViewHolder(ItemContainerSenndMessageBinding itemContainerSenndMessageBinding) {
            super(itemContainerSenndMessageBinding.getRoot());
            binding = itemContainerSenndMessageBinding;
        }

        void setData(ChatMessage chatMessage) {
            Bitmap bitmap = getBitmapFromEncodedString(chatMessage.image);
            Log.e("seen",chatMessage.message+" : adapter seen : "+chatMessage.seen);
            if (chatMessage.seen==true){
                binding.imageView1.setImageResource(R.drawable.ic_baseline_done_all_blue);
            }else{
                binding.imageView1.setImageResource(R.drawable.ic_baseline_done_all_24);
            }
            binding.imageView.setImageBitmap(bitmap);
            binding.textMessage.setText(chatMessage.message);
            binding.textDateTime.setText(chatMessage.dateTime);
            binding.rls.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    boolean isValid = URLUtil.isValidUrl(chatMessage.message);
                    if (isValid == true) {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(chatMessage.message));
                        itemView.getContext().startActivity(i);
                    }
                }
            });
            binding.rls.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    binding.rls.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            boolean isValid = URLUtil.isValidUrl(chatMessage.message);
                            if (isValid == true) {
                                Intent i = new Intent(Intent.ACTION_VIEW);
                                i.setData(Uri.parse(chatMessage.message));
                                itemView.getContext().startActivity(i);
                            }
                        }
                    });
                    AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                    builder.setMessage("Delete Message?");
                    builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            FirebaseFirestore database = FirebaseFirestore.getInstance();
                            database.collection(Constants.KEY_COLLECTION_CHAT).document(chatMessage.chatId);
                            DocumentReference documentReference =
                                    database.collection(Constants.KEY_COLLECTION_CHAT).document(chatMessage.chatId);
                            documentReference.update(Constants.KEY_MESSAGE, "You deleted this message", Constants.KEY_IMAGE, null);
                            binding.textMessage.setText("You deleted this message");
                            binding.imageView.setImageBitmap(null);
                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Toast.makeText(view.getContext(), "Cancel", Toast.LENGTH_SHORT).show();
                        }
                    });
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                    return false;
                }
            });
        }
    }

    static class ReceiverMessageViewHolder extends RecyclerView.ViewHolder {

        private final ItemContainerReceivedMessageBinding binding;

        ReceiverMessageViewHolder(ItemContainerReceivedMessageBinding itemContainerReceivedMessageBinding) {
            super(itemContainerReceivedMessageBinding.getRoot());
            binding = itemContainerReceivedMessageBinding;
        }

        void setData(ChatMessage chatMessage, Bitmap receiverProfileImage) {
            Bitmap bitmap = getBitmapFromEncodedString(chatMessage.image);
            binding.imageView.setImageBitmap(bitmap);
            binding.textMessage.setText(chatMessage.message);
            binding.textDateTime.setText(chatMessage.dateTime);
            if (receiverProfileImage != null) {
                binding.imageProfile.setImageBitmap(receiverProfileImage);
            }
            binding.rlr.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    binding.rlr.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            boolean isValid = URLUtil.isValidUrl(chatMessage.message);
                            if (isValid == true) {
                                Intent i = new Intent(Intent.ACTION_VIEW);
                                i.setData(Uri.parse(chatMessage.message));
                                itemView.getContext().startActivity(i);
                            }
                        }
                    });
                    binding.rlr.setOnClickListener(null);
                    AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                    builder.setMessage("Delete Message?");
                    builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            FirebaseFirestore database = FirebaseFirestore.getInstance();
                            database.collection(Constants.KEY_COLLECTION_CHAT).document(chatMessage.chatId);
                            DocumentReference documentReference =
                                    database.collection(Constants.KEY_COLLECTION_CHAT).document(chatMessage.chatId);
                            documentReference.update(Constants.KEY_MESSAGE, "You deleted this message", Constants.KEY_IMAGE, null);
                            binding.textMessage.setText("You deleted this message");
                            binding.imageView.setImageBitmap(null);
                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Toast.makeText(view.getContext(), "Cancel", Toast.LENGTH_SHORT).show();
                        }
                    });
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                    binding.rlr.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            boolean isValid = URLUtil.isValidUrl(chatMessage.message);
                            if (isValid == true) {
                                Intent i = new Intent(Intent.ACTION_VIEW);
                                i.setData(Uri.parse(chatMessage.message));
                                itemView.getContext().startActivity(i);
                            }
                        }
                    });
                    return false;
                }
            });
        }
    }

    private static Bitmap getBitmapFromEncodedString(String encodedImage) {
        if (encodedImage != null) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else {
            return null;
        }

    }
}
