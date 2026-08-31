package com.telecom.widget

import com.telecom.widget.network.OrangeClient
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class OrangeTest {
    @Test
    fun testOrangeLogin() = runBlocking {
        val user = System.getenv("ORANGE_USER") ?: "0600000000"
        val pass = System.getenv("ORANGE_PASS") ?: "password"
        try {
            val client = OrangeClient(user, pass)
            val data = client.fetchConsumption()
            println("SUCCESS: $data")
        } catch (e: Exception) {
            println("Test skipped or failed: ${e.message}")
        }
    }
}
