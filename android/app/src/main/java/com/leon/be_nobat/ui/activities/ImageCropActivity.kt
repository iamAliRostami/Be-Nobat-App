package com.leon.be_nobat.ui.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.google.android.material.button.MaterialButton
import com.leon.be_nobat.R
import com.leon.be_nobat.helpers.AppToast
import com.leon.be_nobat.ui.views.CropImageView
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ImageCropRequest(
    val sourceUri: Uri,
    val aspectRatio: Float = 0f, // <= 0f for free crop, 1f for 1:1, 16f/9f for cover
)

class ImageCropContract : ActivityResultContract<ImageCropRequest, Uri?>() {
    override fun createIntent(context: Context, input: ImageCropRequest): Intent =
        Intent(context, ImageCropActivity::class.java).apply {
            putExtra(ImageCropActivity.EXTRA_SOURCE_URI, input.sourceUri.toString())
            putExtra(ImageCropActivity.EXTRA_ASPECT_RATIO, input.aspectRatio)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

class ImageCropActivity : AppCompatActivity() {

    private lateinit var cropView: CropImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_crop)

        cropView = findViewById(R.id.cropImageView)
        val sourceUriStr = intent.getStringExtra(EXTRA_SOURCE_URI)
        val aspectRatio = intent.getFloatExtra(EXTRA_ASPECT_RATIO, 0f)
        if (sourceUriStr.isNullOrBlank()) {
            finish()
            return
        }

        val bitmap = loadOrientedBitmap(Uri.parse(sourceUriStr))
        if (bitmap == null) {
            AppToast.show(this, "خواندن تصویر انجام نشد.")
            finish()
            return
        }

        cropView.setImageBitmap(bitmap)
        if (aspectRatio > 0f) cropView.setAspectRatio(aspectRatio)

        findViewById<MaterialButton>(R.id.btnCropCancel).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        findViewById<MaterialButton>(R.id.btnRotate).setOnClickListener {
            cropView.rotate90()
        }
        findViewById<MaterialButton>(R.id.btnAspectFree).setOnClickListener {
            cropView.setAspectRatio(0f)
        }
        findViewById<MaterialButton>(R.id.btnAspectSquare).setOnClickListener {
            cropView.setAspectRatio(1f)
        }
        findViewById<MaterialButton>(R.id.btnAspectWide).setOnClickListener {
            cropView.setAspectRatio(16f / 9f)
        }
        findViewById<MaterialButton>(R.id.btnCropConfirm).setOnClickListener {
            val cropped = cropView.getCroppedBitmap() ?: return@setOnClickListener
            val outFile = File(cacheDir, "cropped_${UUID.randomUUID()}.jpg")
            FileOutputStream(outFile).use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            setResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(outFile)))
            finish()
        }
    }

    private fun loadOrientedBitmap(uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val orientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        }
        if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } catch (_: Exception) {
        null
    }

    companion object {
        const val EXTRA_SOURCE_URI = "extra_source_uri"
        const val EXTRA_ASPECT_RATIO = "extra_aspect_ratio"
    }
}
