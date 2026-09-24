import SwiftUI

@main
struct WealthApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
        }
    }
}

/// Pairing until there is a dashboard, the tabs after.
struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var phase

    var body: some View {
        if model.paired {
            TabView {
                OverviewView()
                    .tabItem { Label("Overview", systemImage: "chart.line.uptrend.xyaxis") }
                AccountsView()
                    .tabItem { Label("Accounts", systemImage: "building.columns") }
                NavigationStack { TransactionsView(account: nil) }
                    .tabItem { Label("Rows", systemImage: "arrow.up.arrow.down") }
                SettingsView()
                    .tabItem { Label("Settings", systemImage: "gearshape") }
            }
            .tint(.gain)
            .task { await model.refresh() }
            .onChange(of: phase) { _, now in
                if now == .active { Task { await model.refresh() } }
            }
        } else {
            PairView()
        }
    }
}
