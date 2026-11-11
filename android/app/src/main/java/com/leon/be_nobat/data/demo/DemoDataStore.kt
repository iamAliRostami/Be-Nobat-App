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
        if (!storageFile.exists() && backup.exists()) runCatching { backup.renameTo(storageFile) }
        val loaded = if (storageFile.exists()) {
            runCatching {
                val existing = json.decodeFromString(DemoDatabaseState.serializer(), storageFile.readText())
                enrichWithMedia(existing)
            }.getOrElse {
                DemoSeedData.createInitialState().also(::persist)
            }
        } else DemoSeedData.createInitialState().also(::persist)
        state = loaded
        return loaded
    }

    private fun enrichWithMedia(state: DemoDatabaseState): DemoDatabaseState {
        val initial = DemoSeedData.createInitialState()
        val bizMap = initial.businesses.associateBy { it.id }
        val branchMap = initial.branches.associateBy { it.id }
        var modified = false

        val newBusinesses = state.businesses.map { b ->
            val seed = bizMap[b.id]
            if (seed != null && (b.avatarUrl.isNullOrBlank() || b.headerUrl.isNullOrBlank() || b.gallery.isEmpty())) {
                modified = true
                b.copy(
                    avatarUrl = b.avatarUrl ?: seed.avatarUrl,
                    headerUrl = b.headerUrl ?: seed.headerUrl,
                    gallery = if (b.gallery.isEmpty()) seed.gallery else b.gallery
                )
            } else b
        }

        val newBranches = state.branches.map { br ->
            val seed = branchMap[br.id]
            if (seed != null && (br.avatarUrl.isNullOrBlank() || br.headerUrl.isNullOrBlank() || br.gallery.isEmpty())) {
                modified = true
                br.copy(
                    avatarUrl = br.avatarUrl ?: seed.avatarUrl,
                    headerUrl = br.headerUrl ?: seed.headerUrl,
                    gallery = if (br.gallery.isEmpty()) seed.gallery else br.gallery
                )
            } else br
        }

        return if (modified) {
            val updated = state.copy(businesses = newBusinesses, branches = newBranches)
            persist(updated)
            updated
        } else state
    }

    private fun persist(value: DemoDatabaseState) {
        directory.mkdirs()
        val temporary = File(directory, "demo_database.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(json.encodeToString(DemoDatabaseState.serializer(), value).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        val backup = File(directory, "demo_database.bak")
        if (backup.exists()) runCatching { backup.delete() }
        if (storageFile.exists()) runCatching { storageFile.renameTo(backup) }
        if (!temporary.renameTo(storageFile)) {
            if (backup.exists()) runCatching { backup.renameTo(storageFile) }
            error("ذخیره اطلاعات دمو انجام نشد.")
        }
        runCatching { backup.delete() }
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
