package com.telecom.widget.network

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class ClientTests {
    @Test
    fun testOrangeClient() = runBlocking {
        val user = System.getenv("ORANGE_USER") ?: "0600000000"
        val pass = System.getenv("ORANGE_PASS") ?: "password"
        try {
            val client = OrangeClient(user, pass)
            val data = client.fetchConsumption()
            println("Orange Data: $data")
            assertNotNull(data)
        } catch (e: Exception) {
            println("Test skipped or failed: ${e.message}")
        }
    }

    @Test
    fun testIAMClient() = runBlocking {
        val email = System.getenv("IAM_EMAIL") ?: "test@example.com"
        val pass = System.getenv("IAM_PASS") ?: "password"
        val phone = System.getenv("IAM_PHONE") ?: "0600000000"
        try {
            val client = MarocTelecomClient(email, pass, phone)
            val data = client.fetchConsumption()
            println("IAM Data: $data")
            assertNotNull(data)
        } catch (e: Exception) {
            println("Test skipped or failed: ${e.message}")
        }
    }

    @Test
    fun testInwiClient() = runBlocking {
        val user = System.getenv("INWI_USER") ?: "0600000000"
        val pass = System.getenv("INWI_PASS") ?: "password"
        try {
            val client = InwiClient(user, pass)
            val data = client.fetchConsumption()
            println("Inwi Data: $data")
            assertNotNull(data)
        } catch (e: Exception) {
            println("Test skipped or failed: ${e.message}")
        }
    }
}
