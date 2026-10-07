package com.leon.be_nobat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leon.be_nobat.BuildConfig
import com.leon.be_nobat.data.ApiFailure
import com.leon.be_nobat.data.BeNobatApi
import com.leon.be_nobat.data.SecureSession
import com.leon.be_nobat.data.text
import com.leon.be_nobat.domain.BookingRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

data class Screen(val name: String = "home", val id: String = "", val context: String = "")
data class NativeState(val screen: Screen = Screen(), val data: JSONObject = JSONObject(), val loading: Boolean = false,
    val error: String? = null, val revision: Int = 0)

class NativeViewModel(application: Application) : AndroidViewModel(application) {
    val preferences = application.getSharedPreferences("settings", 0)
    private val secureSession = SecureSession(application)
    var session: JSONObject? = secureSession.read(); private set
    val user: JSONObject get() = session?.optJSONObject("user") ?: JSONObject()
    val signedIn get() = session?.text("accessToken")?.isNotBlank() == true
    val serverUrl get() = (preferences.getString("server_url", null) ?: BuildConfig.API_BASE_URL).let {
        if (it.isBlank()) "" else runCatching { BookingRules.serverUrl(it, BuildConfig.DEBUG) }.getOrDefault("")
    }
    val api = BeNobatApi({ serverUrl }, { session?.text("accessToken") })
    private val mutable = MutableStateFlow(NativeState())
    val state = mutable.asStateFlow()
    val drafts = mutableMapOf<String, String>()
    val stack = mutableListOf<Screen>()
    private var job: Job? = null
    private var revision = 0
    fun move(screen: Screen, push: Boolean = true) {
        job?.cancel()
        if (push && screen != mutable.value.screen) stack.add(mutable.value.screen)
        mutable.value = NativeState(screen = screen, revision = ++revision)
    }
    fun back(): Screen? = if (stack.isEmpty()) null else stack.removeAt(stack.lastIndex)
    fun load(block: suspend () -> JSONObject) {
        job?.cancel()
        val screen = mutable.value.screen
        mutable.value = mutable.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val data = block()
                mutable.value = NativeState(screen, data, revision = ++revision)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail(error) }
        }
    }
    fun action(block: suspend () -> Unit) {
        if (mutable.value.loading) return
        val previousLoad = job
        mutable.value = mutable.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try { block(); if (job === previousLoad) mutable.value = mutable.value.copy(loading = false) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail(error) }
        }
    }
    private fun fail(error: Exception) {
        val code = (error as? ApiFailure)?.code ?: "network"
        if (error is ApiFailure && error.status == 401) {
            clearSession()
            if (mutable.value.screen.name !in listOf("home", "business", "login", "register")) {
                drafts.clear()
                mutable.value = mutable.value.copy(data = JSONObject(), revision = ++revision)
            }
        }
        mutable.value = mutable.value.copy(loading = false, error = code)
    }
    fun saveSession(value: JSONObject) { secureSession.save(value); session = value }
    fun updateUser(value: JSONObject) { session?.put("user", value); session?.let(secureSession::save) }
    fun clearSession() { secureSession.clear(); session = null }
    fun showError(code: String) { mutable.value = mutable.value.copy(error = code) }
}
