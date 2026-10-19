package com.leon.be_nobat.data.demo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/** A transaction is published in memory only after its durable file replacement succeeds. */
class DemoDataStore(private val directory: File, private val json: Json) {
    constructor(context: Context, json: Json) : this(context.filesDir, json)
    private val mutex = Mutex()
    private val storageFile = File(directory, "demo_database.json")
    private var state: DemoDatabaseState? = null

    private fun load(): DemoDatabaseState {
        state?.let { return it }
        val backup = File(directory, "demo_database.bak")
        if (!storageFile.exists() && backup.exists()) check(backup.renameTo(storageFile))
        val loaded = if (storageFile.exists()) {
            // Never silently discard a user's edits on a read/decoding error.
            json.decodeFromString(DemoDatabaseState.serializer(), storageFile.readText())
        } else DemoSeedData.createInitialState().also(::persist)
        state = loaded
        return loaded
    }

    private fun persist(value: DemoDatabaseState) {
        directory.mkdirs()
        val temporary = File(directory, "demo_database.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(json.encodeToString(DemoDatabaseState.serializer(), value).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        val backup = File(directory, "demo_database.bak")
        if (backup.exists()) check(backup.delete())
        if (storageFile.exists()) check(storageFile.renameTo(backup))
        if (!temporary.renameTo(storageFile)) {
            if (backup.exists()) check(backup.renameTo(storageFile))
            error("ذخیره اطلاعات دمو انجام نشد.")
        }
        backup.delete()
    }

    suspend fun <T> read(block: (DemoDatabaseState) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { block(load()) }
    }

    suspend fun <T> update(block: (DemoDatabaseState) -> Pair<DemoDatabaseState, T>): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val (next, result) = block(load())
            persist(next)
            state = next
            result
        }
    }

    suspend fun resetToSeed(): DemoDatabaseState = update {
        val initial = DemoSeedData.createInitialState()
        initial to initial
    }
}
