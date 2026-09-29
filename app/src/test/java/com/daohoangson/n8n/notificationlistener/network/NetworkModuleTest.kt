package com.daohoangson.n8n.notificationlistener.network

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun `webhookApi sends to absolute url`() = runTest {
        val mockWebServer = MockWebServer()
        mockWebServer.start()
        try {
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            val url = mockWebServer.url("/webhook/test").toString()
            val body = "{}".toRequestBody("application/json".toMediaType())
            val response = NetworkModule.webhookApi.sendNotification(url, body)

            assertTrue(response.isSuccessful)
            assertEquals("/webhook/test", mockWebServer.takeRequest().path)
        } finally {
            mockWebServer.shutdown()
        }
    }
}
