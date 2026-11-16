package com.v2ray.ang.ui.home

import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object CountryFlagHelper {

    private val FLAG_EMOJI_REGEX = Regex("[\\uD83C][\\uDDE6-\\uDDFF][\\uD83C][\\uDDE6-\\uDDFF]")

    fun countryCodeToEmoji(code: String?): String? {
        if (code == null || code.length != 2) return null
        val upper = code.uppercase(Locale.ROOT)
        if (!upper.all { it in 'A'..'Z' }) return null
        val first = Character.toChars(0x1F1E6 + (upper[0] - 'A'))
        val second = Character.toChars(0x1F1E6 + (upper[1] - 'A'))
        return String(first) + String(second)
    }

    fun getFlagForServer(guid: String?, remarks: String?): String? {
        // Priority 1: Measured exit country from cache
        if (!guid.isNullOrEmpty()) {
            val cachedLoc = MmkvManager.decodeSettingsString("exit_loc_$guid")
            if (!cachedLoc.isNullOrBlank()) {
                val emoji = countryCodeToEmoji(cachedLoc)
                if (emoji != null) return emoji
            }
        }

        // Priority 2: Flag emoji already present in remarks
        if (!remarks.isNullOrBlank()) {
            val match = FLAG_EMOJI_REGEX.find(remarks)
            if (match != null) return match.value
        }

        // Priority 3: Keyword mapping
        if (!remarks.isNullOrBlank()) {
            val mapped = mapRemarksToEmoji(remarks)
            if (mapped != null) return mapped
        }

        // Priority 4: Null (Fallback to Gold Globe icon in UI)
        return null
    }

    private fun mapRemarksToEmoji(remarks: String): String? {
        val lower = remarks.lowercase(Locale.ROOT)
        return when {
            // Switzerland
            lower.contains("سوئیس") || lower.contains("switzerland") || lower.contains("swiss") || lower.contains("zurich") || lower.contains("geneva") -> "🇨🇭"
            // Sweden
            lower.contains("سوئد") || lower.contains("sweden") || lower.contains("stockholm") -> "🇸🇪"
            // Austria
            lower.contains("اتریش") || lower.contains("austria") || lower.contains("vienna") -> "🇦🇹"
            // Poland
            lower.contains("لهستان") || lower.contains("poland") || lower.contains("warsaw") -> "🇵🇱"
            // Spain
            lower.contains("اسپانیا") || lower.contains("spain") || lower.contains("madrid") || lower.contains("barcelona") -> "🇪🇸"
            // UAE
            lower.contains("امارات") || lower.contains("uae") || lower.contains("dubai") || lower.contains("abu dhabi") -> "🇦🇪"
            // Russia
            lower.contains("روسیه") || lower.contains("russia") || lower.contains("moscow") || lower.contains("petersburg") -> "🇷🇺"
            // Japan
            lower.contains("ژاپن") || lower.contains("japan") || lower.contains("tokyo") || lower.contains("osaka") -> "🇯🇵"
            // Singapore
            lower.contains("سنگاپور") || lower.contains("singapore") -> "🇸🇬"
            // India
            lower.contains("هند") || lower.contains("هندوستان") || lower.contains("india") || lower.contains("mumbai") || lower.contains("delhi") -> "🇮🇳"
            // Brazil
            lower.contains("برزیل") || lower.contains("brazil") || lower.contains("sao paulo") -> "🇧🇷"
            // Australia
            lower.contains("استرالیا") || lower.contains("australia") || lower.contains("sydney") || lower.contains("melbourne") -> "🇦🇺"
            // Norway
            lower.contains("نروژ") || lower.contains("norway") || lower.contains("oslo") -> "🇳🇴"
            // Denmark
            lower.contains("دانمارک") || lower.contains("denmark") || lower.contains("copenhagen") -> "🇩🇰"
            // Belgium
            lower.contains("بلژیک") || lower.contains("belgium") || lower.contains("brussels") -> "🇧🇪"
            // Czechia
            lower.contains("چک") || lower.contains("czech") || lower.contains("czechia") || lower.contains("prague") -> "🇨🇿"
            // Romania
            lower.contains("رومانی") || lower.contains("romania") || lower.contains("bucharest") -> "🇷🇴"
            // Hungary
            lower.contains("مجارستان") || lower.contains("hungary") || lower.contains("budapest") -> "🇭🇺"
            // Greece
            lower.contains("یونان") || lower.contains("greece") || lower.contains("athens") -> "🇬🇷"
            // Portugal
            lower.contains("پرتغال") || lower.contains("portugal") || lower.contains("lisbon") -> "🇵🇹"
            // Ireland
            lower.contains("ایرلند") || lower.contains("ireland") || lower.contains("dublin") -> "🇮🇪"
            // Hong Kong
            lower.contains("هنگ کنگ") || lower.contains("hongkong") || lower.contains("hong kong") -> "🇭🇰"
            // Taiwan
            lower.contains("تایوان") || lower.contains("taiwan") || lower.contains("taipei") -> "🇹🇼"
            // South Korea
            lower.contains("کره") || lower.contains("korea") || lower.contains("seoul") -> "🇰🇷"
            // China
            lower.contains("چین") || lower.contains("china") || lower.contains("beijing") || lower.contains("shanghai") -> "🇨🇳"
            // Argentina
            lower.contains("آرژانتین") || lower.contains("argentina") || lower.contains("buenos aires") -> "🇦🇷"
            // South Africa
            lower.contains("آفریقای جنوبی") || lower.contains("south africa") || lower.contains("cape town") -> "🇿🇦"
            // Turkey
            lower.contains("ترکیه") || lower.contains("turkey") || lower.contains("istanbul") || lower.contains("turk") -> "🇹🇷"
            // Germany
            lower.contains("آلمان") || lower.contains("المان") || lower.contains("germany") || lower.contains("deutschland") || lower.contains("frankfurt") || lower.contains("berlin") -> "🇩🇪"
            // Bulgaria
            lower.contains("بلغارستان") || lower.contains("bulgaria") || lower.contains("sofia") -> "🇧🇬"
            // Netherlands
            lower.contains("هلند") || lower.contains("netherlands") || lower.contains("amsterdam") || lower.contains("dutch") -> "🇳🇱"
            // Ukraine
            lower.contains("اوکراین") || lower.contains("ukraine") || lower.contains("kyiv") -> "🇺🇦"
            // USA
            lower.contains("آمریکا") || lower.contains("امریکا") || lower.contains("usa") || lower.contains("america") || lower.contains("united states") -> "🇺🇸"
            // Finland
            lower.contains("فنلاند") || lower.contains("finland") || lower.contains("helsinki") -> "🇫🇮"
            // Azerbaijan
            lower.contains("آذربایجان") || lower.contains("اذربایجان") || lower.contains("azerbaijan") || lower.contains("baku") -> "🇦🇿"
            // France
            lower.contains("فرانسه") || lower.contains("france") || lower.contains("paris") -> "🇫🇷"
            // Albania
            lower.contains("آلبانی") || lower.contains("البانی") || lower.contains("albania") || lower.contains("tirana") -> "🇦🇱"
            // UK
            lower.contains("انگلیس") || lower.contains("بریتانیا") || lower.contains("uk") || lower.contains("england") || lower.contains("london") || lower.contains("britain") -> "🇬🇧"
            // Canada
            lower.contains("کانادا") || lower.contains("canada") || lower.contains("toronto") || lower.contains("montreal") -> "🇨🇦"
            // Italy
            lower.contains("ایتالیا") || lower.contains("italy") || lower.contains("milan") || lower.contains("rome") -> "🇮🇹"
            // Iran
            lower.contains("ایران") || lower.contains("iran") || lower.contains("tehran") -> "🇮🇷"
            else -> null
        }
    }

    suspend fun resolveAndCacheExitCountry(guid: String) {
        if (guid.isBlank()) return
        withContext(Dispatchers.IO) {
            try {
                var loc: String? = null
                // 1. Try cloudflare trace
                try {
                    val url = URL("https://www.cloudflare.com/cdn-cgi/trace")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 4000
                        instanceFollowRedirects = true
                    }
                    if (conn.responseCode in 200..299) {
                        BufferedReader(InputStreamReader(conn.inputStream)).useLines { lines ->
                            for (line in lines) {
                                if (line.startsWith("loc=")) {
                                    val code = line.substring(4).trim()
                                    if (code.length == 2) {
                                        loc = code
                                        break
                                    }
                                }
                            }
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {}

                // 2. Fallback to ip-api
                if (loc.isNullOrBlank()) {
                    try {
                        val url = URL("https://ip-api.com/json/?fields=status,countryCode")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 4000
                            readTimeout = 4000
                        }
                        if (conn.responseCode in 200..299) {
                            val resp = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(resp)
                            if (json.optString("status") == "success") {
                                val code = json.optString("countryCode")
                                if (code.length == 2) loc = code
                            }
                        }
                        conn.disconnect()
                    } catch (_: Exception) {}
                }

                if (!loc.isNullOrBlank()) {
                    MmkvManager.encodeSettings("exit_loc_$guid", loc)
                }
            } catch (_: Exception) {}
        }
    }
}
