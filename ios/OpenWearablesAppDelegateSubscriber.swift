import ExpoModulesCore
import UIKit
import BackgroundTasks

public class OpenWearablesAppDelegateSubscriber: ExpoAppDelegateSubscriber {

    public func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        // Register BGTaskScheduler identifiers at launch — must happen before
        // the app finishes launching (i.e. here, not lazily).
        SyncManager.shared.registerBGTasks()
        return true
    }

    public func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        // Allow any in-flight URLSession background tasks to complete.
        // We don't use a custom background URLSession configuration, so just
        // call through to let the system clean up.
        completionHandler()
    }
}
