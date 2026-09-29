package com.daohoangson.n8n.notificationlistener.config

import com.daohoangson.n8n.notificationlistener.utils.NotificationData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationFilterEngine(
    private val configProvider: () -> WebhookConfig = { DefaultWebhookConfig.config }
) {

    @Inject
    constructor(configStore: WebhookConfigStore) : this({ configStore.config.value })

    fun isIgnored(notificationData: NotificationData): Boolean {
        return configProvider().ignoredPackages.contains(notificationData.packageName)
    }
    
    fun findMatchingUrls(notificationData: NotificationData): List<WebhookUrl> {
        return configProvider().urls.filter { webhookUrl ->
            webhookUrl.rules.any { rule ->
                matchesRule(notificationData, rule)
            }
        }
    }
    
    private fun matchesRule(notificationData: NotificationData, rule: FilterRule): Boolean {
        if (rule.packageName != FilterRule.ANY_PACKAGE && rule.packageName != notificationData.packageName) {
            return false
        }
        
        if (rule.titleRegex == null && rule.textRegex == null) {
            return true
        }
        
        rule.titleRegex?.let { titleRegex ->
            val title = notificationData.title ?: ""
            if (!titleRegex.matches(title)) {
                return false
            }
        }
        
        rule.textRegex?.let { textRegex ->
            val text = notificationData.text ?: ""
            if (!textRegex.matches(text)) {
                return false
            }
        }
        
        return true
    }
    
}