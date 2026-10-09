package com.example.presentation

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.example.R
import com.example.data.local.preferences.AppPreferences
import com.example.data.local.preferences.ThemePreferences
import com.example.domain.model.AppPrimaryColor
import com.example.service.TestService
import com.example.util.LocaleHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.presentation.compose.DrawerMenuCompose
import com.example.presentation.compose.WifiCardTheme
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.widget.CustomToolbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import timber.log.Timber
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var toolbar: CustomToolbar
    private var currentNavTag by mutableStateOf("home")
    
    private val appPreferences: AppPreferences by inject()
    private val themePreferences: ThemePreferences by inject()
    private val sessionRepository: com.example.domain.repository.ISessionRepository by inject()

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.d("POST_NOTIFICATIONS permission granted")
        } else {
            Timber.w("POST_NOTIFICATIONS permission denied")
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getPersistedLocale(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // App Theme loading before super.onCreate()
        val themeMode = try {
            val sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            sharedPreferences.getString("theme", "dark") ?: "dark"
        } catch (_: Exception) {
            "dark"
        }
        val nightMode = when (themeMode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)

        // Apply centralized primary color theme before creating view hierarchy
        val primaryColorKey = ThemePreferences.getPrimaryColorSync(this)
        val primaryColor = AppPrimaryColor.fromKey(primaryColorKey)
        setTheme(primaryColor.styleResId)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Clean up any stale active sessions on launch ONLY if no test is currently actively running
        if (!TestService.isRunning.value) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    sessionRepository.cleanUpOrphanedSessions()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to clean up stale active sessions on app launch")
                }
            }
        }

        // Request notification permission if on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)

        val isUnlocked = runBlocking { appPreferences.isUnlocked.first() }
        val failedAttempts = runBlocking { appPreferences.failedAttempts.first() }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        if (savedInstanceState == null) {
            val inflater = navController.navInflater
            val graph = inflater.inflate(R.navigation.nav_graph)
            if (isUnlocked) {
                graph.setStartDestination(R.id.nav_home_fragment)
            } else if (failedAttempts >= 3) {
                graph.setStartDestination(R.id.nav_locked_fragment)
            } else {
                graph.setStartDestination(R.id.nav_security_fragment)
            }
            navController.graph = graph
        }

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home_fragment,
                R.id.nav_history_fragment,
                R.id.nav_settings_fragment
            ),
            drawerLayout
        )

        // Setup ActionBar with Navigation Controller
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)

        fun navigateSingleTop(destId: Int) {
            if (navController.currentDestination?.id == destId) return
            val navOptions = androidx.navigation.NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(
                    navController.graph.findStartDestination().id,
                    inclusive = false,
                    saveState = true
                )
                .build()
            try {
                navController.navigate(destId, null, navOptions)
            } catch (e: Exception) {
                navController.navigate(destId)
            }
        }

        // Modern Compose Drawer Setup matching 'شكل القائمة الجانبية.jpg'
        val drawerComposeView: ComposeView = findViewById(R.id.drawer_compose_view)
        drawerComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WifiCardTheme {
                    DrawerMenuCompose(
                        activeItem = currentNavTag,
                        onItemClick = { item ->
                            drawerLayout.closeDrawer(GravityCompat.START)
                            when (item) {
                                "home" -> navigateSingleTop(R.id.nav_home_fragment)
                                "test" -> navigateSingleTop(R.id.nav_test_fragment)
                                "history" -> navigateSingleTop(R.id.nav_history_fragment)
                                "routers" -> navigateSingleTop(R.id.nav_router_manager_fragment)
                                "settings" -> navigateSingleTop(R.id.nav_settings_fragment)
                                "about" -> {
                                    val versionText = getString(R.string.main_version_info)
                                    com.example.util.DialogHelper.showCustomDialog(
                                        context = this@MainActivity,
                                        title = getString(R.string.about_title),
                                        message = getString(R.string.about_message) + versionText,
                                        dialogType = com.example.util.DialogHelper.DialogType.INFO,
                                        iconRes = R.drawable.ic_info,
                                        positiveButtonText = getString(R.string.btn_close)
                                    )
                                }
                                "exit" -> {
                                    com.example.util.DialogHelper.showCustomDialog(
                                        context = this@MainActivity,
                                        title = getString(R.string.main_confirm_exit_title),
                                        message = getString(R.string.main_confirm_exit_msg),
                                        dialogType = com.example.util.DialogHelper.DialogType.WARNING,
                                        iconRes = R.drawable.ic_nav_exit,
                                        positiveButtonText = getString(R.string.main_exit),
                                        positiveAction = {
                                            finishAffinity()
                                        },
                                        negativeButtonText = getString(R.string.main_cancel)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        // Dynamic Graphic Header Setup (Real Images)
        val appHeaderContainer: android.view.View = findViewById(R.id.app_header_container)
        val layoutHeaderHome: android.view.View = findViewById(R.id.layout_header_home)
        val layoutHeaderPages: android.view.View = findViewById(R.id.layout_header_pages)
        val btnHomeDrawer: android.widget.ImageButton = findViewById(R.id.btn_home_drawer)
        val btnPagesNav: android.widget.ImageButton = findViewById(R.id.btn_pages_nav)
        val tvHeaderTitle: android.widget.TextView = findViewById(R.id.tv_header_title)
        val tvHeaderSubtitle: android.widget.TextView = findViewById(R.id.tv_header_subtitle)

        btnHomeDrawer.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        btnPagesNav.setOnClickListener {
            val curId = navController.currentDestination?.id
            if (curId == R.id.nav_router_form_fragment) {
                navController.navigateUp()
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Bottom Nav setup
        val bottomNav: BottomNavigationView = findViewById(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)
        bottomNav.setOnItemSelectedListener { item ->
            navigateSingleTop(item.itemId)
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            currentNavTag = when (destination.id) {
                R.id.nav_home_fragment -> "home"
                R.id.nav_test_fragment -> "test"
                R.id.nav_history_fragment -> "history"
                R.id.nav_router_manager_fragment, R.id.nav_router_form_fragment -> "routers"
                R.id.nav_settings_fragment -> "settings"
                else -> "home"
            }
            when (destination.id) {
                R.id.nav_security_fragment, R.id.nav_locked_fragment -> {
                    toolbar.visibility = android.view.View.GONE
                    appHeaderContainer.visibility = android.view.View.GONE
                    bottomNav.visibility = android.view.View.GONE
                    drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
                R.id.nav_home_fragment -> {
                    toolbar.visibility = android.view.View.GONE
                    appHeaderContainer.visibility = android.view.View.VISIBLE
                    layoutHeaderHome.visibility = android.view.View.VISIBLE
                    layoutHeaderPages.visibility = android.view.View.GONE
                    bottomNav.visibility = android.view.View.VISIBLE
                    drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
                }
                else -> {
                    toolbar.visibility = android.view.View.GONE
                    appHeaderContainer.visibility = android.view.View.VISIBLE
                    layoutHeaderHome.visibility = android.view.View.GONE
                    layoutHeaderPages.visibility = android.view.View.VISIBLE
                    bottomNav.visibility = android.view.View.VISIBLE
                    drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)

                    val pillBg = when (primaryColorKey.lowercase()) {
                        "blue" -> R.drawable.bg_badge_pill_blue
                        "purple" -> R.drawable.bg_badge_pill_purple
                        "yellow" -> R.drawable.bg_badge_pill_yellow
                        "green" -> R.drawable.bg_badge_pill_green
                        else -> R.drawable.bg_badge_pill_red
                    }
                    tvHeaderSubtitle.setBackgroundResource(pillBg)

                    when (destination.id) {
                        R.id.nav_test_fragment -> {
                            tvHeaderTitle.text = getString(R.string.menu_test)
                            tvHeaderSubtitle.text = getString(R.string.test_live_stream)
                            btnPagesNav.setImageResource(R.drawable.ic_menu)
                        }
                        R.id.nav_history_fragment -> {
                            tvHeaderTitle.text = getString(R.string.menu_history)
                            tvHeaderSubtitle.text = getString(R.string.header_history_sub)
                            btnPagesNav.setImageResource(R.drawable.ic_menu)
                        }
                        R.id.nav_settings_fragment -> {
                            tvHeaderTitle.text = getString(R.string.menu_settings)
                            tvHeaderSubtitle.text = getString(R.string.header_settings_sub)
                            btnPagesNav.setImageResource(R.drawable.ic_menu)
                        }
                        R.id.nav_router_manager_fragment -> {
                            tvHeaderTitle.text = getString(R.string.menu_router_manager)
                            tvHeaderSubtitle.text = getString(R.string.header_router_manager_sub)
                            btnPagesNav.setImageResource(R.drawable.ic_menu)
                        }
                        R.id.nav_router_form_fragment -> {
                            tvHeaderTitle.text = getString(R.string.header_router_form_title)
                            tvHeaderSubtitle.text = getString(R.string.header_router_form_sub)
                            btnPagesNav.setImageResource(R.drawable.ic_back)
                        }
                        else -> {
                            tvHeaderTitle.text = destination.label ?: getString(R.string.app_name)
                            tvHeaderSubtitle.text = getString(R.string.app_subtitle)
                            btnPagesNav.setImageResource(R.drawable.ic_back)
                        }
                    }
                }
            }
        }
    }

    fun openDrawer() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
