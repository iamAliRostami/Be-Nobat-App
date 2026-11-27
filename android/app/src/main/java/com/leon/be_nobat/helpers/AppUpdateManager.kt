package com.leon.be_nobat.helpers

import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leon.be_nobat.BuildConfig
import com.leon.be_nobat.R
import com.leon.be_nobat.data.remote.BeNobatApiClient
import com.leon.be_nobat.domain.model.AppUpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {

    private var hasCheckedInThisSession = false

    fun checkForUpdate(
        activity: Activity,
        apiClient: BeNobatApiClient,
        scope: CoroutineScope,
        forceCheck: Boolean = false,
        onUpdateNotAvailable: (() -> Unit)? = null,
    ) {
        if (!forceCheck && hasCheckedInThisSession) {
            return
        }

        val currentVersionCode = BuildConfig.VERSION_CODE

        scope.launch {
            val result = apiClient.checkAppUpdate(currentVersionCode)
            result.onSuccess { updateInfo ->
                hasCheckedInThisSession = true
                if (updateInfo.hasUpdate && updateInfo.versionCode > currentVersionCode) {
                    showUpdateDialog(activity, updateInfo)
                } else {
                    onUpdateNotAvailable?.invoke()
                }
            }.onFailure {
                // در صورت بروز خطا در دریافت نسخه، جریان کاربری قطع نشود
                onUpdateNotAvailable?.invoke()
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, info: AppUpdateInfo) {
        if (activity.isFinishing || activity.isDestroyed) return

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)
        val tvVersion = view.findViewById<TextView>(R.id.tvUpdateVersion)
        val tvNotes = view.findViewById<TextView>(R.id.tvReleaseNotes)
        val btnBazaar = view.findViewById<MaterialButton>(R.id.btnUpdateBazaar)
        val btnDirect = view.findViewById<MaterialButton>(R.id.btnUpdateDirect)
        val btnLater = view.findViewById<MaterialButton>(R.id.btnUpdateLater)
        val layoutProgress = view.findViewById<View>(R.id.layoutDownloadProgress)
        val tvDownloadStatus = view.findViewById<TextView>(R.id.tvDownloadStatus)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBarDownload)
        val layoutActions = view.findViewById<View>(R.id.layoutActionButtons)

        tvVersion.text = "نسخه ${info.versionName} (کد ${info.versionCode})"
        tvNotes.text = if (info.releaseNotes.isNotBlank()) info.releaseNotes else "بهبود عملکرد و ارتقای پایداری سامانه"

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setCancelable(!info.isForceUpdate)
            .create()

        if (info.isForceUpdate) {
            btnLater.visibility = View.GONE
            dialog.setCanceledOnTouchOutside(false)
        } else {
            btnLater.visibility = View.VISIBLE
            btnLater.setOnClickListener {
                dialog.dismiss()
            }
        }

        // ۱. به‌روزرسانی از کافه بازار
        btnBazaar.setOnClickListener {
            openBazaar(activity, info.bazaarPackageName)
            if (!info.isForceUpdate) {
                dialog.dismiss()
            }
        }

        // ۲. دانلود مستقیم فایل APK
        btnDirect.setOnClickListener {
            layoutProgress.visibility = View.VISIBLE
            layoutActions.visibility = View.GONE
            dialog.setCancelable(false)

            downloadApk(
                activity = activity,
                downloadUrl = info.downloadUrl,
                versionName = info.versionName,
                onProgress = { percent ->
                    activity.runOnUiThread {
                        if (percent >= 0) {
                            progressBar.isIndeterminate = false
                            progressBar.progress = percent
                            tvDownloadStatus.text = "در حال دانلود فایل به‌روزرسانی ($percent%)..."
                        } else {
                            progressBar.isIndeterminate = true
                            tvDownloadStatus.text = "در حال برقراری ارتباط و دانلود..."
                        }
                    }
                },
                onSuccess = { savedLocation ->
                    activity.runOnUiThread {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            dialog.dismiss()
                            showDownloadCompletedDialog(activity, info.versionName, savedLocation)
                        }
                    }
                },
                onError = { message ->
                    activity.runOnUiThread {
                        layoutProgress.visibility = View.GONE
                        layoutActions.visibility = View.VISIBLE
                        dialog.setCancelable(!info.isForceUpdate)
                        Toast.makeText(activity, "خطا در دانلود فایل: $message", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        dialog.show()
    }

    private fun openBazaar(context: Context, packageName: String) {
        val targetPackage = if (packageName.isNotBlank()) packageName else context.packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("bazaar://details?id=$targetPackage")
                setPackage("com.farsitel.bazaar")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // کافه بازار نصب نیست؛ باز کردن در مرورگر وب
            try {
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://cafebazaar.ir/app/$targetPackage")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "امکان باز کردن صفحه کافه‌بازار وجود ندارد.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openConnectionWithRedirects(
        initialUrl: String,
        maxRedirects: Int = 10,
    ): HttpURLConnection {
        var currentUrl = initialUrl.trim()
        var redirectCount = 0

        while (true) {
            val url = URL(currentUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("Accept-Encoding", "identity")
            }
            connection.connect()

            val code = connection.responseCode
            val isRedirect = code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308

            if (!isRedirect) {
                return connection
            }

            val location = connection.getHeaderField("Location")
            connection.disconnect()

            if (location.isNullOrBlank()) {
                throw IllegalStateException("سرور کد $code را بدون آدرس مقصد برگرداند.")
            }
            if (redirectCount >= maxRedirects) {
                throw IllegalStateException("تعداد تغییر مسیرهای سرور بیش از حد مجاز است.")
            }

            // پشتیبانی از آدرس‌های نسبی و مطلق (شامل تغییر پروتکل http به https)
            currentUrl = URL(url, location.trim()).toString()
            redirectCount++
        }
    }

    private fun downloadApk(
        activity: Activity,
        downloadUrl: String,
        versionName: String,
        onProgress: (Int) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null
            try {
                if (downloadUrl.isBlank()) {
                    withContext(Dispatchers.Main) {
                        onError("آدرس دانلود معتبر نیست.")
                    }
                    return@launch
                }

                connection = openConnectionWithRedirects(downloadUrl)

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    val code = connection.responseCode
                    withContext(Dispatchers.Main) {
                        onError("سرور کد $code را برگرداند.")
                    }
                    return@launch
                }

                val fileLength = connection.contentLength
                val fileName = "BeNobat-v${versionName.trim().ifBlank { "latest" }}.apk"

                val savedLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToDownloadsMediaStore(activity, connection, fileName, fileLength, onProgress)
                } else {
                    saveToLegacyDownloads(activity, connection, fileName, fileLength, onProgress)
                }

                withContext(Dispatchers.Main) {
                    onSuccess(savedLocation)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "خطای ناشناخته")
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun saveToDownloadsMediaStore(
        context: Context,
        connection: HttpURLConnection,
        fileName: String,
        fileLength: Int,
        onProgress: (Int) -> Unit,
    ): String {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("امکان ایجاد فایل در پوشه دانلودها وجود ندارد.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                copyStreamWithProgress(connection, output, fileLength, onProgress)
            } ?: throw IllegalStateException("خطا در باز کردن مسیر ذخیره‌سازی.")

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            return "پوشه دانلودها (Download/$fileName)"
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun saveToLegacyDownloads(
        context: Context,
        connection: HttpURLConnection,
        fileName: String,
        fileLength: Int,
        onProgress: (Int) -> Unit,
    ): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!dir.exists()) dir.mkdirs()
        val apkFile = File(dir, fileName)
        if (apkFile.exists()) {
            apkFile.delete()
        }

        FileOutputStream(apkFile).use { output ->
            copyStreamWithProgress(connection, output, fileLength, onProgress)
        }

        return apkFile.absolutePath
    }

    private fun copyStreamWithProgress(
        connection: HttpURLConnection,
        output: java.io.OutputStream,
        fileLength: Int,
        onProgress: (Int) -> Unit,
    ) {
        connection.inputStream.use { input ->
            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    onProgress((total * 100 / fileLength).toInt())
                } else {
                    onProgress(-1)
                }
                output.write(data, 0, count)
            }
            output.flush()
        }
    }

    private fun showDownloadCompletedDialog(
        activity: Activity,
        versionName: String,
        savedLocation: String,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        MaterialAlertDialogBuilder(activity)
            .setTitle("دانلود تکمیل شد")
            .setMessage(
                "فایل نسخه $versionName با موفقیت دانلود شد و در مسیر زیر ذخیره گردید:\n\n" +
                    "$savedLocation\n\n" +
                    "لطفاً وارد مدیریت فایل (بخش دانلودها) شوید و روی فایل بزنید تا نصب شود.",
            )
            .setPositiveButton("متوجه شدم", null)
            .setNeutralButton("باز کردن دانلودها") { _, _ ->
                try {
                    val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(
                        activity,
                        "لطفاً از طریق فایل منیجر گوشی به پوشه دانلودها بروید.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            .show()
    }
}
