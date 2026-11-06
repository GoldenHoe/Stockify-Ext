package com.rsps1008.stockify

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination
import androidx.lifecycle.ViewModel
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rsps1008.stockify.data.TextSizeMode
import com.rsps1008.stockify.data.SettingsDataStore
import com.rsps1008.stockify.data.AutomaticCloudBackupWorker
import com.rsps1008.stockify.data.AUTOMATIC_CLOUD_BACKUP_WORK_NAME
import com.rsps1008.stockify.ui.navigation.NavGraph
import com.rsps1008.stockify.ui.navigation.Screen
import com.rsps1008.stockify.ui.screens.AppLockScreen
import com.rsps1008.stockify.ui.screens.UPDATE_HIGHLIGHTS_VERSION
import com.rsps1008.stockify.ui.screens.UpdateHighlightsDialog
import com.rsps1008.stockify.ui.screens.shouldShowUpdateHighlights
import com.rsps1008.stockify.ui.text.DisplayLanguage
import com.rsps1008.stockify.ui.theme.StockifyTheme
import com.rsps1008.stockify.ui.text.Text
import com.rsps1008.stockify.ui.text.localized
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var appLockEnabled: Boolean? by mutableStateOf(null)
    private val appLockSession: AppLockSessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DisplayLanguage.updateFrom(this)
        val stockifyApplication = application as StockifyApplication
        val dataStore = stockifyApplication.settingsDataStore
        lifecycleScope.launch {
            launch { stockifyApplication.updateTaiwanStockListIfDue() }
            launch { stockifyApplication.updateUsStockListIfDue() }
            launch { stockifyApplication.updateCnStockListIfDue() }
        }
        lifecycleScope.launch {
            dataStore.themeFlow.collect { theme ->
                val mode = when (theme) {
                    "Light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "Dark", "AMOLED" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (AppCompatDelegate.getDefaultNightMode() != mode) {
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
            }
        }
        lifecycleScope.launch {
            dataStore.appLockEnabledFlow.collect { enabled ->
                val previous = appLockEnabled
                appLockEnabled = enabled
                when {
                    !enabled -> appLockSession.unlock()
                    previous == false -> appLockSession.unlock()
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            val theme by dataStore.themeFlow.collectAsState(initial = "System")
            val textSizeMode by dataStore.textSizeModeFlow.collectAsState(initial = TextSizeMode.DEFAULT)
            val navController = rememberNavController()
            StockifyTheme(
                amoledTheme = theme == "AMOLED",
                textScale = TextSizeMode.scale(textSizeMode)
            ) {
                when (appLockEnabled) {
                    null -> Surface(modifier = Modifier.fillMaxSize()) {}
                    false -> MainScreen(navController, dataStore)
                    true -> if (appLockSession.unlocked) {
                        MainScreen(navController, dataStore)
                    } else {
                        AppLockScreen(
                            activity = this@MainActivity,
                            settingsDataStore = dataStore,
                            onUnlocked = appLockSession::unlock
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            val request = OneTimeWorkRequestBuilder<AutomaticCloudBackupWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                AUTOMATIC_CLOUD_BACKUP_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
        if (!isChangingConfigurations && appLockEnabled == true) {
            appLockSession.lock()
        }
    }
}

class AppLockSessionViewModel : ViewModel() {
    var unlocked: Boolean by mutableStateOf(false)
        private set

    fun unlock() {
        unlocked = true
    }

    fun lock() {
        unlocked = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    settingsDataStore: SettingsDataStore
) {
    val lastShownUpdateVersion by settingsDataStore.lastUpdateHighlightsVersionFlow
        .collectAsState(initial = BuildConfig.VERSION_NAME)
    val coroutineScope = rememberCoroutineScope()
    var updateHighlightsDismissedForSession by rememberSaveable { mutableStateOf(false) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isAddTransactionScreen = currentDestination?.hierarchy?.any {
        it.route == Screen.AddTransaction.route
    } == true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isAddTransactionScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    AppNavigationItem(
                        selected = currentDestination.isOn(Screen.Holdings.route),
                        label = "持股",
                        icon = Icons.Outlined.PieChart,
                        onClick = { navigateToTopLevel(navController, Screen.Holdings.route) }
                    )
                    AppNavigationItem(
                        selected = currentDestination.isOn(Screen.Transactions.route),
                        label = "交易",
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        onClick = { navigateToTopLevel(navController, Screen.Transactions.route) }
                    )
                    AppNavigationItem(
                        selected = currentDestination.isOn(Screen.DataManagement.route),
                        label = "数据",
                        icon = Icons.Outlined.CloudSync,
                        onClick = { navigateToTopLevel(navController, Screen.DataManagement.route) }
                    )
                    AppNavigationItem(
                        selected = currentDestination.isOn(Screen.Settings.route),
                        label = "设置",
                        icon = Icons.Outlined.Settings,
                        onClick = { navigateToTopLevel(navController, Screen.Settings.route) }
                    )
                }
            }
        },
        floatingActionButton = {
            // Contextual FAB: the quick "add transaction" action belongs to the two
            // data screens; showing it on Settings/Data management only covered content.
            val showAddFab = !isAddTransactionScreen &&
                (currentDestination.isOn(Screen.Holdings.route) ||
                    currentDestination.isOn(Screen.Transactions.route))
            if (showAddFab) {
                FloatingActionButton(
                    onClick = { navigateToAddTransaction(navController) },
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "新增交易".localized())
                }
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        )
    }

    if (!updateHighlightsDismissedForSession &&
        shouldShowUpdateHighlights(BuildConfig.VERSION_NAME, lastShownUpdateVersion)
    ) {
        UpdateHighlightsDialog(
            onDismiss = { updateHighlightsDismissedForSession = true },
            onDismissUntilNextVersion = {
                updateHighlightsDismissedForSession = true
                coroutineScope.launch {
                    settingsDataStore.setLastUpdateHighlightsVersion(UPDATE_HIGHLIGHTS_VERSION)
                }
            }
        )
    }
}

@Composable
private fun RowScope.AppNavigationItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        label = {
            Text(text = label, maxLines = 1, softWrap = false)
        }
    )
}

private fun NavDestination?.isOn(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } == true

private fun navigateToAddTransaction(navController: androidx.navigation.NavHostController) {
    val currentEntry = navController.currentBackStackEntry
    val onDetail = currentEntry?.destination?.route == Screen.StockDetail.route
    val stockCode = if (onDetail) currentEntry?.arguments?.getString("stockCode") else null
    val market = if (onDetail) currentEntry?.arguments?.getString("market") else null
    navController.navigate(
        Screen.AddTransaction.createRoute(stockCode = stockCode, market = market)
    ) {
        launchSingleTop = true
    }
}

private fun navigateToTopLevel(
    navController: androidx.navigation.NavHostController,
    route: String
) {
    if (navController.currentDestination?.route == Screen.AddTransaction.route) {
        navController.popBackStack()
    }
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}
