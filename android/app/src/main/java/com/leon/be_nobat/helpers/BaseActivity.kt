package com.leon.be_nobat.helpers

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.ThemeManager
import com.leon.be_nobat.data.local.TokenManager
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.leon.be_nobat.data.remote.BeNobatApiConfig
import com.leon.be_nobat.domain.model.User
import com.leon.be_nobat.domain.repository.AuthRepository
import com.leon.be_nobat.ui.activities.*
import com.leon.be_nobat.ui.view_models.base.MainViewModel
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView
import android.widget.TextView
import coil.load
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

abstract class BaseActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private val themePreferences: ThemeManager by inject()
    private val tokenManager: TokenManager by inject()
    private val authRepository: AuthRepository by inject()
    private val viewModel = MainViewModel(themePreferences)
    private var isMenuVisible = false
    private var isHorizontalButtonsVisible = true

    @get:LayoutRes
    protected abstract val layoutResourceId: Int

    protected open val applyBottomInsetsOnRoot: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLocaleManager.currentLanguage = getCurrentLanguage()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_base)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                if (applyBottomInsetsOnRoot) systemBars.bottom else 0
            )
            insets
        }
        val container = findViewById<FrameLayout>(R.id.baseContentContainer)
        LayoutInflater.from(this).inflate(layoutResourceId, container, true)

        toolbar = findViewById(R.id.baseToolbar)
        setSupportActionBar(toolbar)

        setupViews()
        observeViewModel()
    }

    protected var currentAdminMenuItemId: Int? = null
        private set

    override fun onResume() {
        super.onResume()
        AppLocaleManager.currentLanguage = getCurrentLanguage()
        refreshAdminDrawerHeader()
        currentAdminMenuItemId?.let { menuItemId ->
            findViewById<NavigationView>(R.id.navView)?.let { navView ->
                syncAdminDrawerSelection(navView, menuItemId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentAdminMenuItemId?.let { menuItemId ->
            findViewById<NavigationView>(R.id.navView)?.let { navView ->
                syncAdminDrawerSelection(navView, menuItemId)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (isMenuVisible) {
            menuInflater.inflate(R.menu.main_menu, menu)
            menu.add(0, R.id.action_appointment_notifications, 10, "اعلان‌های نوبت").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.action_language)?.isVisible = isHorizontalButtonsVisible
            menu.findItem(R.id.switchTheme)?.isVisible = isHorizontalButtonsVisible
            return true
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_appointment_notifications -> startActivity(Intent(this, AppointmentNotificationsActivity::class.java))
            R.id.action_language -> {
                val anchor = findViewById<View>(R.id.action_language) ?: toolbar
                showLanguageMenu(anchor)
            }
            R.id.switchTheme -> setupThemeToggle()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    fun getCurrentLanguage(): String {
        val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            val tag = locales.get(0)?.language
            if (!tag.isNullOrBlank()) {
                AppLocaleManager.currentLanguage = tag
                return tag
            }
        }
        val configLocale = resources.configuration.locales.get(0)?.language
        if (!configLocale.isNullOrBlank()) {
            AppLocaleManager.currentLanguage = configLocale
            return configLocale
        }
        return AppLocaleManager.currentLanguage
    }

    fun getCurrentLanguageLabel(): String = when (getCurrentLanguage()) {
        "en" -> "EN"
        "ar" -> "عر"
        else -> "فا"
    }

    fun setupThemeToggle() {
        val isNight =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        viewModel.toggleTheme(isNight)
    }

    fun showLanguageMenu(anchor: View) {
        showPopupMenu(anchor, R.menu.language_menu) { menuItemId ->
            val languageTag = when (menuItemId) {
                R.id.language_persian -> "fa"
                R.id.language_arabic -> "ar"
                R.id.language_english -> "en"
                else -> return@showPopupMenu
            }
            if (getCurrentLanguage() != languageTag) {
                viewModel.switchLanguage(languageTag)
            }
        }
    }


    protected abstract fun setupViews()
    protected abstract fun observeViewModel()
    protected open fun onSearchClicked() {}
    protected open fun onFilterClicked() {}
    protected open fun onSortClicked() {}
    protected open fun onRefreshClicked() {}

    protected fun setHorizontalButtonsVisibility(visible: Boolean) {
        isHorizontalButtonsVisible = visible
        invalidateOptionsMenu()
    }

    protected fun setMenuVisibility(visible: Boolean) {
        isMenuVisible = visible
        invalidateOptionsMenu()
    }

    protected fun showPopupMenu(view: View, menuRes: Int, onMenuItemClick: (Int) -> Unit) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(menuRes, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            onMenuItemClick(item.itemId)
            true
        }
        popup.show()
    }

    protected fun setToolbarTitle(title: String) {
        toolbar.title = title
        setupBaseToolbar()
    }

    protected fun setToolbarTitle(hide: Boolean) {
        if (hide)
            hideToolbar()
        else
            setupBaseToolbar()
    }

    private fun setupBaseToolbar() {
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun hideToolbar() {
        toolbar.visibility = View.GONE
    }

    /**
     * Initializes the modern main header bar used across top-level screens
     * (Home, Appointments, Favorites, Profile), hiding the legacy dull toolbar
     * and setting up the title, theme toggle, language menu, and admin panel shortcut if staff.
     */
    protected fun setupMainHeader(title: CharSequence? = null) {
        setToolbarTitle(true) // Hide the legacy dull toolbar

        val themeButton = findViewById<View>(R.id.btnThemeToggle)
        val header = themeButton?.parent as? android.widget.LinearLayout
        if (header != null && header.findViewById<View>(R.id.btnAppointmentNotifications) == null) {
            val size = (48 * resources.displayMetrics.density).toInt()
            val button = androidx.appcompat.widget.AppCompatImageButton(this).apply {
                id = R.id.btnAppointmentNotifications
                contentDescription = "اعلان‌های نوبت"
                setImageResource(R.drawable.ic_appointment_notification)
                imageTintList = android.content.res.ColorStateList.valueOf(
                    com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                setBackgroundResource(android.R.drawable.list_selector_background)
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                setOnClickListener { startActivity(Intent(this@BaseActivity, AppointmentNotificationsActivity::class.java)) }
            }
            header.addView(button, header.indexOfChild(themeButton), android.widget.LinearLayout.LayoutParams(size, size))
        }
        val headerTitle = title ?: getString(R.string.app_name)
        findViewById<android.widget.TextView>(R.id.tvHeaderTitle)?.text = headerTitle
        findViewById<View>(R.id.btnThemeToggle)?.setOnClickListener {
            setupThemeToggle()
        }
        findViewById<View>(R.id.btnLanguage)?.setOnClickListener {
            showLanguageMenu(it)
        }
        val btnAdmin = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAdminPanel)
        if (btnAdmin != null) {
            lifecycleScope.launch {
                val session = tokenManager.currentSession()
                if (session?.user?.isStaff == true) {
                    btnAdmin.visibility = View.VISIBLE
                    btnAdmin.setOnClickListener {
                        startActivity(Intent(this@BaseActivity, AdminHomeActivity::class.java))
                    }
                } else {
                    btnAdmin.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Initializes the modern admin header bar used across admin & platform screens,
     * hiding the legacy base toolbar and configuring title, drawer/back, theme toggle,
     * language picker, and public view shortcuts.
     */
    protected fun setupAdminHeader(
        title: CharSequence,
        currentMenuItemId: Int? = null,
        onBack: (() -> Unit)? = null
    ) {
        setToolbarTitle(true) // Hide the legacy dull toolbar

        findViewById<android.widget.TextView>(R.id.tvHeaderTitle)?.text = title

        val btnToggle = findViewById<View>(R.id.btnToggleDrawer)
        val btnBack = findViewById<View>(R.id.btnBack)

        if (currentMenuItemId != null) {
            btnToggle?.visibility = View.VISIBLE
            btnBack?.visibility = View.GONE
            setupAdminDrawer(currentMenuItemId)
        } else {
            btnToggle?.visibility = View.GONE
            btnBack?.visibility = View.VISIBLE
            btnBack?.setOnClickListener {
                if (onBack != null) onBack() else onBackPressedDispatcher.onBackPressed()
            }
        }

        findViewById<View>(R.id.btnThemeToggle)?.setOnClickListener {
            setupThemeToggle()
        }
        findViewById<View>(R.id.btnLanguage)?.setOnClickListener {
            showLanguageMenu(it)
        }
        findViewById<View>(R.id.btnPublicView)?.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finishAffinity()
        }
    }

    suspend fun canManageBusiness(): Boolean {
        val session = tokenManager.currentSession()
        val mgmt = tokenManager.currentManagementContext()
        return mgmt?.canManageBusiness ?: session?.user?.canManageBusiness ?: false
    }

    suspend fun isPlatformAdmin(): Boolean {
        val session = tokenManager.currentSession()
        val mgmt = tokenManager.currentManagementContext()
        return mgmt?.isPlatformAdmin ?: session?.user?.isPlatformAdmin ?: false
    }

    protected fun requireBusinessManagementPermission() {
        lifecycleScope.launch {
            if (!canManageBusiness()) {
                showToast(R.string.error_access_denied)
                finish()
            }
        }
    }

    protected fun requirePlatformAdminPermission() {
        lifecycleScope.launch {
            if (!isPlatformAdmin()) {
                showToast(R.string.error_access_denied)
                finish()
            }
        }
    }

    /**
     * Synchronizes the single checked item across all groups and submenus of the admin navigation drawer.
     */
    protected fun syncAdminDrawerSelection(navView: NavigationView, targetItemId: Int) {
        fun updateMenuCheckedState(menu: Menu) {
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                if (item.hasSubMenu()) {
                    item.subMenu?.let { updateMenuCheckedState(it) }
                }
                if (item.isCheckable) {
                    item.isChecked = (item.itemId == targetItemId)
                }
            }
        }
        updateMenuCheckedState(navView.menu)
        navView.setCheckedItem(targetItemId)
    }

    /**
     * Configures the shared navigation drawer for admin destinations with role-based permissions.
     */
    protected fun setupAdminDrawer(currentMenuItemId: Int) {
        this.currentAdminMenuItemId = currentMenuItemId
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout) ?: return
        val navView = findViewById<NavigationView>(R.id.navView) ?: return

        syncAdminDrawerSelection(navView, currentMenuItemId)

        drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                currentAdminMenuItemId?.let { syncAdminDrawerSelection(navView, it) }
            }
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (slideOffset > 0f) {
                    currentAdminMenuItemId?.let { syncAdminDrawerSelection(navView, it) }
                }
            }
        })

        lifecycleScope.launch {
            val session = tokenManager.currentSession()
            val user = session?.user
            val mgmt = tokenManager.currentManagementContext()
            val canManage = mgmt?.canManageBusiness ?: user?.canManageBusiness ?: false
            val isPlatform = mgmt?.isPlatformAdmin ?: user?.isPlatformAdmin ?: false

            // Bind drawer header if present
            val headerView = if (navView.headerCount > 0) navView.getHeaderView(0) else null
            if (headerView != null && user != null) {
                bindDrawerHeader(headerView, user)
                headerView.setOnClickListener {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    startActivity(Intent(this@BaseActivity, ProfileActivity::class.java))
                }
            }

            // Role-based menu visibility
            val menu = navView.menu
            menu.findItem(R.id.menu_group_business)?.isVisible = canManage
            menu.setGroupVisible(R.id.group_business, canManage)

            menu.findItem(R.id.menu_group_platform)?.isVisible = isPlatform
            menu.setGroupVisible(R.id.group_platform, isPlatform)
        }

        findViewById<View>(R.id.btnToggleDrawer)?.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        findViewById<View>(R.id.btnThemeToggle)?.setOnClickListener {
            setupThemeToggle()
        }
        findViewById<View>(R.id.btnLanguage)?.setOnClickListener {
            showLanguageMenu(it)
        }
        findViewById<View>(R.id.btnPublicView)?.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finishAffinity()
        }

        navView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            if (item.itemId == currentMenuItemId) {
                syncAdminDrawerSelection(navView, currentMenuItemId)
                return@setNavigationItemSelectedListener true
            }

            // Immediately ensure this activity's drawer does not retain the clicked item as checked
            syncAdminDrawerSelection(navView, currentMenuItemId)

            lifecycleScope.launch {
                val canManage = canManageBusiness()
                val isPlatform = isPlatformAdmin()

                when (item.itemId) {
                    R.id.drawer_dashboard -> {
                        startActivity(Intent(this@BaseActivity, AdminHomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        if (this@BaseActivity !is AdminHomeActivity) {
                            finish()
                        }
                    }
                    R.id.drawer_calendar -> navigateAdminDestination(AdminCalendarActivity::class.java)
                    R.id.drawer_analytics -> navigateAdminDestination(com.leon.be_nobat.ui.activities.AdminAnalyticsActivity::class.java)
                    R.id.drawer_branches -> {
                        if (!canManage) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(AdminBranchesActivity::class.java)
                    }
                    R.id.drawer_services -> {
                        if (!canManage) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(AdminServicesActivity::class.java)
                    }
                    R.id.drawer_availability -> {
                        if (!canManage) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(AdminAvailabilityActivity::class.java)
                    }
                    R.id.drawer_reviews -> {
                        if (!canManage) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(AdminReviewsActivity::class.java)
                    }
                    R.id.drawer_customers -> {
                        if (!canManage) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(AdminCustomersActivity::class.java)
                    }
                    R.id.drawer_team -> {
                        if (!canManage) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(AdminTeamActivity::class.java)
                    }
                    R.id.drawer_platform_businesses -> {
                        if (!isPlatform) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(PlatformBusinessesActivity::class.java)
                    }
                    R.id.drawer_platform_catalog -> {
                        if (!isPlatform) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(PlatformCatalogActivity::class.java)
                    }
                    R.id.drawer_platform_categories -> {
                        if (!isPlatform) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(PlatformCategoriesActivity::class.java)
                    }
                    R.id.drawer_platform_access -> {
                        if (!isPlatform) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(PlatformAccessActivity::class.java)
                    }
                    R.id.drawer_platform_requests -> {
                        if (!isPlatform) showToast(R.string.error_access_denied)
                        else navigateAdminDestination(PlatformRequestsActivity::class.java)
                    }
                    R.id.drawer_back_to_user_view -> {
                        startActivity(Intent(this@BaseActivity, HomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finishAffinity()
                    }
                    R.id.drawer_logout -> {
                        com.leon.be_nobat.ui.activities.auth.LogoutHelper.logoutAndRestart(this@BaseActivity)
                    }
                }
            }
            false
        }

        if (this !is AdminHomeActivity) {
            onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            })
        }
    }

    private fun navigateAdminDestination(targetActivity: Class<*>) {
        val intent = Intent(this, targetActivity).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        if (this !is AdminHomeActivity) {
            finish()
        }
    }

    protected fun refreshAdminDrawerHeader() {
        val navView = findViewById<NavigationView>(R.id.navView) ?: return
        if (navView.headerCount == 0) return
        val headerView = navView.getHeaderView(0) ?: return

        lifecycleScope.launch {
            val session = tokenManager.currentSession()
            val user = session?.user
            if (user != null) {
                bindDrawerHeader(headerView, user)
            }
            runCatching {
                authRepository.getMe().onSuccess { freshUser ->
                    bindDrawerHeader(headerView, freshUser)
                }
            }
        }
    }

    private fun bindDrawerHeader(headerView: View, user: User) {
        val tvInitial = headerView.findViewById<TextView>(R.id.tvDrawerAvatarInitial)
        val ivAvatar = headerView.findViewById<ImageView>(R.id.ivDrawerAvatar)
        val cardAvatar = headerView.findViewById<View>(R.id.cardDrawerAvatar)
        val tvName = headerView.findViewById<TextView>(R.id.tvDrawerUserName)
        val tvRole = headerView.findViewById<TextView>(R.id.tvDrawerUserRole)
        val tvEmail = headerView.findViewById<TextView>(R.id.tvDrawerUserEmail)

        tvName?.text = user.displayName
        tvRole?.text = user.roleLabel(this@BaseActivity)
        tvEmail?.text = user.email ?: user.phoneNumber.orEmpty()

        cardAvatar?.setOnClickListener {
            startActivity(Intent(this@BaseActivity, ProfileActivity::class.java))
        }

        val avatarUrl = user.avatarUrl
        if (ivAvatar != null) {
            tvInitial?.isVisible = false
            ivAvatar.isVisible = true

            fun showDefaultAvatar() {
                ivAvatar.setImageResource(R.drawable.ic_person)
                ivAvatar.scaleType = ImageView.ScaleType.CENTER_INSIDE
                val pad = (9 * resources.displayMetrics.density).toInt()
                ivAvatar.setPadding(pad, pad, pad, pad)
                ivAvatar.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(ivAvatar, com.google.android.material.R.attr.colorOnPrimaryContainer)
                )
            }

            if (avatarUrl.isNullOrBlank()) {
                showDefaultAvatar()
            } else if (avatarUrl.startsWith("data:image/")) {
                val bitmap = runCatching {
                    val encoded = avatarUrl.substringAfter("base64,")
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
                if (bitmap != null) {
                    ivAvatar.setPadding(0, 0, 0, 0)
                    ivAvatar.imageTintList = null
                    ivAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
                    ivAvatar.setImageBitmap(bitmap)
                } else {
                    showDefaultAvatar()
                }
            } else {
                val fullUrl = BeNobatApiConfig.resolveMediaUrl(avatarUrl)
                if (!fullUrl.isNullOrBlank()) {
                    ivAvatar.setPadding(0, 0, 0, 0)
                    ivAvatar.imageTintList = null
                    ivAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
                    ivAvatar.load(fullUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_person)
                        error(R.drawable.ic_person)
                        listener(
                            onSuccess = { _, _ ->
                                ivAvatar.setPadding(0, 0, 0, 0)
                                ivAvatar.imageTintList = null
                                ivAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
                            },
                            onError = { _, _ ->
                                showDefaultAvatar()
                            }
                        )
                    }
                } else {
                    showDefaultAvatar()
                }
            }
        }
    }

    /**
     * Updates the admin cardError state with formatted, localized error message.
     */
    protected fun renderAdminError(error: Any?, onRetry: (() -> Unit)? = null) {
        val cardError = findViewById<View>(R.id.cardError) ?: return
        val tvErrorTitle = findViewById<android.widget.TextView>(R.id.tvErrorTitle)
        val tvError = findViewById<android.widget.TextView>(R.id.tvError)
        val ivErrorIcon = findViewById<android.widget.ImageView>(R.id.ivErrorIcon)

        val errorMsg = when (error) {
            is Throwable -> error.message
            is String -> error
            else -> null
        }?.lowercase() ?: ""

        if (error != null && !errorMsg.contains("cancel") && !errorMsg.contains("standalonecoroutine")) {
            val formatted = ErrorFormatter.resolve(this, error)
            tvErrorTitle?.text = formatted.title
            tvError?.text = formatted.description
            cardError.visibility = View.VISIBLE

            if (formatted.isAuthError) {
                ivErrorIcon?.setImageResource(R.drawable.ic_person)
                cardError.setOnClickListener {
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        putExtra(LoginActivity.EXTRA_RETURN_TARGET, this@BaseActivity::class.java.name)
                    }
                    startActivity(intent)
                }
            } else {
                ivErrorIcon?.setImageResource(if (formatted.isConnectionError) R.drawable.ic_wifi_off else R.drawable.ic_refresh)
                if (onRetry != null) {
                    cardError.setOnClickListener { onRetry() }
                } else {
                    cardError.setOnClickListener(null)
                }
            }
        } else {
            cardError.visibility = View.GONE
        }
    }

    private var lastBackPressTime = 0L
    private var backToast: AppToast? = null

    /**
     * Shows a toast matching the application font and theme.
     */
    fun showToast(message: CharSequence, isLong: Boolean = false) {
        AppToast.makeText(this, message, if (isLong) 1 else 0).show()
    }

    /**
     * Shows a toast matching the application font and theme from a string resource.
     */
    fun showToast(messageRes: Int, isLong: Boolean = false) {
        AppToast.makeText(this, messageRes, if (isLong) 1 else 0).show()
    }

    /**
     * Enables double-back-to-exit behavior.
     * When pressed once, shows a toast prompting the user to press again within [intervalMs].
     * If pressed a second time within [intervalMs], triggers [onExit] (default finishAffinity()).
     * [intercept] can return true to consume the back press (e.g. closing an open drawer)
     * without triggering the exit flow.
     */
    protected fun setupDoubleBackToExit(
        intercept: (() -> Boolean)? = null,
        intervalMs: Long = 2000L,
        onExit: () -> Unit = { finishAffinity() }
    ) {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (intercept?.invoke() == true) {
                    return
                }

                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < intervalMs) {
                    backToast?.cancel()
                    onExit()
                } else {
                    lastBackPressTime = now
                    backToast?.cancel()
                    backToast = AppToast.makeText(
                        this@BaseActivity,
                        getString(R.string.press_back_again_to_exit),
                        0
                    )
                    backToast?.show()
                }
            }
        })
    }
}