package com.leon.be_nobat.ui.activities

import android.content.Intent
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.AuthException
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.view_models.RegisterUiState
import com.leon.be_nobat.ui.view_models.RegisterViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class RegisterActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_register

    private val viewModel: RegisterViewModel by viewModel()

    override fun setupViews() {
        setMenuVisibility(false)
        setToolbarTitle(true) // hide the toolbar

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRegister).setOnClickListener {
            val name = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDisplayName).text.toString()
            val email = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmail).text.toString()
            val phone = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPhone).text.toString()
            val pass = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPassword).text.toString()
            viewModel.register(name, email, phone, pass)
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGoLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is RegisterUiState.Idle -> Unit
                        is RegisterUiState.Loading -> {
                            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRegister).apply {
                                text = getString(R.string.registering)
                                isEnabled = false
                            }
                            findViewById<android.widget.TextView>(R.id.tvError).isVisible = false
                        }
                        is RegisterUiState.Success -> routeAfterRegister(state.session.user)
                        is RegisterUiState.Error -> {
                            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRegister).apply {
                                text = getString(R.string.register)
                                isEnabled = true
                            }
                            val errView = findViewById<android.widget.TextView>(R.id.tvError)
                            errView.isVisible = true
                            errView.text = when (state.error as? AuthException) {
                                AuthException.InvalidIdentifier -> getString(R.string.error_invalid_identifier)
                                AuthException.EmptyPassword -> getString(R.string.error_empty_password)
                                AuthException.DuplicateEmail -> getString(R.string.error_duplicate_email)
                                AuthException.WeakPassword -> getString(R.string.error_weak_password)
                                AuthException.TooManyRequests -> getString(R.string.error_too_many_requests)
                                AuthException.NetworkUnavailable -> getString(R.string.error_network_unavailable)
                                AuthException.RequestTimedOut -> getString(R.string.error_request_timed_out)
                                AuthException.ServiceUnavailable -> getString(R.string.error_service_unavailable)
                                else -> state.error.message ?: getString(R.string.unexpected_error)
                            }
                            viewModel.reset()
                        }
                    }
                }
            }
        }
    }

    private fun routeAfterRegister(user: com.leon.be_nobat.domain.model.User) {
        val intent = if (user.isStaff) {
            Intent(this, AdminHomeActivity::class.java)
        } else {
            Intent(this, HomeActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
    }
}
