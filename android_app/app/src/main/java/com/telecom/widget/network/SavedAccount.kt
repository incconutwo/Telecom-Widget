package com.telecom.widget.network

import java.util.UUID

data class SavedAccount(
    val id: String = UUID.randomUUID().toString(),
    val operator: String, // "Maroc Telecom", "Orange", "Inwi"
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val selectedLine: String? = null,
    val cookies: List<String> = emptyList(),
    val cachedData: ConsumptionData? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val liveNotificationEnabled: Boolean = false
)
