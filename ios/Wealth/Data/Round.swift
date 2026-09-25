import BackgroundTasks
import Foundation
import UserNotifications
import WidgetKit

/// The background round: every few hours, when iOS allows, send what
/// the thumb decided on the train, fetch the figures, redraw the Home
/// screen — and say something only when there is something to say.
///
/// It does not push. The dashboard is on the owner's own network and
/// this app has no server in the middle; a poll every few hours is what
/// that buys, and it costs one request. What it will not do is say the
/// same thing twice: the last notice is remembered, and a notice only
/// goes out when it has changed.
enum Round {
    static let id = "fr.smarthomeworld.wealth.round"

    /// On: ask for the next round. Off: none — a switch that leaves a
    /// task behind is a lie.
    static func schedule(_ on: Bool) {
        guard on else {
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: id)
            return
        }
        let request = BGAppRefreshTaskRequest(identifier: id)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 3 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func run() async {
        let store = Store()
        guard store.paired else { return }
        schedule(store.watch)
        await AppModel.flush(store)
        guard let fresh = try? await store.api().snapshot() else { return }
        store.cache(fresh)
        WidgetCenter.shared.reloadAllTimelines()
        if store.watch { await notice(store, fresh) }
    }

    /// Asked when the switch is turned on — the moment it makes sense,
    /// not on the first start, when it means nothing yet.
    static func askToNotify() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
    }

    /// What is worth a buzz, in the order it matters: money about to run
    /// out, then a bank link that has stopped, then a queue that has
    /// grown. Anything smaller belongs on the screen, not the lock screen.
    static func notice(_ store: Store, _ s: Snapshot) async {
        guard let news = pick(s), store.lastNotice != news.key else { return }
        let content = UNMutableNotificationContent()
        content.title = news.title
        content.body = news.body
        content.sound = .default
        let request = UNNotificationRequest(identifier: "wealth-watch", content: content, trigger: nil)
        // Without permission nothing is posted and nothing remembered, so
        // the notice survives to the next round.
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }
        if (try? await UNUserNotificationCenter.current().add(request)) != nil {
            store.lastNotice = news.key
        }
    }

    /// The notice for a snapshot, if any: a key that changes when the
    /// news does, a title and a line.
    static func pick(_ s: Snapshot) -> (key: String, title: String, body: String)? {
        let ccy = s.currency
        if let below = s.upcoming?.belowZero {
            let account = below.account ?? String(localized: "An account")
            let payment = below.name ?? String(localized: "A payment")
            return ("below:\(below.date ?? "")",
                    String(localized: "\(account) runs out on \(Fmt.day(below.date))"),
                    String(localized: "\(payment) takes it to \(Fmt.money(below.running, ccy))."))
        }
        let red = s.sync?.red ?? 0
        if red > 0 {
            return ("red:\(red)",
                    red == 1 ? String(localized: "A bank link has stopped")
                             : String(localized: "\(red) bank links have stopped"),
                    String(localized: "The dashboard could not sync. Open it to reconnect."))
        }
        let queue = (s.waiting?.uncategorised ?? 0) + (s.waiting?.unassignedSpending ?? 0)
        if queue >= 10 {
            return ("queue:\(queue / 10)",
                    String(localized: "\(queue) rows are waiting"),
                    String(localized: "A thumb and a few minutes clears them — Triage."))
        }
        return nil
    }
}
