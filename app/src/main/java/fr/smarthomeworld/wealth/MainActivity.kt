package fr.smarthomeworld.wealth

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
        setContent {
            WealthTheme {
                val vm: MainViewModel = viewModel()
                App(vm, ::askFingerprint)
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

private enum class Tab { Overview, Accounts, Transactions, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: MainViewModel, unlock: (() -> Unit) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Overview) }
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
                title = { Text(account?.name ?: when (tab) {
                    Tab.Overview -> "Overview"
                    Tab.Accounts -> "Accounts"
                    Tab.Transactions -> "Transactions"
                    Tab.Settings -> "Settings"
                }) },
                actions = {
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
                    selected = tab == Tab.Accounts,
                    onClick = { tab = Tab.Accounts; account = null },
                    icon = { Icon(Icons.Default.AccountBalance, null) }, label = { Text("Accounts") })
                NavigationBarItem(
                    selected = tab == Tab.Transactions,
                    onClick = { tab = Tab.Transactions; account = null; vm.loadTransactions() },
                    icon = { Icon(Icons.Default.SwapVert, null) }, label = { Text("Rows") })
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
                tab == Tab.Settings -> SettingsScreen(
                    server = state.serverName,
                    lock = vm.lockEnabled,
                    onLock = { vm.setLock(it) },
                    onForget = { vm.forget() },
                )
                snap == null && state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                snap == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.error ?: "Nothing yet — pull to refresh.", Modifier.padding(24.dp))
                }
                tab == Tab.Accounts -> AccountsScreen(snap.accounts, snap.baseCurrency) { a ->
                    account = a; vm.loadTransactions(a.id)
                }
                else -> OverviewScreen(
                    snapshot = snap, at = state.at, stale = state.stale, error = state.error,
                    onAccounts = { tab = Tab.Accounts },
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
    onLock: (Boolean) -> Unit,
    onForget: () -> Unit,
) {
    var locked by remember { mutableStateOf(lock) }
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
        OutlinedButton(onClick = onForget) { Text("Forget this dashboard") }
        Text(
            "Forgetting removes the token from this phone. The dashboard keeps " +
                "working; pair again with a new code whenever you like.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
