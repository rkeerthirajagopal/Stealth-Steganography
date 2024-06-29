package com.example.mychat.activites;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.net.Uri;
import android.graphics.Matrix;

import com.example.mychat.R;

public class MaskDecode extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_IMAGE_UPLOAD = 2;

    private ImageView imageView, encryptedImageView;
    private Button uploadImageBtn, decryptBtn, saveDecryptImageBtn;

    private Bitmap originalBitmap, decryptedBitmap;
    private ExecutorService backgroundExecutor;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mask_decode);

        imageView = findViewById(R.id.imageView);
        uploadImageBtn = findViewById(R.id.uploadImageBtn);
        encryptedImageView = findViewById(R.id.encryptedImageView);
        decryptBtn = findViewById(R.id.decryptBtn);
        saveDecryptImageBtn = findViewById(R.id.saveDecryptImageBtn);

        requestStoragePermissions();

        backgroundExecutor = Executors.newSingleThreadExecutor();

        uploadImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openGallery(REQUEST_IMAGE_UPLOAD);
            }
        });

        decryptBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (originalBitmap != null) {
                    backgroundExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            decryptedBitmap = decryptImage(originalBitmap);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    imageView.setImageBitmap(decryptedBitmap);
                                    encryptedImageView.setImageBitmap(decryptedBitmap);
                                    Toast.makeText(MaskDecode.this, "Image decrypted.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                }
            }
        });

        saveDecryptImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (decryptedBitmap != null) {
                    saveImage(decryptedBitmap, "decrypted_image.jpg");
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
            }
        }
    }

    private Bitmap decryptImage(Bitmap uploadedImage) {
        Bitmap decryptedBitmap = Bitmap.createBitmap(uploadedImage.getWidth(), uploadedImage.getHeight(), Bitmap.Config.RGB_565);

        for (int x = 0; x < uploadedImage.getWidth(); x++) {
            for (int y = 0; y < uploadedImage.getHeight(); y++) {
                int pixel;

                // Swap the pixels based on the condition
                pixel = (x % 2 == 0) ? uploadedImage.getPixel(x, y) : uploadedImage.getPixel(x, uploadedImage.getHeight() - 1 - y);

                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);

                // Remove the introduced noise
                red = Math.min(255, Math.max(0, red));
                green = Math.min(255, Math.max(0, green));
                blue = Math.min(255, Math.max(0, blue));

                int decryptedPixel = Color.rgb(red, green, blue);
                decryptedBitmap.setPixel(x, y, decryptedPixel);
            }
        }

        Matrix matrixMirror = new Matrix();
        matrixMirror.preScale(-1, 1);
        decryptedBitmap = Bitmap.createBitmap(decryptedBitmap, 0, 0, decryptedBitmap.getWidth(), decryptedBitmap.getHeight(), matrixMirror, true);

        Matrix matrix = new Matrix();
        matrix.postRotate(180);
        return Bitmap.createBitmap(decryptedBitmap, 0, 0, decryptedBitmap.getWidth(), decryptedBitmap.getHeight(), matrix, true);
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

                    Toast.makeText(MaskDecode.this, "Image saved: " + filename, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MaskDecode.this, "Error saving image.", Toast.LENGTH_SHORT).show();
        }
    }
}
