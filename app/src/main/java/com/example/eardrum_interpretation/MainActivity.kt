package com.example.eardrum_interpretation

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private var currentBitmap: Bitmap? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onImagePicked(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val onPickClick = View.OnClickListener { launchPicker() }
        findViewById<Button>(R.id.btnPickImage).setOnClickListener(onPickClick)
        findViewById<Button>(R.id.btnRetryWarning).setOnClickListener(onPickClick)
        findViewById<Button>(R.id.btnRetryResult).setOnClickListener(onPickClick)
    }

    private fun launchPicker() {
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun onImagePicked(uri: Uri) {
        val bitmap = decodeBitmap(uri)
        if (bitmap == null) {
            Toast.makeText(this, "圖片讀取失敗，請重新選擇", Toast.LENGTH_SHORT).show()
            return
        }
        currentBitmap = bitmap
        // TEMP verification for step 4: replaced by the real quality-check / result flow next.
        Toast.makeText(this, "已選擇圖片：${bitmap.width}x${bitmap.height}", Toast.LENGTH_SHORT).show()
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }
    }
}
