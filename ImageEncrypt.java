package com.example.mychat.activites;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.mychat.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ImageEncrypt extends AppCompatActivity {

    private static final int READ_EXTERNAL_STORAGE_PERMISSION_REQUEST = 100;

    private Button encryptBtn, decryptBtn;
    private EditText encImgToText;
    private ImageView imageView;
    private ClipboardManager clipboardManager;

    private String sImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_encrypt);

        encryptBtn = findViewById(R.id.enc_img_btn);
        decryptBtn = findViewById(R.id.dec_img_btn);
        encImgToText = findViewById(R.id.encText);
        imageView = findViewById(R.id.imageView);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        encryptBtn.setOnClickListener(view -> requestReadExternalStoragePermission());

        decryptBtn.setOnClickListener(this::decodeImage);
    }

    private void requestReadExternalStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_PERMISSION_REQUEST);
        } else {
            selectImage();
        }
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), READ_EXTERNAL_STORAGE_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            selectImage();
        } else {
            Toast.makeText(this, "Permission Denied!", Toast.LENGTH_SHORT).show();
        }
    }

    private void decodeImage(View view) {
        byte[] bytes = Base64.decode(encImgToText.getText().toString(), Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        imageView.setImageBitmap(bitmap);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_REQUEST && resultCode == RESULT_OK && data != null) {
            handleImageSelection(data.getData());
        }
    }

    private void handleImageSelection(Uri uri) {
        Bitmap bitmap;
        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);

        try {
            bitmap = ImageDecoder.decodeBitmap(source);
            encodeImage(bitmap);
            Toast.makeText(this, "Image encrypted! Click on Decrypt to restore!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void encodeImage(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byte[] bytes = stream.toByteArray();
        sImage = Base64.encodeToString(bytes, Base64.DEFAULT);
        encImgToText.setText(sImage);
    }

    public void copyImgCode(View view) {
        String data = encImgToText.getText().toString().trim();
        if (!data.isEmpty()) {
            ClipData temp = ClipData.newPlainText("text", data);
            clipboardManager.setPrimaryClip(temp);
            Toast.makeText(this, "Copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }
}
