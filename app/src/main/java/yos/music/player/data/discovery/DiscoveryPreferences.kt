package yos.music.player.data.discovery

import androidx.compose.runtime.mutableStateOf
import com.funny.data_saver.core.mutableDataSaverStateOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import yos.music.player.data.SettingsSaver

object DiscoveryPreferences {
    val sectionTitles = linkedMapOf("mix" to "Your Mix", "daily" to "Made for You", "recent" to "Recently Played", "added" to "Recently Added", "albums" to "Your Albums", "artists" to "Your Artists", "stats" to "Listening Overview")
    var sectionOrder by mutableDataSaverStateOf(dataSaverInterface = SettingsSaver, key = "discovery_home_order", initialValue = sectionTitles.keys.joinToString(","))
    var hiddenSections by mutableDataSaverStateOf(dataSaverInterface = SettingsSaver, key = "discovery_home_hidden", initialValue = "")
    var mixSize by mutableDataSaverStateOf(dataSaverInterface = SettingsSaver, key = "discovery_mix_size", initialValue = 50)
    var mixRevision by mutableDataSaverStateOf(dataSaverInterface = SettingsSaver, key = "discovery_mix_revision", initialValue = 0)
    var searchJson by mutableDataSaverStateOf(dataSaverInterface = SettingsSaver, key = "discovery_search_history", initialValue = "[]")
    var rulesJson by mutableDataSaverStateOf(dataSaverInterface = SettingsSaver, key = "discovery_smart_rules", initialValue = "[]")
    var excludedJson by mutableDataSaverStateOf(dataSaverInterface = SettingsSaver, key = "discovery_excluded", initialValue = "[]")
    var mixCacheJson by mutableDataSaverStateOf(dataSaverInterface = SettingsSaver, key = "discovery_daily_cache", initialValue = "")
    var mixCacheKey by mutableDataSaverStateOf(dataSaverInterface = SettingsSaver, key = "discovery_daily_key", initialValue = "")
    private val gson = Gson()
    private inline fun <reified T> read(json: String, fallback: T): T = runCatching { gson.fromJson<T>(json, object : TypeToken<T>() {}.type) ?: fallback }.getOrDefault(fallback)
    fun sections(): List<String> = (sectionOrder.split(',') + sectionTitles.keys).distinct().filter { it in sectionTitles && it !in hiddenSections.split(',') }
    fun searchHistory(): List<String> = read(searchJson, emptyList<String>())
    fun rememberSearch(query: String) { if (query.trim().isNotBlank()) searchJson = gson.toJson((listOf(query.trim()) + searchHistory()).distinct().take(12)) }
    fun rules(): List<SmartMixRule> = read(rulesJson, emptyList<SmartMixRule>())
    fun saveRule(rule: SmartMixRule) { rulesJson = gson.toJson(rules().filterNot { it.id == rule.id } + rule) }
    fun removeRule(id: String) { rulesJson = gson.toJson(rules().filterNot { it.id == id }) }
    fun excluded(): Set<String> = read(excludedJson, emptyList<String>()).toSet()
    fun exclude(id: String) { excludedJson = gson.toJson((excluded() + id).toList()); mixRevision++ }
    fun cachedMixes(key: String): List<DiscoveryMix>? = if (mixCacheKey == key) read(mixCacheJson, emptyList<DiscoveryMix>()).takeIf { it.isNotEmpty() } else null
    fun cacheMixes(key: String, mixes: List<DiscoveryMix>) { mixCacheJson = gson.toJson(mixes); mixCacheKey = key }
}
