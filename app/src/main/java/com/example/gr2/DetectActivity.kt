package com.example.gr2

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class DetectActivity : AppCompatActivity() {
    lateinit var imageView: ImageView
    private lateinit var recognizer: TextRecognizer
    private lateinit var text2Speech: TextToSpeech
    private var imgUri: Uri? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detect)

        imageView = findViewById(R.id.imageView)
        // Lấy đường dẫn ảnh từ Intent
        val imagePath = intent.getStringExtra("IMAGE_PATH")
        imgUri = getFileUri(imagePath!!)


        // Kiểm tra xem đường dẫn có tồn tại hay không
        if (!imagePath.isNullOrEmpty()) {
            // Load ảnh từ đường dẫn và hiển thị nó trên ImageView
            val bitmap = BitmapFactory.decodeFile(imagePath)
            imageView.setImageBitmap(bitmap)
        }
//
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        text2Speech = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                text2Speech.language = java.util.Locale.ENGLISH
            }
        }
        if (imgUri == null) {

        } else {
            recognitionFromImage()
        }

    }
    private fun recognitionFromImage() {

        try {
            val inputImage = InputImage.fromFilePath(this, imgUri!!)
            val taskResult = recognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    val recognizeText = text.text
                    text2Speech.speak(recognizeText, TextToSpeech.QUEUE_ADD, null, null)

                    Log.d("TAG", "onSuccess: $recognizeText")
                }

                .addOnFailureListener { e ->
                    showToast("Recognize Failed ${e.message}")
                    Log.e("TAG", "onFailure: ${e.message}")
                }
        } catch (e: Exception) {
            showToast("Failed ${e.message}")
            Log.e("TAG", "recognitionFromImage: ${e.message}")
        }
    }

    private fun showToast(mess: String) {
        Toast.makeText(this, mess, Toast.LENGTH_SHORT).show()
    }
    private fun getFileUri(filePath: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            FileProvider.getUriForFile(this, applicationContext.packageName + ".provider", File(filePath))
        } else {
            Uri.fromFile(File(filePath))
        }
    }

}