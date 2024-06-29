package com.example.mychat.activites;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import com.example.mychat.R;
import com.example.mychat.adapters.ChatAdapter;
import com.example.mychat.databinding.ActivityChatBinding;
import com.example.mychat.models.ChatMessage;
import com.example.mychat.models.User;
import com.example.mychat.network.ApiClient;
import com.example.mychat.network.ApiServer;
import com.example.mychat.utilities.Constants;
import com.example.mychat.utilities.PreferenceManager;
import com.google.android.gms.tasks.NativeOnCompleteListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.concurrent.locks.Condition;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class ChatActivity extends BaseActivity{
    private ActivityChatBinding binding;
    private User receivedUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversionId = null;
    private Boolean isReceiverAvailable = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        LoadReceivedDetails();
        init();
        listenMessages();
    }
    private void init(){
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages=new ArrayList<>();
        chatAdapter = new ChatAdapter(
                getBitmapFromEncodedString(receivedUser.image),
                chatMessages,
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );
        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void sendMessage(){
        HashMap<String, Object>message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID,preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID,receivedUser.id);
        message.put(Constants.KEY_MESSAGE,binding.inputMessage.getText().toString());
        message.put(Constants.KEY_TIMESTAMP,new Date());
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if (conversionId != null){
            updateConversion(binding.inputMessage.getText().toString());
        }else {
            HashMap<String,Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_ID,preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME,preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE,preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID,receivedUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME,receivedUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE,receivedUser.image);
            conversion.put(Constants.KEY_LAST_MESSAGE,binding.inputMessage.getText().toString());
            conversion.put(Constants.KEY_TIMESTAMP,new Date());
            addConversion(conversion);
        }
        if(!isReceiverAvailable) {

            try {

                JSONArray tokens= new JSONArray();
                tokens.put(receivedUser.token);

                JSONObject data= new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());

                JSONObject body= new JSONObject();

                body.put(Constants.REMOTE_MSG_DATA, data);
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                sendNotification (body.toString());

            } catch (Exception exception) {
                showToast(exception.getMessage());
            }
        }
        binding.inputMessage.setText(null);
    }

    private void showToast(String message){
        Toast.makeText(getApplicationContext(),message,Toast.LENGTH_SHORT).show();
    }
    private void sendNotification(String messageBody){
        ApiClient.getClient().create(ApiServer.class).sendMessage(
                Constants.getRemoteMsgHeader(),
                messageBody
        ).enqueue(new Callback<String>(){

            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if(response.isSuccessful()){
                    try{
                        if (response.body() != null){
                            JSONObject reponseJson =new JSONObject(response.body());
                            JSONArray results = reponseJson.getJSONArray("Results");
                            if (reponseJson.getInt("Failure")==1){
                                JSONObject errror = (JSONObject) results.get(0);
                                showToast(errror.getString("Error"));
                                return;
                            }
                        }
                    }catch(JSONException e){
                        e.printStackTrace();
                    }
                    showToast("Notification sent Successfully ");
                }else {
                    showToast("Error: "+response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call,@NonNull Throwable t) {
                showToast(t.getMessage());
            }
        });
    }

    private void listenAvailabilityOfReveiver(){
        database.collection(Constants.KEY_COLLECTION_USERS).document(
                receivedUser.id
        ).addSnapshotListener(ChatActivity.this,(value, error) -> {
            if(error !=null){
                return;
            }
        if(value != null){
            if(value.getLong(Constants.KEY_AVAILABILITY) !=null){
                int availability = Objects.requireNonNull(
                        value.getLong(Constants.KEY_AVAILABILITY)
                ).intValue();
                isReceiverAvailable = availability ==1;
            }
            receivedUser.token = value.getString(Constants.KEY_FCM_TOKEN);
        }
        if(isReceiverAvailable){
            binding.textAvailability.setVisibility(View.VISIBLE);
        }else {
            binding.textAvailability.setVisibility(View.GONE);
        }
        });
    }


    private  void listenMessages(){
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID,preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID,receivedUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID,receivedUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID,preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }
    private final EventListener<QuerySnapshot> eventListener =(value, error) -> {
      if(error != null){
          return;
      }
      if(value != null){
          int count = chatMessages.size();
          for(DocumentChange documentChange : value.getDocumentChanges()){
              if(documentChange.getType() == DocumentChange.Type.ADDED){
                  ChatMessage chatMessage = new ChatMessage();
                  chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                  chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                  chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                  chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                  chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                  chatMessages.add(chatMessage);
              }
          }
          Collections.sort(chatMessages, Comparator.comparing(obj -> obj.dateObject));
          if(count ==0){
              chatAdapter.notifyDataSetChanged();
          }
          else {
              chatAdapter.notifyItemRangeInserted(chatMessages.size(),chatMessages.size());
              binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size()-1);
          }
          binding.chatRecyclerView.setVisibility(View.VISIBLE);
      }
      binding.progressBar.setVisibility(View.GONE);
      if (conversionId == null){
          checkForConversion();
      }
    };
    private Bitmap getBitmapFromEncodedString(String encodedImage){
        byte[] bytes= Base64.decode(encodedImage,Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length);
    }

    private void LoadReceivedDetails(){
        receivedUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receivedUser.name);
    }

    private void setListeners(){
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v->sendMessage());
    }
    private String getReadableDateTime(Date date){
        return new SimpleDateFormat("MMMM dd,yyyy - hh:mm", Locale.getDefault()).format(date);
    }

    private void addConversion(HashMap<String,Object> conversion){
        database.collection(Constants.KEY_COLLECTION_CONVERSATION)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }
    private  void updateConversion(String message){
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_CONVERSATION).document(conversionId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE,message,
                Constants.KEY_TIMESTAMP,new Date()
        );
    }
    private void checkForConversion(){
        if(chatMessages.size() !=0){
            checkForConversionRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receivedUser.id
            );
            checkForConversionRemotely(
                    receivedUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }
    private void checkForConversionRemotely(String senderId,String receiverId){
        database.collection(Constants.KEY_COLLECTION_CONVERSATION)
                .whereEqualTo(Constants.KEY_SENDER_ID,senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID,receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
        if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReveiver();
    }
}