package com.example.mychat.activites;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.example.mychat.R;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.ContentValues;
import android.content.ContentResolver;
import java.io.OutputStream;
import android.net.Uri;

public class MaskEncode extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_IMAGE_UPLOAD = 2;
    private static final int REQUEST_IMAGE_ENCRYPT = 3;

    private ImageView imageView, encryptedImageView;
    private Button uploadImageBtn, encryptBtn, saveEncryptImageBtn;

    private Bitmap originalBitmap, encryptedBitmap;
    private ExecutorService backgroundExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mask_encode);

        imageView = findViewById(R.id.imageView);
        encryptedImageView = findViewById(R.id.encryptedImageView);
        uploadImageBtn = findViewById(R.id.uploadImageBtn);
        encryptBtn = findViewById(R.id.encryptBtn);
        saveEncryptImageBtn = findViewById(R.id.saveEncryptImageBtn);

        requestStoragePermissions();

        backgroundExecutor = Executors.newSingleThreadExecutor();

        uploadImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openGallery(REQUEST_IMAGE_UPLOAD);
            }
        });

        encryptBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (originalBitmap != null) {
                    backgroundExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            encryptedBitmap = encryptImage(originalBitmap);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    imageView.setImageBitmap(originalBitmap);
                                    encryptedImageView.setImageBitmap(encryptedBitmap);
                                    Toast.makeText(MaskEncode.this, "Image encrypted.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                }
            }
        });

        saveEncryptImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (encryptedBitmap != null) {
                    saveImage(encryptedBitmap, "encrypted_image.jpg");
                }
            }
        });
    }

    private void requestStoragePermissions() {
        int writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        if (writePermission != PackageManager.PERMISSION_GRANTED || readPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openGallery(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_UPLOAD) {
                try {
                    originalBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
                    imageView.setImageBitmap(originalBitmap);
                    Toast.makeText(this, "Image uploaded.", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (requestCode == REQUEST_IMAGE_ENCRYPT) {
                try {
                    encryptedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
                    Toast.makeText(this, "Encrypted image uploaded.", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Bitmap encryptImage(Bitmap original) {
        Bitmap encryptedBitmap = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Bitmap.Config.RGB_565);

        for (int x = 0; x < original.getWidth(); x++) {
            for (int y = 0; y < original.getHeight(); y++) {
                int pixel;

                // Swap the pixels based on the condition
                pixel = (x % 2 == 0) ? original.getPixel(x, original.getHeight() - 1 - y) : original.getPixel(x, y);

                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);

                // Introduce more significant random noise for an annoying effect
                int noise = (int) (Math.random() * 100) - 50;

                // Apply the noise to each color component
                red = Math.min(255, Math.max(0, red + noise));
                green = Math.min(255, Math.max(0, green + noise));
                blue = Math.min(255, Math.max(0, blue + noise));

                int encryptedPixel = Color.rgb(red, green, blue);
                encryptedBitmap.setPixel(x, y, encryptedPixel);
            }
        }

        return encryptedBitmap;
    }

    private void saveImage(Bitmap bitmap, String filename) {
        try {
            ContentResolver contentResolver = getContentResolver();
            String displayName = filename;

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

            Uri imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (imageUri != null) {
                OutputStream outputStream = contentResolver.openOutputStream(imageUri);
                if (outputStream != null) {
                    // Compress and save the image with JPEG format and reduced quality
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                    outputStream.close();

                    Toast.makeText(MaskEncode.this, "Image saved: " + filename, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MaskEncode.this, "Error saving image.", Toast.LENGTH_SHORT).show();
        }
    }
}
