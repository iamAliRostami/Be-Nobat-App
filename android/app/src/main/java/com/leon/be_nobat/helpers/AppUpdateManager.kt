package com.leon.be_nobat.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
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

        // ۲. دانلود و نصب مستقیم
        btnDirect.setOnClickListener {
            layoutProgress.visibility = View.VISIBLE
            layoutActions.visibility = View.GONE
            dialog.setCancelable(false)

            downloadAndInstallApk(
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
                onSuccess = { file ->
                    activity.runOnUiThread {
                        dialog.dismiss()
                        installApk(activity, file)
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

    private fun downloadAndInstallApk(
        activity: Activity,
        downloadUrl: String,
        versionName: String,
        onProgress: (Int) -> Unit,
        onSuccess: (File) -> Unit,
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
                val cacheDir = activity.externalCacheDir ?: activity.cacheDir
                val apkFile = File(cacheDir, "benobat_update_v${versionName.replace(".", "_")}.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            if (fileLength > 0) {
                                val percent = (total * 100 / fileLength).toInt()
                                onProgress(percent)
                            } else {
                                onProgress(-1)
                            }
                            output.write(data, 0, count)
                        }
                        output.flush()
                    }
                }

                withContext(Dispatchers.Main) {
                    onSuccess(apkFile)
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

    private fun installApk(context: Context, apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "خطا در فراخوانی نصاب بسته اندروید: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
