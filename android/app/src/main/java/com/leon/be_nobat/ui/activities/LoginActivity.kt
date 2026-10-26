package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.AuthException
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.view_models.auth.AuthViewModel
import com.leon.be_nobat.ui.view_models.auth.LoginUiState
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginActivity : BaseActivity(), View.OnClickListener {
    override val layoutResourceId: Int = R.layout.activity_login
    private val authViewModel: AuthViewModel by viewModel()

    private val identityInput by lazy { findViewById<TextInputLayout>(R.id.tilIdentity) }
    private val passwordInput by lazy { findViewById<TextInputLayout>(R.id.tilPassword) }
    private val loginButton by lazy { findViewById<MaterialButton>(R.id.btnLogin) }

    override fun setupViews() {
        if (com.leon.be_nobat.BuildConfig.DEMO_MODE) {
            identityInput.helperText = getString(R.string.demo_accounts_hint)
            passwordInput.helperText = getString(R.string.demo_password_hint)
            val demoButton = MaterialButton(this).apply {
                text = getString(R.string.demo_login_button)
                setOnClickListener { showDemoOptions() }
            }
            (findViewById<MaterialButton>(R.id.btnGuest).parent as android.widget.LinearLayout).addView(demoButton)
        }
        setToolbarTitle(true) // hide toolbar (login has its own header)
        findViewById<View>(R.id.btnThemeToggle).setOnClickListener(this)
        findViewById<MaterialButton>(R.id.btnLanguage).setOnClickListener(this)
        findViewById<MaterialButton>(R.id.btnGuest).setOnClickListener(this)
        loginButton.setOnClickListener(this)
        findViewById<MaterialButton?>(R.id.tvRegister)?.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        findViewById<TextInputEditText>(R.id.etPassword).setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin()
                true
            } else false
        }
        if (intent.getStringExtra(EXTRA_RETURN_TARGET) == null) {
            setupDoubleBackToExit()
        }
    }

    private fun showDemoOptions() {
        val accounts = arrayOf("user@benobat.ir", "manager@benobat.ir", "admin@benobat.ir", "staff@benobat.ir")
        val roles = arrayOf(
            getString(R.string.role_customer),
            getString(R.string.role_manager),
            getString(R.string.role_admin),
            getString(R.string.role_staff),
            getString(R.string.demo_reset_positive)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.demo_login_dialog_title)
            .setItems(roles) { _, index ->
                if (index < accounts.size) {
                    identityInput.editText?.setText(accounts[index])
                    passwordInput.editText?.setText("Demo1234!")
                } else {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setMessage(getString(R.string.demo_reset_confirm_msg))
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.demo_reset_positive) { _, _ ->
                            lifecycleScope.launch {
                                runCatching {
                                    org.koin.java.KoinJavaComponent.get<com.leon.be_nobat.data.demo.DemoDataStore>(com.leon.be_nobat.data.demo.DemoDataStore::class.java).resetToSeed()
                                    org.koin.java.KoinJavaComponent.get<com.leon.be_nobat.data.local.TokenManager>(com.leon.be_nobat.data.local.TokenManager::class.java).clear()
                                }.onSuccess { showToast(getString(R.string.demo_reset_success), isLong = true) }
                                    .onFailure { showToast(it.message ?: "", isLong = true) }
                            }
                        }.show()
                }
            }.show()
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.loginState.collect { renderLoginState(it) }
            }
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnThemeToggle -> setupThemeToggle()
            R.id.btnLanguage -> showLanguageMenu(v)
            R.id.btnGuest -> {
                startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
            }
            R.id.btnLogin -> submitLogin()
        }
    }

    private fun submitLogin() {
        clearInputErrors()
        authViewModel.login(
            identityInput.editText?.text?.toString().orEmpty(),
            passwordInput.editText?.text?.toString().orEmpty(),
        )
    }

    private fun renderLoginState(state: LoginUiState) {
        loginButton.isEnabled = state !is LoginUiState.Loading
        loginButton.text = if (state is LoginUiState.Loading) {
            getString(R.string.logging_in)
        } else {
            getString(R.string.login)
        }
        when (state) {
            is LoginUiState.Error -> {
                val message = getString(state.error.messageResource())
                when (state.error) {
                    AuthException.InvalidIdentifier -> identityInput.error = message
                    AuthException.EmptyPassword -> passwordInput.error = message
                    else -> showToast(message, isLong = true)
                }
                authViewModel.resetState()
            }

            is LoginUiState.Success -> routeAfterLogin(state.session.user)

            LoginUiState.Idle, LoginUiState.Loading -> Unit
        }
    }

    companion object {
        const val EXTRA_RETURN_TARGET = "extra_return_target"
        const val EXTRA_BUSINESS_ID = "extra_business_id"
    }

    private fun routeAfterLogin(user: com.leon.be_nobat.domain.model.User) {
        val returnTarget = intent.getStringExtra(EXTRA_RETURN_TARGET)
        if (!returnTarget.isNullOrBlank()) {
            try {
                val targetClass = Class.forName(returnTarget)
                val targetIntent = Intent(this, targetClass).apply {
                    intent.getStringExtra(EXTRA_BUSINESS_ID)?.let { putExtra("businessId", it) }
                }
                startActivity(targetIntent)
                finish()
                return
            } catch (_: Exception) {}
        }

        // Role-based routing: admin/managers/staff go to admin home; customers to customer home.
        val target = if (user.isStaff) {
            Intent(this, AdminHomeActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }
        target.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(target)
        finishAffinity()
    }

    private fun clearInputErrors() {
        identityInput.error = null
        passwordInput.error = null
    }

    private fun Throwable.messageResource(): Int = when (this) {
        AuthException.InvalidIdentifier -> R.string.error_invalid_identifier
        AuthException.EmptyPassword -> R.string.error_empty_password
        AuthException.InvalidCredentials -> R.string.error_invalid_credentials
        AuthException.AccountLocked -> R.string.error_account_locked
        AuthException.DuplicateEmail -> R.string.error_duplicate_email
        AuthException.PasswordMismatch -> R.string.error_password_mismatch
        AuthException.WeakPassword -> R.string.error_weak_password
        AuthException.TooManyRequests -> R.string.error_too_many_requests
        AuthException.ServiceUnavailable -> R.string.error_service_unavailable
        AuthException.NetworkUnavailable -> R.string.error_network_unavailable
        AuthException.RequestTimedOut -> R.string.error_request_timed_out
        AuthException.Unauthorized -> R.string.error_unauthorized
        AuthException.Forbidden -> R.string.error_forbidden
        AuthException.NotFound -> R.string.error_not_found
        AuthException.Conflict -> R.string.error_conflict
        else -> R.string.unexpected_error
    }
}