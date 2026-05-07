package com.contextos.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic

class MapsDistanceMatrixClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var client: MapsDistanceMatrixClient

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                var request = chain.request()
                if (request.url.host == "maps.googleapis.com") {
                    val newUrl = request.url.newBuilder()
                        .host(mockWebServer.hostName)
                        .port(mockWebServer.port)
                        .scheme("http")
                        .build()
                    request = request.newBuilder().url(newUrl).build()
                }
                chain.proceed(request)
            }
            .build()

        client = MapsDistanceMatrixClient(okHttpClient)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getEstimatedTravelTime parses valid response correctly`() = runTest {
        val jsonResponse = """
            {
               "destination_addresses" : [ "San Francisco, CA, USA" ],
               "origin_addresses" : [ "Mountain View, CA, USA" ],
               "rows" : [
                  {
                     "elements" : [
                        {
                           "distance" : {
                              "text" : "59.9 km",
                              "value" : 59887
                           },
                           "duration" : {
                              "text" : "48 mins",
                              "value" : 2880
                           },
                           "duration_in_traffic" : {
                              "text" : "55 mins",
                              "value" : 3300
                           },
                           "status" : "OK"
                        }
                     ]
                  }
               ],
               "status" : "OK"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val departureTime = System.currentTimeMillis()
        val result = client.getEstimatedTravelTime(
            originLat = 37.3861,
            originLng = -122.0839,
            destinationAddress = "San Francisco, CA",
            departureTimeMs = departureTime
        )

        assertNotNull(result)
        assertEquals(2880L, result?.durationSeconds)
        assertEquals(3300L, result?.durationInTraffic)
        assertEquals(departureTime + 3300L * 1000, result?.estimatedArrivalMs)
    }

    @Test
    fun `getEstimatedTravelTime handles API error gracefully`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = client.getEstimatedTravelTime(
            originLat = 37.3861,
            originLng = -122.0839,
            destinationAddress = "San Francisco, CA"
        )

        assertNull(result)
    }

    @Test
    fun `getEstimatedTravelTime returns null for invalid API status`() = runTest {
        val jsonResponse = """
            {
               "destination_addresses" : [],
               "origin_addresses" : [],
               "rows" : [],
               "status" : "REQUEST_DENIED"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val result = client.getEstimatedTravelTime(
            originLat = 37.3861,
            originLng = -122.0839,
            destinationAddress = "San Francisco, CA"
        )

        assertNull(result)
    }
}
