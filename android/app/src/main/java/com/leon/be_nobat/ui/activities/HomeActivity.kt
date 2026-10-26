package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.leon.be_nobat.domain.model.Category
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
        setupMainHeader(getString(R.string.platform_businesses))

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
            scrollToView(target)
        }

        // Setup Bank Mehr Quick Shortcuts
        findViewById<View>(R.id.btnShortcutTopCenters)?.setOnClickListener {
            viewModel.setSortAndFilter(com.leon.be_nobat.ui.view_models.BusinessSortOption.RATING, instantOnly = false)
            val target = findViewById<View>(R.id.tvTopCentersTitle) ?: businessList
            scrollToView(target)
        }
        findViewById<View>(R.id.btnShortcutSlots)?.setOnClickListener {
            viewModel.setSortAndFilter(viewModel.state.value.sortOption, instantOnly = true)
            val target = findViewById<View>(R.id.tvTopCentersTitle) ?: businessList
            scrollToView(target)
        }
        findViewById<View>(R.id.btnShortcutHowItWorks)?.setOnClickListener {
            showHowItWorksDialog()
        }
        findViewById<View>(R.id.btnShortcutUpgrade)?.setOnClickListener {
            checkAuthAndNavigate(BusinessUpgradeActivity::class.java)
        }

        // Setup Categories Horizontal List
        categoryAdapter = HomeCategoryAdapter(getCategoryList(viewModel.state.value.category, viewModel.state.value.categories)) { clicked ->
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
            viewModel.setSortAndFilter(viewModel.state.value.sortOption, instantOnly = false)
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

        val hideKeyboard = {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
            searchInput.clearFocus()
        }

        searchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_NULL
            ) {
                viewModel.updateQuery(v.text.toString().trim())
                viewModel.refresh(initial = true)
                hideKeyboard()
                val target = findViewById<View>(R.id.tvTopCentersTitle) ?: businessList
                scrollToView(target)
                true
            } else {
                false
            }
        }

        val searchLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchLayout)
        searchLayout?.setEndIconOnClickListener {
            searchInput.text?.clear()
            viewModel.updateQuery("")
            viewModel.refresh(initial = true)
            hideKeyboard()
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString().orEmpty().trim()
                if (text.isEmpty() && viewModel.state.value.query.isNotEmpty()) {
                    viewModel.updateQuery("")
                    viewModel.refresh(initial = true)
                }
            }
        })

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

    override fun onResume() {
        super.onResume()
        viewModel.refreshFavoritesOnly()
    }

    private fun scrollToView(target: View) {
        var top = 0
        var current: View? = target
        while (current != null && current != homeScrollView) {
            top += current.top
            current = current.parent as? View
        }
        homeScrollView.smoothScrollTo(0, top.coerceAtLeast(0))
    }

    private fun selectCategory(cat: String) {
        viewModel.updateCategory(cat)
        viewModel.refresh(initial = true)
        categoryAdapter.submitList(getCategoryList(cat, viewModel.state.value.categories))
        if (cat.isNotEmpty()) {
            val target = findViewById<View>(R.id.tvTopCentersTitle) ?: businessList
            scrollToView(target)
        }
    }

    private fun getCategoryList(
        selectedCategory: String,
        serverCategories: List<Category> = viewModel.state.value.categories
    ): List<HomeCategoryItem> {
        val allItem = HomeCategoryItem("", getString(R.string.cat_all), "✦", selectedCategory.isEmpty())
        val items = if (serverCategories.isNotEmpty()) {
            serverCategories.map { cat ->
                HomeCategoryItem(
                    name = cat.name,
                    label = cat.name,
                    icon = categoryIconFor(cat.name),
                    isSelected = cat.name == selectedCategory
                )
            }
        } else {
            val defs = listOf(
                Triple("آرایشگاه و زیبایی", getString(R.string.cat_beauty), "✦"),
                Triple("پزشکی و سلامت", getString(R.string.cat_general_med), "✚"),
                Triple("دندانپزشکی", getString(R.string.cat_dental), "♡"),
                Triple("تعمیرگاه و خودرو", getString(R.string.cat_auto), "🚗"),
                Triple("ورزش و تناسب اندام", getString(R.string.cat_fitness), "⚖"),
                Triple("مشاوره و روانشناسی", getString(R.string.cat_counseling), "⌁"),
                Triple("دامپزشکی و پت‌شاپ", getString(R.string.cat_pets), "🐾"),
                Triple("آتلیه و عکاسی", getString(R.string.cat_photography), "📷"),
                Triple("ماساژ و تندرستی", getString(R.string.cat_massage), "♙"),
                Triple("فیزیوتراپی و توان‌بخشی", getString(R.string.cat_physiotherapy), "⚕"),
                Triple("پوست و زیبایی", getString(R.string.cat_skin), "✦"),
                Triple("مو و آرایش", getString(R.string.cat_hair), "✂"),
                Triple("ناخن", getString(R.string.cat_nails), "✿"),
                Triple("پیرایش آقایان", getString(R.string.cat_men), "✄"),
            )
            defs.map { (name, label, icon) ->
                HomeCategoryItem(
                    name = name,
                    label = label,
                    icon = icon,
                    isSelected = name == selectedCategory,
                )
            }
        }
        return listOf(allItem) + items
    }

    private fun categoryIconFor(name: String): String {
        return when {
            name.contains("زیبایی") || name.contains("پوست") -> "✦"
            name.contains("دندان") -> "♡"
            name.contains("ماساژ") -> "♙"
            name.contains("مشاوره") || name.contains("روان") -> "⌁"
            name.contains("پزشک") || name.contains("درمان") -> "✚"
            name.contains("فیزیوتراپی") || name.contains("توان") -> "⚕"
            name.contains("ورزش") || name.contains("باشگاه") -> "⚖"
            name.contains("مو") || name.contains("پیرایش") || name.contains("آرایش") -> "✂"
            name.contains("ناخن") -> "✿"
            name.contains("دامپزشک") || name.contains("حیوان") -> "🐾"
            name.contains("خودرو") || name.contains("ماشین") -> "🚗"
            name.contains("آموزش") || name.contains("تدریس") -> "📚"
            name.contains("حقوق") || name.contains("وکالت") -> "⚖"
            else -> "✦"
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

    private fun showHowItWorksDialog() {
        val message = buildString {
            append(getString(R.string.how_it_works_subtitle))
            append("\n\n")
            append(getString(R.string.how_it_works_step1_title))
            append("\n")
            append(getString(R.string.how_it_works_step1_desc))
            append("\n\n")
            append(getString(R.string.how_it_works_step2_title))
            append("\n")
            append(getString(R.string.how_it_works_step2_desc))
            append("\n\n")
            append(getString(R.string.how_it_works_step3_title))
            append("\n")
            append(getString(R.string.how_it_works_step3_desc))
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.how_it_works_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
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
    private var lastServerCategories: List<Category> = emptyList()

    private fun render(state: HomeUiState) {
        swipeRefresh.isRefreshing = state.refreshing
        loadingProgress.isVisible = state.loading && !state.refreshing && state.items.isEmpty()
        emptyStateText.isVisible = !state.loading && state.items.isEmpty() && state.error == null
        errorText.isVisible = state.error != null
        errorText.text = state.error?.let { com.leon.be_nobat.helpers.ErrorFormatter.format(this, it) }

        val filterParts = mutableListOf<String>()
        if (state.category.isNotEmpty()) {
            filterParts.add("${getString(R.string.filter)}: ${state.category}")
        }
        if (state.filterInstantOnly) {
            filterParts.add(getString(R.string.filter_instant_booking))
        }

        if (filterParts.isNotEmpty()) {
            tvActiveCategoryFilter.isVisible = true
            tvActiveCategoryFilter.text = filterParts.joinToString(" · ")
            btnClearCategory.isVisible = true
        } else {
            tvActiveCategoryFilter.isVisible = false
            btnClearCategory.isVisible = false
        }

        if (lastCategory != state.category || lastServerCategories != state.categories) {
            lastCategory = state.category
            lastServerCategories = state.categories
            categoryAdapter.submitList(getCategoryList(state.category, state.categories))
        }

        val favChanged = lastFavoriteSnapshot != null && lastFavoriteSnapshot != state.favoriteSnapshot
        lastFavoriteSnapshot = state.favoriteSnapshot
        adapter.submitList(state.items)
        if (favChanged) {
            adapter.notifyDataSetChanged()
        }
    }
}
