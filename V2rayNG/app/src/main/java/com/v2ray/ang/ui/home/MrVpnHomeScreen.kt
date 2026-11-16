package com.v2ray.ang.ui.home

import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainDestination
import com.v2ray.ang.ui.main.MainViewModel
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// --- Persian Utilities ---
fun toPersianDigits(text: String): String {
    val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    val sb = StringBuilder(text.length)
    for (ch in text) {
        if (ch in '0'..'9') {
            sb.append(persianDigits[ch - '0'])
        } else {
            sb.append(ch)
        }
    }
    return sb.toString()
}

object FlagHelper {
    private val FLAG_REGEX = Regex("[\\uD83C][\\uDDE6-\\uDDFF][\\uD83C][\\uDDE6-\\uDDFF]")

    fun getFlag(remarks: String?): String {
        if (remarks.isNullOrBlank()) return "🌐"
        val match = FLAG_REGEX.find(remarks)
        if (match != null) return match.value

        val lower = remarks.lowercase(Locale.ROOT)
        return when {
            lower.contains("ترکیه") || lower.contains("turkey") || lower.contains("istanbul") || lower.contains("turk") -> "🇹🇷"
            lower.contains("آلمان") || lower.contains("المان") || lower.contains("germany") || lower.contains("deutschland") || lower.contains("frankfurt") || lower.contains("berlin") -> "🇩🇪"
            lower.contains("بلغارستان") || lower.contains("bulgaria") || lower.contains("sofia") -> "🇧🇬"
            lower.contains("هلند") || lower.contains("netherlands") || lower.contains("amsterdam") || lower.contains("dutch") -> "🇳🇱"
            lower.contains("اوکراین") || lower.contains("ukraine") || lower.contains("kyiv") -> "🇺🇦"
            lower.contains("آمریکا") || lower.contains("امریکا") || lower.contains("usa") || lower.contains("america") || lower.contains("united states") -> "🇺🇸"
            lower.contains("فنلاند") || lower.contains("finland") || lower.contains("helsinki") -> "🇫🇮"
            lower.contains("آذربایجان") || lower.contains("اذربایجان") || lower.contains("azerbaijan") || lower.contains("baku") -> "🇦🇿"
            lower.contains("فرانسه") || lower.contains("france") || lower.contains("paris") -> "🇫🇷"
            lower.contains("آلبانی") || lower.contains("البانی") || lower.contains("albania") || lower.contains("tirana") -> "🇦🇱"
            lower.contains("انگلیس") || lower.contains("بریتانیا") || lower.contains("uk") || lower.contains("england") || lower.contains("london") || lower.contains("britain") -> "🇬🇧"
            lower.contains("کانادا") || lower.contains("canada") || lower.contains("toronto") -> "🇨🇦"
            lower.contains("ایتالیا") || lower.contains("italy") || lower.contains("milan") || lower.contains("rome") -> "🇮🇹"
            else -> "🌐"
        }
    }
}

enum class MainTab(@DrawableRes val iconRes: Int, @StringRes val labelRes: Int) {
    Home(R.drawable.ic_tab_home_24dp, R.string.tab_home),
    Servers(R.drawable.ic_tab_servers_24dp, R.string.tab_servers),
    Usage(R.drawable.ic_tab_usage_24dp, R.string.tab_usage),
    Settings(R.drawable.ic_sliders_24dp, R.string.tab_settings)
}

@Composable
fun MrVpnHomeScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkTheme = LocalDarkTheme.current
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isConnected = uiState.isRunning
    val scope = rememberCoroutineScope()

    // Active servers in current group
    val currentServersFlow = remember(uiState.selectedGroupId) {
        mainViewModel.serversForGroup(uiState.selectedGroupId)
    }
    val servers by currentServersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Currently selected server
    val selectedServer = remember(servers, uiState.selectedGuid) {
        servers.find { it.guid == uiState.selectedGuid } ?: servers.firstOrNull()
    }

    // Active subscription usage info
    val activeGroup = remember(uiState.groups, uiState.selectedGroupId) {
        uiState.groups.find { it.id == uiState.selectedGroupId } ?: uiState.groups.firstOrNull()
    }
    val usageInfo: SubscriptionUsageInfo? = activeGroup?.usageInfo

    // Live duration timer
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var connectionStartRealtime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            if (connectionStartRealtime == 0L) {
                connectionStartRealtime = SystemClock.elapsedRealtime()
            }
            while (isActive) {
                val elapsed = (SystemClock.elapsedRealtime() - connectionStartRealtime) / 1000L
                elapsedSeconds = elapsed.coerceAtLeast(0L)
                delay(1000L)
            }
        } else {
            connectionStartRealtime = 0L
            elapsedSeconds = 0L
        }
    }

    val hours = elapsedSeconds / 3600L
    val minutes = (elapsedSeconds % 3600L) / 60L
    val seconds = elapsedSeconds % 60L
    val timerString = toPersianDigits(String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds))

    // Smart connect state
    var isSmartConnecting by remember { mutableStateOf(false) }

    val onSmartConnectClick: () -> Unit = {
        if (servers.isEmpty()) {
            context.toast(context.getString(R.string.no_server_available))
        } else {
            isSmartConnecting = true
            scope.launch {
                val results = withContext(Dispatchers.IO) {
                    servers.map { server ->
                        async {
                            val host = server.profile.server ?: ""
                            val port = server.profile.serverPort?.toIntOrNull() ?: 443
                            val time = if (host.isNotEmpty()) {
                                SpeedtestManager.socketConnectTime(host, port, 2000)
                            } else -1L
                            Pair(server, time)
                        }
                    }.awaitAll()
                }

                isSmartConnecting = false
                val reachable = results.filter { it.second > 0L }.sortedBy { it.second }
                if (reachable.isNotEmpty()) {
                    val fastest = reachable.first().first
                    onAction(MainAction.SelectServer(fastest.guid))
                    if (!isConnected) {
                        onAction(MainAction.ToggleService)
                    } else {
                        onAction(MainAction.RestartService)
                    }
                } else {
                    context.toast(context.getString(R.string.no_server_available))
                }
            }
        }
    }

    // Force RTL layout as requested
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(if (isDarkTheme) Color(0xFF060607) else Color(0xFFF9F9FA))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header (Logo + MR VPN wordmark leading; Bell + Settings chips trailing)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Leading: Brand (Logo ~28dp + MR VPN Text)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF060607),
                        border = BorderStroke(1.dp, Color(0xFF232329))
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color(0xFFF5F2EA) else Color(0xFF101013),
                        letterSpacing = 0.5.sp
                    )
                }

                // Trailing: Bell & Settings icon chips
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Bell chip with small gold dot badge (opens Telegram channel)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF101013) else Color(0xFFECECEF))
                            .border(1.dp, Color(0xFF232329), CircleShape)
                            .clickable {
                                Utils.openUri(context, "https://t.me/MR_vpnStore")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bell_24dp),
                            contentDescription = "Notifications",
                            modifier = Modifier.size(19.dp),
                            tint = if (isDarkTheme) Color(0xFFC9C9CE) else Color(0xFF333333)
                        )
                        // Gold dot badge at top-right
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 7.dp, end = 8.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD8B45A))
                        )
                    }

                    // Settings chip
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF101013) else Color(0xFFECECEF))
                            .border(1.dp, Color(0xFF232329), CircleShape)
                            .clickable { onOpenSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sliders_24dp),
                            contentDescription = "Settings",
                            modifier = Modifier.size(19.dp),
                            tint = if (isDarkTheme) Color(0xFFC9C9CE) else Color(0xFF333333)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // 2. Connect Ring Button (216dp, 3dp gold border, inner hairline ring inset 10dp, shield-check icon)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onAction(MainAction.ToggleService) },
                contentAlignment = Alignment.Center
            ) {
                if (isConnected) {
                    // Soft static halo
                    Box(
                        modifier = Modifier
                            .size(256.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x21D8B45A), // rgba(216,180,90,.13)
                                        Color(0x00D8B45A)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }

                // Outer Ring 216dp
                Box(
                    modifier = Modifier
                        .size(216.dp)
                        .clip(CircleShape)
                        .background(if (isDarkTheme) Color(0xFF0B0B0D) else Color(0xFFF0F0F3))
                        .border(
                            width = if (isConnected) 3.dp else 1.5.dp,
                            color = if (isConnected) Color(0xFFD8B45A) else Color(0xFF232329),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner hairline ring inset 10dp
                    Box(
                        modifier = Modifier
                            .size(196.dp)
                            .border(
                                width = 1.dp,
                                color = Color(0xFF232329),
                                shape = CircleShape
                            )
                    )

                    // Shield Check icon
                    Icon(
                        painter = painterResource(R.drawable.ic_shield_check_96dp),
                        contentDescription = "Connect",
                        modifier = Modifier.size(92.dp),
                        tint = if (isConnected) Color(0xFFD8B45A) else Color(0xFF6E6E76)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Status Block (Green dot + «متصل هستید» or Gray dot + «متصل نیستید»)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Color(0xFF30D158) else Color(0xFF6E6E76))
                )
                Text(
                    text = stringResource(if (isConnected) R.string.status_connected else R.string.status_disconnected),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isConnected) Color(0xFF30D158) else Color(0xFF9A97A3)
                )
            }

            // Duration Timer in Persian Digits
            Text(
                text = timerString,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDarkTheme) Color(0xFFF5F2EA) else Color(0xFF101013),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Server line: {server name} • پینگ {n}ms
            val delayVal = selectedServer?.testDelayMillis ?: 0L
            val serverName = selectedServer?.profile?.remarks ?: stringResource(R.string.status_disconnected)
            val subText = if (delayVal > 0L) {
                "$serverName • ${stringResource(R.string.ping_label)} ${toPersianDigits(delayVal.toString())}ms"
            } else {
                serverName
            }
            Text(
                text = subText,
                fontSize = 14.sp,
                color = Color(0xFF9A97A3),
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Server Card (Radius 20dp, #101013, border 1px #232329)
            if (selectedServer != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenServers() },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDarkTheme) Color(0xFF101013) else Color(0xFFFFFFFF),
                    border = BorderStroke(1.dp, Color(0xFF232329))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Flag avatar circle
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isDarkTheme) Color(0xFF18181D) else Color(0xFFEEEEEE))
                                .border(1.dp, Color(0x29FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = FlagHelper.getFlag(selectedServer.profile.remarks),
                                fontSize = 24.sp
                            )
                        }

                        // Server name
                        Text(
                            text = selectedServer.profile.remarks,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = if (isDarkTheme) Color(0xFFF5F2EA) else Color(0xFF101013),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Signal bars
                        SignalBars(delayMillis = delayVal)

                        // Chevron
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_left_24dp),
                            contentDescription = null,
                            tint = Color(0xFF6E6E76),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                // If nothing is imported yet: Show «افزودن اشتراک» with Paste & Scan QR
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDarkTheme) Color(0xFF101013) else Color(0xFFFFFFFF),
                    border = BorderStroke(1.dp, Color(0xFF232329))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.add_subscription),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD8B45A)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onAction(MainAction.ImportClipboard) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFD8B45A))
                            ) {
                                Text(
                                    text = stringResource(R.string.paste_link),
                                    fontSize = 12.sp,
                                    color = Color(0xFFD8B45A)
                                )
                            }
                            OutlinedButton(
                                onClick = { onAction(MainAction.ImportQRcode) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFD8B45A))
                            ) {
                                Text(
                                    text = stringResource(R.string.scan_qr),
                                    fontSize = 12.sp,
                                    color = Color(0xFFD8B45A)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Smart Connect Button (Primary solid #D8B45A, dark text #1C1A14, radius 16dp)
            Button(
                onClick = onSmartConnectClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD8B45A),
                    contentColor = Color(0xFF1C1A14)
                ),
                contentPadding = PaddingValues(13.dp)
            ) {
                if (isSmartConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFF1C1A14),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "در حال انتخاب سریع‌ترین سرور...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1C1A14)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_bolt_24dp),
                        contentDescription = null,
                        tint = Color(0xFF1C1A14),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_smart_connect),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1C1A14)
                    )
                }
            }

            // 6. Stats Row (Remaining Volume & Remaining Time with thin progress bars)
            if (usageInfo != null && (usageInfo.total > 0L || usageInfo.expire > 0L || usageInfo.used > 0L)) {
                Spacer(modifier = Modifier.height(20.dp))

                val remainingBytes = (usageInfo.total - usageInfo.used).coerceAtLeast(0L)
                val volumeFraction = if (usageInfo.total > 0L) {
                    (remainingBytes.toFloat() / usageInfo.total.toFloat()).coerceIn(0f, 1f)
                } else 1f

                val volumeText = if (usageInfo.total <= 0L) {
                    stringResource(R.string.unlimited)
                } else if (remainingBytes >= 1_000_000_000L) {
                    val gb = remainingBytes.toDouble() / 1_000_000_000.0
                    toPersianDigits(String.format(Locale.US, "%.2f", gb)) + " " + stringResource(R.string.unit_gb)
                } else {
                    val mb = remainingBytes / 1_000_000L
                    toPersianDigits(mb.toString()) + " " + stringResource(R.string.unit_mb)
                }

                val nowSec = System.currentTimeMillis() / 1000L
                val daysLeft = if (usageInfo.expire > 0L) {
                    ((usageInfo.expire - nowSec) / 86400L).coerceAtLeast(0L)
                } else -1L

                val timeText = if (daysLeft >= 0L) {
                    "${toPersianDigits(daysLeft.toString())} ${stringResource(R.string.days_suffix)}"
                } else {
                    stringResource(R.string.unlimited)
                }
                val timeFraction = if (daysLeft >= 0L) {
                    (daysLeft.toFloat() / 30f).coerceIn(0f, 1f)
                } else 1f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Volume block
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = volumeText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDarkTheme) Color(0xFFF5F2EA) else Color(0xFF101013)
                        )
                        Text(
                            text = stringResource(R.string.remaining_volume),
                            fontSize = 13.sp,
                            color = Color(0xFF9A97A3),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp, start = 4.dp, end = 4.dp)
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF232329))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = volumeFraction)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD8B45A))
                            )
                        }
                    }

                    // Center hairline divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(56.dp)
                            .background(Color(0xFF1B1B20))
                    )

                    // Time block
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = timeText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDarkTheme) Color(0xFFF5F2EA) else Color(0xFF101013)
                        )
                        Text(
                            text = stringResource(R.string.remaining_time),
                            fontSize = 13.sp,
                            color = Color(0xFF9A97A3),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp, start = 4.dp, end = 4.dp)
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF232329))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = timeFraction)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD8B45A))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SignalBars(delayMillis: Long, modifier: Modifier = Modifier) {
    val barColor = when {
        delayMillis in 1..200 -> Color(0xFF30D158) // Green
        delayMillis in 201..350 -> Color(0xFFD8B45A) // Gold
        delayMillis > 350 -> Color(0xFFBA1A1A) // Red
        else -> Color(0xFFD8B45A)
    }
    val activeBars = when {
        delayMillis in 1..100 -> 4
        delayMillis in 101..200 -> 3
        delayMillis in 201..350 -> 2
        delayMillis > 350 -> 1
        else -> 0
    }
    val heights = listOf(6.dp, 10.dp, 14.dp, 18.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEachIndexed { index, h ->
            val isActive = (index < activeBars) || (delayMillis == 0L && index < 2)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) barColor else barColor.copy(alpha = 0.28f))
            )
        }
    }
}

@Composable
fun MrVpnBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalDarkTheme.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (isDarkTheme) Color(0xFF060607) else Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, Color(0xFF1B1B20))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0x21D8B45A) else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = stringResource(tab.labelRes),
                            modifier = Modifier.size(21.dp),
                            tint = if (isSelected) Color(0xFFD8B45A) else Color(0xFF8B8894)
                        )
                    }
                    Text(
                        text = stringResource(tab.labelRes),
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFFD8B45A) else Color(0xFF8B8894)
                    )
                }
            }
        }
    }
}
