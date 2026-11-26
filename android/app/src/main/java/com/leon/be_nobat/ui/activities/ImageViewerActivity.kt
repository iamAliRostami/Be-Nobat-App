package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import coil.load
import com.leon.be_nobat.R
import com.leon.be_nobat.ui.views.ZoomableImageView

class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val source = intent.getStringExtra(EXTRA_IMAGE_SOURCE)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val caption = intent.getStringExtra(EXTRA_CAPTION)

        findViewById<ImageButton>(R.id.btnViewerClose).setOnClickListener { finish() }

        val tvTitle = findViewById<TextView>(R.id.tvViewerTitle)
        tvTitle.text = title.orEmpty()
        tvTitle.isVisible = !title.isNullOrBlank()

        val tvCaption = findViewById<TextView>(R.id.tvViewerCaption)
        tvCaption.text = caption.orEmpty()
        tvCaption.isVisible = !caption.isNullOrBlank()

        val imageView = findViewById<ZoomableImageView>(R.id.zoomableImageView)
        if (source.isNullOrBlank()) {
            finish()
            return
        }

        if (source.startsWith("data:image/")) {
            try {
                val encoded = source.substringAfter("base64,")
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) imageView.setImageBitmap(bmp) else finish()
            } catch (_: Exception) {
                finish()
            }
        } else {
            imageView.load(source) {
                error(R.drawable.ic_empty_box)
            }
        }
    }

    companion object {
        const val EXTRA_IMAGE_SOURCE = "extra_image_source"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CAPTION = "extra_caption"

        fun open(context: Context, source: String?, title: String? = null, caption: String? = null) {
            if (source.isNullOrBlank()) return
            context.startActivity(Intent(context, ImageViewerActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_SOURCE, source)
                title?.let { putExtra(EXTRA_TITLE, it) }
                caption?.let { putExtra(EXTRA_CAPTION, it) }
            })
        }
    }
}
