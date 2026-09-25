package fr.smarthomeworld.wealth

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.smarthomeworld.wealth.data.Account
import fr.smarthomeworld.wealth.ui.*

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The home-screen shortcut asks for the triage straight away.
        val start = if (intent?.action == TRIAGE_ACTION) Tab.Triage else Tab.Overview
        setContent {
            WealthTheme {
                val vm: MainViewModel = viewModel()
                App(vm, ::askFingerprint, start)
            }
        }
    }

    /** The phone's own lock, used as this app's. Anything the phone
     *  accepts to unlock itself is accepted here — a fingerprint, a
     *  face, or the PIN when neither is set up. */
    private fun askFingerprint(onOk: () -> Unit) {
        val can = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) { onOk(); return }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Wealth Dashboard")
                .setSubtitle("Unlock to see the figures")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build())
    }
}

private enum class Tab { Overview, Portfolio, Triage, Cashflow, Transactions, Settings }

/** What the shortcut sends; an explicit component, so no filter is
 *  needed for it. */
const val TRIAGE_ACTION = "fr.smarthomeworld.wealth.action.TRIAGE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: MainViewModel, unlock: (() -> Unit) -> Unit, start: Tab = Tab.Overview) {
    val state by vm.state.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val triage by vm.triage.collectAsStateWithLifecycle()
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val cashflow by vm.cashflow.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(start) }
    LaunchedEffect(start) { if (start == Tab.Triage) vm.loadTriage(owning = false) }
    var account by remember { mutableStateOf<Account?>(null) }

    if (!state.paired) {
        PairScreen(state.pairing, state.error) { url, code -> vm.pair(url, code) }
        return
    }
    if (state.locked) {
        LockScreen { unlock { vm.unlock() } }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val heading = when (tab) {
                        Tab.Overview -> "Overview"
                        Tab.Portfolio -> "Portfolio"
                        Tab.Triage -> "Triage"
                        Tab.Cashflow -> "Cashflow"
                        Tab.Transactions -> "Transactions"
                        Tab.Settings -> "Settings"
                    }
                    Text(account?.name ?: heading)
                },
                actions = {
                    // Two refreshes, and the difference matters: the
                    // left one re-reads what the dashboard already
                    // knows, the right one makes it fetch quotes and
                    // exchange rates. Neither talks to a bank — that is
                    // the dashboard's own sync, and it takes minutes.
                    IconButton(onClick = { vm.refreshMarket() }, enabled = !state.pricing) {
                        if (state.pricing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.ShowChart, contentDescription = "Kurse aktualisieren")
                        }
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.Overview && account == null,
                    onClick = { tab = Tab.Overview; account = null },
                    icon = { Icon(Icons.Default.Insights, null) }, label = { Text("Overview") })
                NavigationBarItem(
                    selected = tab == Tab.Portfolio,
                    onClick = { tab = Tab.Portfolio; account = null; vm.loadPortfolio() },
                    icon = { Icon(Icons.Default.PieChart, null) }, label = { Text("Portfolio") })
                NavigationBarItem(
                    selected = tab == Tab.Triage,
                    onClick = { tab = Tab.Triage; account = null; vm.loadTriage(owning = false) },
                    icon = {
                        val queue = (state.snapshot?.waiting?.uncategorised ?: 0) + triage.waiting
                        BadgedBox(badge = { if (queue > 0) Badge { Text(queue.coerceAtMost(99).toString()) } }) {
                            Icon(Icons.Default.Style, null)
                        }
                    },
                    label = { Text("Triage") })
                NavigationBarItem(
                    selected = tab == Tab.Cashflow || tab == Tab.Transactions,
                    onClick = { tab = Tab.Cashflow; account = null; vm.loadCashflow() },
                    icon = { Icon(Icons.Default.SwapVert, null) }, label = { Text("Cashflow") })
                NavigationBarItem(
                    selected = tab == Tab.Settings,
                    onClick = { tab = Tab.Settings; account = null },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            val snap = state.snapshot
            when {
                account != null -> TransactionsScreen(txns, account!!.name) { q ->
                    vm.loadTransactions(account!!.id, q)
                }
                tab == Tab.Transactions -> TransactionsScreen(txns, "everything") { q ->
                    vm.loadTransactions(null, q)
                }
                tab == Tab.Cashflow -> CashflowScreen(
                    state = cashflow,
                    baseCurrency = snap?.baseCurrency ?: "EUR",
                    onMonths = { vm.loadCashflow(it) },
                    onRefresh = { vm.loadCashflow() },
                    onTransactions = { tab = Tab.Transactions; vm.loadTransactions() },
                )
                tab == Tab.Portfolio -> PortfolioScreen(
                    state = portfolio,
                    accounts = snap?.accounts.orEmpty(),
                    onPeriod = { vm.setPeriod(it) },
                    onAccount = { account = it; vm.loadTransactions(it.id) },
                    onRefresh = { vm.loadPortfolio() },
                )
                tab == Tab.Triage -> Column(Modifier.fillMaxSize()) {
                    // Two queues, one screen: what has no category, and
                    // what nobody has claimed. The dashboard counts both.
                    val unowned = snap?.waiting?.unassignedSpending ?: 0
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SegmentedButton(
                            selected = !triage.owning, onClick = { vm.loadTriage(owning = false) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("Category") }
                        SegmentedButton(
                            selected = triage.owning, onClick = { vm.loadTriage(owning = true) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(if (unowned > 0) "Whose ($unowned)" else "Whose") }
                    }
                    TriageScreen(
                        owning = triage.owning, rows = triage.rows, remaining = triage.remaining,
                        categories = triage.categories, people = triage.people,
                        waiting = triage.waiting, loading = triage.loading, error = triage.error,
                        onDecide = { row, category, owner, pattern, remember ->
                            vm.decide(row, category, owner, pattern, remember)
                        },
                        onUndo = { vm.undoLast() },
                        onRefresh = { vm.loadTriage(triage.owning) },
                    )
                }
                tab == Tab.Settings -> SettingsScreen(
                    server = state.serverName,
                    lock = vm.lockEnabled,
                    watch = vm.watchEnabled,
                    onLock = { vm.setLock(it) },
                    onWatch = { vm.setWatch(it) },
                    onForget = { vm.forget() },
                )
                snap == null && state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                snap == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.error ?: "Nothing yet — pull to refresh.", Modifier.padding(24.dp))
                }
                else -> OverviewScreen(
                    snapshot = snap, at = state.at, stale = state.stale, error = state.error,
                    history = portfolio.history.points,
                    excluded = state.excluded,
                    onToggleClass = { vm.toggleClass(it) },
                    onAccounts = { tab = Tab.Portfolio; vm.loadPortfolio() },
                    onTransactions = { tab = Tab.Transactions; vm.loadTransactions() },
                )
            }
            if (state.loading && snap != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    server: String?,
    lock: Boolean,
    watch: Boolean,
    onLock: (Boolean) -> Unit,
    onWatch: (Boolean) -> Unit,
    onForget: () -> Unit,
) {
    var locked by remember { mutableStateOf(lock) }
    var watching by remember { mutableStateOf(watch) }
    // Android 13 asks before anything may reach the lock screen. Ask at
    // the moment the switch is turned on, which is the moment it makes
    // sense — not on the first start, when it means nothing yet.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Paired with ${server ?: "your dashboard"}", style = MaterialTheme.typography.bodyLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ask to unlock", style = MaterialTheme.typography.bodyMedium)
                Text("Fingerprint, face or the phone's PIN before the figures show.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = locked, onCheckedChange = { locked = it; onLock(it) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Watch in the background", style = MaterialTheme.typography.bodyMedium)
                Text("Every few hours: send what you decided offline, refresh the " +
                     "figures and the widget, and say something when an account is " +
                     "about to run out, a bank link has stopped, or the queue has grown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = watching, onCheckedChange = {
                watching = it
                onWatch(it)
                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            })
        }
        OutlinedButton(onClick = onForget) { Text("Forget this dashboard") }
        Text(
            "Forgetting removes the token from this phone. The dashboard keeps " +
                "working; pair again with a new code whenever you like.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
