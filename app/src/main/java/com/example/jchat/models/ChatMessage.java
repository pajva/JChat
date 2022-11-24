package com.example.jchat.models;

import com.google.firebase.firestore.DocumentChange;

import java.util.Date;

public class ChatMessage {

    public String senderId, receiverId, message, dateTime, image;
    public Date dateObject;
    public String chatId, chatName, chatImage;
    public Boolean seen;
}
