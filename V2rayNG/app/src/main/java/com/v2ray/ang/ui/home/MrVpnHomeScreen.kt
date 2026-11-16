package com.v2ray.ang.ui.home

import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.service.RealPingWorkerService
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainDestination
import com.v2ray.ang.ui.main.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

// --- Persian Digits Formatter Helper ---
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

fun isLoopbackServer(server: String?): Boolean {
    if (server.isNullOrBlank()) return false
    val trimmed = server.trim().lowercase()
    if (trimmed == "localhost" || trimmed == "::1") return true
    if (trimmed.startsWith("127.")) return true
    return false
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
    val isTesting = uiState.isTesting
    val scope = rememberCoroutineScope()

    // Respect system animation duration scale
    val areAnimationsEnabled = remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        } catch (_: Exception) {
            true
        }
    }

    // Active servers in current group, filtering out fake info-lines (F6)
    val currentServersFlow = remember(uiState.selectedGroupId) {
        mainViewModel.serversForGroup(uiState.selectedGroupId)
    }
    val rawServers by currentServersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val servers = remember(rawServers) {
        rawServers.filterNot { isLoopbackServer(it.profile.server) }
    }

    // Currently selected server
    val selectedServer = remember(servers, uiState.selectedGuid) {
        servers.find { it.guid == uiState.selectedGuid } ?: servers.firstOrNull()
    }

    // F4: Stats must read the subscription that OWNS the currently selected config!
    val activeSubId = selectedServer?.profile?.subscriptionId
    val activeGroup = remember(uiState.groups, activeSubId, uiState.selectedGroupId) {
        if (!activeSubId.isNullOrEmpty()) {
            uiState.groups.find { it.id == activeSubId }
        } else {
            uiState.groups.find { it.id == uiState.selectedGroupId } ?: uiState.groups.firstOrNull()
        }
    }
    val usageInfo: SubscriptionUsageInfo? = activeGroup?.usageInfo

    // F10: Resolve exit country when connected
    LaunchedEffect(isConnected, selectedServer?.guid) {
        if (isConnected && selectedServer != null && selectedServer.guid.isNotBlank()) {
            CountryFlagHelper.resolveAndCacheExitCountry(selectedServer.guid)
        }
    }

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

    // Connecting state tracking
    var isConnectingLocal by remember { mutableStateOf(false) }
    LaunchedEffect(isConnected) {
        if (isConnected) isConnectingLocal = false
    }

    // Smart connect state
    var isSmartConnecting by remember { mutableStateOf(false) }

    val onSmartConnectClick: () -> Unit = {
        val validConfigs = servers.filterNot { isLoopbackServer(it.profile.server) }
        if (validConfigs.isEmpty()) {
            context.toast(context.getString(R.string.no_server_available))
        } else {
            isSmartConnecting = true
            scope.launch {
                val results = mutableMapOf<String, Long>()
                val worker = RealPingWorkerService(
                    context = context,
                    guids = validConfigs.map { it.guid },
                    onlyTcp = false
                ) { event ->
                    when (event) {
                        is RealPingEvent.Result -> {
                            if (event.delayMillis > 0L) {
                                synchronized(results) {
                                    results[event.guid] = event.delayMillis
                                }
                            }
                        }
                        is RealPingEvent.Finish -> {
                            isSmartConnecting = false
                            val fastest = synchronized(results) {
                                results.minByOrNull { it.value }
                            }
                            if (fastest != null) {
                                MmkvManager.encodeServerTestDelayMillis(
                                    fastest.key,
                                    fastest.value
                                )
                                onAction(MainAction.SelectServer(fastest.key))
                                if (!isConnected) {
                                    isConnectingLocal = true
                                    onAction(MainAction.ToggleService)
                                } else {
                                    onAction(MainAction.RestartService)
                                }
                            } else {
                                context.toast(context.getString(R.string.no_server_available))
                            }
                        }
                        else -> {}
                    }
                }
                worker.start()
            }
        }
    }

    // Force RTL layout
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(if (isDarkTheme) Color(0xFF060607) else Color(0xFFF9F9FA))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header (F7: Bell removed; Only MR Logo + MR VPN wordmark on leading, ONE Settings chip on trailing)
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
                        text = "MR VPN",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color(0xFFF5F2EA) else Color(0xFF101013),
                        letterSpacing = 0.5.sp
                    )
                }

                // Trailing: ONE Settings icon chip (F7)
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

            Spacer(modifier = Modifier.height(28.dp))

            // 2. Connect Ring Button with F8 Animations
            val isConnecting = isConnectingLocal && !isConnected
            val infiniteTransition = rememberInfiniteTransition(label = "connectAnim")

            // Rotating arc for connecting state (F8: 1s per rev, ~23% circumference)
            val arcRotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = if (isConnecting && areAnimationsEnabled) 360f else 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "arcRotation"
            )

            // Blinking dot for connecting state
            val connectingDotAlpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isConnecting && areAnimationsEnabled) 0.2f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotAlpha"
            )

            // Ripple 1 for connected state (F8: scale 1 -> 1.55, fade out, 2.8s cycle)
            val ripple1Progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = if (isConnected && areAnimationsEnabled) 1f else 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ripple1"
            )

            // Ripple 2 (delayed 1.4s)
            val ripple2Progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = if (isConnected && areAnimationsEnabled) 1f else 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2800, delayMillis = 1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ripple2"
            )

            // Shield pop-in scale (0.9 -> 1.18 -> 1)
            val shieldPopScale = remember { Animatable(if (isConnected) 1f else 0.9f) }
            val shieldHeartbeat by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isConnected && areAnimationsEnabled) 0.94f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "heartbeat"
            )

            LaunchedEffect(isConnected) {
                if (isConnected && areAnimationsEnabled) {
                    shieldPopScale.snapTo(0.9f)
                    shieldPopScale.animateTo(1.18f, tween(350, easing = FastOutSlowInEasing))
                    shieldPopScale.animateTo(1f, tween(350, easing = LinearOutSlowInEasing))
                } else if (!isConnected && areAnimationsEnabled) {
                    shieldPopScale.animateTo(0.9f, tween(300))
                }
            }

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isConnected) {
                            isConnectingLocal = true
                        }
                        onAction(MainAction.ToggleService)
                    },
                contentAlignment = Alignment.Center
            ) {
                // Ripples while connected
                if (isConnected && areAnimationsEnabled) {
                    val r1Scale = 1f + (0.55f * ripple1Progress)
                    val r1Alpha = (1f - ripple1Progress) * 0.35f
                    Box(
                        modifier = Modifier
                            .size(216.dp)
                            .scale(r1Scale)
                            .border(1.5.dp, Color(0xFFD8B45A).copy(alpha = r1Alpha), CircleShape)
                    )

                    val r2Scale = 1f + (0.55f * ripple2Progress)
                    val r2Alpha = (1f - ripple2Progress) * 0.35f
                    Box(
                        modifier = Modifier
                            .size(216.dp)
                            .scale(r2Scale)
                            .border(1.5.dp, Color(0xFFD8B45A).copy(alpha = r2Alpha), CircleShape)
                    )
                }

                // Connect Ring Canvas (216dp)
                Canvas(
                    modifier = Modifier
                        .size(216.dp)
                        .clip(CircleShape)
                        .background(if (isDarkTheme) Color(0xFF0B0B0D) else Color(0xFFF0F0F3))
                ) {
                    val strokeWidth = if (isConnected) 3.dp.toPx() else 1.5.dp.toPx()
                    val ringColor = when {
                        isConnected -> Color(0xFFD8B45A)
                        isConnecting -> Color(0xFF2E2E33)
                        else -> Color(0xFF2E2E33)
                    }

                    // Background ring
                    drawCircle(
                        color = ringColor,
                        style = Stroke(width = strokeWidth)
                    )

                    // Connecting rotating gold arc segment (~23% = 82.8 degrees)
                    if (isConnecting) {
                        drawArc(
                            color = Color(0xFFD8B45A),
                            startAngle = arcRotation,
                            sweepAngle = 82.8f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Inner hairline ring inset 10dp
                    drawCircle(
                        color = Color(0xFF232329),
                        radius = size.minDimension / 2f - 10.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Shield icon with pop-in and heartbeat
                val totalShieldScale = if (isConnected && areAnimationsEnabled) {
                    shieldPopScale.value * shieldHeartbeat
                } else if (isConnecting) {
                    0.95f
                } else {
                    1f
                }

                Icon(
                    painter = painterResource(R.drawable.ic_shield_check_96dp),
                    contentDescription = "Connect",
                    modifier = Modifier
                        .size(92.dp)
                        .scale(totalShieldScale),
                    tint = if (isConnected) Color(0xFFD8B45A) else Color(0xFF6E6E76)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Status Block
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                val dotColor = when {
                    isConnected -> Color(0xFF30D158)
                    isConnecting -> Color(0xFFD8B45A)
                    else -> Color(0xFF6E6E76)
                }
                val dotAlpha = if (isConnecting) connectingDotAlpha else 1f

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = dotAlpha))
                )
                val statusText = when {
                    isConnected -> "متصل هستید"
                    isConnecting -> "در حال اتصال…"
                    else -> "متصل نیستید"
                }
                val statusTextColor = when {
                    isConnected -> Color(0xFF30D158)
                    isConnecting -> Color(0xFFD8B45A)
                    else -> Color(0xFF9A97A3)
                }
                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusTextColor
                )
            }

            // Duration Timer in Persian Digits (F2: No letterSpacing; F8: Animated flip/fade per second)
            AnimatedContent(
                targetState = timerString,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(450)) { height -> height } + fadeIn(animationSpec = tween(450)))
                        .togetherWith(slideOutVertically(animationSpec = tween(450)) { height -> -height } + fadeOut(animationSpec = tween(450)))
                },
                label = "timerAnimation"
            ) { targetTimer ->
                Text(
                    text = targetTimer,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDarkTheme) Color(0xFFF5F2EA) else Color(0xFF101013),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Server line: {server name} • پینگ {n}ms
            val delayVal = selectedServer?.testDelayMillis ?: 0L
            val serverName = selectedServer?.profile?.remarks ?: "متصل نیستید"
            val subText = if (delayVal > 0L) {
                "$serverName • پینگ ${toPersianDigits(delayVal.toString())}ms"
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
                val flagEmoji = CountryFlagHelper.getFlagForServer(
                    selectedServer.guid,
                    selectedServer.profile.remarks
                )

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
                        // Flag avatar circle: emoji or neutral gold globe icon (F3)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isDarkTheme) Color(0xFF18181D) else Color(0xFFEEEEEE))
                                .border(1.dp, Color(0x29FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (flagEmoji != null) {
                                Text(
                                    text = flagEmoji,
                                    fontSize = 22.sp
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_globe_gold),
                                    contentDescription = null,
                                    tint = Color(0xFFD8B45A),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Server name
                        Text(
                            text = selectedServer.profile.remarks,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp
                            ),
                            color = if (isDarkTheme) Color(0xFFF5F2EA) else Color(0xFF101013),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // F9: Signal bars in champagne gold
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
                            text = "افزودن اشتراک",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD8B45A)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { onAction(MainAction.ImportClipboard) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFD8B45A))
                            ) {
                                Text("جای‌گذاری لینک", color = Color(0xFFD8B45A), fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { onAction(MainAction.ImportQRcode) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFD8B45A))
                            ) {
                                Text("اسکن بارکد", color = Color(0xFFD8B45A), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Smart Connect Button (Real ping test across configs)
            Button(
                onClick = onSmartConnectClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD8B45A),
                    contentColor = Color(0xFF1C1A14)
                ),
                contentPadding = PaddingValues(horizontal = 20.dp),
                enabled = !isSmartConnecting
            ) {
                if (isSmartConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF1C1A14),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "در حال یافتن بهترین سرور…",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
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
                        text = "اتصال هوشمند",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1C1A14)
                    )
                }
            }

            // 6. Stats Row (F4: Correct active subscription data with binary GiB units)
            if (usageInfo != null && (usageInfo.total > 0L || usageInfo.expire > 0L || usageInfo.used > 0L)) {
                Spacer(modifier = Modifier.height(20.dp))

                val remainingBytes = (usageInfo.total - usageInfo.used).coerceAtLeast(0L)
                val volumeFraction = if (usageInfo.total > 0L) {
                    (remainingBytes.toFloat() / usageInfo.total.toFloat()).coerceIn(0f, 1f)
                } else 1f

                // Binary GiB (1024^3) and MiB (1024^2) calculation
                val oneGiB = 1024.0 * 1024.0 * 1024.0
                val oneMiB = 1024.0 * 1024.0

                val volumeText = if (usageInfo.total <= 0L) {
                    "نامحدود"
                } else if (remainingBytes >= 1_000_000_000L) {
                    val gb = remainingBytes.toDouble() / oneGiB
                    toPersianDigits(String.format(Locale.US, "%.1f", gb)) + " گیگابایت"
                } else {
                    val mb = (remainingBytes.toDouble() / oneMiB).toLong()
                    toPersianDigits(mb.toString()) + " مگابایت"
                }

                val nowSec = System.currentTimeMillis() / 1000L
                val daysLeft = if (usageInfo.expire > 0L) {
                    ((usageInfo.expire - nowSec) / 86400L).coerceAtLeast(0L)
                } else -1L

                val timeText = if (daysLeft >= 0L) {
                    "${toPersianDigits(daysLeft.toString())} روز"
                } else {
                    "نامحدود"
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
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDarkTheme) Color(0xFFF5F2EA) else Color(0xFF101013)
                        )
                        Text(
                            text = "حجم باقیمانده",
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
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDarkTheme) Color(0xFFF5F2EA) else Color(0xFF101013)
                        )
                        Text(
                            text = "زمان باقیمانده",
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

// F9: Champagne gold signal bars everywhere
@Composable
fun SignalBars(delayMillis: Long, modifier: Modifier = Modifier) {
    val activeBars = when {
        delayMillis in 1..100 -> 4
        delayMillis in 101..200 -> 3
        delayMillis in 201..350 -> 2
        delayMillis > 350 -> 1
        else -> 0
    }
    val heights = listOf(7.dp, 11.dp, 15.dp, 19.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEachIndexed { index, h ->
            val isActive = (index < activeBars)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isActive) Color(0xFFD8B45A)
                        else Color(0xFFD8B45A).copy(alpha = 0.22f)
                    )
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
                .padding(top = 8.dp, bottom = 12.dp),
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
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) Color(0x21D8B45A) else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(tab.iconRes),
                            contentDescription = stringResource(tab.labelRes),
                            modifier = Modifier.size(20.dp),
                            tint = if (isSelected) Color(0xFFD8B45A) else Color(0xFF8B8894)
                        )
                    }
                    Text(
                        text = stringResource(tab.labelRes),
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) Color(0xFFD8B45A) else Color(0xFF8B8894)
                    )
                }
            }
        }
    }
}
