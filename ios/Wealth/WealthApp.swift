import SwiftUI
import LocalAuthentication

@main
struct WealthApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
        }
        .backgroundTask(.appRefresh(Round.id)) { await Round.run() }
    }
}

enum AppTab: Hashable {
    case overview, portfolio, triage, cashflow, settings
}

/// Pairing until there is a dashboard, the lock when it is up, the tabs
/// after.
struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var phase
    @State private var tab = AppTab.overview
    private let actions = QuickActions.shared

    var body: some View {
        Group {
            if !model.paired {
                PairView()
            } else if model.locked {
                LockView()
            } else {
                tabs
            }
        }
        .onChange(of: phase) { _, now in
            switch now {
            case .background:
                model.lockIfEnabled()
                Round.schedule(model.paired && model.watchEnabled)
            case .active:
                if model.paired && !model.locked { Task { await model.refresh() } }
                openRequested()
            default:
                break
            }
        }
        .onChange(of: actions.requested) { _, _ in openRequested() }
        .onAppear { openRequested() }
    }

    private var tabs: some View {
        TabView(selection: $tab) {
            OverviewView()
                .tabItem { Label("Overview", systemImage: "chart.line.uptrend.xyaxis") }
                .tag(AppTab.overview)
            PortfolioView()
                .tabItem { Label("Portfolio", systemImage: "chart.pie") }
                .tag(AppTab.portfolio)
            TriageView()
                .tabItem { Label("Triage", systemImage: "rectangle.stack") }
                .badge(min((model.snapshot?.waiting?.uncategorised ?? 0) + model.waiting, 99))
                .tag(AppTab.triage)
            // Cash flow, with the rows one button away — or the rows
            // alone against a dashboard older than the cashflow tool.
            Group {
                if model.supports("0.73.0") {
                    NavigationStack { CashflowView() }
                        .tabItem { Label("Cash flow", systemImage: "arrow.up.arrow.down") }
                } else {
                    NavigationStack { TransactionsView(account: nil) }
                        .tabItem { Label("Rows", systemImage: "arrow.up.arrow.down") }
                }
            }
            .tag(AppTab.cashflow)
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }
                .tag(AppTab.settings)
        }
        .tint(.gain)
        .task {
            await model.refresh()
            await model.flush()
        }
    }

    /// The Home-screen quick action asks for the triage straight away.
    private func openRequested() {
        guard let wanted = actions.requested else { return }
        if wanted == QuickActions.triage { tab = .triage }
        actions.requested = nil
    }
}

/// The device's own lock, used as this app's: anything the phone accepts
/// to unlock itself — Face ID, Touch ID or the passcode — is accepted
/// here.
struct LockView: View {
    @Environment(AppModel.self) private var model
    @State private var failed = false

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "lock.fill").font(.system(size: 44)).foregroundStyle(.secondary)
            Text("Wealth is locked").font(.title3.weight(.semibold))
            if failed {
                Text("Not unlocked. Try again when you are ready.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Button("Unlock") { Task { await unlock() } }
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task { await unlock() }
    }

    private func unlock() async {
        let context = LAContext()
        var problem: NSError?
        // No passcode on the phone means no lock to borrow: the switch in
        // Settings cannot make the phone safer than its owner did.
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &problem) else {
            model.locked = false
            return
        }
        do {
            if try await context.evaluatePolicy(.deviceOwnerAuthentication,
                                                 localizedReason: String(localized: "Unlock to see the figures")) {
                model.locked = false
                Task { await model.refresh() }
            }
        } catch {
            failed = true
        }
    }
}

/// The Home-screen quick actions (Info.plist, UIApplicationShortcutItems),
/// handed from UIKit to SwiftUI.
@Observable
final class QuickActions {
    static let shared = QuickActions()
    static let triage = "fr.smarthomeworld.wealth.triage"
    var requested: String?
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        // A cold start from the quick action arrives here, not in the
        // scene delegate.
        if let item = options.shortcutItem { QuickActions.shared.requested = item.type }
        let config = UISceneConfiguration(name: nil, sessionRole: session.role)
        config.delegateClass = SceneDelegate.self
        return config
    }
}

final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(_ windowScene: UIWindowScene,
                     performActionFor shortcutItem: UIApplicationShortcutItem,
                     completionHandler: @escaping (Bool) -> Void) {
        QuickActions.shared.requested = shortcutItem.type
        completionHandler(true)
    }
}
