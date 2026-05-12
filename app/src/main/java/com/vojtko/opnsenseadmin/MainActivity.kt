package com.vojtko.opnsenseadmin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vojtko.opnsenseadmin.ui.theme.AlertRed
import com.vojtko.opnsenseadmin.ui.theme.Cloud
import com.vojtko.opnsenseadmin.ui.theme.HarborBlue
import com.vojtko.opnsenseadmin.ui.theme.MistSoft
import com.vojtko.opnsenseadmin.ui.theme.OPNSenseAdminTheme
import com.vojtko.opnsenseadmin.ui.theme.Seafoam
import com.vojtko.opnsenseadmin.ui.theme.SignalOrange
import com.vojtko.opnsenseadmin.ui.theme.SignalOrangeSoft
import com.vojtko.opnsenseadmin.ui.theme.SlateNight
import com.vojtko.opnsenseadmin.ui.theme.SlatePanel
import com.vojtko.opnsenseadmin.ui.theme.SlatePanelAlt
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import javax.net.ssl.SSLException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OPNSenseAdminTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OPNSenseAdminApp()
                }
            }
        }
    }
}

private enum class AppSection(val title: String, val badge: String) {
    Dashboard("Dashboard", "Live"),
    Firewall("Firewall", "Rules"),
    Interfaces("Interfaces", "Ports"),
    Services("Services", "VPN"),
    Updates("Updates", "Core"),
    Notifications("Notifications", "Alerts")
}

private val drawerSections = listOf(
    AppSection.Dashboard,
    AppSection.Firewall,
    AppSection.Interfaces,
    AppSection.Services,
    AppSection.Updates
)

private sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Ready(val snapshot: DashboardSnapshot) : DashboardUiState
    data class Failure(val message: String) : DashboardUiState
}

private sealed interface ServicesUiState {
    data object Loading : ServicesUiState
    data class Ready(val services: List<ServiceEntry>) : ServicesUiState
    data class Failure(val message: String) : ServicesUiState
}

private sealed interface FirewallUiState {
    data object Loading : FirewallUiState
    data class Ready(val rules: List<FirewallRuleEntry>) : FirewallUiState
    data class Failure(val message: String) : FirewallUiState
}

private sealed interface InterfacesUiState {
    data object Loading : InterfacesUiState
    data class Ready(val interfaces: List<InterfaceEntry>) : InterfacesUiState
    data class Failure(val message: String) : InterfacesUiState
}

private sealed interface UpdatesUiState {
    data object Loading : UpdatesUiState
    data class Ready(val status: FirmwareStatus) : UpdatesUiState
    data class Failure(val message: String) : UpdatesUiState
}

private sealed interface NotificationsUiState {
    data object Loading : NotificationsUiState
    data class Ready(val notifications: List<SystemNotification>) : NotificationsUiState
    data class Failure(val message: String) : NotificationsUiState
}

private sealed interface NotificationIndicatorState {
    data object Loading : NotificationIndicatorState
    data class Ready(val hasNotifications: Boolean) : NotificationIndicatorState
    data object Failure : NotificationIndicatorState
}

private const val DASHBOARD_REFRESH_INTERVAL_MS = 5_000L
private const val NOTIFICATION_REFRESH_INTERVAL_MS = 30_000L

@Composable
fun OPNSenseAdminApp() {
    val context = LocalContext.current
    val sessionStore = remember(context) { SessionStore(context) }
    val savedSession = remember { sessionStore.load() }

    var endpoint by rememberSaveable { mutableStateOf(savedSession.endpoint) }
    var apiKey by rememberSaveable { mutableStateOf(savedSession.apiKey) }
    var apiSecret by rememberSaveable { mutableStateOf(savedSession.apiSecret) }
    var rememberDevice by rememberSaveable { mutableStateOf(savedSession.rememberDevice) }
    var ignoreInvalidSsl by rememberSaveable { mutableStateOf(savedSession.ignoreInvalidSsl) }
    var isLoggedIn by rememberSaveable {
        mutableStateOf(
            savedSession.rememberDevice &&
                savedSession.endpoint.isNotBlank() &&
                savedSession.apiKey.isNotBlank() &&
                savedSession.apiSecret.isNotBlank()
        )
    }
    var selectedSection by rememberSaveable { mutableStateOf(AppSection.Dashboard) }

    if (isLoggedIn) {
        AdminShell(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl,
            selectedSection = selectedSection,
            onSectionSelected = { selectedSection = it },
            onLogout = { isLoggedIn = false }
        )
    } else {
        LoginScreen(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            rememberDevice = rememberDevice,
            ignoreInvalidSsl = ignoreInvalidSsl,
            onEndpointChange = { endpoint = it },
            onApiKeyChange = { apiKey = it },
            onApiSecretChange = { apiSecret = it },
            onRememberDeviceChange = { rememberDevice = it },
            onIgnoreInvalidSslChange = { ignoreInvalidSsl = it },
            onLogin = {
                if (endpoint.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank()) {
                    if (rememberDevice) {
                        sessionStore.save(
                            SavedSession(
                                endpoint = endpoint,
                                apiKey = apiKey,
                                apiSecret = apiSecret,
                                rememberDevice = true,
                                ignoreInvalidSsl = ignoreInvalidSsl
                            )
                        )
                    } else {
                        sessionStore.clear()
                    }
                    isLoggedIn = true
                }
            }
        )
    }
}

@Composable
private fun LoginScreen(
    endpoint: String,
    apiKey: String,
    apiSecret: String,
    rememberDevice: Boolean,
    ignoreInvalidSsl: Boolean,
    onEndpointChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onApiSecretChange: (String) -> Unit,
    onRememberDeviceChange: (Boolean) -> Unit,
    onIgnoreInvalidSslChange: (Boolean) -> Unit,
    onLogin: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SlateNight, SlatePanel, HarborBlue)
                )
            )
            .navigationBarsPadding()
    ) {
        val minCardHeight = maxHeight - 40.dp
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SlatePanel.copy(alpha = 0.94f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .heightIn(min = minCardHeight),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    StatusChip(label = "OPNsense Remote Admin", accent = SignalOrange)
                    Text(
                        text = "Connect to your firewall",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Cloud
                    )
                    Text(
                        text = "Use the router endpoint plus OPNsense API key and secret. Live metrics use HTTPS and Basic auth, so a trusted certificate on the router matters for Android clients.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MistSoft
                    )
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = onEndpointChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Router URL") },
                        supportingText = { Text("Example: https://fw.office.example") },
                        shape = RoundedCornerShape(18.dp)
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key") },
                        supportingText = { Text("OPNsense uses the API key as the Basic auth username") },
                        shape = RoundedCornerShape(18.dp)
                    )
                    OutlinedTextField(
                        value = apiSecret,
                        onValueChange = onApiSecretChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API secret") },
                        supportingText = { Text("Used as the Basic auth password for diagnostics requests") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(18.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trusted device",
                                style = MaterialTheme.typography.titleMedium,
                                color = Cloud
                            )
                            Text(
                                text = "Save credentials locally and reopen directly into the dashboard.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MistSoft
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(checked = rememberDevice, onCheckedChange = onRememberDeviceChange)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ignore invalid SSL",
                                style = MaterialTheme.typography.titleMedium,
                                color = Cloud
                            )
                            Text(
                                text = "Accept self-signed or mismatched certificates for this router.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MistSoft
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(checked = ignoreInvalidSsl, onCheckedChange = onIgnoreInvalidSslChange)
                    }
                    Button(
                        onClick = onLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalOrange,
                            contentColor = SlateNight
                        )
                    ) {
                        Text("Open dashboard", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        text = "Ignoring invalid SSL disables certificate and hostname verification. Use it only for routers you control.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MistSoft
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminShell(
    endpoint: String,
    apiKey: String,
    apiSecret: String,
    ignoreInvalidSsl: Boolean,
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    onLogout: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val notificationIndicator by produceState<NotificationIndicatorState>(
        initialValue = NotificationIndicatorState.Loading,
        endpoint,
        apiKey,
        apiSecret,
        ignoreInvalidSsl
    ) {
        suspend fun loadNotificationState() {
            value = runCatching {
                OpnSenseRepository.fetchSystemNotifications(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )
            }.fold(
                onSuccess = { notifications ->
                    NotificationIndicatorState.Ready(notifications.isNotEmpty())
                },
                onFailure = {
                    NotificationIndicatorState.Failure
                }
            )
        }

        loadNotificationState()
        while (true) {
            delay(NOTIFICATION_REFRESH_INTERVAL_MS)
            loadNotificationState()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    endpoint = endpoint,
                    selectedSection = selectedSection,
                    onSectionSelected = { section ->
                        onSectionSelected(section)
                        scope.launch { drawerState.close() }
                    },
                    onLogout = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ShellTopBar(
                    title = selectedSection.title,
                    badge = selectedSection.badge,
                    notificationState = notificationIndicator,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNotificationsClick = { onSectionSelected(AppSection.Notifications) }
                )
            }
        ) { scaffoldPadding ->
            val contentModifier = Modifier.padding(scaffoldPadding)
            when (selectedSection) {
                AppSection.Dashboard -> DashboardScreen(
                    modifier = contentModifier,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl,
                    onLogout = onLogout
                )

                AppSection.Services -> ServicesScreen(
                    modifier = contentModifier,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )

                AppSection.Firewall -> FirewallScreen(
                    modifier = contentModifier,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )

                AppSection.Interfaces -> InterfacesScreen(
                    modifier = contentModifier,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )

            AppSection.Updates -> UpdatesScreen(
                modifier = contentModifier,
                endpoint = endpoint,
                apiKey = apiKey,
                apiSecret = apiSecret,
                ignoreInvalidSsl = ignoreInvalidSsl
            )

            AppSection.Notifications -> NotificationsScreen(
                modifier = contentModifier,
                endpoint = endpoint,
                apiKey = apiKey,
                apiSecret = apiSecret,
                ignoreInvalidSsl = ignoreInvalidSsl
            )
        }
    }
}
}

@Composable
private fun DashboardScreen(
    modifier: Modifier = Modifier,
    endpoint: String,
    apiKey: String,
    apiSecret: String,
    ignoreInvalidSsl: Boolean,
    onLogout: () -> Unit
) {
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var liveCpuRatio by remember { mutableStateOf<Float?>(null) }
    val uiState by produceState<DashboardUiState>(
        initialValue = DashboardUiState.Loading,
        endpoint,
        apiKey,
        apiSecret,
        ignoreInvalidSsl,
        refreshToken
    ) {
        suspend fun loadDashboard() {
            if (value !is DashboardUiState.Ready) {
                value = DashboardUiState.Loading
            }
            value = try {
                DashboardUiState.Ready(
                    OpnSenseRepository.fetchDashboard(
                        endpoint = endpoint,
                        apiKey = apiKey,
                        apiSecret = apiSecret,
                        ignoreInvalidSsl = ignoreInvalidSsl
                    )
                )
            } catch (exception: Exception) {
                DashboardUiState.Failure(exception.toDashboardMessage())
            }
        }

        loadDashboard()
        while (true) {
            delay(DASHBOARD_REFRESH_INTERVAL_MS)
            loadDashboard()
        }
    }

    val snapshot = (uiState as? DashboardUiState.Ready)?.snapshot
    val displayedSnapshot = snapshot?.let { readySnapshot ->
        liveCpuRatio?.let { cpuRatio ->
            readySnapshot.copy(
                cpuCurrentRatio = cpuRatio,
                stats = readySnapshot.stats.map { stat ->
                    if (stat.title == "CPU load") {
                        stat.copy(
                            value = formatPercentForUi(cpuRatio),
                            usage = cpuRatio
                        )
                    } else {
                        stat
                    }
                }
            )
        } ?: readySnapshot
    }

    LaunchedEffect(endpoint, apiKey, apiSecret, ignoreInvalidSsl) {
        liveCpuRatio = null
        runCatching {
            OpnSenseRepository.streamCpuUsage(
                endpoint = endpoint,
                apiKey = apiKey,
                apiSecret = apiSecret,
                ignoreInvalidSsl = ignoreInvalidSsl
            ) { sample ->
                liveCpuRatio = sample
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            DashboardHero(
                endpoint = endpoint,
                snapshot = displayedSnapshot,
                isLoading = uiState is DashboardUiState.Loading,
                onRefresh = { refreshToken++ },
                onLogout = onLogout
            )
        }
        when (val state = uiState) {
            DashboardUiState.Loading -> {
                item {
                    StateCard(
                        title = "Fetching diagnostics",
                        message = "Connecting to the OPNsense API and collecting live resource metrics."
                    )
                }
            }

            is DashboardUiState.Failure -> {
                item {
                    StateCard(
                        title = "Connection failed",
                        message = state.message
                    )
                }
            }

            is DashboardUiState.Ready -> {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(state.snapshot.stats) { index, stat ->
                            val displayedStat = if (index == 0 && displayedSnapshot != null) {
                                displayedSnapshot.stats[index]
                            } else {
                                stat
                            }
                            StatCard(stat = displayedStat, accent = statAccent(index))
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardChartCard(
                            title = "CPU history",
                            subtitle = state.snapshot.stats.firstOrNull()?.detail ?: "System health",
                            points = state.snapshot.cpuHistory,
                            yAxisLabels = listOf("100%", "75%", "50%", "25%", "0%"),
                            accent = HarborBlue,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DashboardChartCard(
                            title = "Traffic history",
                            subtitle = state.snapshot.trafficLabel,
                            points = state.snapshot.trafficHistory,
                            yAxisLabels = listOf("Peak", "75%", "50%", "25%", "0%"),
                            accent = SignalOrange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    InterfaceStatsCard(state.snapshot.interfaceStats)
                }
            }
        }
    }
}

@Composable
private fun ShellTopBar(
    title: String,
    badge: String,
    notificationState: NotificationIndicatorState,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Open menu"
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
        IconButton(onClick = onNotificationsClick) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Open notifications",
                tint = when (notificationState) {
                    is NotificationIndicatorState.Ready ->
                        if (notificationState.hasNotifications) AlertRed else HarborBlue
                    NotificationIndicatorState.Loading -> MaterialTheme.colorScheme.onSurfaceVariant
                    NotificationIndicatorState.Failure -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun DrawerContent(
    endpoint: String,
    selectedSection: AppSection,
    onSectionSelected: (AppSection) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("OPNsense Admin", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = endpoint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        drawerSections.forEach { section ->
            NavigationDrawerItem(
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = if (selectedSection == section) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                    },
                                    shape = CircleShape
                                )
                        )
                        Column {
                            Text(section.title)
                            Text(
                                text = section.badge,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                    }
                },
                selected = selectedSection == section,
                onClick = { onSectionSelected(section) }
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Log out")
        }
    }
}

@Composable
private fun ServicesScreen(
    modifier: Modifier = Modifier,
    endpoint: String,
    apiKey: String,
    apiSecret: String,
    ignoreInvalidSsl: Boolean
) {
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<PendingServiceAction?>(null) }
    val uiState by produceState<ServicesUiState>(
        initialValue = ServicesUiState.Loading,
        endpoint,
        apiKey,
        apiSecret,
        ignoreInvalidSsl,
        refreshToken
    ) {
        value = ServicesUiState.Loading
        value = try {
            ServicesUiState.Ready(
                OpnSenseRepository.fetchServices(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )
            )
        } catch (exception: Exception) {
            ServicesUiState.Failure(exception.toDashboardMessage())
        }
    }

    LaunchedEffect(pendingAction) {
        val request = pendingAction ?: return@LaunchedEffect
        val error = runCatching {
            OpnSenseRepository.controlService(
                endpoint = endpoint,
                apiKey = apiKey,
                apiSecret = apiSecret,
                ignoreInvalidSsl = ignoreInvalidSsl,
                name = request.service.name,
                id = request.service.serviceId,
                action = request.action
            )
        }.exceptionOrNull()?.let { throwable ->
            (throwable as? Exception)?.toDashboardMessage() ?: (throwable.message ?: "Service action failed.")
        }
        actionMessage = if (error == null) {
            "${request.service.name} ${request.action.command} requested."
        } else {
            "${request.service.name}: $error"
        }
        pendingAction = null
        refreshToken++
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ServicesHeader(
                endpoint = endpoint,
                actionMessage = actionMessage,
                onRefresh = { refreshToken++ }
            )
        }
        when (val state = uiState) {
            ServicesUiState.Loading -> {
                item {
                    StateCard(
                        title = "Loading services",
                        message = "Fetching the live OPNsense service inventory and current daemon status."
                    )
                }
            }

            is ServicesUiState.Failure -> {
                item {
                    StateCard(
                        title = "Service API unavailable",
                        message = state.message
                    )
                }
            }

            is ServicesUiState.Ready -> {
                if (state.services.isEmpty()) {
                    item {
                        StateCard(
                            title = "No services returned",
                            message = "The router responded, but it did not expose any services in `/api/core/service/search` for this API user."
                        )
                    }
                } else {
                    itemsIndexed(state.services) { _, service ->
                        val busy = pendingAction?.service?.let(::serviceActionKey) == serviceActionKey(service)
                        ServiceCard(
                            service = service,
                            busy = busy,
                            onAction = { action ->
                                actionMessage = "${action.command.replaceFirstChar(Char::uppercase)} ${service.name}..."
                                pendingAction = PendingServiceAction(service = service, action = action)
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class PendingServiceAction(
    val service: ServiceEntry,
    val action: ServiceAction
)

@Composable
private fun FirewallScreen(
    modifier: Modifier = Modifier,
    endpoint: String,
    apiKey: String,
    apiSecret: String,
    ignoreInvalidSsl: Boolean
) {
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var pendingToggle by remember { mutableStateOf<PendingFirewallToggle?>(null) }
    val uiState by produceState<FirewallUiState>(
        initialValue = FirewallUiState.Loading,
        endpoint,
        apiKey,
        apiSecret,
        ignoreInvalidSsl,
        refreshToken
    ) {
        value = FirewallUiState.Loading
        value = try {
            FirewallUiState.Ready(
                OpnSenseRepository.fetchFirewallRules(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )
            )
        } catch (exception: Exception) {
            FirewallUiState.Failure(exception.toDashboardMessage())
        }
    }

    LaunchedEffect(pendingToggle) {
        val request = pendingToggle ?: return@LaunchedEffect
        val error = runCatching {
            OpnSenseRepository.toggleFirewallRule(
                endpoint = endpoint,
                apiKey = apiKey,
                apiSecret = apiSecret,
                ignoreInvalidSsl = ignoreInvalidSsl,
                uuid = request.rule.uuid,
                enabled = request.enabled
            )
        }.exceptionOrNull()?.let { throwable ->
            (throwable as? Exception)?.toDashboardMessage() ?: (throwable.message ?: "Firewall action failed.")
        }
        actionMessage = if (error == null) {
            "${request.rule.description} ${if (request.enabled) "enabled" else "disabled"}."
        } else {
            "${request.rule.description}: $error"
        }
        pendingToggle = null
        refreshToken++
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FirewallHeader(
                endpoint = endpoint,
                actionMessage = actionMessage,
                onRefresh = { refreshToken++ }
            )
        }
        when (val state = uiState) {
            FirewallUiState.Loading -> {
                item {
                    StateCard(
                        title = "Loading firewall rules",
                        message = "Fetching the OPNsense automation firewall rule set."
                    )
                }
            }

            is FirewallUiState.Failure -> {
                item {
                    StateCard(
                        title = "Firewall API unavailable",
                        message = state.message
                    )
                }
            }

            is FirewallUiState.Ready -> {
                if (state.rules.isEmpty()) {
                    item {
                        StateCard(
                            title = "No firewall rules returned",
                            message = "This API only exposes rules managed by Firewall Automation / Rules [new]. Classic firewall rules will not appear here."
                        )
                    }
                } else {
                    itemsIndexed(state.rules) { _, rule ->
                        FirewallRuleCard(
                            rule = rule,
                            busy = pendingToggle?.rule?.uuid == rule.uuid,
                            onToggle = { enabled ->
                                actionMessage = "${if (enabled) "Enabling" else "Disabling"} ${rule.description}..."
                                pendingToggle = PendingFirewallToggle(rule, enabled)
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class PendingFirewallToggle(
    val rule: FirewallRuleEntry,
    val enabled: Boolean
)

private enum class UpdateAction {
    Check,
    Install
}

private data class PendingUpdateAction(val action: UpdateAction)
private data class PendingNotificationDismiss(val subject: String)

@Composable
private fun InterfacesScreen(
    modifier: Modifier = Modifier,
    endpoint: String,
    apiKey: String,
    apiSecret: String,
    ignoreInvalidSsl: Boolean
) {
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var pendingReloadIdentifier by remember { mutableStateOf<String?>(null) }
    val uiState by produceState<InterfacesUiState>(
        initialValue = InterfacesUiState.Loading,
        endpoint,
        apiKey,
        apiSecret,
        ignoreInvalidSsl,
        refreshToken
    ) {
        value = InterfacesUiState.Loading
        value = try {
            InterfacesUiState.Ready(
                OpnSenseRepository.fetchInterfaces(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )
            )
        } catch (exception: Exception) {
            InterfacesUiState.Failure(exception.toDashboardMessage())
        }
    }

    LaunchedEffect(pendingReloadIdentifier) {
        val identifier = pendingReloadIdentifier ?: return@LaunchedEffect
        val error = runCatching {
            OpnSenseRepository.reloadInterface(
                endpoint = endpoint,
                apiKey = apiKey,
                apiSecret = apiSecret,
                ignoreInvalidSsl = ignoreInvalidSsl,
                identifier = identifier
            )
        }.exceptionOrNull()?.let { throwable ->
            (throwable as? Exception)?.toDashboardMessage() ?: (throwable.message ?: "Interface reload failed.")
        }
        actionMessage = if (error == null) {
            "$identifier reload requested."
        } else {
            "$identifier: $error"
        }
        pendingReloadIdentifier = null
        refreshToken++
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            InterfacesHeader(
                endpoint = endpoint,
                actionMessage = actionMessage,
                onRefresh = { refreshToken++ }
            )
        }
        when (val state = uiState) {
            InterfacesUiState.Loading -> {
                item {
                    StateCard(
                        title = "Loading interfaces",
                        message = "Fetching interface details from the OPNsense overview API."
                    )
                }
            }

            is InterfacesUiState.Failure -> {
                item {
                    StateCard(
                        title = "Interfaces API unavailable",
                        message = state.message
                    )
                }
            }

            is InterfacesUiState.Ready -> {
                if (state.interfaces.isEmpty()) {
                    item {
                        StateCard(
                            title = "No interfaces returned",
                            message = "The router responded, but did not expose any interface rows in the overview export."
                        )
                    }
                } else {
                    itemsIndexed(state.interfaces) { _, item ->
                        InterfaceCard(
                            item = item,
                            busy = pendingReloadIdentifier == item.identifier,
                            onReload = {
                                actionMessage = "Reloading ${item.identifier}..."
                                pendingReloadIdentifier = item.identifier
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesScreen(
    modifier: Modifier = Modifier,
    endpoint: String,
    apiKey: String,
    apiSecret: String,
    ignoreInvalidSsl: Boolean
) {
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<PendingUpdateAction?>(null) }
    val uiState by produceState<UpdatesUiState>(
        initialValue = UpdatesUiState.Loading,
        endpoint,
        apiKey,
        apiSecret,
        ignoreInvalidSsl,
        refreshToken
    ) {
        value = UpdatesUiState.Loading
        value = try {
            UpdatesUiState.Ready(
                OpnSenseRepository.fetchFirmwareStatus(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )
            )
        } catch (exception: Exception) {
            UpdatesUiState.Failure(exception.toDashboardMessage())
        }
    }

    LaunchedEffect(pendingAction) {
        val request = pendingAction ?: return@LaunchedEffect
        val error = runCatching {
            when (request.action) {
                UpdateAction.Check -> OpnSenseRepository.checkForUpdates(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )
                UpdateAction.Install -> OpnSenseRepository.installUpdates(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )
            }
        }.exceptionOrNull()?.let { throwable ->
            (throwable as? Exception)?.toDashboardMessage() ?: (throwable.message ?: "Firmware action failed.")
        }
        actionMessage = if (error == null) {
            when (request.action) {
                UpdateAction.Check -> "Update check requested."
                UpdateAction.Install -> "System update requested."
            }
        } else {
            error
        }
        pendingAction = null
        refreshToken++
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            UpdatesHeader(
                endpoint = endpoint,
                actionMessage = actionMessage,
                onRefresh = { refreshToken++ }
            )
        }
        when (val state = uiState) {
            UpdatesUiState.Loading -> {
                item {
                    StateCard(
                        title = "Loading firmware status",
                        message = "Fetching current version, available updates, and upgrade activity from OPNsense."
                    )
                }
            }

            is UpdatesUiState.Failure -> {
                item {
                    StateCard(
                        title = "Firmware API unavailable",
                        message = state.message
                    )
                }
            }

            is UpdatesUiState.Ready -> {
                item {
                    FirmwareStatusCard(
                        status = state.status,
                        busy = pendingAction != null,
                        onCheck = {
                            actionMessage = "Checking for updates..."
                            pendingAction = PendingUpdateAction(UpdateAction.Check)
                        },
                        onInstall = {
                            actionMessage = "Requesting system update..."
                            pendingAction = PendingUpdateAction(UpdateAction.Install)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationsScreen(
    modifier: Modifier = Modifier,
    endpoint: String,
    apiKey: String,
    apiSecret: String,
    ignoreInvalidSsl: Boolean
) {
    var refreshToken by rememberSaveable { mutableIntStateOf(0) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var pendingDismiss by remember { mutableStateOf<PendingNotificationDismiss?>(null) }
    val uiState by produceState<NotificationsUiState>(
        initialValue = NotificationsUiState.Loading,
        endpoint,
        apiKey,
        apiSecret,
        ignoreInvalidSsl,
        refreshToken
    ) {
        value = NotificationsUiState.Loading
        value = try {
            NotificationsUiState.Ready(
                OpnSenseRepository.fetchSystemNotifications(
                    endpoint = endpoint,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    ignoreInvalidSsl = ignoreInvalidSsl
                )
            )
        } catch (exception: Exception) {
            NotificationsUiState.Failure(exception.toDashboardMessage())
        }
    }

    LaunchedEffect(pendingDismiss) {
        val request = pendingDismiss ?: return@LaunchedEffect
        val error = runCatching {
            OpnSenseRepository.dismissSystemNotification(
                endpoint = endpoint,
                apiKey = apiKey,
                apiSecret = apiSecret,
                ignoreInvalidSsl = ignoreInvalidSsl,
                subject = request.subject
            )
        }.exceptionOrNull()?.let { throwable ->
            (throwable as? Exception)?.toDashboardMessage() ?: (throwable.message ?: "Dismiss failed.")
        }
        actionMessage = error ?: "Notification dismissed."
        pendingDismiss = null
        refreshToken++
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            NotificationsHeader(
                endpoint = endpoint,
                actionMessage = actionMessage,
                onRefresh = { refreshToken++ }
            )
        }
        when (val state = uiState) {
            NotificationsUiState.Loading -> {
                item {
                    StateCard(
                        title = "Loading notifications",
                        message = "Fetching system status alerts from OPNsense."
                    )
                }
            }

            is NotificationsUiState.Failure -> {
                item {
                    StateCard(
                        title = "Notifications API unavailable",
                        message = state.message
                    )
                }
            }

            is NotificationsUiState.Ready -> {
                if (state.notifications.isEmpty()) {
                    item {
                        StateCard(
                            title = "No pending notifications",
                            message = "OPNsense reports no active system status messages right now."
                        )
                    }
                } else {
                    itemsIndexed(state.notifications) { _, notification ->
                        NotificationCard(
                            notification = notification,
                            busy = pendingDismiss?.subject == notification.subject,
                            onDismiss = { pendingDismiss = PendingNotificationDismiss(notification.subject) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHero(
    endpoint: String,
    snapshot: DashboardSnapshot?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(HarborBlue, SlatePanelAlt, SlatePanel)
                    )
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        label = if (isLoading) "Refreshing" else "Connected",
                        accent = if (isLoading) SignalOrangeSoft else Seafoam
                    )
                    Text(
                        text = snapshot?.systemName ?: "Welcome back",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Cloud
                    )
                    Text(
                        text = snapshot?.versionSummary?.ifBlank { endpoint } ?: endpoint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cloud.copy(alpha = 0.78f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onRefresh) {
                        Text("Refresh")
                    }
                    TextButton(onClick = onLogout) {
                        Text("Log out")
                    }
                }
            }
            if (!snapshot?.updateHint.isNullOrBlank()) {
                Text(
                    text = snapshot?.updateHint.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Cloud.copy(alpha = 0.74f)
                )
            }
            HorizontalDivider(color = Cloud.copy(alpha = 0.14f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroMetric(
                    label = "Load avg",
                    value = snapshot?.loadAverage ?: "...",
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    label = "Refreshed",
                    value = snapshot?.refreshedAt ?: "--:--",
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    label = "Uptime",
                    value = snapshot?.uptime ?: "...",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MistSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Cloud,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun StateCard(title: String, message: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun StatCard(stat: DashboardStat, accent: Color) {
    Card(
        modifier = Modifier.width(188.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stat.title, style = MaterialTheme.typography.titleMedium)
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(accent, CircleShape)
                )
            }
            Text(
                text = stat.value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            UsageBar(progress = stat.usage, color = accent)
            Text(
                text = stat.detail,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun UsageBar(progress: Float, color: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        drawRoundRect(
            color = color.copy(alpha = 0.14f),
            cornerRadius = CornerRadius(size.height / 2, size.height / 2)
        )
        drawRoundRect(
            color = color,
            size = Size(width = size.width * progress.coerceIn(0f, 1f), height = size.height),
            cornerRadius = CornerRadius(size.height / 2, size.height / 2)
        )
    }
}

@Composable
private fun DashboardChartCard(
    title: String,
    subtitle: String,
    points: List<DashboardPoint>,
    yAxisLabels: List<String>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            if (points.size >= 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    YAxisLabels(
                        labels = yAxisLabels,
                        modifier = Modifier.height(180.dp)
                    )
                    LineChart(
                        points = points,
                        accent = accent,
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    points.forEach { point ->
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    }
                }
            } else {
                Text(
                    text = "The router did not expose enough history to render this chart yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun YAxisLabels(
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(30.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LineChart(
    points: List<DashboardPoint>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val pointFillColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val spacing = size.width / (points.size - 1)
        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { index, point ->
            val x = spacing * index
            val y = size.height - (point.value.coerceIn(0f, 1f) * size.height)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        repeat(4) { index ->
            val y = size.height * (index / 3f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEachIndexed { index, point ->
            val x = spacing * index
            val y = size.height - (point.value.coerceIn(0f, 1f) * size.height)
            drawCircle(color = pointFillColor, radius = 5.dp.toPx(), center = Offset(x, y))
            drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
private fun InterfaceStatsCard(interfaces: List<InterfaceStat>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Interface statistics", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Live overview from the interfaces API. Values depend on what this OPNsense version exposes in the overview export endpoint.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
            if (interfaces.isEmpty()) {
                Text(
                    text = "No interface statistics were returned by the router.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            } else {
                interfaces.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = listOf(item.device, item.address).filter { it.isNotBlank() }.joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StatusChip(
                                label = item.statusLabel,
                                accent = if (item.statusLabel.contains("up", ignoreCase = true) ||
                                    item.statusLabel.contains("active", ignoreCase = true)
                                ) Seafoam else SignalOrange
                            )
                            Text(
                                text = "In ${item.inRate}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
                            )
                            Text(
                                text = "Out ${item.outRate}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServicesHeader(
    endpoint: String,
    actionMessage: String?,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Services", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = endpoint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                    )
                }
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
            Text(
                text = actionMessage ?: "Use the controls below to start, stop, or restart OPNsense services exposed by the core service API.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun FirewallHeader(
    endpoint: String,
    actionMessage: String?,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Firewall", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = endpoint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                    )
                }
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
            Text(
                text = actionMessage ?: "Rules below come from OPNsense Firewall Automation / Rules [new]. Toggling applies the config immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun InterfacesHeader(
    endpoint: String,
    actionMessage: String?,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Interfaces", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = endpoint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                    )
                }
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
            Text(
                text = actionMessage ?: "Live interface overview from OPNsense. Reload requests call the interface reconfigure endpoint for the selected identifier.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun UpdatesHeader(
    endpoint: String,
    actionMessage: String?,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Updates", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = endpoint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                    )
                }
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
            Text(
                text = actionMessage ?: "Use this section to trigger a firmware check and install available updates. Major upgrades remain higher-risk operations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun NotificationsHeader(
    endpoint: String,
    actionMessage: String?,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Notifications", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = endpoint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                    )
                }
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
            Text(
                text = actionMessage ?: "System status notifications mirror the alerts shown in the OPNsense web UI, including crash and subsystem warnings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notification: SystemNotification,
    busy: Boolean,
    onDismiss: () -> Unit
) {
    val accent = notificationAccent(notification.statusCode)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(if (busy) "Dismissing..." else "Dismiss")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(label = notification.age, accent = accent)
                if (notification.location.isNotBlank()) {
                    StatusChip(label = "Open in UI", accent = HarborBlue)
                }
            }
        }
    }
}

@Composable
private fun FirmwareStatusCard(
    status: FirmwareStatus,
    busy: Boolean,
    onCheck: () -> Unit,
    onInstall: () -> Unit
) {
    val showInstallButton = hasAvailableUpdates(status)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Firmware status", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Current ${status.productVersion} • Latest ${status.latestVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                    )
                }
                if (status.needsReboot) {
                    StatusChip(label = "Reboot required", accent = SignalOrange)
                }
            }
            UpdatesDetailRow("Status", status.statusMessage)
            UpdatesDetailRow("Available updates", status.updatesAvailable)
            UpdatesDetailRow("Download size", status.downloadSize)
            UpdatesDetailRow("Last check", status.lastCheck)
            UpdatesDetailRow("Upgrade activity", status.upgradeStatus)
            BoxWithConstraints {
                val stackButtons = maxWidth < 480.dp
                if (stackButtons) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onCheck,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (busy) "Working..." else "Check for updates")
                        }
                        if (showInstallButton) {
                            Button(
                                onClick = onInstall,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SignalOrange,
                                    contentColor = SlateNight
                                )
                            ) {
                                Text("Install updates")
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onCheck,
                            enabled = !busy,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (busy) "Working..." else "Check for updates")
                        }
                        if (showInstallButton) {
                            Button(
                                onClick = onInstall,
                                enabled = !busy,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SignalOrange,
                                    contentColor = SlateNight
                                )
                            ) {
                                Text("Install updates")
                            }
                        }
                    }
                }
            }
            Text(
                text = "This uses the standard firmware update path. Major release upgrades still deserve console or out-of-band access.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun UpdatesDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InterfaceCard(
    item: InterfaceEntry,
    busy: Boolean,
    onReload: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = listOf(item.name, item.device, item.linkType).filter { it.isNotBlank() && it != "-" }.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    onClick = onReload,
                    enabled = !busy
                ) {
                    Text(if (busy) "Reloading..." else "Reload")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(
                    label = item.statusLabel,
                    accent = if (item.statusLabel.contains("up", ignoreCase = true) ||
                        item.statusLabel.contains("active", ignoreCase = true) ||
                        item.statusLabel.contains("running", ignoreCase = true)
                    ) Seafoam else SignalOrange
                )
                StatusChip(
                    label = if (item.isEnabled) "Enabled" else "Disabled",
                    accent = if (item.isEnabled) HarborBlue else MistSoft
                )
            }
            InterfaceDetailRow(label = "IPv4", value = item.ipv4)
            InterfaceDetailRow(label = "IPv6", value = item.ipv6)
            InterfaceDetailRow(label = "Received", value = item.inRate)
            InterfaceDetailRow(label = "Transmitted", value = item.outRate)
        }
    }
}

@Composable
private fun InterfaceDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FirewallRuleCard(
    rule: FirewallRuleEntry,
    busy: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = rule.description,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${rule.interfaceName} • ${rule.protocol.uppercase()} • ${rule.action.uppercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = rule.isEnabled,
                    enabled = !busy,
                    onCheckedChange = onToggle
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(
                    label = if (rule.isEnabled) "Enabled" else "Disabled",
                    accent = if (rule.isEnabled) Seafoam else SignalOrange
                )
                StatusChip(
                    label = rule.action.lowercase().replaceFirstChar(Char::uppercase),
                    accent = when (rule.action.lowercase()) {
                        "pass" -> HarborBlue
                        "block", "reject" -> AlertRed
                        else -> MistSoft
                    }
                )
            }
            FirewallEndpointRow(label = "Source", value = rule.source)
            FirewallEndpointRow(label = "Destination", value = rule.destination)
            if (busy) {
                Text(
                    text = "Applying change...",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun FirewallEndpointRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ServiceCard(
    service: ServiceEntry,
    busy: Boolean,
    onAction: (ServiceAction) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = service.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = service.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                StatusChip(
                    label = if (service.isRunning) "Running" else "Stopped",
                    accent = if (service.isRunning) Seafoam else AlertRed
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(
                    label = service.statusLabel,
                    accent = if (service.isRunning) HarborBlue else SignalOrange
                )
                if (service.isLocked) {
                    StatusChip(
                        label = "Locked",
                        accent = AlertRed
                    )
                }
                service.isEnabled?.let { enabled ->
                    StatusChip(
                        label = if (enabled) "Enabled" else "Disabled",
                        accent = if (enabled) Seafoam else MistSoft
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServiceActionButton(
                    contentDescription = "Start ${service.name}",
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                    },
                    enabled = !busy && !service.isRunning && !service.isLocked,
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(ServiceAction.Start) }
                )
                ServiceActionButton(
                    contentDescription = "Stop ${service.name}",
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null
                        )
                    },
                    enabled = !busy && service.isRunning && !service.isLocked,
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(ServiceAction.Stop) }
                )
                ServiceActionButton(
                    contentDescription = if (busy) "Working on ${service.name}" else "Restart ${service.name}",
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null
                        )
                    },
                    enabled = !busy && !service.isLocked,
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(ServiceAction.Restart) }
                )
            }
            if (service.isLocked) {
                Text(
                    text = "This service is reported as locked by OPNsense and may not accept start/stop actions through the generic core service API.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun ServiceActionButton(
    contentDescription: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .background(
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                },
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}

@Composable
private fun ActionPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun StatusChip(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent, CircleShape)
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = accent)
        }
    }
}

@Composable
private fun PlaceholderScreen(
    modifier: Modifier = Modifier,
    section: AppSection,
    onBackToDashboard: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusChip(label = section.badge, accent = HarborBlue)
                Text(text = section.title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "This menu section is designed but not connected yet. Add the matching OPNsense endpoints after the dashboard integration is stable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                )
                Button(onClick = onBackToDashboard, shape = RoundedCornerShape(18.dp)) {
                    Text("Back to dashboard")
                }
            }
        }
    }
}

private fun statAccent(index: Int): Color = when (index) {
    0 -> Seafoam
    1 -> HarborBlue
    2 -> SignalOrange
    else -> AlertRed
}

private fun serviceActionKey(service: ServiceEntry): String = "${service.name}:${service.id}"

private fun formatPercentForUi(ratio: Float?): String {
    if (ratio == null) {
        return "N/A"
    }
    val percent = ratio * 100f
    return if (percent >= 99.95f) {
        "100%"
    } else {
        java.text.DecimalFormat("0.00").format(percent.toDouble()) + "%"
    }
}

private fun hasAvailableUpdates(status: FirmwareStatus): Boolean {
    val raw = status.updatesAvailable.trim()
    val numeric = raw.toIntOrNull()
    if (numeric != null) {
        return numeric > 0
    }
    return raw.isNotBlank() &&
        raw != "0" &&
        !raw.equals("none", ignoreCase = true) &&
        !raw.equals("unknown", ignoreCase = true)
}

private fun notificationAccent(statusCode: String): Color = when (statusCode.lowercase()) {
    "error", "danger", "2" -> AlertRed
    "warning", "warn", "1" -> SignalOrange
    "info", "notice" -> HarborBlue
    else -> MistSoft
}

private fun Exception.toDashboardMessage(): String = when (this) {
    is SSLException -> "TLS verification failed. Install a trusted certificate on the router or configure Android trust for that certificate chain."
    is SocketTimeoutException -> "The router did not answer in time. Check reachability, DNS, and whether the API is enabled."
    is ConnectException -> "The router could not be reached. Verify the URL, port, and whether the web interface is exposed."
    else -> message ?: "Unexpected error while reading OPNsense diagnostics."
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LoginPreview() {
    OPNSenseAdminTheme {
        LoginScreen(
            endpoint = "https://router.example.net",
            apiKey = "user-api-key",
            apiSecret = "secret-value",
            rememberDevice = true,
            ignoreInvalidSsl = false,
            onEndpointChange = {},
            onApiKeyChange = {},
            onApiSecretChange = {},
            onRememberDeviceChange = {},
            onIgnoreInvalidSslChange = {},
            onLogin = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DashboardPreview() {
    OPNSenseAdminTheme {
        DashboardHero(
            endpoint = "https://router.example.net",
            snapshot = DashboardSnapshot(
                endpoint = "https://router.example.net",
                systemName = "fw.office.example",
                versionSummary = "OPNsense 26.1-amd64",
                updateHint = "Click to check for updates.",
                uptime = "17 days, 04:11:00",
                loadAverage = "0.33, 0.41, 0.38",
                refreshedAt = "09:15",
                cpuCurrentRatio = 0.32f,
                trafficCurrentRatio = 0.44f,
                stats = emptyList(),
                cpuHistory = emptyList(),
                trafficHistory = emptyList(),
                trafficLabel = "WAN traffic",
                interfaceStats = emptyList()
            ),
            isLoading = false,
            onRefresh = {},
            onLogout = {}
        )
    }
}
