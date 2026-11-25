import Foundation

public struct PreparedShare: Equatable {
    public var title: String?
    public var text: String?
    public var url: URL?
    public var files: [URL]
    public var dialogTitle: String
}

public enum ShareFailure: Error, Equatable {
    case invalid(String)
    case canceled
    case inProgress
    case unavailable(String)

    public var code: String {
        switch self {
        case .invalid:
            return "INVALID_SHARE"
        case .canceled:
            return "SHARE_CANCELED"
        case .inProgress:
            return "SHARE_IN_PROGRESS"
        case .unavailable:
            return "UNAVAILABLE"
        }
    }

    public var message: String {
        switch self {
        case .invalid(let message), .unavailable(let message):
            return message
        case .canceled:
            return "Share canceled"
        case .inProgress:
            return "Can't share while sharing is in progress"
        }
    }
}

public enum SharePayload {
    public static let titleLimit = 200
    public static let textLimit = 5000
    public static let urlLimit = 2048
    public static let fileUrlLimit = 4096
    public static let fileLimit = 10
    public static let dialogTitleLimit = 80

    public static func prepare(_ raw: [String: Any]) throws -> PreparedShare {
        let title = try optionalText(raw["title"], name: "title", max: titleLimit)
        let text = try optionalText(raw["text"], name: "text", max: textLimit)
        let dialogTitle = try optionalText(raw["dialogTitle"], name: "dialogTitle", max: dialogTitleLimit) ?? "Share"
        var files = try fileList(raw["files"])
        var url: URL?
        if let value = raw["url"], !(value is NSNull) {
            switch try httpOrFile(value) {
            case .file(let file):
                files.insert(file, at: 0)
            case .http(let http):
                url = http
            }
        }
        let unique = dedupe(files)
        if unique.count > fileLimit {
            throw ShareFailure.invalid("At most \(fileLimit) files can be shared.")
        }
        if text == nil && url == nil && unique.isEmpty {
            throw ShareFailure.invalid("Share at least one of text, url, or files.")
        }
        return PreparedShare(title: title, text: text, url: url, files: unique, dialogTitle: dialogTitle)
    }

    private static func optionalText(_ value: Any?, name: String, max: Int) throws -> String? {
        if value == nil || value is NSNull { return nil }
        guard let text = value as? String else {
            throw ShareFailure.invalid("\(name) must be a string.")
        }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return nil }
        if trimmed.count > max {
            throw ShareFailure.invalid("\(name) must be \(max) characters or fewer.")
        }
        return trimmed
    }

    private static func fileList(_ value: Any?) throws -> [URL] {
        if value == nil || value is NSNull { return [] }
        let items: [Any]
        if let list = value as? [String] {
            items = list
        } else if let list = value as? [Any] {
            items = list
        } else if let list = value as? NSArray {
            items = list.map { $0 }
        } else {
            throw ShareFailure.invalid("files must be an array of file:// URLs.")
        }
        return try items.map { item in
            guard let text = item as? String else {
                throw ShareFailure.invalid("Each file must be a file:// URL.")
            }
            return try fileUrl(text)
        }
    }

    private static func httpOrFile(_ value: Any) throws -> ShareLink {
        guard let text = value as? String else {
            throw ShareFailure.invalid("url must be a string.")
        }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if unsafe(trimmed) {
            throw ShareFailure.invalid("url must be an http, https, or file URL.")
        }
        guard let parsed = URL(string: trimmed), let scheme = parsed.scheme?.lowercased() else {
            throw ShareFailure.invalid("url must be an http, https, or file URL.")
        }
        if scheme == "http" || scheme == "https" {
            if trimmed.count > urlLimit {
                throw ShareFailure.invalid("url must be \(urlLimit) characters or fewer.")
            }
            if parsed.host?.isEmpty != false {
                throw ShareFailure.invalid("url must include a host.")
            }
            return .http(parsed)
        }
        if scheme == "file" {
            return .file(try fileUrl(trimmed))
        }
        throw ShareFailure.invalid("url must be an http, https, or file URL.")
    }

    private static func fileUrl(_ value: String) throws -> URL {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count > fileUrlLimit {
            throw ShareFailure.invalid("A file URL must be \(fileUrlLimit) characters or fewer.")
        }
        if unsafe(trimmed) {
            throw ShareFailure.invalid("Each file must be a file:// URL.")
        }
        guard let parsed = URL(string: trimmed), parsed.scheme?.lowercased() == "file" else {
            throw ShareFailure.invalid("Each file must be a file:// URL.")
        }
        if hasDotSegment(trimmed) {
            throw ShareFailure.invalid("File URLs must be an absolute local path.")
        }
        if let host = parsed.host, !host.isEmpty, host.lowercased() != "localhost" {
            throw ShareFailure.invalid("File URLs must point at a local file.")
        }
        if parsed.user != nil || parsed.password != nil || parsed.query != nil || parsed.fragment != nil {
            throw ShareFailure.invalid("File URLs must be an absolute local path.")
        }
        let path = parsed.path
        if !path.hasPrefix("/") || path == "/" || path.hasSuffix("/") || path.contains("\\") || path.contains("\0") {
            throw ShareFailure.invalid("File URLs must be an absolute local path.")
        }
        if path.split(separator: "/").contains("..") {
            throw ShareFailure.invalid("File URLs must be an absolute local path.")
        }
        return parsed
    }

    private static func hasDotSegment(_ value: String) -> Bool {
        guard let decoded = value.removingPercentEncoding else { return true }
        return decoded.split(whereSeparator: { $0 == "/" || $0 == "\\" }).contains("..")
    }

    private static func unsafe(_ value: String) -> Bool {
        value.unicodeScalars.contains { scalar in
            scalar.value <= 0x1F || scalar.value == 0x7F || CharacterSet.whitespacesAndNewlines.contains(scalar)
        }
    }

    private static func dedupe(_ values: [URL]) -> [URL] {
        var seen = Set<String>()
        var unique: [URL] = []
        for value in values {
            let key = value.absoluteString
            if seen.insert(key).inserted {
                unique.append(value)
            }
        }
        return unique
    }
}

private enum ShareLink {
    case http(URL)
    case file(URL)
}
