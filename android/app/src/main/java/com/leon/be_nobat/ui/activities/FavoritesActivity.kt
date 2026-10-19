package com.leon.be_nobat.ui.activities

import android.content.Intent
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.adapters.BusinessAdapter
import com.leon.be_nobat.ui.view_models.FavoritesUiState
import com.leon.be_nobat.ui.view_models.FavoritesViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class FavoritesActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_favorites
    override val applyBottomInsetsOnRoot: Boolean = false

    private val viewModel: FavoritesViewModel by viewModel()
    private lateinit var adapter: BusinessAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private val tokenManager: TokenManager by inject()

    override fun setupViews() {
        setupMainHeader(getString(R.string.favorites_title))

        lifecycleScope.launch {
            if (tokenManager.currentSession() == null) {
                val intent = Intent(this@FavoritesActivity, LoginActivity::class.java).apply {
                    putExtra(LoginActivity.EXTRA_RETURN_TARGET, FavoritesActivity::class.java.name)
                }
                startActivity(intent)
                finish()
                return@launch
            }
        }

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { viewModel.load() }

        adapter = BusinessAdapter(
            onClick = { biz ->
                startActivity(Intent(this, BusinessDetailActivity::class.java).apply {
                    putExtra("businessId", biz.id)
                })
            },
            onFavoriteToggle = { biz, _ -> viewModel.remove(biz.id) },
            isFavorite = { true },
        )
        findViewById<RecyclerView>(R.id.favoritesList).apply {
            layoutManager = LinearLayoutManager(this@FavoritesActivity)
            this.adapter = this@FavoritesActivity.adapter
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener(BottomNavigationView.OnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { startActivity(Intent(this, HomeActivity::class.java)); finish(); true }
                R.id.nav_appointments -> { startActivity(Intent(this, AppointmentsActivity::class.java)); finish(); true }
                R.id.nav_favorites -> { true }
                R.id.nav_profile -> { startActivity(Intent(this, ProfileActivity::class.java)); finish(); true }
                else -> false
            }
        })
        bottomNav.selectedItemId = R.id.nav_favorites
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: FavoritesUiState) {
        swipeRefresh.isRefreshing = state.loading
        findViewById<android.widget.ProgressBar>(R.id.loadingProgress).isVisible = state.loading && state.items.isEmpty()
        findViewById<android.widget.TextView>(R.id.emptyStateText).isVisible = !state.loading && state.items.isEmpty()
        adapter.submitList(state.items)
    }
}
