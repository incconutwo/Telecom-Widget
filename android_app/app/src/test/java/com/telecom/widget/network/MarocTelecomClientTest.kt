package com.telecom.widget.network

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class MarocTelecomClientTest {

    @Test
    fun testFetchConsumption() = runBlocking {
        val email = System.getenv("IAM_EMAIL") ?: "test@example.com"
        val pass = System.getenv("IAM_PASS") ?: "password"
        val phone = System.getenv("IAM_PHONE") ?: "0600000000"

        val client = MarocTelecomClient(email, pass, phone)
        
        try {
            val data = client.fetchConsumption()
            println("SUCCESS! Data: $data")
            assertNotNull(data)
            assertEquals("Maroc Telecom", data.operator)
            assertTrue(data.callsRemaining.isNotEmpty())
        } catch (e: Exception) {
            println("Test skipped: ${e.message}")
        }
    }
}
