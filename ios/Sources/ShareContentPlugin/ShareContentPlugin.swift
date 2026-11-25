import Capacitor
import UIKit

@objc(ShareContentPlugin)
public class ShareContentPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "ShareContentPlugin"
    public let jsName = "ShareContent"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "canShare", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "share", returnType: CAPPluginReturnPromise),
    ]

    private let lock = NSLock()
    private var presenting = false

    @objc func canShare(_ call: CAPPluginCall) {
        call.resolve(["value": true])
    }

    @objc func share(_ call: CAPPluginCall) {
        let prepared: PreparedShare
        do {
            prepared = try SharePayload.prepare(dictionary(call))
        } catch let error as ShareFailure {
            call.reject(error.message, error.code)
            return
        } catch {
            call.reject("share() expects an options object.", "INVALID_SHARE")
            return
        }

        var items: [Any] = []
        if let text = prepared.text {
            items.append(text)
        }
        if let url = prepared.url {
            items.append(url)
        }
        items.append(contentsOf: prepared.files)
        let subject = prepared.title

        DispatchQueue.main.async { [weak self] in
            guard let self else {
                call.reject("Sharing is not available.", "UNAVAILABLE")
                return
            }
            guard self.beginSharing() else {
                call.reject(ShareFailure.inProgress.message, ShareFailure.inProgress.code)
                return
            }
            var presenter = self.bridge?.viewController
            while let presented = presenter?.presentedViewController {
                if presented is UIActivityViewController {
                    self.endSharing()
                    call.reject(ShareFailure.inProgress.message, ShareFailure.inProgress.code)
                    return
                }
                presenter = presented
            }
            guard let presenter, let presenterView = presenter.view else {
                self.endSharing()
                call.reject("Sharing is not available.", "UNAVAILABLE")
                return
            }

            let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
            if let subject {
                controller.setValue(subject, forKey: "subject")
            }
            controller.completionWithItemsHandler = { [weak self] activity, completed, _, activityError in
                self?.endSharing()
                if let activityError {
                    call.reject(activityError.localizedDescription, "UNAVAILABLE")
                    return
                }
                if completed {
                    call.resolve(["activityType": activity?.rawValue ?? ""])
                } else {
                    call.reject(ShareFailure.canceled.message, ShareFailure.canceled.code)
                }
            }
            if let popover = controller.popoverPresentationController {
                popover.sourceView = presenterView
                popover.sourceRect = CGRect(x: presenterView.bounds.midX, y: presenterView.bounds.midY, width: 0, height: 0)
                popover.permittedArrowDirections = []
            }
            presenter.present(controller, animated: true)
        }
    }

    private func beginSharing() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if presenting { return false }
        presenting = true
        return true
    }

    private func endSharing() {
        lock.lock()
        presenting = false
        lock.unlock()
    }

    private func dictionary(_ call: CAPPluginCall) -> [String: Any] {
        guard let options = call.options as? [String: Any] else { return [:] }
        var raw: [String: Any] = [:]
        for (key, value) in options where !(value is NSNull) {
            raw[key] = value
        }
        return raw
    }
}
