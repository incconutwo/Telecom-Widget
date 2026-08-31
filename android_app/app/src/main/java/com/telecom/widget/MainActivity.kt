package com.telecom.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.os.Build
import com.telecom.widget.notification.TelecomLiveNotificationHelper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.composables.icons.lucide.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.telecom.widget.glance.ConsumptionWidget
import com.telecom.widget.network.*
import com.telecom.widget.security.*
import com.telecom.widget.ui.theme.TelecomWidgetTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// ── M3 Expressive Bounce Click Modifier with Tactile Haptics ───────────────────

@Composable
fun Modifier.bounceClick(scaleDown: Float = 0.96f): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
        label = "bounceScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        isPressed = false
                    }
                }
            }
        }
}

// ── M3 Expressive Odometer Rolling Text Animation ─────────────────────────────

@Composable
fun AnimatedRollingText(
    text: String,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                initialOffsetY = { fullHeight -> fullHeight / 2 }
            ) + fadeIn(animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f)))
            .togetherWith(
                slideOutVertically(
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                    targetOffsetY = { fullHeight -> -fullHeight / 2 }
                ) + fadeOut(animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f))
            )
        },
        label = "odometerRollingText"
    ) { targetText ->
        Text(
            text = targetText,
            style = style,
            color = color,
            modifier = modifier
        )
    }
}

// ── ViewModel with Multi-Account Support ──────────────────────────────────────

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val gson = Gson()

    val SAVED_ACCOUNTS_KEY = stringPreferencesKey("saved_accounts_json")
    val ACTIVE_ACCOUNT_ID_KEY = stringPreferencesKey("active_account_id")
    val CACHED_DATA_KEY = stringPreferencesKey("cached_data")
    val HTTP_COOKIES_KEY = stringPreferencesKey("http_cookies")

    var isInitializing by mutableStateOf(true); private set
    var savedAccounts by mutableStateOf<List<SavedAccount>>(emptyList())
    var activeAccountId by mutableStateOf<String?>(null)
    var isAddAccountMode by mutableStateOf(false)

    var operator   by mutableStateOf("Maroc Telecom")
    var email      by mutableStateOf("")
    var phone      by mutableStateOf("")
    var password   by mutableStateOf("")
    var isLoading  by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var error      by mutableStateOf<String?>(null)
    var consumptionData by mutableStateOf<ConsumptionData?>(null)
    var pendingLineSelection by mutableStateOf<MultiLineException?>(null)

    init {
        viewModelScope.launch {
            loadAccounts()
            isInitializing = false
        }
    }

    private suspend fun loadAccounts() {
        val prefs = getApplication<Application>().dataStore.data.first()
        val accountsJson = prefs[SAVED_ACCOUNTS_KEY]
        val listType = object : TypeToken<List<SavedAccount>>() {}.type
        var storedAccounts: MutableList<SavedAccount> = if (!accountsJson.isNullOrEmpty()) {
            try { gson.fromJson(accountsJson, listType) } catch (_: Exception) { mutableListOf() }
        } else mutableListOf()

        var needsMigration = false

        // Legacy single-account migration
        if (storedAccounts.isEmpty()) {
            val legacyOp = prefs[stringPreferencesKey("operator")]
            val legacyPass = prefs[stringPreferencesKey("password")]
            if (!legacyOp.isNullOrEmpty() && !legacyPass.isNullOrEmpty()) {
                val legacyAcc = SavedAccount(
                    operator = legacyOp,
                    email = prefs[stringPreferencesKey("email")] ?: "",
                    phone = prefs[stringPreferencesKey("phone")] ?: "",
                    password = legacyPass,
                    selectedLine = prefs[stringPreferencesKey("selected_line")]
                )
                storedAccounts.add(legacyAcc)
                needsMigration = true
            }
        } else {
            // Check if any stored account has an unencrypted password
            if (storedAccounts.any { !CryptoManager.isEncrypted(it.password) && it.password.isNotEmpty() }) {
                needsMigration = true
            }
        }

        val decryptedAccounts = storedAccounts.toDecryptedFromStorage().toMutableList()

        if (needsMigration) {
            getApplication<Application>().dataStore.edit { p ->
                p[SAVED_ACCOUNTS_KEY] = gson.toJson(decryptedAccounts.toEncryptedForStorage())
                if (decryptedAccounts.isNotEmpty()) {
                    p[ACTIVE_ACCOUNT_ID_KEY] = prefs[ACTIVE_ACCOUNT_ID_KEY] ?: decryptedAccounts.first().id
                }
                // Purge legacy plaintext keys
                p.remove(stringPreferencesKey("operator"))
                p.remove(stringPreferencesKey("password"))
                p.remove(stringPreferencesKey("email"))
                p.remove(stringPreferencesKey("phone"))
                p.remove(stringPreferencesKey("selected_line"))
            }
        }

        savedAccounts = decryptedAccounts
        activeAccountId = prefs[ACTIVE_ACCOUNT_ID_KEY] ?: decryptedAccounts.firstOrNull()?.id

        val activeAcc = decryptedAccounts.find { it.id == activeAccountId } ?: decryptedAccounts.firstOrNull()
        if (activeAcc != null) {
            operator = activeAcc.operator
            email = activeAcc.email
            phone = activeAcc.phone
            password = activeAcc.password
            consumptionData = activeAcc.cachedData
        }
        TelecomLiveNotificationHelper.updateAll(getApplication(), decryptedAccounts)
    }

    fun switchAccount(account: SavedAccount) {
        activeAccountId = account.id
        operator = account.operator
        email = account.email
        phone = account.phone
        password = account.password
        consumptionData = account.cachedData
        isAddAccountMode = false
        error = null

        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().dataStore.edit { p ->
                p[ACTIVE_ACCOUNT_ID_KEY] = account.id
                if (account.cachedData != null) {
                    p[CACHED_DATA_KEY] = gson.toJson(account.cachedData)
                }
            }
            ConsumptionWidget().updateAll(getApplication())
        }
    }

    fun deleteAccount(account: SavedAccount) {
        val updated = savedAccounts.filter { it.id != account.id }
        savedAccounts = updated
        val nextActive = updated.firstOrNull()
        activeAccountId = nextActive?.id

        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().dataStore.edit { p ->
                p[SAVED_ACCOUNTS_KEY] = gson.toJson(updated.toEncryptedForStorage())
                if (nextActive != null) {
                    p[ACTIVE_ACCOUNT_ID_KEY] = nextActive.id
                    if (nextActive.cachedData != null) {
                        p[CACHED_DATA_KEY] = gson.toJson(nextActive.cachedData)
                    }
                } else {
                    p.remove(ACTIVE_ACCOUNT_ID_KEY)
                    p.remove(CACHED_DATA_KEY)
                }
            }
            ConsumptionWidget().updateAll(getApplication())
            TelecomLiveNotificationHelper.cancelNotification(getApplication(), account.id)
        }

        if (nextActive != null) {
            switchAccount(nextActive)
        } else {
            consumptionData = null
            isAddAccountMode = false
        }
    }

    fun toggleLiveNotification(accountId: String, enabled: Boolean) {
        val updated = savedAccounts.map {
            if (it.id == accountId) it.copy(liveNotificationEnabled = enabled) else it
        }
        savedAccounts = updated

        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().dataStore.edit { p ->
                p[SAVED_ACCOUNTS_KEY] = gson.toJson(updated.toEncryptedForStorage())
            }
            val target = updated.find { it.id == accountId }
            if (target != null) {
                if (enabled) {
                    TelecomLiveNotificationHelper.updateNotification(getApplication(), target)
                } else {
                    TelecomLiveNotificationHelper.cancelNotification(getApplication(), accountId)
                }
            }
        }
    }

    fun addNewAccountClick() {
        isAddAccountMode = true
        operator = "Maroc Telecom"
        email = ""
        phone = ""
        password = ""
        error = null
    }

    fun cancelAddAccount() {
        if (savedAccounts.isNotEmpty()) {
            isAddAccountMode = false
            val activeAcc = savedAccounts.find { it.id == activeAccountId } ?: savedAccounts.first()
            switchAccount(activeAcc)
        }
    }

    fun login(selectedLine: String? = null, manualRefresh: Boolean = false) {
        if (!manualRefresh) isLoading = true else isRefreshing = true
        error = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentCookies = savedAccounts.find { it.id == activeAccountId }?.cookies ?: emptyList()
                val loginIdentifier = if (operator == "Maroc Telecom") email else (if (email.isNotEmpty()) email else phone)
                val targetPhone = if (operator == "Maroc Telecom") phone else loginIdentifier

                val client = when (operator) {
                    "Maroc Telecom" -> MarocTelecomClient(email, password, phone, currentCookies)
                    "Orange" -> OrangeClient(loginIdentifier, password, selectedLine ?: loginIdentifier, currentCookies)
                    else -> InwiClient(loginIdentifier, password, selectedLine ?: loginIdentifier, currentCookies)
                }

                val data = client.fetchConsumption()
                consumptionData = data

                val existingIndex = savedAccounts.indexOfFirst { 
                    it.operator == operator && (if (operator == "Maroc Telecom") it.email == email else (it.phone == loginIdentifier || it.email == loginIdentifier))
                }

                val isLiveEnabled = if (existingIndex >= 0) savedAccounts[existingIndex].liveNotificationEnabled else false
                val currentAccId = if (existingIndex >= 0) savedAccounts[existingIndex].id else UUID.randomUUID().toString()
                val updatedAcc = SavedAccount(
                    id = currentAccId,
                    operator = operator,
                    email = if (operator == "Maroc Telecom") email else "",
                    phone = if (operator == "Maroc Telecom") phone else loginIdentifier,
                    password = password,
                    selectedLine = selectedLine ?: targetPhone,
                    cookies = client.currentCookies,
                    cachedData = data,
                    lastUpdated = System.currentTimeMillis(),
                    liveNotificationEnabled = isLiveEnabled
                )

                val updatedList = savedAccounts.toMutableList()
                if (existingIndex >= 0) {
                    updatedList[existingIndex] = updatedAcc
                } else {
                    updatedList.add(updatedAcc)
                }

                savedAccounts = updatedList
                activeAccountId = currentAccId
                isAddAccountMode = false

                getApplication<Application>().dataStore.edit { prefs ->
                    prefs[SAVED_ACCOUNTS_KEY] = gson.toJson(updatedList.toEncryptedForStorage())
                    prefs[ACTIVE_ACCOUNT_ID_KEY] = currentAccId
                    prefs[CACHED_DATA_KEY] = gson.toJson(data)
                    prefs[HTTP_COOKIES_KEY] = gson.toJson(client.currentCookies)
                }

                ConsumptionWidget().updateAll(getApplication())
                TelecomLiveNotificationHelper.updateNotification(getApplication(), updatedAcc)
                scheduleBackgroundSync()

            } catch (e: MultiLineException) {
                pendingLineSelection = e
            } catch (e: Exception) {
                error = e.message ?: "Authentication failed"
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun completeLineSelection(line: String) {
        val multiLine = pendingLineSelection ?: return
        pendingLineSelection = null
        isLoading = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentCookies = savedAccounts.find { it.id == activeAccountId }?.cookies ?: emptyList()
                val loginIdentifier = if (operator == "Maroc Telecom") email else (if (email.isNotEmpty()) email else phone)

                val client = if (operator == "Orange") {
                    OrangeClient(loginIdentifier, password, line, currentCookies)
                } else {
                    InwiClient(loginIdentifier, password, line, currentCookies)
                }

                val data = if (operator == "Orange") {
                    (client as OrangeClient).submitLineSelection(line, multiLine.token ?: "")
                } else {
                    (client as InwiClient).submitLineSelection(line, multiLine.token ?: "")
                }

                consumptionData = data

                val existingIndex = savedAccounts.indexOfFirst { it.operator == operator && (it.phone == loginIdentifier || it.email == loginIdentifier) }
                val isLiveEnabled = if (existingIndex >= 0) savedAccounts[existingIndex].liveNotificationEnabled else false
                val currentAccId = if (existingIndex >= 0) savedAccounts[existingIndex].id else UUID.randomUUID().toString()
                val updatedAcc = SavedAccount(
                    id = currentAccId,
                    operator = operator,
                    email = if (operator == "Maroc Telecom") email else "",
                    phone = loginIdentifier,
                    password = password,
                    selectedLine = line,
                    cookies = client.currentCookies,
                    cachedData = data,
                    lastUpdated = System.currentTimeMillis(),
                    liveNotificationEnabled = isLiveEnabled
                )

                val updatedList = savedAccounts.toMutableList()
                if (existingIndex >= 0) updatedList[existingIndex] = updatedAcc else updatedList.add(updatedAcc)

                savedAccounts = updatedList
                activeAccountId = currentAccId
                isAddAccountMode = false

                getApplication<Application>().dataStore.edit { prefs ->
                    prefs[SAVED_ACCOUNTS_KEY] = gson.toJson(updatedList.toEncryptedForStorage())
                    prefs[ACTIVE_ACCOUNT_ID_KEY] = currentAccId
                    prefs[CACHED_DATA_KEY] = gson.toJson(data)
                    prefs[HTTP_COOKIES_KEY] = gson.toJson(client.currentCookies)
                }

                ConsumptionWidget().updateAll(getApplication())
                TelecomLiveNotificationHelper.updateNotification(getApplication(), updatedAcc)
                scheduleBackgroundSync()

            } catch (e: Exception) {
                error = e.message ?: "Line selection failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun logout() {
        activeAccountId?.let { id ->
            val acc = savedAccounts.find { it.id == id }
            if (acc != null) deleteAccount(acc)
        }
    }

    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ConsumptionSyncWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "ConsumptionSync",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        SmartSyncManager.scheduleNextAlarm(getApplication())
    }
}

// ── MainActivity with Sidebar Drawer ──────────────────────────────────────────

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.telecom.widget.network.SmartSyncManager.startNetworkMonitoring(this)
        setContent {
            TelecomWidgetTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = viewModel.savedAccounts.isNotEmpty() && !viewModel.isAddAccountMode,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(min = 300.dp, max = 340.dp),
                            drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            AppSidebarContent(
                                accounts = viewModel.savedAccounts,
                                activeAccountId = viewModel.activeAccountId,
                                onAccountSelected = { acc ->
                                    viewModel.switchAccount(acc)
                                    scope.launch { drawerState.close() }
                                },
                                onAddAccount = {
                                    viewModel.addNewAccountClick()
                                    scope.launch { drawerState.close() }
                                },
                                onDeleteAccount = { acc ->
                                    viewModel.deleteAccount(acc)
                                }
                            )
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        if (viewModel.isInitializing) {
                            // Initializing
                        } else {
                            AnimatedContent(
                                targetState = if (viewModel.isAddAccountMode) null else viewModel.consumptionData,
                                transitionSpec = {
                                    (fadeIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f)) +
                                     scaleIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f), initialScale = 0.92f))
                                    .togetherWith(
                                        fadeOut(animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f)) +
                                        scaleOut(animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f), targetScale = 0.95f)
                                    )
                                },
                                label = "screenTransition"
                            ) { currentData ->
                                if (currentData != null) {
                                    DashboardScreen(
                                        data = currentData,
                                        viewModel = viewModel,
                                        onOpenDrawer = { scope.launch { drawerState.open() } }
                                    )
                                } else {
                                    LoginScreen(
                                        viewModel = viewModel,
                                        canCancel = viewModel.savedAccounts.isNotEmpty() && viewModel.isAddAccountMode,
                                        onCancel = { viewModel.cancelAddAccount() }
                                    )
                                }
                            }

                            // Multi-line selection bottom sheet
                            viewModel.pendingLineSelection?.let { multiLine ->
                                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                                ModalBottomSheet(
                                    onDismissRequest = { viewModel.pendingLineSelection = null },
                                    sheetState = sheetState
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(stringResource(R.string.select_line), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                        multiLine.lines.forEach { line ->
                                            Button(
                                                onClick = { viewModel.completeLineSelection(line) },
                                                modifier = Modifier.fillMaxWidth().bounceClick(),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            ) {
                                                Text(line, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── App Sidebar Content ───────────────────────────────────────────────────────

@Composable
fun AppSidebarContent(
    accounts: List<SavedAccount>,
    activeAccountId: String?,
    onAccountSelected: (SavedAccount) -> Unit,
    onAddAccount: () -> Unit,
    onDeleteAccount: (SavedAccount) -> Unit
) {
    var accountToDelete by remember { mutableStateOf<SavedAccount?>(null) }

    accountToDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            icon = { Icon(Lucide.Trash2, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.delete_account_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.delete_account_confirm, acc.operator, if (acc.phone.isNotEmpty()) acc.phone else acc.email))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = accountToDelete
                        accountToDelete = null
                        if (target != null) onDeleteAccount(target)
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Lucide.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.accounts_section),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.accounts_connected, accounts.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val haptic = LocalHapticFeedback.current
            accounts.forEach { acc ->
                val isActive = acc.id == activeAccountId
                val bgColor by animateColorAsState(
                    targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                    label = "sidebarAccBg_${acc.id}"
                )
                val textColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

                val itemInteractionSource = remember { MutableInteractionSource() }
                val isItemPressed by itemInteractionSource.collectIsPressedAsState()
                val itemScale by animateFloatAsState(
                    targetValue = if (isItemPressed) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
                    label = "sidebarAccScale_${acc.id}"
                )

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAccountSelected(acc)
                    },
                    interactionSource = itemInteractionSource,
                    shape = RoundedCornerShape(16.dp),
                    color = bgColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = itemScale
                            scaleY = itemScale
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = acc.operator,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                            Text(
                                text = if (acc.phone.isNotEmpty()) acc.phone else acc.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor.copy(alpha = 0.8f)
                            )
                        }

                        if (accounts.size > 1) {
                            IconButton(onClick = { accountToDelete = acc }, modifier = Modifier.size(28.dp)) {
                                Icon(Lucide.Trash2, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val addInteractionSource = remember { MutableInteractionSource() }
        val isAddPressed by addInteractionSource.collectIsPressedAsState()
        val addScale by animateFloatAsState(
            targetValue = if (isAddPressed) 0.96f else 1f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
            label = "sidebarAddScale"
        )

        Button(
            onClick = onAddAccount,
            interactionSource = addInteractionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .graphicsLayer {
                    scaleX = addScale
                    scaleY = addScale
                },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_account), fontWeight = FontWeight.Bold)
        }
    }
}

// ── LoginScreen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    canCancel: Boolean = false,
    onCancel: () -> Unit = {}
) {
    val operators = listOf("Maroc Telecom", "Orange", "Inwi")
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    var showPrivacyDialog by remember { mutableStateOf(false) }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = {
                Icon(
                    Lucide.ShieldCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    stringResource(R.string.privacy_security),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Lucide.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.local_storage_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.local_storage_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Lucide.Globe, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.no_intermediary_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.no_intermediary_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Lucide.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.openSource_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.openSource_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(stringResource(R.string.done), fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (canCancel) stringResource(R.string.add_account_title) else stringResource(R.string.welcome),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { showPrivacyDialog = true },
                        modifier = Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
                            .bounceClick()
                    ) {
                        Icon(
                            Lucide.Info,
                            contentDescription = stringResource(R.string.privacy_security),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (canCancel) {
                    TextButton(onClick = onCancel, modifier = Modifier.bounceClick()) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Text(stringResource(R.string.operator), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExpressiveOperatorButtonGroup(
                operators = operators,
                selectedOperator = viewModel.operator,
                onOperatorSelected = { viewModel.operator = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                val emailLabel = if (viewModel.operator == "Maroc Telecom") stringResource(R.string.email) else stringResource(R.string.email_or_phone)

                OutlinedTextField(
                    value = viewModel.email,
                    onValueChange = { viewModel.email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(emailLabel) },
                    leadingIcon = {
                        Icon(if (viewModel.operator == "Maroc Telecom") Lucide.Mail else Lucide.Globe, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (viewModel.operator == "Maroc Telecom") KeyboardType.Email else KeyboardType.Text,
                        imeAction = if (viewModel.operator == "Maroc Telecom") ImeAction.Next else ImeAction.Done
                    )
                )

                AnimatedVisibility(
                    visible = viewModel.operator == "Maroc Telecom",
                    enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(),
                    exit = shrinkVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = viewModel.phone,
                            onValueChange = { viewModel.phone = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.phone)) },
                            leadingIcon = { Icon(Lucide.Phone, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = { Icon(Lucide.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Lucide.EyeOff else Lucide.Eye, contentDescription = null)
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        viewModel.login()
                    })
                )
            }

            viewModel.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // M3 Expressive Shape-Morphing Login Button
            ExpressiveShapeMorphButton(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.login()
                },
                isLoading = viewModel.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                AnimatedContent(
                    targetState = viewModel.isLoading,
                    transitionSpec = {
                        (fadeIn(spring(dampingRatio = 0.8f, stiffness = 350f)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 350f)))
                            .togetherWith(fadeOut(spring(dampingRatio = 0.8f, stiffness = 350f)) + scaleOut(spring(dampingRatio = 0.8f, stiffness = 350f)))
                    },
                    label = "btnLoadingAnim"
                ) { loading ->
                    if (loading) {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val thinStroke = remember(density) {
                            androidx.compose.ui.graphics.drawscope.Stroke(
                                width = with(density) { 2.dp.toPx() },
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                            stroke = thinStroke,
                            trackStroke = thinStroke
                        )
                    } else {
                        Text(
                            text = if (canCancel) stringResource(R.string.connect_account) else stringResource(R.string.log_in),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

// ── Expressive Shape-Morphing Button Component ────────────────────────────────

@Composable
fun ExpressiveShapeMorphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    defaultCorner: androidx.compose.ui.unit.Dp = 16.dp,
    pressedCorner: androidx.compose.ui.unit.Dp = 28.dp,
    loadingCorner: androidx.compose.ui.unit.Dp = 28.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetCorner = when {
        isLoading -> loadingCorner
        isPressed -> pressedCorner
        else -> defaultCorner
    }

    val animatedCorner by animateDpAsState(
        targetValue = targetCorner,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "m3eShapeMorphCorner"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 450f),
        label = "m3eShapeMorphScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled && !isLoading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(animatedCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        content = content
    )
}

// ── Expressive Shape-Morphing Operator ButtonGroup (M3E Connected Expansion) ──

@Composable
fun ExpressiveOperatorButtonGroup(
    operators: List<String>,
    selectedOperator: String,
    onOperatorSelected: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        operators.forEachIndexed { index, op ->
            val isSelected = op == selectedOperator
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            // Subtle animated weight expansion on selection
            val animatedWeight by animateFloatAsState(
                targetValue = if (isSelected) 1.25f else 1.0f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
                label = "btnGroup_weight_$op"
            )

            // Shape morphing: Selected becomes a full standalone pill (24dp), unselected takes connected corners
            val cornerStart by animateDpAsState(
                targetValue = if (isSelected) 24.dp else if (index == 0) 20.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
                label = "cornerStart_$op"
            )
            val cornerEnd by animateDpAsState(
                targetValue = if (isSelected) 24.dp else if (index == operators.lastIndex) 20.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
                label = "cornerEnd_$op"
            )

            val animatedBgColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else if (isPressed) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
                label = "btnGroup_bg_$op"
            )

            val animatedContentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f),
                label = "btnGroup_content_$op"
            )

            Surface(
                onClick = {
                    if (!isSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOperatorSelected(op)
                    }
                },
                interactionSource = interactionSource,
                shape = RoundedCornerShape(
                    topStart = cornerStart,
                    bottomStart = cornerStart,
                    topEnd = cornerEnd,
                    bottomEnd = cornerEnd
                ),
                color = animatedBgColor,
                contentColor = animatedContentColor,
                modifier = Modifier
                    .weight(animatedWeight)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = op,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ── DashboardScreen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    data: ConsumptionData,
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showDisconnectDialog by remember { mutableStateOf(false) }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            icon = { Icon(Lucide.LogOut, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.delete_account_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.delete_account_confirm, data.operator, data.phoneNumber))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.logout()
                    }
                ) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    val pullToRefreshState = rememberPullToRefreshState()

    // ── M3 Expressive Rubber-Band Elastic Drag Physics ───────────────────────
    val targetPullOffset = remember(pullToRefreshState.distanceFraction, viewModel.isRefreshing) {
        if (viewModel.isRefreshing) {
            72.dp
        } else {
            val fraction = pullToRefreshState.distanceFraction
            if (fraction <= 0f) {
                0.dp
            } else {
                // Exponential resistance curve (rubber-banding)
                val maxDrag = 110f
                val damped = maxDrag * (1f - kotlin.math.exp(-fraction * 0.7f))
                damped.dp
            }
        }
    }

    val animatedContentOffset by animateDpAsState(
        targetValue = targetPullOffset,
        animationSpec = spring(
            dampingRatio = 0.75f, // M3 Medium Bouncy damping
            stiffness = 380f      // Responsive tactile recoil
        ),
        label = "m3ExpressivePullOffset"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.bounceClick()) {
                        Icon(Lucide.Menu, contentDescription = stringResource(R.string.accounts_section))
                    }
                },
                title = { Text(data.operator, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(context, WidgetConfigActivity::class.java))
                    }, modifier = Modifier.bounceClick()) {
                        Icon(Lucide.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    IconButton(
                        onClick = { viewModel.login(manualRefresh = true) },
                        enabled = !viewModel.isRefreshing,
                        modifier = Modifier.bounceClick()
                    ) {
                        if (viewModel.isRefreshing) {
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val thinStroke = remember(density) {
                                androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = with(density) { 1.75.dp.toPx() },
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                stroke = thinStroke,
                                trackStroke = thinStroke
                            )
                        } else {
                            Icon(Lucide.RefreshCw, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                    IconButton(onClick = { showDisconnectDialog = true }, modifier = Modifier.bounceClick()) {
                        Icon(Lucide.LogOut, contentDescription = stringResource(R.string.remove))
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = viewModel.isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.login(manualRefresh = true)
            },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            indicator = {
                val fraction = pullToRefreshState.distanceFraction
                val isVisible = viewModel.isRefreshing || fraction > 0.05f

                val indicatorScale by animateFloatAsState(
                    targetValue = if (isVisible) (fraction.coerceIn(0.4f, 1f)) else 0f,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
                    label = "indicatorScale"
                )

                val indicatorTranslationY by animateDpAsState(
                    targetValue = if (viewModel.isRefreshing) 20.dp else (fraction * 28f).coerceAtMost(36f).dp,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                    label = "indicatorTranslationY"
                )

                if (isVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                translationY = indicatorTranslationY.toPx()
                                scaleX = indicatorScale
                                scaleY = indicatorScale
                                alpha = if (viewModel.isRefreshing) 1f else (fraction * 1.5f).coerceIn(0f, 1f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Official Standalone Morphing Shape Indicator
                        LoadingIndicator(
                            modifier = Modifier.size(42.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val sleekWavyStroke = remember(density) {
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = with(density) { 3.5.dp.toPx() },
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = animatedContentOffset.toPx()
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Phone Number Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(data.operator, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(data.phoneNumber, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }

                // Live Status Notification Toggle Card
                val activeAccount = viewModel.savedAccounts.find { it.id == viewModel.activeAccountId } ?: viewModel.savedAccounts.firstOrNull()
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted && activeAccount != null) {
                        viewModel.toggleLiveNotification(activeAccount.id, true)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Lucide.BellRing,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    stringResource(R.string.live_status_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.live_status_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val isEnabled = activeAccount?.liveNotificationEnabled == true
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        return@Switch
                                    }
                                }
                                if (activeAccount != null) {
                                    viewModel.toggleLiveNotification(activeAccount.id, checked)
                                }
                            }
                        )
                    }
                }

                // Internet Card with Animated Progress Bar
                val internetDisplay = if (data.internetPercent != null) "${data.internetRemaining} (${data.internetPercent.toInt()}%)" else data.internetRemaining
                val internetProgress = remember(data.internetRemaining, data.internetPercent) {
                    if (data.internetPercent != null) {
                        (data.internetPercent / 100f).coerceIn(0f, 1f)
                    } else {
                        val trimmed = data.internetRemaining.trim()
                        if (trimmed.equals("N/A", ignoreCase = true) || trimmed.startsWith("0 ") || trimmed == "0" || trimmed.isEmpty()) {
                            0f
                        } else {
                            val m = Regex("""(\d+(?:\.\d+)?)\s*(Go|Mo|MB|GB)""", RegexOption.IGNORE_CASE).find(data.internetRemaining)
                            if (m != null) {
                                val (num, unit) = m.destructured
                                val v = num.toFloatOrNull() ?: 0f
                                if (unit.uppercase().startsWith("G")) (v / 30f).coerceIn(0f, 1f) else (v / 1024f).coerceIn(0f, 1f)
                            } else 0f
                        }
                    }
                }
                val animatedInternet by animateFloatAsState(
                    targetValue = internetProgress,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f),
                    label = "internetProgressAnim"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Lucide.Globe, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.internet), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        AnimatedRollingText(
                            text = internetDisplay,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        LinearWavyProgressIndicator(
                            progress = { animatedInternet },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            stroke = sleekWavyStroke,
                            trackStroke = sleekWavyStroke,
                            amplitude = { 0.2f },
                            wavelength = 18.dp,
                            waveSpeed = 4.dp
                        )
                    }
                }

                // Calls Card with Animated Progress Bar
                val callsDisplay = if (data.callsPercent != null) "${data.callsRemaining} (${data.callsPercent.toInt()}%)" else data.callsRemaining
                val callsProgress = remember(data.callsRemaining, data.callsPercent) {
                    if (data.callsPercent != null) {
                        (data.callsPercent / 100f).coerceIn(0f, 1f)
                    } else {
                        val trimmed = data.callsRemaining.trim()
                        if (trimmed.equals("N/A", ignoreCase = true) || trimmed.startsWith("0 ") || trimmed == "0" || trimmed.isEmpty()) {
                            0f
                        } else {
                            val m = Regex("""(\d+)\s*h\s*(\d+)\s*min""", RegexOption.IGNORE_CASE).find(data.callsRemaining)
                            if (m != null) {
                                val (h, min) = m.destructured
                                val totalMin = (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)
                                (totalMin / 300f).coerceIn(0f, 1f)
                            } else {
                                val minOnly = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(data.callsRemaining)
                                if (minOnly != null) {
                                    val mVal = minOnly.groupValues[1].toIntOrNull() ?: 0
                                    (mVal / 300f).coerceIn(0f, 1f)
                                } else 0f
                            }
                        }
                    }
                }
                val animatedCalls by animateFloatAsState(
                    targetValue = callsProgress,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f),
                    label = "callsProgressAnim"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Lucide.Phone, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.calls), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        AnimatedRollingText(
                            text = callsDisplay,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        LinearWavyProgressIndicator(
                            progress = { animatedCalls },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            stroke = sleekWavyStroke,
                            trackStroke = sleekWavyStroke,
                            amplitude = { 0.2f },
                            wavelength = 18.dp,
                            waveSpeed = 4.dp
                        )
                    }
                }

                // Structured Details Breakdown
                if (!data.structuredDetails.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.plan_details),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            data.structuredDetails.forEach { detail ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(detail.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(detail.value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
