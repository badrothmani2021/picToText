package com.example.imagetotextocrscanner;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_TEXT = "com.fast.EXTRA_TEXT";
    private static final int PICK_IMAGE_REQUEST = 100;
    private static final int CAMERA_REQUEST = 101;
    private Button detectTextBtn;
    private ImageView imageView;
    private TextView txtView;
    private Bitmap imageBitmap;
    private ProgressBar progressBar;
    private Dialog myDialog, myDialog1;
    private Uri cameraImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });

        AdView adView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        progressBar = findViewById(R.id.progressBar1);
        myDialog = new Dialog(this);
        Button captureImageBtn = findViewById(R.id.capture_image_btn);
        detectTextBtn = findViewById(R.id.detect_text_image_btn);
        imageView = findViewById(R.id.image_view);
        txtView = findViewById(R.id.text_display);

        Toolbar toolbar = findViewById(R.id.toolbar1);
        toolbar.setTitleTextColor(Color.WHITE);
        setSupportActionBar(toolbar);
        myDialog1 = new Dialog(this);

        captureImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImagePicker();
            }
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        //TODO this one is deprecated
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void startCrop(Uri sourceUri) {
        // Create a time-stamped filename for the cropped image
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "CROPPED_" + timeStamp + ".jpg";

        // Create the destination URI in the cache directory
        File destinationFile = new File(getCacheDir(), fileName);
        Uri destinationUri = Uri.fromFile(destinationFile);

        // Configure and start UCrop
        UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1) // Square crop
                .withMaxResultSize(1000, 1000)
                .start(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.image) {
            openImagePicker();
            detectTextBtn.setVisibility(View.VISIBLE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST && data != null) {
                // User picked an image from gallery
                Uri selectedImageUri = data.getData();
                startCrop(selectedImageUri);
            }
            else if (requestCode == UCrop.REQUEST_CROP) {
                // Handle UCrop result
                final Uri resultUri = UCrop.getOutput(data);
                if (resultUri != null) {
                    try {
                        imageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
                        imageView.setImageBitmap(imageBitmap);
                        detectTextBtn.setVisibility(View.VISIBLE);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
        else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
            if (cropError != null) {
                Toast.makeText(this, "Crop error: " + cropError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Rest of your existing methods remain the same...
    public void detecttext(View view) {
        detectTextFromImage();
        progressBar.setVisibility(View.VISIBLE);
    }

    private boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public void writeFile(View view) {
        if (isExternalStorageWritable() && checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            File textFile = new File(Environment.getExternalStorageDirectory().toString(), "myfile.txt");
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(textFile);
                fileOutputStream.write(txtView.getText().toString().getBytes());
                fileOutputStream.close();
                Toast.makeText(this, "File Saved.", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "Cannot Write to External Storage.", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void detectTextFromImage() {
        InputImage image = InputImage.fromBitmap(imageBitmap, 0);
        TextRecognizerOptions options = new TextRecognizerOptions.Builder().build();
        TextRecognizer recognizer = TextRecognition.getClient(options);
        recognizer.process(image)
                .addOnSuccessListener(texts -> processTextRecognitionResult(texts))
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Toast.makeText(this, "Text detection failed", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
    }

    private void processTextRecognitionResult(Text texts) {
        List<Text.TextBlock> blocks = texts.getTextBlocks();
        if (blocks.size() == 0) {
            Toast.makeText(this, "No text found", Toast.LENGTH_SHORT).show();
        } else {
            StringBuilder extractedText = new StringBuilder();
            for (Text.TextBlock block : texts.getTextBlocks()) {
                extractedText.append(block.getText()).append("\n");
            }
            txtView.setText(extractedText.toString());
            openActivity2();
        }
        progressBar.setVisibility(View.GONE);
    }

    private void openActivity2() {
        String text = txtView.getText().toString();
        Intent intent = new Intent(this, detail.class);
        intent.putExtra(EXTRA_TEXT, text);
        startActivity(intent);
    }
}