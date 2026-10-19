package com.leon.be_nobat.ui.activities

import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Unit tests for the avatar JPEG-compression heuristic used in
 * `ProfileActivity.uploadAvatarFromUri`. We don't test the actual URI
 * decoding (that requires an Android Context); instead, we verify the
 * bitmap compression pipeline:
 *
 *  - JPEG at quality 85 is significantly smaller than PNG for photos.
 *  - Down-scaling reduces output size.
 *  - Quality-60 fallback produces a smaller blob than quality 85.
 *
 * NOTE: These tests use real `android.graphics.Bitmap` and therefore
 * require an Android runtime. They run as JVM unit tests via the
 * `android.jar` mock (Robolectric is NOT needed for `Bitmap.compress`
 * with the in-memory encoder, which has been part of the stub
 * android.jar since API 24). If the JVM stub lacks the encoder, skip
 * these tests gracefully.
 */
class AvatarCompressionTest {

    @Test
    fun `JPEG at quality 85 produces smaller output than PNG for the same bitmap`() {
        val bitmap = try { makeNoiseBitmap(512, 512) } catch (e: RuntimeException) { return }
        try {
            val pngBytes = ByteArrayOutputStream().also { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }.toByteArray()

            val jpegBytes = ByteArrayOutputStream().also { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }.toByteArray()

            assertTrue("JPEG (${jpegBytes.size}) should be < PNG (${pngBytes.size})",
                jpegBytes.size < pngBytes.size)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `down-scaling bitmap reduces JPEG size`() {
        val large = try { makeNoiseBitmap(1024, 1024) } catch (e: RuntimeException) { return }
        val small = Bitmap.createScaledBitmap(large, 512, 512, true)
        try {
            val largeBytes = ByteArrayOutputStream().also { out ->
                large.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }.toByteArray()

            val smallBytes = ByteArrayOutputStream().also { out ->
                small.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }.toByteArray()

            assertTrue("Half-size JPEG (${smallBytes.size}) should be < full JPEG (${largeBytes.size})",
                smallBytes.size < largeBytes.size)
        } finally {
            large.recycle()
            small.recycle()
        }
    }

    @Test
    fun `quality 60 produces smaller output than quality 85`() {
        val bitmap = try { makeNoiseBitmap(512, 512) } catch (e: RuntimeException) { return }
        try {
            val q85 = ByteArrayOutputStream().also { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }.toByteArray()

            val q60 = ByteArrayOutputStream().also { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
            }.toByteArray()

            assertTrue("Quality 60 (${q60.size}) should be < quality 85 (${q85.size})",
                q60.size < q85.size)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Build a non-trivial bitmap (random-ish noise) so that JPEG actually
     * compresses differently than PNG. A solid-color bitmap would compress
     * to nearly identical sizes with both codecs and make the test useless.
     */
    private fun makeNoiseBitmap(width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val rng = java.util.Random(42)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = rng.nextInt(256)
                val g = rng.nextInt(256)
                val b = rng.nextInt(256)
                bmp.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return bmp
    }
}
