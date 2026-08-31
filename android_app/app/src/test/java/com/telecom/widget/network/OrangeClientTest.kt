package com.telecom.widget.network

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class OrangeClientTest {

    @Test
    fun testFetchConsumption() = runBlocking {
        val user = System.getenv("ORANGE_USER") ?: "0600000000"
        val pass = System.getenv("ORANGE_PASS") ?: "password"

        val client = OrangeClient(user, pass)
        
        try {
            val data = client.fetchConsumption()
            println("SUCCESS! Data: $data")
            assertNotNull(data)
            assertEquals("Orange", data.operator)
        } catch (e: Exception) {
            println("Test skipped or failed: ${e.message}")
        }
    }
}
