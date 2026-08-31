package com.telecom.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SeslSeekBar
import androidx.core.content.res.ResourcesCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telecom.widget.glance.ConsumptionWidget
import com.telecom.widget.network.ConsumptionData
import com.telecom.widget.network.SavedAccount
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.widget.CardItemView
import dev.oneuiproject.oneui.widget.RadioItemViewGroup
import dev.oneuiproject.oneui.widget.RadioItemView
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class WidgetConfigActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var opacityLevel = 2 // default to 70%
    private var colorMode = "system"
    private var font = "one_ui_sans"
    private var tapAction = "refresh"
    private var showExtraDetails = true
    private var consumptionData: ConsumptionData? = null
    
    private var savedAccounts: List<SavedAccount> = emptyList()
    private var selectedAccountId: String = ""

    private val WIDGET_OPACITY_KEY = intPreferencesKey("widget_opacity")
    private val WIDGET_COLOR_MODE_KEY = stringPreferencesKey("widget_color_mode")
    private val WIDGET_FONT_KEY = stringPreferencesKey("widget_font")
    private val WIDGET_EXTRA_DETAILS_KEY = booleanPreferencesKey("widget_show_extra_details")
    private val SAVED_ACCOUNTS_KEY = stringPreferencesKey("saved_accounts_json")
    private val ACTIVE_ACCOUNT_ID_KEY = stringPreferencesKey("active_account_id")

    companion object {
        private val OPACITY_PRESETS = intArrayOf(0, 102, 178, 240)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        intent.extras?.let {
            appWidgetId = it.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        runBlocking {
            val prefs = dataStore.data.first()
            val accountsJson = prefs[SAVED_ACCOUNTS_KEY]
            val accountsListType = object : TypeToken<List<SavedAccount>>() {}.type
            savedAccounts = if (!accountsJson.isNullOrEmpty()) {
                try { Gson().fromJson(accountsJson, accountsListType) } catch (_: Exception) { emptyList() }
            } else emptyList()

            val boundAccountKey = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                stringPreferencesKey("widget_account_$appWidgetId")
            } else null

            selectedAccountId = (if (boundAccountKey != null) prefs[boundAccountKey] else null)
                ?: prefs[ACTIVE_ACCOUNT_ID_KEY]
                ?: savedAccounts.firstOrNull()?.id
                ?: ""

            val activeAcc = savedAccounts.find { it.id == selectedAccountId } ?: savedAccounts.firstOrNull()
            consumptionData = activeAcc?.cachedData

            val savedOpacity = (if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) prefs[intPreferencesKey("widget_opacity_$appWidgetId")] else null)
                ?: prefs[WIDGET_OPACITY_KEY] ?: 80
            val alpha255 = (savedOpacity * 255 / 100).coerceIn(0, 255)
            opacityLevel = closestOpacityLevel(alpha255)

            colorMode = (if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) prefs[stringPreferencesKey("widget_color_mode_$appWidgetId")] else null)
                ?: prefs[WIDGET_COLOR_MODE_KEY] ?: "system"

            font = (if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) prefs[stringPreferencesKey("widget_font_$appWidgetId")] else null)
                ?: prefs[WIDGET_FONT_KEY] ?: "one_ui_sans"

            tapAction = (if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) prefs[stringPreferencesKey("widget_tap_action_$appWidgetId")] else null)
                ?: prefs[stringPreferencesKey("widget_tap_action")] ?: "refresh"

            showExtraDetails = (if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) prefs[booleanPreferencesKey("widget_show_extra_details_$appWidgetId")] else null)
                ?: prefs[WIDGET_EXTRA_DETAILS_KEY] ?: true
        }

        setContentView(R.layout.activity_widget_config)
        initViews()
    }

    private fun initViews() {
        val toolbarLayout = findViewById<ToolbarLayout>(R.id.widget_config_root)
        toolbarLayout.setNavigationButtonOnClickListener { finish() }

        // Setup Accounts List
        renderAccountRows()

        // Setup Colours & Font CardItemViews
        val cardColours = findViewById<CardItemView>(R.id.tint_row)
        val cardFont = findViewById<CardItemView>(R.id.font_row)

        updateColoursSummary(cardColours)
        updateFontSummary(cardFont)

        cardColours.setOnClickListener { pickColours(cardColours) }
        cardFont.setOnClickListener { pickFont(cardFont) }

        // Setup Transparency Seekbar
        val seekBar = findViewById<SeslSeekBar>(R.id.opacity_slider)
        val thumb = findViewById<View>(R.id.opacity_thumb_visual)
        val tick0 = findViewById<View>(R.id.opacity_tick_0)
        val tick1 = findViewById<View>(R.id.opacity_tick_1)
        val tick2 = findViewById<View>(R.id.opacity_tick_2)
        val tick3 = findViewById<View>(R.id.opacity_tick_3)
        val ticks = arrayOf(tick0, tick1, tick2, tick3)

        seekBar.alpha = 0f
        seekBar.progressDrawable?.alpha = 0
        seekBar.max = 3
        seekBar.progress = opacityLevel

        seekBar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeslSeekBar?, progress: Int, fromUser: Boolean) {
                opacityLevel = progress.coerceIn(0, 3)
                updateSliderVisuals(thumb, ticks, opacityLevel)
                renderPreview()
            }
            override fun onStartTrackingTouch(sb: SeslSeekBar?) {}
            override fun onStopTrackingTouch(sb: SeslSeekBar?) {}
        })

        seekBar.post {
            updateSliderVisuals(thumb, ticks, opacityLevel)
        }

        // Setup Tap Action Selection
        val tapActionGroup = findViewById<RadioItemViewGroup>(R.id.tap_action_group)
        if (tapAction == "app") {
            tapActionGroup?.check(R.id.tap_app_row)
        } else {
            tapActionGroup?.check(R.id.tap_refresh_row)
        }
        tapActionGroup?.setOnCheckedChangeListener { _, checkedId ->
            tapAction = if (checkedId == R.id.tap_app_row) "app" else "refresh"
        }

        // Setup Show Extra Details Switch
        val extraSwitch = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.extra_details_switch)
        val extraRow = findViewById<View>(R.id.extra_details_row)
        extraSwitch?.isChecked = showExtraDetails
        extraSwitch?.setOnCheckedChangeListener { _, isChecked ->
            showExtraDetails = isChecked
        }
        extraRow?.setOnClickListener {
            extraSwitch?.toggle()
        }

        // Setup Bottom Action Buttons (Cancel / Save)
        findViewById<View>(R.id.btn_cancel).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_save).setOnClickListener { saveAndFinish() }

        renderPreview()
    }

    private fun updateSliderVisuals(thumb: View, ticks: Array<View>, progress: Int) {
        val activeIndex = progress.coerceIn(0, ticks.lastIndex)
        for (i in ticks.indices) {
            ticks[i].alpha = if (i == activeIndex) 0f else 1f
        }
        val targetTick = ticks[activeIndex]
        thumb.post {
            val tickCenter = targetTick.x + targetTick.width / 2f
            thumb.translationX = tickCenter - thumb.width / 2f
            thumb.visibility = View.VISIBLE
        }
    }

    private fun pickAccount(anchor: View) {
        val options = savedAccounts.map { 
            "${it.operator} (${if (it.phone.isNotEmpty()) it.phone else it.email})" 
        }
        val currentIndex = savedAccounts.indexOfFirst { it.id == selectedAccountId }.coerceAtLeast(0)

        showDropdownPopup(anchor, options, currentIndex) { index ->
            val acc = savedAccounts[index]
            selectedAccountId = acc.id
            consumptionData = acc.cachedData
            findViewById<TextView>(R.id.account_phone_text).text = if (acc.phone.isNotEmpty()) acc.phone else acc.email
            findViewById<TextView>(R.id.account_operator_text).text = acc.operator
            renderPreview()
        }
    }

    private fun updateColoursSummary(card: CardItemView) {
        card.summary = when (colorMode) {
            "light" -> "White"
            "dark" -> "Black"
            else -> "Match with Dark mode"
        }
    }

    private fun updateFontSummary(card: CardItemView) {
        card.summary = if (font == "one_ui_sans") "One UI Sans" else "Google Sans Flex"
    }

    private fun pickColours(anchor: View) {
        val options = listOf("Match with Dark mode", "White", "Black")
        val values = listOf("system", "light", "dark")
        val currentIndex = values.indexOf(colorMode).coerceAtLeast(0)

        showDropdownPopup(anchor, options, currentIndex) { index ->
            colorMode = values[index]
            updateColoursSummary(anchor as CardItemView)
            renderPreview()
        }
    }

    private fun pickFont(anchor: View) {
        val options = listOf("One UI Sans", "Google Sans Flex")
        val values = listOf("one_ui_sans", "google_sans_flex")
        val currentIndex = values.indexOf(font).coerceAtLeast(0)

        showDropdownPopup(anchor, options, currentIndex) { index ->
            font = values[index]
            updateFontSummary(anchor as CardItemView)
            renderPreview()
        }
    }

    private fun showDropdownPopup(anchor: View, items: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
        val adapter = object : ArrayAdapter<String>(this, R.layout.dropdown_item_checked, R.id.dropdown_label, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val check = view.findViewById<ImageView>(R.id.dropdown_check)
                val text = view.findViewById<TextView>(R.id.dropdown_label)
                if (position == selectedIndex) {
                    check.visibility = View.VISIBLE
                    text.setTextColor(Color.parseColor("#0381FE"))
                } else {
                    check.visibility = View.GONE
                    val defaultColor = if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
                        Color.WHITE else Color.BLACK
                    text.setTextColor(defaultColor)
                }
                return view
            }
        }

        val popup = ListPopupWindow(this).apply {
            setAdapter(adapter)
            anchorView = anchor
            width = (240 * resources.displayMetrics.density).toInt()
            height = ListPopupWindow.WRAP_CONTENT
            isModal = true
            setSelection(selectedIndex)
            horizontalOffset = (18 * resources.displayMetrics.density).toInt()
            verticalOffset = (-8 * resources.displayMetrics.density).toInt()
        }
        popup.setOnItemClickListener { _, _, position, _ ->
            onSelected(position)
            popup.dismiss()
        }
        popup.show()
    }

    private fun renderPreview() {
        val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (colorMode) {
            "dark" -> true
            "light" -> false
            else -> isNight
        }

        val pillWidget = findViewById<LinearLayout>(R.id.preview_pill_widget)
        val primaryText = findViewById<TextView>(R.id.preview_primary_text)
        val secondaryText = findViewById<TextView>(R.id.preview_secondary_text)

        val alphaInt = OPACITY_PRESETS[opacityLevel.coerceIn(0, OPACITY_PRESETS.lastIndex)]
        val baseColor = if (isDark) Color.rgb(22, 22, 22) else Color.rgb(255, 255, 255)
        val pillBgColor = Color.argb(alphaInt, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))

        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32 * resources.displayMetrics.density
            setColor(pillBgColor)
        }
        pillWidget.background = bgDrawable

        val textColor = if (isDark) Color.rgb(244, 239, 244) else Color.rgb(28, 27, 31)
        val subTextColor = if (isDark) Color.rgb(202, 196, 208) else Color.rgb(73, 69, 79)

        primaryText.setTextColor(textColor)
        secondaryText.setTextColor(subTextColor)

        val primaryTf = if (font == "one_ui_sans") {
            ResourcesCompat.getFont(this, R.font.one_ui_sans)
        } else {
            ResourcesCompat.getFont(this, R.font.google_sans_flex_bold)
        }
        val secondaryTf = if (font == "one_ui_sans") {
            ResourcesCompat.getFont(this, R.font.one_ui_sans)
        } else {
            ResourcesCompat.getFont(this, R.font.google_sans_flex_regular)
        }

        primaryText.typeface = primaryTf
        secondaryText.typeface = secondaryTf

        if (consumptionData != null) {
            val compactCalls = (consumptionData?.callsRemaining ?: "12h 30min").replace(Regex("""\s*\d+\s*(?:s|sec|secondes?)\b""", RegexOption.IGNORE_CASE), "").let {
                if (it.count { ch -> ch == ':' } == 2) it.substringBeforeLast(':') else it
            }.trim()
            primaryText.text = "${consumptionData?.internetRemaining ?: "18.45 Go"}  $compactCalls"
            secondaryText.text = "${consumptionData?.operator ?: "MT"} • ${consumptionData?.phoneNumber ?: "0661482915"}"
        } else {
            primaryText.text = "18.45 Go  12h 30min"
            secondaryText.text = "Maroc Telecom • 0661482915"
        }
    }

    private fun closestOpacityLevel(alpha: Int): Int =
        OPACITY_PRESETS.indices.minByOrNull { kotlin.math.abs(OPACITY_PRESETS[it] - alpha) } ?: 2

    private fun renderAccountRows() {
        val accountGroup = findViewById<ViewGroup>(R.id.account_group) ?: return
        accountGroup.removeAllViews()

        if (savedAccounts.isEmpty()) {
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = (18 * resources.displayMetrics.density).toInt()
                val padV = (14 * resources.displayMetrics.density).toInt()
                setPadding(padH, padV, padH, padV)
            }
            val radio = RadioButton(this).apply {
                isChecked = true
                isClickable = false
                isFocusable = false
            }
            row.addView(radio)
            val textLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (14 * resources.displayMetrics.density).toInt()
                }
                orientation = LinearLayout.VERTICAL
            }
            val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            textLayout.addView(TextView(this).apply {
                text = "0610653694"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            })
            textLayout.addView(TextView(this).apply {
                text = "Maroc Telecom"
                textSize = 13f
                setTextColor(if (isNight) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70"))
            })
            row.addView(textLayout)
            accountGroup.addView(row)
            return
        }

        savedAccounts.forEachIndexed { index, account ->
            if (index > 0) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    ).apply {
                        marginStart = (48 * resources.displayMetrics.density).toInt()
                        marginEnd = (16 * resources.displayMetrics.density).toInt()
                    }
                    val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    setBackgroundColor(if (isNight) Color.parseColor("#2C2C2E") else Color.parseColor("#E5E5EA"))
                }
                accountGroup.addView(divider)
            }

            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = (18 * resources.displayMetrics.density).toInt()
                val padV = (14 * resources.displayMetrics.density).toInt()
                setPadding(padH, padV, padH, padV)
                isClickable = true
                isFocusable = true
                val outValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }

            val isSelected = account.id == selectedAccountId || (selectedAccountId.isEmpty() && index == 0)
            if (isSelected) {
                selectedAccountId = account.id
                consumptionData = account.cachedData
            }

            val radio = RadioButton(this).apply {
                id = View.generateViewId()
                isChecked = isSelected
                isClickable = false
                isFocusable = false
            }
            row.addView(radio)

            val textLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (14 * resources.displayMetrics.density).toInt()
                }
                orientation = LinearLayout.VERTICAL
            }

            val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

            val phoneView = TextView(this).apply {
                text = if (account.phone.isNotEmpty()) account.phone else account.email
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            }

            val opView = TextView(this).apply {
                text = account.operator
                textSize = 13f
                setTextColor(if (isNight) Color.parseColor("#8E8E93") else Color.parseColor("#6C6C70"))
            }

            textLayout.addView(phoneView)
            textLayout.addView(opView)
            row.addView(textLayout)

            row.setOnClickListener {
                selectedAccountId = account.id
                consumptionData = account.cachedData
                renderAccountRows()
                renderPreview()
            }

            accountGroup.addView(row)
        }
    }

    private fun saveAndFinish() {
        val alphaVal = OPACITY_PRESETS[opacityLevel.coerceIn(0, OPACITY_PRESETS.lastIndex)]
        val opacityPercent = (alphaVal * 100 / 255)

        runBlocking {
            dataStore.edit { prefs ->
                prefs[WIDGET_OPACITY_KEY] = opacityPercent
                prefs[WIDGET_COLOR_MODE_KEY] = colorMode
                prefs[WIDGET_FONT_KEY] = font
                prefs[stringPreferencesKey("widget_tap_action")] = tapAction
                prefs[WIDGET_EXTRA_DETAILS_KEY] = showExtraDetails

                if (selectedAccountId.isNotEmpty()) {
                    prefs[ACTIVE_ACCOUNT_ID_KEY] = selectedAccountId
                    val selAcc = savedAccounts.find { it.id == selectedAccountId }
                    if (selAcc?.cachedData != null) {
                        prefs[stringPreferencesKey("cached_data")] = Gson().toJson(selAcc.cachedData)
                    }
                }

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    if (selectedAccountId.isNotEmpty()) {
                        prefs[stringPreferencesKey("widget_account_$appWidgetId")] = selectedAccountId
                    }
                    prefs[intPreferencesKey("widget_opacity_$appWidgetId")] = opacityPercent
                    prefs[stringPreferencesKey("widget_color_mode_$appWidgetId")] = colorMode
                    prefs[stringPreferencesKey("widget_font_$appWidgetId")] = font
                    prefs[stringPreferencesKey("widget_tap_action_$appWidgetId")] = tapAction
                    prefs[booleanPreferencesKey("widget_show_extra_details_$appWidgetId")] = showExtraDetails
                }
            }
        }

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)

        runBlocking {
            ConsumptionWidget().updateAll(applicationContext)
        }
        finish()
    }
}
