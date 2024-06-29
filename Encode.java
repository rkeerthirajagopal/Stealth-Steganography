package com.example.mychat.activites;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.example.imagestegnographylibrary.Text.ImageSteganography;
import com.example.imagestegnographylibrary.Text.TextEncoding;
import com.example.imagestegnographylibrary.Text.AsyncTaskCallback.TextEncodingCallback;
import com.example.mychat.R;

public class Encode extends AppCompatActivity implements TextEncodingCallback {

    private static final int SELECT_PICTURE = 100;
    private static final String TAG = "Encode Class";

    private TextView whether_encoded;
    private ImageView imageView;
    private EditText message;
    private EditText secret_key;

    private TextEncoding textEncoding;
    private ImageSteganography imageSteganography;
    private ProgressDialog save;
    private Uri filepath;

    private Bitmap original_image;
    private Bitmap encoded_image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_encode);

        whether_encoded = findViewById(R.id.whether_encoded);
        imageView = findViewById(R.id.imageview);
        message = findViewById(R.id.message);
        secret_key = findViewById(R.id.secret_key);

        Button choose_image_button = findViewById(R.id.choose_image_button);
        Button encode_button = findViewById(R.id.encode_button);
        Button save_image_button = findViewById(R.id.save_image_button);

        checkAndRequestPermissions();

        choose_image_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ImageChooser();
            }
        });

        encode_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                whether_encoded.setText("");
                if (filepath != null) {
                    if (message.getText() != null) {
                        imageSteganography = new ImageSteganography(message.getText().toString(),
                                secret_key.getText().toString(),
                                original_image);
                        textEncoding = new TextEncoding(Encode.this, Encode.this);
                        textEncoding.execute(imageSteganography);
                    }
                }
            }
        });

        save_image_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (encoded_image != null) {
                    saveImageAsync(encoded_image);
                } else {
                    Toast.makeText(Encode.this, "No encoded image to save", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void ImageChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_PICTURE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            filepath = data.getData();
            try {
                original_image = MediaStore.Images.Media.getBitmap(getContentResolver(), filepath);
                imageView.setImageBitmap(original_image);
            } catch (IOException e) {
                Log.d(TAG, "Error : " + e);
            }
        }
    }

    @Override
    public void onStartTextEncoding() {
        // Whatever you want to do at the start of text encoding
    }

    @Override
    public void onCompleteTextEncoding(ImageSteganography result) {
        if (result != null && result.isEncoded()) {
            encoded_image = result.getEncoded_image();
            whether_encoded.setText("Encoded");
            imageView.setImageBitmap(encoded_image);
        }
    }

    private void saveImageAsync(final Bitmap imgToSave) {
        new SaveImageAsyncTask().execute(imgToSave);
    }

    private class SaveImageAsyncTask extends AsyncTask<Bitmap, Void, Boolean> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            save = new ProgressDialog(Encode.this);
            save.setMessage("Saving, Please Wait...");
            save.setTitle("Saving Image");
            save.setIndeterminate(false);
            save.setCancelable(false);
            save.show();
        }

        @Override
        protected Boolean doInBackground(Bitmap... bitmaps) {
            if (bitmaps.length > 0) {
                Bitmap imgToSave = bitmaps[0];
                return saveImageToMediaStore(imgToSave);
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result) {
                Toast.makeText(Encode.this, "Image saved successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(Encode.this, "Image save failed", Toast.LENGTH_SHORT).show();
            }
            if (save != null && save.isShowing()) {
                save.dismiss();
            }
        }
    }

    private boolean saveImageToMediaStore(Bitmap image) {
        try {
            ContentResolver contentResolver = getContentResolver();
            String displayName = "Encoded.png";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            Uri imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (imageUri != null) {
                OutputStream outputStream = contentResolver.openOutputStream(imageUri);
                if (outputStream != null) {
                    boolean saved = image.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                    outputStream.close();
                    return saved;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void checkAndRequestPermissions() {
        int ReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (ReadPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), 1);
        }
    }
}
