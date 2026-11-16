package com.v2ray.ang.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.ui.compose.LocalDarkTheme
import com.v2ray.ang.ui.main.MainViewModel
import java.util.Locale

@Composable
fun MrVpnUsageScreen(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalDarkTheme.current
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

    val currentServersFlow = remember(uiState.selectedGroupId) {
        mainViewModel.serversForGroup(uiState.selectedGroupId)
    }
    val rawServers by currentServersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val servers = remember(rawServers) {
        rawServers.filterNot { isLoopbackServer(it.profile.server) }
    }

    val selectedServer = remember(servers, uiState.selectedGuid) {
        servers.find { it.guid == uiState.selectedGuid } ?: servers.firstOrNull()
    }

    val activeSubId = selectedServer?.profile?.subscriptionId
    val activeGroup = remember(uiState.groups, activeSubId, uiState.selectedGroupId) {
        if (!activeSubId.isNullOrEmpty()) {
            uiState.groups.find { it.id == activeSubId }
        } else {
            uiState.groups.find { it.id == uiState.selectedGroupId } ?: uiState.groups.firstOrNull()
        }
    }
    val usageInfo: SubscriptionUsageInfo? = activeGroup?.usageInfo

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(if (isDarkTheme) Color(0xFF060607) else Color(0xFFFFFFFF))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "گزارش مصرف",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 1. Subscription Overview Card
                if (usageInfo != null && (usageInfo.total > 0L || usageInfo.used > 0L || usageInfo.expire > 0L)) {
                    item {
                        val oneGiB = 1024.0 * 1024.0 * 1024.0
                        val usedGB = String.format(Locale.US, "%.2f", usageInfo.used.toDouble() / oneGiB)
                        val totalGB = String.format(Locale.US, "%.0f", usageInfo.total.toDouble() / oneGiB)
                        val remainingBytes = (usageInfo.total - usageInfo.used).coerceAtLeast(0L)
                        val remainingGB = String.format(Locale.US, "%.2f", remainingBytes.toDouble() / oneGiB)
                        val progress = if (usageInfo.total > 0L) {
                            (usageInfo.used.toFloat() / usageInfo.total.toFloat()).coerceIn(0.02f, 1f)
                        } else 0.02f

                        val nowSec = System.currentTimeMillis() / 1000L
                        val daysLeft = if (usageInfo.expire > 0L) {
                            ((usageInfo.expire - nowSec) / 86400L).coerceAtLeast(0L)
                        } else -1L

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = if (isDarkTheme) Color(0xFF101013) else Color(0xFFF6F6F7),
                            border = BorderStroke(1.dp, Color(0xFF232329))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = activeGroup?.remarks?.ifEmpty { "اشتراک فعال" } ?: "اشتراک فعال",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Volume row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "مصرف شده",
                                            fontSize = 12.sp,
                                            color = Color(0xFF8B8B93)
                                        )
                                        Text(
                                            text = "${toPersianDigits(usedGB)} گیگابایت",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "باقیمانده از ${toPersianDigits(totalGB)} گیگ",
                                            fontSize = 12.sp,
                                            color = Color(0xFF8B8B93)
                                        )
                                        Text(
                                            text = "${toPersianDigits(remainingGB)} گیگابایت",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFD8B45A)
                                        )
                                    }
                                }

                                // Progress bar
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .fillMaxWidth()
                                        .height(6.dp)
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

                                // Expiry line
                                if (daysLeft >= 0L) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "زمان باقیمانده تا پایان اعتبار:",
                                            fontSize = 13.sp,
                                            color = Color(0xFF8B8B93)
                                        )
                                        Text(
                                            text = "${toPersianDigits(daysLeft.toString())} روز",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD8B45A)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Server List Section Title
                item {
                    Text(
                        text = "سرورهای این گروه (${toPersianDigits(servers.size.toString())})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD8B45A),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // 3. Server items with protocol & ping
                items(servers, key = { it.guid }) { serverItem ->
                    val flagEmoji = remember(serverItem.guid, serverItem.profile.remarks) {
                        CountryFlagHelper.getFlagForServer(serverItem.guid, serverItem.profile.remarks)
                    }
                    val isSelected = serverItem.guid == uiState.selectedGuid
                    val delayVal = serverItem.testDelayMillis

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) Color(0x0DD8B45A) else if (isDarkTheme) Color(0xFF101013) else Color(0xFFF6F6F7),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFD8B45A) else Color(0xFF232329))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Flag
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isDarkTheme) Color(0xFF18181D) else Color(0xFFEEEEEE))
                                    .border(1.dp, Color(0x29FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (flagEmoji != null) {
                                    Text(text = flagEmoji, fontSize = 19.sp)
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_globe_gold),
                                        contentDescription = null,
                                        tint = Color(0xFFD8B45A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Remarks
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = serverItem.profile.remarks,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDarkTheme) Color(0xFFF3F3F4) else Color(0xFF111114),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = serverItem.profile.configType.name.uppercase(Locale.ROOT),
                                    fontSize = 11.sp,
                                    color = Color(0xFFD8B45A)
                                )
                            }

                            // Ping
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (delayVal > 0L) "${toPersianDigits(delayVal.toString())}ms" else "—",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF8B8B93)
                                )
                                SignalBars(delayMillis = delayVal)
                            }
                        }
                    }
                }
            }
        }
    }
}
