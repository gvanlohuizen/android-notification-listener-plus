package com.daohoangson.n8n.notificationlistener.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebhookConfigSerializerTest {

    @Test
    fun roundTrip_preservesUrlsRulesAndIgnoredPackages() {
        val config = WebhookConfig(
            urls = listOf(
                WebhookUrl(
                    url = "https://example.com/webhook/a",
                    name = "A",
                    rules = listOf(
                        FilterRule(packageName = FilterRule.ANY_PACKAGE),
                        FilterRule(
                            packageName = "com.slack",
                            titleRegex = ".*urgent.*".toRegex(),
                            textRegex = "\\d+".toRegex()
                        )
                    )
                )
            ),
            ignoredPackages = listOf("com.android.systemui")
        )

        val restored = WebhookConfigSerializer.fromJson(WebhookConfigSerializer.toJson(config))!!

        assertEquals(1, restored.urls.size)
        val url = restored.urls[0]
        assertEquals("https://example.com/webhook/a", url.url)
        assertEquals("A", url.name)
        assertEquals(2, url.rules.size)
        assertEquals("*", url.rules[0].packageName)
        assertNull(url.rules[0].titleRegex)
        assertEquals("com.slack", url.rules[1].packageName)
        assertEquals(".*urgent.*", url.rules[1].titleRegex?.pattern)
        assertEquals("\\d+", url.rules[1].textRegex?.pattern)
        assertEquals(listOf("com.android.systemui"), restored.ignoredPackages)
    }

    @Test
    fun fromJson_returnsNull_forMalformedJson() {
        assertNull(WebhookConfigSerializer.fromJson("{not json"))
    }

    @Test
    fun fromJson_skipsIncompleteEntries() {
        val json = """
            {"urls":[{"name":"no url"},{"url":"https://example.com","rules":[{"titleRegex":"x"},{"packageName":"com.a"}]}]}
        """.trimIndent()

        val config = WebhookConfigSerializer.fromJson(json)!!

        assertEquals(1, config.urls.size)
        assertEquals("https://example.com", config.urls[0].name)
        assertEquals(listOf("com.a"), config.urls[0].rules.map { it.packageName })
        assertEquals(emptyList<String>(), config.ignoredPackages)
    }
}
