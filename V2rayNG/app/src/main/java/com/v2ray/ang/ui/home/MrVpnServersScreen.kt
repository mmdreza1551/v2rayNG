package com.v2ray.ang.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MrVpnServersScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onSelectServer: (String) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String, String) -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalDarkTheme.current
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val currentServersFlow = remember(uiState.selectedGroupId) {
        mainViewModel.serversForGroup(uiState.selectedGroupId)
    }
    val rawServers by currentServersFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // F6: Filter out fake loopback info-lines from display
    val servers = remember(rawServers, searchQuery) {
        rawServers.filterNot { isLoopbackServer(it.profile.server) }.filter {
            if (searchQuery.isBlank()) true
            else it.profile.remarks.contains(searchQuery, ignoreCase = true) ||
                    it.profile.server.orEmpty().contains(searchQuery, ignoreCase = true)
        }
    }

    val selectedGroup = remember(uiState.groups, uiState.selectedGroupId) {
        uiState.groups.find { it.id == uiState.selectedGroupId } ?: uiState.groups.firstOrNull()
    }
    val usageInfo: SubscriptionUsageInfo? = selectedGroup?.usageInfo

    val isTesting = uiState.isTesting

    // Spin animation for subscription refresh
    var isRefreshingSub by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "spinTransition")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isRefreshingSub) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(if (isDarkTheme) Color(0xFF060607) else Color(0xFFFFFFFF))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header: Title «سرورها» + Search & Add chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "سرورها",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Search Chip
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF101013) else Color(0xFFF6F6F7))
                            .border(1.dp, Color(0xFF232329), CircleShape)
                            .clickable { showSearch = !showSearch },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24dp),
                            contentDescription = "Search",
                            modifier = Modifier.size(19.dp),
                            tint = if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114)
                        )
                    }

                    // Add Chip
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF101013) else Color(0xFFF6F6F7))
                            .border(1.dp, Color(0xFF232329), CircleShape)
                            .clickable { onOpenImport() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add_24dp),
                            contentDescription = "Add",
                            modifier = Modifier.size(20.dp),
                            tint = if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114)
                        )
                    }
                }
            }

            // Search input field
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("جستجوی سرور…", fontSize = 14.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (isDarkTheme) Color(0xFF101013) else Color(0xFFF6F6F7),
                        unfocusedContainerColor = if (isDarkTheme) Color(0xFF101013) else Color(0xFFF6F6F7),
                        focusedIndicatorColor = Color(0xFFD8B45A),
                        unfocusedIndicatorColor = Color(0xFF232329)
                    )
                )
            }

            // «تست پینگ همه» Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable {
                        mainViewModel.testAllRealPing(onlyTcp = false)
                    },
                shape = RoundedCornerShape(14.dp),
                color = if (isDarkTheme) Color(0xFF101013) else Color(0xFFF6F6F7),
                border = BorderStroke(1.dp, Color(0xFF232329))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bolt_24dp),
                        contentDescription = null,
                        tint = Color(0xFFD8B45A),
                        modifier = Modifier
                            .size(19.dp)
                            .scale(if (isTesting) 1.2f else 1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTesting) "در حال تست…" else "تست پینگ همه",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114)
                    )
                }
            }

            // Subscription Group Chips (Horizontal scrolling)
            if (uiState.groups.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.groups.forEach { group ->
                        val isSelected = group.id == uiState.selectedGroupId
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) Color(0xFFD8B45A) else if (isDarkTheme) Color(0xFF101013) else Color(0xFFF6F6F7))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFFD8B45A) else Color(0xFF232329),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    mainViewModel.subscriptionIdChanged(group.id)
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = group.remarks.ifEmpty { "اشتراک" },
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0B0B0B) else if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Subscription info card
            if (usageInfo != null && (usageInfo.total > 0L || usageInfo.used > 0L || usageInfo.expire > 0L)) {
                val oneGiB = 1024.0 * 1024.0 * 1024.0
                val usedGB = String.format(Locale.US, "%.1f", usageInfo.used.toDouble() / oneGiB)
                val totalGB = String.format(Locale.US, "%.0f", usageInfo.total.toDouble() / oneGiB)
                val progress = if (usageInfo.total > 0L) {
                    (usageInfo.used.toFloat() / usageInfo.total.toFloat()).coerceIn(0.03f, 1f)
                } else 0.03f

                val nowSec = System.currentTimeMillis() / 1000L
                val daysLeft = if (usageInfo.expire > 0L) {
                    ((usageInfo.expire - nowSec) / 86400L).coerceAtLeast(0L)
                } else -1L

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDarkTheme) Color(0xFF101013) else Color(0xFFF6F6F7),
                    border = BorderStroke(1.dp, Color(0xFF232329))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedGroup?.remarks?.ifEmpty { "اشتراک من" } ?: "اشتراک من",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114)
                            )

                            // Refresh button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1FD8B45A))
                                    .clickable {
                                        isRefreshingSub = true
                                        onAction(MainAction.UpdateSubscriptions)
                                        scope.launch {
                                            delay(1500)
                                            isRefreshingSub = false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_refresh_24dp),
                                    contentDescription = "Refresh",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(spinAngle),
                                    tint = Color(0xFFD8B45A)
                                )
                            }
                        }

                        // Progress bar
                        Box(
                            modifier = Modifier
                                .padding(vertical = 10.dp)
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF232329))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD8B45A))
                            )
                        }

                        // Small text row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${toPersianDigits(usedGB)} از ${toPersianDigits(totalGB)} گیگ مصرف شده",
                                fontSize = 12.5.sp,
                                color = Color(0xFF8B8B93)
                            )

                            if (daysLeft >= 0L) {
                                Text(
                                    text = "${toPersianDigits(daysLeft.toString())} روز مانده",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF8B8B93)
                                )
                            }
                        }
                    }
                }
            }

            // Server cards list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(servers, key = { it.guid }) { serverItem ->
                    ServerCardRow(
                        serverItem = serverItem,
                        isSelected = serverItem.guid == uiState.selectedGuid,
                        isDarkTheme = isDarkTheme,
                        onSelect = {
                            onSelectServer(serverItem.guid)
                        },
                        onShare = { onShareServer(serverItem.guid, serverItem.profile) },
                        onEdit = { onEditServer(serverItem.guid, serverItem.profile) },
                        onRemove = { onRemoveServer(serverItem.guid, serverItem.profile.remarks) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerCardRow(
    serverItem: ServersCache,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onSelect: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }

    val flagEmoji = remember(serverItem.guid, serverItem.profile.remarks) {
        CountryFlagHelper.getFlagForServer(serverItem.guid, serverItem.profile.remarks)
    }

    val delayMillis = serverItem.testDelayMillis
    val isDead = delayMillis <= 0L
    val protocolName = serverItem.profile.configType.name.uppercase(Locale.ROOT)

    val cardScale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            cardScale.animateTo(0.985f, tween(100))
        } else {
            cardScale.animateTo(1f, tween(100))
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .scale(cardScale.value)
                .combinedClickable(
                    onClick = { onSelect() },
                    onLongClick = {
                        isPressed = true
                        showMenu = true
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) {
                Color(0x0DD8B45A)
            } else if (isDarkTheme) {
                Color(0xFF101013)
            } else {
                Color(0xFFF6F6F7)
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (isSelected) Color(0xFFD8B45A) else Color(0xFF232329)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circular Flag avatar (40dp)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isDarkTheme) Color(0xFF18181D) else Color(0xFFEEEEEE))
                        .border(1.dp, Color(0x29FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (flagEmoji != null) {
                        Text(
                            text = flagEmoji,
                            fontSize = 20.sp
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_globe_gold),
                            contentDescription = null,
                            tint = Color(0xFFD8B45A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Info column: Name + Protocol chip
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = serverItem.profile.remarks,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDead) Color(0xFF8B8B93) else if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0x4DD8B45A), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = protocolName,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFD8B45A),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Trailing: Delay text + 4 gold signal bars
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (delayMillis > 0L) "${toPersianDigits(delayMillis.toString())}ms" else "—",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B8B93)
                    )

                    SignalBars(delayMillis = delayMillis)
                }
            }
        }

        // Long-press Context Menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = {
                showMenu = false
                isPressed = false
            },
            modifier = Modifier
                .background(if (isDarkTheme) Color(0xFF16161A) else Color(0xFFFFFFFF))
                .border(1.dp, Color(0xFF232329), RoundedCornerShape(14.dp))
        ) {
            DropdownMenuItem(
                text = { Text("اشتراک‌گذاری", fontSize = 14.sp) },
                onClick = {
                    showMenu = false
                    isPressed = false
                    onShare()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_share_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF8B8B93)
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("ویرایش", fontSize = 14.sp) },
                onClick = {
                    showMenu = false
                    isPressed = false
                    onEdit()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF8B8B93)
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("حذف", fontSize = 14.sp, color = Color(0xFFBA1A1A)) },
                onClick = {
                    showMenu = false
                    isPressed = false
                    onRemove()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFFBA1A1A)
                    )
                }
            )
        }
    }
}
