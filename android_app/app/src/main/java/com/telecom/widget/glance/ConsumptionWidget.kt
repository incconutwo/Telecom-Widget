package com.telecom.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import com.telecom.widget.R
import androidx.work.*
import com.google.gson.Gson
import com.telecom.widget.dataStore
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider
import com.telecom.widget.network.ConsumptionData
import com.telecom.widget.network.ConsumptionSyncWorker
import kotlinx.coroutines.flow.first

object SamsungUtils {
    val isOneUi: Boolean by lazy {
        runCatching {
            Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null) > 0
        }.getOrDefault(false) || Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }
}

data class WidgetThemeSettings(
    val opacityPercent: Int = 80,
    val colorMode: String = "system",
    val font: String = "one_ui_sans",
    val tapAction: String = "refresh",
    val showExtraDetails: Boolean = true
)

class ConsumptionWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 40.dp),  // 2x1
            DpSize(230.dp, 40.dp),  // 3x1
            DpSize(300.dp, 40.dp),  // 4x1
            DpSize(180.dp, 100.dp), // 2x2
            DpSize(250.dp, 100.dp), // 3x2
            DpSize(300.dp, 120.dp), // 4x2
            DpSize(300.dp, 240.dp), // 4x4 Full Page
            DpSize(300.dp, 360.dp)  // 4x5 / 4x6 Full Page
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.dataStore.data.first()
        val appWidgetManager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
        val appWidgetId = try { appWidgetManager.getAppWidgetId(id) } catch (_: Exception) { -1 }

        val boundAccountKey = if (appWidgetId != -1) stringPreferencesKey("widget_account_$appWidgetId") else null
        val targetAccountId = (if (boundAccountKey != null) prefs[boundAccountKey] else null) ?: prefs[stringPreferencesKey("active_account_id")]

        val accountsJson = prefs[stringPreferencesKey("saved_accounts_json")]
        val listType = object : com.google.gson.reflect.TypeToken<List<com.telecom.widget.network.SavedAccount>>() {}.type
        val accounts: List<com.telecom.widget.network.SavedAccount> = if (!accountsJson.isNullOrEmpty()) {
            try { Gson().fromJson(accountsJson, listType) } catch (_: Exception) { emptyList() }
        } else emptyList()

        val boundAccount = accounts.find { it.id == targetAccountId } ?: accounts.firstOrNull()

        val opacity = (if (appWidgetId != -1) prefs[intPreferencesKey("widget_opacity_$appWidgetId")] else null)
            ?: prefs[intPreferencesKey("widget_opacity")] ?: 80
        val colorMode = (if (appWidgetId != -1) prefs[stringPreferencesKey("widget_color_mode_$appWidgetId")] else null)
            ?: prefs[stringPreferencesKey("widget_color_mode")] ?: "system"
        val font = (if (appWidgetId != -1) prefs[stringPreferencesKey("widget_font_$appWidgetId")] else null)
            ?: prefs[stringPreferencesKey("widget_font")] ?: (if (SamsungUtils.isOneUi) "one_ui_sans" else "google_sans_flex")
        val tapAction = (if (appWidgetId != -1) prefs[stringPreferencesKey("widget_tap_action_$appWidgetId")] else null)
            ?: prefs[stringPreferencesKey("widget_tap_action")] ?: "refresh"
        val showExtraDetails = (if (appWidgetId != -1) prefs[booleanPreferencesKey("widget_show_extra_details_$appWidgetId")] else null)
            ?: prefs[booleanPreferencesKey("widget_show_extra_details")] ?: true

        val themeSettings = WidgetThemeSettings(
            opacityPercent = opacity,
            colorMode = colorMode,
            font = font,
            tapAction = tapAction,
            showExtraDetails = showExtraDetails
        )

        var data: ConsumptionData? = boundAccount?.cachedData
        if (data == null) {
            val cachedJson = prefs[stringPreferencesKey("cached_data")]
            if (!cachedJson.isNullOrEmpty()) {
                try { data = Gson().fromJson(cachedJson, ConsumptionData::class.java) }
                catch (_: Exception) {}
            }
        }

        provideContent {
            WidgetContent(data, themeSettings, context, appWidgetId)
        }
    }

    @Composable
    private fun WidgetContent(data: ConsumptionData?, theme: WidgetThemeSettings, context: Context, appWidgetId: Int) {
        val size = LocalSize.current
        val alphaFloat = (theme.opacityPercent.coerceIn(0, 100)) / 100f
        val btnAlpha = (alphaFloat * 0.9f).coerceAtLeast(0.35f)

        val bgProvider = when (theme.colorMode) {
            "dark" -> ColorProvider(day = Color(0xFF161616).copy(alpha = alphaFloat), night = Color(0xFF161616).copy(alpha = alphaFloat))
            "light" -> ColorProvider(day = Color(0xFFFFFFFF).copy(alpha = alphaFloat), night = Color(0xFFFFFFFF).copy(alpha = alphaFloat))
            else -> ColorProvider(day = Color(0xFFFFFFFF).copy(alpha = alphaFloat), night = Color(0xFF161616).copy(alpha = alphaFloat))
        }

        val primaryProvider = when (theme.colorMode) {
            "dark" -> ColorProvider(day = Color(0xFFD0BCFF), night = Color(0xFFD0BCFF))
            "light" -> ColorProvider(day = Color(0xFF6750A4), night = Color(0xFF6750A4))
            else -> ColorProvider(day = Color(0xFF6750A4), night = Color(0xFFD0BCFF))
        }

        val onSurfaceProvider = when (theme.colorMode) {
            "dark" -> ColorProvider(day = Color(0xFFF4EFF4), night = Color(0xFFF4EFF4))
            "light" -> ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFF1C1B1F))
            else -> ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFF4EFF4))
        }

        val onSurfaceVariantProvider = when (theme.colorMode) {
            "dark" -> ColorProvider(day = Color(0xFFCAC4D0), night = Color(0xFFCAC4D0))
            "light" -> ColorProvider(day = Color(0xFF49454F), night = Color(0xFF49454F))
            else -> ColorProvider(day = Color(0xFF49454F), night = Color(0xFFCAC4D0))
        }

        val btnContainerProvider = when (theme.colorMode) {
            "dark" -> ColorProvider(day = Color(0xFF4F378B).copy(alpha = btnAlpha), night = Color(0xFF4F378B).copy(alpha = btnAlpha))
            "light" -> ColorProvider(day = Color(0xFFEADDFF).copy(alpha = btnAlpha), night = Color(0xFFEADDFF).copy(alpha = btnAlpha))
            else -> ColorProvider(day = Color(0xFFEADDFF).copy(alpha = btnAlpha), night = Color(0xFF4F378B).copy(alpha = btnAlpha))
        }

        val onBtnContainerProvider = when (theme.colorMode) {
            "dark" -> ColorProvider(day = Color(0xFFEADDFF), night = Color(0xFFEADDFF))
            "light" -> ColorProvider(day = Color(0xFF21005D), night = Color(0xFF21005D))
            else -> ColorProvider(day = Color(0xFF21005D), night = Color(0xFFEADDFF))
        }

        val isStrip = size.height < 90.dp
        val isExact2x1 = isStrip && size.width < 220.dp
        val cornerRad = if (isStrip) 32.dp else 24.dp

        val globalTapAction = if (theme.tapAction == "app") {
            androidx.glance.appwidget.action.actionStartActivity(
                android.content.Intent(context, com.telecom.widget.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        } else {
            actionRunCallback<RefreshAction>()
        }

        val settingsIntent = android.content.Intent(context, com.telecom.widget.WidgetConfigActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (appWidgetId != -1) {
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        }
        val settingsAction = androidx.glance.appwidget.action.actionStartActivity(settingsIntent)

        val rootModifier = GlanceModifier
            .fillMaxSize()
            .background(bgProvider)
            .cornerRadius(cornerRad)
            .clickable(globalTapAction)
            .padding(if (isStrip) 12.dp else 16.dp)

        Column(
            modifier = rootModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (data == null) {
                Text(
                    text = "Log in to see balance",
                    style = TextStyle(color = onSurfaceProvider, fontSize = 14.sp)
                )
            } else if (isStrip) {
                // Dynamic clean formatting: compact units and short names only on 2x1 to prevent truncation
                val callsText = if (isExact2x1) formatCompactCalls(data.callsRemaining) else data.callsRemaining
                val opText = if (isExact2x1) formatShortOperator(data.operator) else data.operator
                val phoneText = formatNationalPhone(data.phoneNumber)
                val internetDisplay = if (data.internetPercent != null) "${data.internetRemaining} (${data.internetPercent.toInt()}%)" else data.internetRemaining
                val callsDisplay = if (data.callsPercent != null) "$callsText (${data.callsPercent.toInt()}%)" else callsText

                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$internetDisplay  $callsDisplay",
                        maxLines = 1,
                        style = TextStyle(
                            color = onSurfaceProvider,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isExact2x1) 14.sp else 16.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(3.dp))
                    Text(
                        text = "$opText • $phoneText",
                        maxLines = 1,
                        style = TextStyle(
                            color = onSurfaceVariantProvider,
                            fontSize = 12.sp
                        )
                    )
                }
            } else if (size.width < 220.dp) {
                // 2x2 Compact Square Card
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxWidth()
                        .clickable(globalTapAction)
                ) {
                    CompactLayout(data, primaryProvider, onSurfaceProvider, onSurfaceVariantProvider, context)
                }

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Settings Button
                    Row(
                        modifier = GlanceModifier
                            .clickable(onClick = settingsAction)
                            .background(btnContainerProvider)
                            .cornerRadius(10.dp)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_settings),
                            contentDescription = "Settings",
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(onBtnContainerProvider)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Refresh Button
                    Row(
                        modifier = GlanceModifier
                            .clickable(onClick = actionRunCallback<RefreshAction>())
                            .background(btnContainerProvider)
                            .cornerRadius(10.dp)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_refresh),
                            contentDescription = "Refresh",
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(onBtnContainerProvider)
                        )
                    }
                }
            } else if (size.height >= 220.dp) {
                // 4x4 / 4x5 / 4x6 Full Page Dashboard Widget
                FullPageLayout(
                    data = data,
                    primary = primaryProvider,
                    onSurface = onSurfaceProvider,
                    onSurfaceVariant = onSurfaceVariantProvider,
                    btnContainerColor = btnContainerProvider,
                    onBtnContainerColor = onBtnContainerProvider,
                    context = context,
                    tapAction = theme.tapAction,
                    settingsAction = settingsAction,
                    showExtraDetails = theme.showExtraDetails
                )
            } else {
                // 4x2 Dashboard Card
                FullLayout(
                    data = data,
                    primary = primaryProvider,
                    onSurface = onSurfaceProvider,
                    onSurfaceVariant = onSurfaceVariantProvider,
                    btnContainerColor = btnContainerProvider,
                    onBtnContainerColor = onBtnContainerProvider,
                    context = context,
                    tapAction = theme.tapAction,
                    settingsAction = settingsAction,
                    showExtraDetails = theme.showExtraDetails
                )
            }
        }
    }

    @Composable
    private fun FullPageLayout(
        data: ConsumptionData,
        primary: ColorProvider,
        onSurface: ColorProvider,
        onSurfaceVariant: ColorProvider,
        btnContainerColor: ColorProvider,
        onBtnContainerColor: ColorProvider,
        context: Context,
        tapAction: String,
        settingsAction: androidx.glance.action.Action,
        showExtraDetails: Boolean
    ) {
        val mainTapAction = if (tapAction == "app") {
            androidx.glance.appwidget.action.actionStartActivity(
                android.content.Intent(context, com.telecom.widget.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        } else {
            actionRunCallback<RefreshAction>()
        }

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header: Operator + Phone + Actions
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(mainTapAction)
                ) {
                    Text(
                        text = data.operator,
                        style = TextStyle(color = primary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    )
                    Text(
                        text = formatNationalPhone(data.phoneNumber),
                        style = TextStyle(color = onSurfaceVariant, fontSize = 14.sp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Settings Button
                    Row(
                        modifier = GlanceModifier
                            .clickable(onClick = settingsAction)
                            .background(btnContainerColor)
                            .cornerRadius(12.dp)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_settings),
                            contentDescription = "Settings",
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(onBtnContainerColor)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(8.dp))

                    // Refresh Button
                    Row(
                        modifier = GlanceModifier
                            .clickable(onClick = actionRunCallback<RefreshAction>())
                            .background(btnContainerColor)
                            .cornerRadius(12.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_refresh),
                            contentDescription = context.getString(R.string.refresh),
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(onBtnContainerColor)
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = context.getString(R.string.refresh),
                            style = TextStyle(
                                color = onBtnContainerColor,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(14.dp))

            // Main 2 Big Stat Cards Side by Side (Internet & Calls)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                val internetDisplay = if (data.internetPercent != null) "${data.internetRemaining} (${data.internetPercent.toInt()}%)" else data.internetRemaining
                val cleanCalls = formatCompactCalls(data.callsRemaining)
                val callsDisplay = if (data.callsPercent != null) "$cleanCalls (${data.callsPercent.toInt()}%)" else cleanCalls

                // Internet Card
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(mainTapAction)
                        .background(btnContainerColor)
                        .cornerRadius(16.dp)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_internet),
                            contentDescription = null,
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(onSurfaceVariant)
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = context.getString(R.string.internet),
                            maxLines = 1,
                            style = TextStyle(color = onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = internetDisplay,
                        maxLines = 1,
                        style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    )
                }

                Spacer(modifier = GlanceModifier.width(10.dp))

                // Calls Card
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(mainTapAction)
                        .background(btnContainerColor)
                        .cornerRadius(16.dp)
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_calls),
                            contentDescription = null,
                            modifier = GlanceModifier.size(16.dp),
                            colorFilter = ColorFilter.tint(onSurfaceVariant)
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = context.getString(R.string.calls),
                            maxLines = 1,
                            style = TextStyle(color = onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = callsDisplay,
                        maxLines = 1,
                        style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    )
                }
            }

            if (showExtraDetails) {
                Spacer(modifier = GlanceModifier.height(14.dp))

                // Breakdown Section Header
                Text(
                    text = context.getString(R.string.plan_details),
                    style = TextStyle(
                        color = primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )

                Spacer(modifier = GlanceModifier.height(6.dp))

                // List of structured details
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                ) {
                    if (!data.structuredDetails.isNullOrEmpty()) {
                        data.structuredDetails.forEach { detail ->
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = detail.label,
                                    modifier = GlanceModifier.defaultWeight(),
                                    style = TextStyle(color = onSurfaceVariant, fontSize = 13.sp)
                                )
                                Text(
                                    text = detail.value,
                                    style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = context.getString(R.string.last_updated),
                            style = TextStyle(color = onSurfaceVariant, fontSize = 13.sp)
                        )
                    }
                }
            } else {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }

            // Bottom Quick Action: Open App
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(androidx.glance.appwidget.action.actionStartActivity(android.content.Intent(context, com.telecom.widget.MainActivity::class.java)))
                    .background(btnContainerColor)
                    .cornerRadius(14.dp)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.tap_app),
                    style = TextStyle(
                        color = primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
            }
        }
    }

    @Composable
    private fun CompactLayout(data: ConsumptionData, primary: ColorProvider, onSurface: ColorProvider, onSurfaceVariant: ColorProvider, context: Context) {
        val internetDisplay = if (data.internetPercent != null) "${data.internetRemaining} (${data.internetPercent.toInt()}%)" else data.internetRemaining
        val cleanCalls = formatCompactCalls(data.callsRemaining)
        val callsDisplay = if (data.callsPercent != null) "$cleanCalls (${data.callsPercent.toInt()}%)" else cleanCalls

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                text = data.operator,
                maxLines = 1,
                style = TextStyle(color = primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_internet),
                    contentDescription = null,
                    modifier = GlanceModifier.size(13.dp),
                    colorFilter = ColorFilter.tint(onSurfaceVariant)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(text = context.getString(R.string.internet), maxLines = 1, style = TextStyle(color = onSurfaceVariant, fontSize = 11.sp))
            }
            Text(
                text = internetDisplay,
                maxLines = 1,
                style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            )
            Spacer(modifier = GlanceModifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_calls),
                    contentDescription = null,
                    modifier = GlanceModifier.size(13.dp),
                    colorFilter = ColorFilter.tint(onSurfaceVariant)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(text = context.getString(R.string.calls), maxLines = 1, style = TextStyle(color = onSurfaceVariant, fontSize = 11.sp))
            }
            Text(
                text = callsDisplay,
                maxLines = 1,
                style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            )
        }
    }

    @Composable
    private fun FullLayout(
        data: ConsumptionData,
        primary: ColorProvider,
        onSurface: ColorProvider,
        onSurfaceVariant: ColorProvider,
        btnContainerColor: ColorProvider,
        onBtnContainerColor: ColorProvider,
        context: Context,
        tapAction: String,
        settingsAction: androidx.glance.action.Action,
        showExtraDetails: Boolean
    ) {
        val mainTapAction = if (tapAction == "app") {
            androidx.glance.appwidget.action.actionStartActivity(
                android.content.Intent(context, com.telecom.widget.MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        } else {
            actionRunCallback<RefreshAction>()
        }

        val internetDisplay = if (data.internetPercent != null) "${data.internetRemaining} (${data.internetPercent.toInt()}%)" else data.internetRemaining
        val cleanCalls = formatCompactCalls(data.callsRemaining)
        val callsDisplay = if (data.callsPercent != null) "$cleanCalls (${data.callsPercent.toInt()}%)" else cleanCalls

        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header: Operator + Phone on Left, Actions on Right
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(mainTapAction)
                ) {
                    Text(
                        text = data.operator,
                        maxLines = 1,
                        style = TextStyle(color = primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    )
                    Text(
                        text = formatNationalPhone(data.phoneNumber),
                        maxLines = 1,
                        style = TextStyle(color = onSurfaceVariant, fontSize = 13.sp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Settings Button
                    Row(
                        modifier = GlanceModifier
                            .clickable(onClick = settingsAction)
                            .background(btnContainerColor)
                            .cornerRadius(10.dp)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_settings),
                            contentDescription = context.getString(R.string.widget_settings),
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(onBtnContainerColor)
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(6.dp))

                    // Refresh Button
                    Row(
                        modifier = GlanceModifier
                            .clickable(onClick = actionRunCallback<RefreshAction>())
                            .background(btnContainerColor)
                            .cornerRadius(10.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_refresh),
                            contentDescription = context.getString(R.string.refresh),
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(onBtnContainerColor)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = context.getString(R.string.refresh),
                            style = TextStyle(
                                color = onBtnContainerColor,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Main Stats Content Column (Clickable according to tap action)
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(mainTapAction)
            ) {
                // Main Dual Metrics Row
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_internet),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(onSurfaceVariant)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = context.getString(R.string.internet),
                            maxLines = 1,
                            style = TextStyle(color = onSurfaceVariant, fontSize = 12.sp)
                        )
                    }
                    Text(
                        text = internetDisplay,
                        maxLines = 1,
                        style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    )
                }
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_calls),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(onSurfaceVariant)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = context.getString(R.string.calls),
                            maxLines = 1,
                            style = TextStyle(color = onSurfaceVariant, fontSize = 12.sp)
                        )
                    }
                    Text(
                        text = callsDisplay,
                        maxLines = 1,
                        style = TextStyle(color = onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    )
                }
            }

            // Dynamic structured breakdown details (Appels nationaux, Appels Orange, Solde, Internet, SMS, etc.)
            if (showExtraDetails && !data.structuredDetails.isNullOrEmpty()) {
                Spacer(modifier = GlanceModifier.height(6.dp))
                data.structuredDetails.forEach { detail ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = detail.label,
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = onSurfaceVariant, fontSize = 11.5.sp)
                        )
                        Text(
                            text = detail.value,
                            maxLines = 1,
                            style = TextStyle(color = onSurface, fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
                        )
                    }
                }
            }
        }
    }
    }

    private fun formatCompactCalls(calls: String): String {
        val trimmed = calls.trim()
        if (trimmed.isEmpty() || trimmed.equals("N/A", ignoreCase = true)) return "0h"
        if (trimmed.contains("illimit", ignoreCase = true)) return trimmed

        val res = trimmed.replace(Regex("""\s*\d+\s*(?:s|sec|secondes?)\b""", RegexOption.IGNORE_CASE), "").trim()
        val normalized = if (res.count { it == ':' } >= 2) res.substringBeforeLast(':') else res

        val m = Regex("""0*(\d+)\s*[hH]\s*0*(\d+)\s*(?:min|m)?""", RegexOption.IGNORE_CASE).find(normalized)
        if (m != null) {
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val mins = m.groupValues[2].toIntOrNull() ?: 0
            return when {
                h == 0 && mins == 0 -> "0h"
                h == 0 -> "${mins}min"
                mins == 0 -> "${h}h"
                else -> "${h}h ${mins}m"
            }
        }
        val m2 = Regex("""0*(\d+):0*(\d+)""").find(normalized)
        if (m2 != null) {
            val h = m2.groupValues[1].toIntOrNull() ?: 0
            val mins = m2.groupValues[2].toIntOrNull() ?: 0
            return when {
                h == 0 && mins == 0 -> "0h"
                h == 0 -> "${mins}min"
                mins == 0 -> "${h}h"
                else -> "${h}h ${mins}m"
            }
        }
        val mHOnly = Regex("""0*(\d+)\s*[hH]\b""", RegexOption.IGNORE_CASE).find(normalized)
        if (mHOnly != null) {
            val h = mHOnly.groupValues[1].toIntOrNull() ?: 0
            return "${h}h"
        }
        val mMinOnly = Regex("""0*(\d+)\s*(?:min|m)\b""", RegexOption.IGNORE_CASE).find(normalized)
        if (mMinOnly != null) {
            val mins = mMinOnly.groupValues[1].toIntOrNull() ?: 0
            return "${mins}min"
        }
        return normalized.ifEmpty { calls }
    }

    private fun formatShortOperator(op: String): String {
        return when {
            op.contains("Telecom", ignoreCase = true) || op.contains("IAM", ignoreCase = true) -> "MT"
            op.contains("Inwi", ignoreCase = true) || op.contains("Wana", ignoreCase = true) -> "inwi"
            op.contains("Orange", ignoreCase = true) -> "Orange"
            else -> op.take(6)
        }
    }

    private fun formatNationalPhone(phone: String): String {
        val clean = phone.filter { it.isDigit() }
        return if (clean.startsWith("212") && clean.length >= 12) {
            "0" + clean.substring(3)
        } else if (clean.startsWith("0")) {
            clean
        } else {
            phone
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ConsumptionSyncWorker>().build())
    }
}

class ConsumptionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ConsumptionWidget()
}
