package com.daohoangson.n8n.notificationlistener.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParseException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookConfigStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<WebhookConfig> = _config.asStateFlow()

    fun save(config: WebhookConfig) {
        prefs.edit().putString(KEY_CONFIG, WebhookConfigSerializer.toJson(config)).apply()
        _config.value = config
    }

    private fun load(): WebhookConfig {
        val json = prefs.getString(KEY_CONFIG, null) ?: return InitialWebhookConfig.config
        return WebhookConfigSerializer.fromJson(json) ?: InitialWebhookConfig.config
    }

    companion object {
        private const val PREFS_NAME = "webhook_config"
        private const val KEY_CONFIG = "config"
    }
}

object InitialWebhookConfig {
    val config = WebhookConfig(
        urls = emptyList(),
        ignoredPackages = DefaultWebhookConfig.config.ignoredPackages
    )
}

object WebhookConfigSerializer {
    private val gson = Gson()

    private data class ConfigJson(
        val urls: List<UrlJson>? = null,
        val ignoredPackages: List<String>? = null
    )

    private data class UrlJson(
        val url: String? = null,
        val name: String? = null,
        val rules: List<RuleJson>? = null
    )

    private data class RuleJson(
        val packageName: String? = null,
        val titleRegex: String? = null,
        val textRegex: String? = null
    )

    fun toJson(config: WebhookConfig): String {
        val json = ConfigJson(
            urls = config.urls.map { url ->
                UrlJson(
                    url = url.url,
                    name = url.name,
                    rules = url.rules.map { rule ->
                        RuleJson(
                            packageName = rule.packageName,
                            titleRegex = rule.titleRegex?.pattern,
                            textRegex = rule.textRegex?.pattern
                        )
                    }
                )
            },
            ignoredPackages = config.ignoredPackages
        )
        return gson.toJson(json)
    }

    fun fromJson(json: String): WebhookConfig? {
        val parsed = try {
            gson.fromJson(json, ConfigJson::class.java)
        } catch (e: JsonParseException) {
            null
        } ?: return null

        return WebhookConfig(
            urls = parsed.urls.orEmpty().mapNotNull { url ->
                val address = url.url ?: return@mapNotNull null
                WebhookUrl(
                    url = address,
                    name = url.name ?: address,
                    rules = url.rules.orEmpty().mapNotNull { rule ->
                        val packageName = rule.packageName ?: return@mapNotNull null
                        FilterRule(
                            packageName = packageName,
                            titleRegex = rule.titleRegex?.toRegexOrNull(),
                            textRegex = rule.textRegex?.toRegexOrNull()
                        )
                    }
                )
            },
            ignoredPackages = parsed.ignoredPackages.orEmpty()
        )
    }

    private fun String.toRegexOrNull(): Regex? = try {
        toRegex()
    } catch (e: IllegalArgumentException) {
        null
    }
}
