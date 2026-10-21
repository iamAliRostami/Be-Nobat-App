package com.leon.be_nobat.notifications

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** App-private storage excluded from backup; every account has a separate inbox and baseline. */
class AppointmentNoticeStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(userId: String): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(context.noBackupFilesDir, "appointment-notices-$hash.json"))
    }
    suspend fun read(userId: String): AppointmentNoticeState = withContext(Dispatchers.IO) {
        try { json.decodeFromString<AppointmentNoticeState>(file(userId).openRead().bufferedReader().use { it.readText() }) }
        catch (_: java.io.FileNotFoundException) { AppointmentNoticeState() }
    }
    suspend fun write(userId: String, state: AppointmentNoticeState) = withContext(Dispatchers.IO) {
        val target = file(userId)
        val stream = target.startWrite()
        try {
            stream.write(json.encodeToString(AppointmentNoticeState.serializer(), state).toByteArray())
            target.finishWrite(stream)
        } catch (e: Exception) { target.failWrite(stream); throw e }
    }
}
