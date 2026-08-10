package com.example.eardrum_interpretation

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private var currentBitmap: Bitmap? = null

    private lateinit var groupEmpty: View
    private lateinit var groupWarning: View
    private lateinit var groupResult: View

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

        groupEmpty = findViewById(R.id.groupEmpty)
        groupWarning = findViewById(R.id.groupWarning)
        groupResult = findViewById(R.id.groupResult)

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

        val quality = ImageQualityChecker.check(bitmap)
        if (!quality.passed) {
            showWarningState(bitmap, quality)
        } else {
            runInference(bitmap)
        }
    }

    private fun showWarningState(bitmap: Bitmap, quality: ImageQualityChecker.Result) {
        showGroup(groupWarning)
        findViewById<ImageView>(R.id.warningThumb).setImageBitmap(bitmap)

        val chipBrightness = findViewById<TextView>(R.id.chipBrightness)
        when {
            quality.isDark -> setChip(chipBrightness, getString(R.string.chip_too_dark), warn = true)
            quality.isBright -> setChip(chipBrightness, getString(R.string.chip_too_bright), warn = true)
            else -> setChip(chipBrightness, getString(R.string.chip_ok_brightness), warn = false)
        }

        val chipSharpness = findViewById<TextView>(R.id.chipSharpness)
        if (quality.isBlurry) {
            setChip(chipSharpness, getString(R.string.chip_too_blurry), warn = true)
        } else {
            setChip(chipSharpness, getString(R.string.chip_ok_sharpness), warn = false)
        }
    }

    private fun setChip(chip: TextView, text: String, warn: Boolean) {
        chip.text = text
        if (warn) {
            chip.setBackgroundResource(R.drawable.bg_chip_warn)
            chip.setTextColor(getColor(R.color.apricot))
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_ok)
            chip.setTextColor(getColor(R.color.sage_ink))
        }
    }

    private fun showGroup(visible: View) {
        for (group in listOf(groupEmpty, groupWarning, groupResult)) {
            group.visibility = if (group === visible) View.VISIBLE else View.GONE
        }
    }

    private fun runInference(bitmap: Bitmap) {
        // TODO: replaced by real TFLite inference in the next step.
        Toast.makeText(this, "品質通過，AI 判讀將在下一步實作", Toast.LENGTH_SHORT).show()
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
