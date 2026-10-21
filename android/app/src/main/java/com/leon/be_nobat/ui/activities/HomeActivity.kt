package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.ui.adapters.BusinessAdapter
import com.leon.be_nobat.ui.adapters.HomeCategoryAdapter
import com.leon.be_nobat.ui.adapters.HomeCategoryItem
import com.leon.be_nobat.ui.view_models.HomeUiState
import com.leon.be_nobat.ui.view_models.HomeViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_home
    override val applyBottomInsetsOnRoot: Boolean = false

    private val viewModel: HomeViewModel by viewModel()

    private lateinit var adapter: BusinessAdapter
    private lateinit var categoryAdapter: HomeCategoryAdapter
    private lateinit var businessList: RecyclerView
    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var homeScrollView: NestedScrollView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var loadingProgress: android.widget.ProgressBar
    private lateinit var emptyStateText: TextView
    private lateinit var errorText: TextView
    private lateinit var searchInput: TextInputEditText
    private lateinit var btnClearCategory: MaterialButton
    private lateinit var tvActiveCategoryFilter: TextView

    private val tokenManager: TokenManager by inject()

    override fun setupViews() {
        setupMainHeader()

        businessList = findViewById(R.id.businessList)
        categoriesRecyclerView = findViewById(R.id.categoriesRecyclerView)
        homeScrollView = findViewById(R.id.homeScrollView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        loadingProgress = findViewById(R.id.loadingProgress)
        emptyStateText = findViewById(R.id.emptyStateText)
        errorText = findViewById(R.id.errorText)
        searchInput = findViewById(R.id.searchInput)
        btnClearCategory = findViewById(R.id.btnClearCategory)
        tvActiveCategoryFilter = findViewById(R.id.tvActiveCategoryFilter)

        findViewById<MaterialButton>(R.id.btnFilterSort).setOnClickListener {
            showFilterSortDialog()
        }

        // Setup Hero Banner quick action
        findViewById<View>(R.id.btnHeroAction)?.setOnClickListener {
            viewModel.setSortAndFilter(viewModel.state.value.sortOption, instantOnly = true)
            val target = findViewById<View>(R.id.tvTopCentersTitle) ?: businessList
            homeScrollView.smoothScrollTo(0, target.top)
            target.requestFocus()
        }

        // Setup Bank Mehr Quick Shortcuts
        findViewById<View>(R.id.btnShortcutAppointments)?.setOnClickListener {
            checkAuthAndNavigate(AppointmentsActivity::class.java)
        }
        findViewById<View>(R.id.btnShortcutSlots)?.setOnClickListener {
            viewModel.setSortAndFilter(viewModel.state.value.sortOption, instantOnly = true)
            val target = findViewById<View>(R.id.tvTopCentersTitle) ?: businessList
            homeScrollView.smoothScrollTo(0, target.top)
            target.requestFocus()
        }
        findViewById<View>(R.id.btnShortcutFavorites)?.setOnClickListener {
            checkAuthAndNavigate(FavoritesActivity::class.java)
        }
        findViewById<View>(R.id.btnShortcutProfile)?.setOnClickListener {
            checkAuthAndNavigate(ProfileActivity::class.java)
        }

        // Setup Categories Horizontal List
        categoryAdapter = HomeCategoryAdapter(getCategoryList(viewModel.state.value.category)) { clicked ->
            val newCat = if (clicked.name.isNotEmpty() && clicked.name == viewModel.state.value.category) {
                "" // toggle off if tapped again
            } else {
                clicked.name
            }
            selectCategory(newCat)
        }
        categoriesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        categoriesRecyclerView.adapter = categoryAdapter

        btnClearCategory.setOnClickListener {
            selectCategory("")
        }

        adapter = BusinessAdapter(
            onClick = { biz ->
                val intent = Intent(this, BusinessDetailActivity::class.java).apply {
                    putExtra("businessId", biz.id)
                }
                startActivity(intent)
            },
            onFavoriteToggle = { biz, isFav ->
                lifecycleScope.launch {
                    if (tokenManager.currentSession() == null) {
                        val intent = Intent(this@HomeActivity, LoginActivity::class.java).apply {
                            putExtra(LoginActivity.EXTRA_RETURN_TARGET, HomeActivity::class.java.name)
                        }
                        startActivity(intent)
                    } else {
                        viewModel.toggleFavoriteFor(biz.id, isFav)
                    }
                }
            },
            isFavorite = { id -> viewModel.state.value.favoriteSnapshot[id] ?: false },
        )

        businessList.layoutManager = LinearLayoutManager(this)
        businessList.adapter = adapter

        // Pagination inside NestedScrollView
        homeScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, _, scrollY, _, _ ->
            val totalContentHeight = v.getChildAt(0).measuredHeight
            val scrollViewHeight = v.measuredHeight
            if (scrollY >= (totalContentHeight - scrollViewHeight - 400) && viewModel.state.value.hasMore && !viewModel.state.value.loading) {
                viewModel.loadMore()
            }
        })

        searchInput.setOnEditorActionListener { v, _, _ ->
            viewModel.updateQuery(v.text.toString())
            viewModel.refresh(initial = true)
            true
        }

        swipeRefresh.setOnRefreshListener { viewModel.refresh(initial = false) }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_appointments -> {
                    checkAuthAndNavigate(AppointmentsActivity::class.java)
                    true
                }
                R.id.nav_favorites -> {
                    checkAuthAndNavigate(FavoritesActivity::class.java)
                    true
                }
                R.id.nav_profile -> {
                    checkAuthAndNavigate(ProfileActivity::class.java)
                    true
                }
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_home

        setupDoubleBackToExit()
    }

    private fun selectCategory(cat: String) {
        viewModel.updateCategory(cat)
        viewModel.refresh(initial = true)
        categoryAdapter.submitList(getCategoryList(cat))
    }

    private fun getCategoryList(selectedCategory: String): List<HomeCategoryItem> {
        val defs = listOf(
            Triple("", getString(R.string.cat_all), "✦"),
            Triple("زیبایی و آرایش", getString(R.string.cat_beauty), "✦"),
            Triple("دندانپزشکی", getString(R.string.cat_dental), "♡"),
            Triple("ماساژ و تندرستی", getString(R.string.cat_massage), "♙"),
            Triple("مشاوره و روان‌شناسی", getString(R.string.cat_counseling), "⌁"),
            Triple("پزشکی عمومی", getString(R.string.cat_general_med), "✚"),
            Triple("فیزیوتراپی و توان‌بخشی", getString(R.string.cat_physiotherapy), "⚕"),
            Triple("ورزش و تناسب اندام", getString(R.string.cat_fitness), "⚖"),
            Triple("پوست و زیبایی", getString(R.string.cat_skin), "✦"),
            Triple("مو و آرایش", getString(R.string.cat_hair), "✂"),
            Triple("ناخن", getString(R.string.cat_nails), "✿"),
            Triple("پیرایش آقایان", getString(R.string.cat_men), "✄"),
            Triple("دامپزشکی", getString(R.string.cat_pets), "🐾"),
        )
        return defs.map { (name, label, icon) ->
            HomeCategoryItem(
                name = name,
                label = label,
                icon = icon,
                isSelected = (name.isEmpty() && selectedCategory.isEmpty()) || (name.isNotEmpty() && name == selectedCategory),
            )
        }
    }

    private fun checkAuthAndNavigate(targetClass: Class<*>) {
        lifecycleScope.launch {
            if (tokenManager.currentSession() == null) {
                val intent = Intent(this@HomeActivity, LoginActivity::class.java).apply {
                    putExtra(LoginActivity.EXTRA_RETURN_TARGET, targetClass.name)
                }
                startActivity(intent)
            } else {
                startActivity(Intent(this@HomeActivity, targetClass))
            }
        }
    }

    private fun showFilterSortDialog() {
        val s = viewModel.state.value
        val items = arrayOf(
            getString(R.string.sort_default),
            getString(R.string.sort_highest_rating),
            getString(R.string.sort_most_branches),
            getString(R.string.sort_most_services),
            getString(R.string.sort_name),
        )
        val currentIdx = when (s.sortOption) {
            com.leon.be_nobat.ui.view_models.BusinessSortOption.DEFAULT -> 0
            com.leon.be_nobat.ui.view_models.BusinessSortOption.RATING -> 1
            com.leon.be_nobat.ui.view_models.BusinessSortOption.BRANCHES -> 2
            com.leon.be_nobat.ui.view_models.BusinessSortOption.SERVICES -> 3
            com.leon.be_nobat.ui.view_models.BusinessSortOption.NAME -> 4
        }
        var selectedIdx = currentIdx
        var instantOnly = s.filterInstantOnly

        val dialogView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val cbInstant = com.google.android.material.checkbox.MaterialCheckBox(this).apply {
            text = getString(R.string.filter_instant_booking)
            isChecked = instantOnly
            setOnCheckedChangeListener { _, isChecked -> instantOnly = isChecked }
        }
        dialogView.addView(cbInstant)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.filter_and_sort)
            .setView(dialogView)
            .setSingleChoiceItems(items, currentIdx) { _, which ->
                selectedIdx = which
            }
            .setPositiveButton(R.string.apply_filter) { _, _ ->
                val sort = when (selectedIdx) {
                    1 -> com.leon.be_nobat.ui.view_models.BusinessSortOption.RATING
                    2 -> com.leon.be_nobat.ui.view_models.BusinessSortOption.BRANCHES
                    3 -> com.leon.be_nobat.ui.view_models.BusinessSortOption.SERVICES
                    4 -> com.leon.be_nobat.ui.view_models.BusinessSortOption.NAME
                    else -> com.leon.be_nobat.ui.view_models.BusinessSortOption.DEFAULT
                }
                viewModel.setSortAndFilter(sort, instantOnly)
            }
            .setNeutralButton(R.string.reset_filter) { _, _ ->
                viewModel.setSortAndFilter(com.leon.be_nobat.ui.view_models.BusinessSortOption.DEFAULT, false)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private var lastFavoriteSnapshot: Map<String, Boolean>? = null
    private var lastCategory: String? = null

    private fun render(state: HomeUiState) {
        swipeRefresh.isRefreshing = state.refreshing
        loadingProgress.isVisible = state.loading && !state.refreshing && state.items.isEmpty()
        emptyStateText.isVisible = !state.loading && state.items.isEmpty() && state.error == null
        errorText.isVisible = state.error != null
        errorText.text = state.error?.let { com.leon.be_nobat.helpers.ErrorFormatter.format(this, it) }

        if (state.category.isNotEmpty()) {
            tvActiveCategoryFilter.isVisible = true
            tvActiveCategoryFilter.text = "${getString(R.string.filter)}: ${state.category}"
            btnClearCategory.isVisible = true
        } else {
            tvActiveCategoryFilter.isVisible = false
            btnClearCategory.isVisible = false
        }

        if (lastCategory != state.category) {
            lastCategory = state.category
            categoryAdapter.submitList(getCategoryList(state.category))
        }

        val favChanged = lastFavoriteSnapshot != null && lastFavoriteSnapshot != state.favoriteSnapshot
        lastFavoriteSnapshot = state.favoriteSnapshot
        adapter.submitList(state.items)
        if (favChanged) {
            adapter.notifyDataSetChanged()
        }
    }
}
