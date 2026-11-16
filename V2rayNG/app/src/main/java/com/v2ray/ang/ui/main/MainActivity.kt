package com.v2ray.ang.ui.main

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.backup.BackupActivity
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.home.MainTab
import com.v2ray.ang.ui.home.MrVpnBottomBar
import com.v2ray.ang.ui.home.MrVpnHomeScreen
import com.v2ray.ang.ui.home.MrVpnServersScreen
import com.v2ray.ang.ui.home.MrVpnUsageScreen
import com.v2ray.ang.ui.checkupdate.CheckUpdateActivity
import com.v2ray.ang.ui.logcat.LogcatActivity
import com.v2ray.ang.ui.perappproxy.PerAppProxyActivity
import com.v2ray.ang.ui.routing.RoutingSettingActivity
import com.v2ray.ang.ui.server.ProfileEditorResult
import com.v2ray.ang.ui.server.ServerCustomConfigActivity
import com.v2ray.ang.ui.server.ServerGroupActivity
import com.v2ray.ang.ui.server.ServerHttpActivity
import com.v2ray.ang.ui.server.ServerHysteria2Activity
import com.v2ray.ang.ui.server.ServerProxyChainActivity
import com.v2ray.ang.ui.server.ServerShadowsocksActivity
import com.v2ray.ang.ui.server.ServerSocksActivity
import com.v2ray.ang.ui.server.ServerTrojanActivity
import com.v2ray.ang.ui.server.ServerVlessActivity
import com.v2ray.ang.ui.server.ServerVmessActivity
import com.v2ray.ang.ui.server.ServerWireguardActivity
import com.v2ray.ang.ui.settings.SettingsActivity
import com.v2ray.ang.ui.subscription.SubSettingActivity
import com.v2ray.ang.ui.userasset.UserAssetActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, MainRepository(application as AngApplication))
    }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startV2Ray()
        }

    private val profileEditorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val action = data.getStringExtra(ProfileEditorResult.EXTRA_ACTION)
                ?: return@registerForActivityResult
            if (action != ProfileEditorResult.ACTION_SAVED &&
                action != ProfileEditorResult.ACTION_DELETED
            ) return@registerForActivityResult
            val restartService = data.getBooleanExtra(
                ProfileEditorResult.EXTRA_RESTART_SERVICE, false
            )
            val selectedProfileSaved = action == ProfileEditorResult.ACTION_SAVED &&
                    data.getStringExtra(ProfileEditorResult.EXTRA_GUID) == mainViewModel.uiState.value.selectedGuid
            mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService || selectedProfileSaved) LauncherManager.restartService(this)
        }

    private val settingsActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val restartService = SettingsChangeManager.consumeRestartService()
            val refreshGroups = SettingsChangeManager.consumeSetupGroupTab()
            mainViewModel.refreshUiSettings()
            if (refreshGroups) mainViewModel.onAction(MainAction.RefreshGroups)
            if (restartService) LauncherManager.restartService(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel.onAction(MainAction.Initialize)

        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    @Composable
    override fun ScreenContent() {
        var currentTab by rememberSaveable { mutableStateOf(MainTab.Home) }
        val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

        var showRemoveConfirm by rememberSaveable(stateSaver = ServerDeleteTarget.Saver) {
            mutableStateOf<ServerDeleteTarget?>(null)
        }
        var shareTarget by remember { mutableStateOf<Triple<String, ProfileItem, Boolean>?>(null) }
        var showImportDialog by remember { mutableStateOf(false) }

        BackHandler {
            if (currentTab != MainTab.Home) {
                currentTab = MainTab.Home
            } else {
                moveTaskToBack(false)
            }
        }

        val onActionHandler: (MainAction) -> Unit = { action ->
            when (action) {
                MainAction.ToggleService -> handleFabAction()
                MainAction.TestCurrentServer -> handleLayoutTestClick()
                MainAction.ImportQRcode -> importQRcode()
                MainAction.ImportClipboard -> importClipboard()
                MainAction.ImportConfigLocal -> importConfigLocal()
                is MainAction.ImportManually -> importManually(action.type)
                MainAction.RestartService -> LauncherManager.restartServiceOrStart(this, ::requestServiceStart)
                MainAction.LocateSelectedServer -> mainViewModel.triggerLocateSelectedServer()
                is MainAction.SelectServer -> setSelectServer(action.guid)
                is MainAction.EditServer -> editServer(action.guid, action.profile)
                is MainAction.ShareClipboard -> shareToClipboard(action.guid)
                is MainAction.ShareFullContent -> shareFullContentAsync(action.guid)
                is MainAction.OpenSubscriptionLink -> openSubscriptionLink(action.url)
                else -> mainViewModel.onAction(action)
            }
        }

        val onTabSelected: (MainTab) -> Unit = { tab ->
            when (tab) {
                MainTab.Home -> currentTab = MainTab.Home
                MainTab.Servers -> currentTab = MainTab.Servers
                MainTab.Usage -> currentTab = MainTab.Usage
                MainTab.Settings -> navigateTo(MainDestination.Settings)
            }
        }

        val removeServer: (String, String) -> Unit = { guid, profileName ->
            showRemoveConfirm = ServerDeleteTarget(guid, profileName)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (currentTab) {
                MainTab.Home -> {
                    Scaffold(
                        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                        bottomBar = {
                            MrVpnBottomBar(
                                currentTab = currentTab,
                                onTabSelected = onTabSelected
                            )
                        }
                    ) { innerPadding ->
                        MrVpnHomeScreen(
                            mainViewModel = mainViewModel,
                            onAction = onActionHandler,
                            onNavigate = { route -> navigateTo(route) },
                            onOpenServers = { currentTab = MainTab.Servers },
                            onOpenSettings = { navigateTo(MainDestination.Settings) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
                MainTab.Servers -> {
                    Scaffold(
                        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                        bottomBar = {
                            MrVpnBottomBar(
                                currentTab = currentTab,
                                onTabSelected = onTabSelected
                            )
                        }
                    ) { innerPadding ->
                        MrVpnServersScreen(
                            mainViewModel = mainViewModel,
                            onAction = onActionHandler,
                            onSelectServer = { guid ->
                                setSelectServer(guid)
                                if (mainViewModel.uiState.value.isRunning) {
                                    LauncherManager.restartServiceOrStart(this@MainActivity, ::requestServiceStart)
                                }
                                lifecycleScope.launch {
                                    kotlinx.coroutines.delay(300)
                                    currentTab = MainTab.Home
                                }
                            },
                            onShareServer = { guid, profile ->
                                shareTarget = Triple(guid, profile, false)
                            },
                            onEditServer = { guid, profile ->
                                editServer(guid, profile)
                            },
                            onRemoveServer = removeServer,
                            onOpenImport = {
                                showImportDialog = true
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
                MainTab.Usage -> {
                    Scaffold(
                        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
                        bottomBar = {
                            MrVpnBottomBar(
                                currentTab = currentTab,
                                onTabSelected = onTabSelected
                            )
                        }
                    ) { innerPadding ->
                        MrVpnUsageScreen(
                            mainViewModel = mainViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
                MainTab.Settings -> {
                    LaunchedEffect(Unit) {
                        navigateTo(MainDestination.Settings)
                        currentTab = MainTab.Home
                    }
                }
            }

            // F1: Floating branded notification banner («متصل شد» / «اتصال قطع شد» / «خطا در اتصال»)
            MrVpnFloatingBanner(
                message = uiState.bannerMessage,
                onDismiss = { mainViewModel.onAction(MainAction.DismissBanner) }
            )
        }

        // Dialogs for servers
        if (showRemoveConfirm != null) {
            DeleteConfirmDialog(
                message = "آیا از حذف «${showRemoveConfirm?.profileName}» اطمینان دارید؟",
                onConfirm = {
                    val guid = showRemoveConfirm!!.guid
                    showRemoveConfirm = null
                    onActionHandler(MainAction.RemoveServer(guid))
                },
                onDismiss = { showRemoveConfirm = null }
            )
        }

        if (shareTarget != null) {
            val (guid, profile, more) = shareTarget!!
            ShareMethodDialog(
                guid = guid,
                profile = profile,
                more = more,
                onDismiss = { shareTarget = null },
                onAction = onActionHandler,
                onRemove = removeServer,
            )
        }

        if (uiState.shareQRCodeBitmap != null) {
            QRCodeDialog(
                bitmap = uiState.shareQRCodeBitmap!!,
                onDismiss = { onActionHandler(MainAction.DismissQRCodeDialog) }
            )
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("افزودن کانفیگ", fontWeight = FontWeight.Bold) },
                text = {
                    androidx.compose.foundation.layout.Column(
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showImportDialog = false
                                    importClipboard()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF18181D)
                        ) {
                            Text("جای‌گذاری از کلیپ‌بورد", modifier = Modifier.padding(14.dp), color = Color(0xFFD8B45A))
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showImportDialog = false
                                    importQRcode()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF18181D)
                        ) {
                            Text("اسکن بارکد (QR Code)", modifier = Modifier.padding(14.dp), color = Color(0xFFD8B45A))
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showImportDialog = false
                                    importConfigLocal()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF18181D)
                        ) {
                            Text("ورود از فایل محلی", modifier = Modifier.padding(14.dp), color = Color(0xFFF3F3F4))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("انصراف", color = Color(0xFF8B8B93))
                    }
                },
                containerColor = Color(0xFF101013),
                titleContentColor = Color(0xFFF3F3F4),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }

    @Composable
    private fun MrVpnFloatingBanner(
        message: String?,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        if (message.isNullOrEmpty()) return
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2000L)
            onDismiss()
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 20.dp, end = 20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF101013),
                border = BorderStroke(1.dp, Color(0xFFD8B45A)),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = message,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF3F3F4),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }
    }

    private fun shareToClipboard(guid: String): Boolean =
        AngConfigManager.share2Clipboard(this, guid) == 0

    private fun shareFullContentAsync(guid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.shareFullContent2Clipboard(this@MainActivity, guid)
            withContext(Dispatchers.Main) {
                if (result == 0) toastSuccess(R.string.toast_success)
                else toastError(R.string.toast_failure)
            }
        }
    }

    private fun openSubscriptionLink(url: String) {
        // Links come from a remote panel, so only http(s) and tg:// are ever handed to ACTION_VIEW.
        val safeUrl = SubscriptionInfoParser.sanitizeLink(url) ?: return
        Utils.openUri(this, safeUrl)
    }

    private fun navigateTo(destination: MainDestination) {
        val intent = when (destination) {
            MainDestination.Subscriptions -> Intent(this, SubSettingActivity::class.java)
            MainDestination.PerAppProxy -> Intent(this, PerAppProxyActivity::class.java)
            MainDestination.Routing -> Intent(this, RoutingSettingActivity::class.java)
            MainDestination.UserAssets -> Intent(this, UserAssetActivity::class.java)
            MainDestination.Settings -> Intent(this, SettingsActivity::class.java)
            MainDestination.Support -> {
                Utils.openUri(this, AppConfig.SUPPORT_URL)
                return
            }
            MainDestination.TelegramChannel -> {
                Utils.openUri(this, AppConfig.TG_CHANNEL_URL)
                return
            }
            MainDestination.Logcat -> Intent(this, LogcatActivity::class.java)
            MainDestination.CheckUpdate -> Intent(this, CheckUpdateActivity::class.java)
            MainDestination.BackupRestore -> Intent(this, BackupActivity::class.java)
            MainDestination.About -> Intent(this, AboutActivity::class.java)
            MainDestination.Promotion -> {
                Utils.openUri(
                    this,
                    "${Utils.decode(AppConfig.APP_PROMOTION_URL)}?t=${System.currentTimeMillis()}"
                )
                return
            }
        }
        settingsActivityLauncher.launch(intent)
    }

    private fun handleFabAction() {
        if (mainViewModel.uiState.value.isRunning) {
            LauncherManager.stopService(this)
        } else {
            requestServiceStart()
        }
    }

    private fun requestServiceStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun handleLayoutTestClick() {
        if (mainViewModel.uiState.value.isRunning) {
            mainViewModel.testCurrentServerRealPing()
        }
    }

    private fun startV2Ray() {
        if (mainViewModel.uiState.value.selectedGuid.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
        ) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this)
    }

    private fun importManually(createConfigType: Int) {
        val intent = when (createConfigType) {
            EConfigType.POLICYGROUP.value -> Intent(this, ServerGroupActivity::class.java)
            EConfigType.PROXYCHAIN.value -> Intent(this, ServerProxyChainActivity::class.java)
            EConfigType.VMESS.value -> Intent(this, ServerVmessActivity::class.java)
            EConfigType.VLESS.value -> Intent(this, ServerVlessActivity::class.java)
            EConfigType.SHADOWSOCKS.value -> Intent(this, ServerShadowsocksActivity::class.java)
            EConfigType.SOCKS.value -> Intent(this, ServerSocksActivity::class.java)
            EConfigType.HTTP.value -> Intent(this, ServerHttpActivity::class.java)
            EConfigType.TROJAN.value -> Intent(this, ServerTrojanActivity::class.java)
            EConfigType.WIREGUARD.value -> Intent(this, ServerWireguardActivity::class.java)
            EConfigType.HYSTERIA2.value -> Intent(this, ServerHysteria2Activity::class.java)
            else -> Intent(this, ServerHttpActivity::class.java).apply {
                putExtra("createConfigType", createConfigType)
            }
        }.apply {
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun importQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                mainViewModel.onAction(MainAction.ImportBatchConfig(scanResult))
            }
        }
    }

    private fun importClipboard() {
        try {
            val text = Utils.getClipboard(this)
            mainViewModel.onAction(MainAction.ImportBatchConfig(text))
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
        }
    }

    private fun importConfigLocal() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    mainViewModel.onAction(MainAction.ImportBatchConfig(reader.readText()))
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to read content from URI", e)
            }
        }
    }

    private fun editServer(guid: String, profile: ProfileItem) {
        val activityClass = when (profile.configType) {
            EConfigType.CUSTOM -> ServerCustomConfigActivity::class.java
            EConfigType.POLICYGROUP -> ServerGroupActivity::class.java
            EConfigType.PROXYCHAIN -> ServerProxyChainActivity::class.java
            EConfigType.VMESS -> ServerVmessActivity::class.java
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS -> ServerSocksActivity::class.java
            EConfigType.HTTP -> ServerHttpActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            else -> ServerHttpActivity::class.java
        }
        val intent = Intent(this, activityClass).apply {
            putExtra("guid", guid)
            putExtra("isRunning", mainViewModel.uiState.value.isRunning)
            putExtra("createConfigType", profile.configType.value)
            putExtra("subscriptionId", mainViewModel.uiState.value.selectedGroupId)
        }
        profileEditorLauncher.launch(intent)
    }

    private fun setSelectServer(guid: String) {
        val selected = mainViewModel.uiState.value.selectedGuid
        if (guid != selected) {
            mainViewModel.updateSelectedGuid(guid)
            LauncherManager.restartService(this)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
