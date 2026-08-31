package com.telecom.widget.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// ── Data models (exactly as in APK) ─────────────────────────────────────────

data class ConsumptionDetail(
    val label: String,
    val value: String,
    val iconType: String           // "internet" | "calls" | "sms" | "solde" | "wallet" | etc.
)

data class ConsumptionData(
    val operator: String,
    val phoneNumber: String,
    val callsRemaining: String,
    val callsPercent: Float? = null,
    val internetRemaining: String,
    val internetPercent: Float? = null,
    val extraDetails: String? = null,
    val structuredDetails: List<ConsumptionDetail>? = null
)

// ── TelecomClient interface (exactly as in APK — no login() method) ──────────

interface TelecomClient {
    val currentCookies: List<String>
    suspend fun fetchConsumption(): ConsumptionData
}

// ── MultiLineException (exactly as in APK) ───────────────────────────────────

class MultiLineException(
    val lines: List<String>,
    val token: String,
    val cookies: List<String>
) : java.io.IOException("Multiple lines detected")

// ── InMemoryCookieJar (exactly as in APK — takes List<Cookie>, not strings) ──

class InMemoryCookieJar(initialCookies: List<Cookie> = emptyList()) : CookieJar {
    private val cookies: MutableList<Cookie> = mutableListOf()

    init {
        cookies.addAll(initialCookies)
    }

    fun getAllCookies(): List<Cookie> = cookies.toList()

    fun clear() {
        cookies.clear()
    }

    override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) {
        val newNames = newCookies.map { it.name }
        cookies.removeAll { it.name in newNames }
        cookies.addAll(newCookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies.filter { it.matches(url) }
    }
}

// ── parseCookies top-level function (exactly as in APK) ──────────────────────

fun parseCookies(urlStr: String, cookieStrings: List<String>): List<Cookie> {
    val url = urlStr.toHttpUrlOrNull() ?: return emptyList()
    return cookieStrings.mapNotNull { Cookie.parse(url, it) }
}
