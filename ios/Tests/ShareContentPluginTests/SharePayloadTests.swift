import XCTest
@testable import ShareContentPlugin

final class SharePayloadTests: XCTestCase {
    func testKeepsTextAndHttpsUrl() throws {
        let prepared = try SharePayload.prepare([
            "title": "  Notes  ",
            "text": " Hello ",
            "url": "https://example.com/a",
            "dialogTitle": "   ",
        ])
        XCTAssertEqual(prepared.title, "Notes")
        XCTAssertEqual(prepared.text, "Hello")
        XCTAssertEqual(prepared.url?.absoluteString, "https://example.com/a")
        XCTAssertEqual(prepared.dialogTitle, "Share")
        XCTAssertTrue(prepared.files.isEmpty)
    }

    func testTreatsAFileUrlAsAFile() throws {
        let prepared = try SharePayload.prepare([
            "url": "file:///tmp/photo.jpg",
            "files": ["file:///tmp/photo.jpg", "file://localhost/tmp/other.png"],
        ])
        XCTAssertNil(prepared.url)
        XCTAssertEqual(prepared.files.map(\.path), ["/tmp/photo.jpg", "/tmp/other.png"])
    }

    func testRejectsEmptyAndUnsafeInput() {
        XCTAssertEqual(code([:]), "INVALID_SHARE")
        XCTAssertEqual(code(["title": "Only a subject"]), "INVALID_SHARE")
        XCTAssertEqual(code(["text": 1]), "INVALID_SHARE")
        XCTAssertEqual(code(["url": "javascript:alert(1)"]), "INVALID_SHARE")
        XCTAssertEqual(code(["url": "data:text/plain,hello"]), "INVALID_SHARE")
        XCTAssertEqual(code(["url": "file:///tmp/../etc/passwd"]), "INVALID_SHARE")
        XCTAssertEqual(code(["files": ["https://example.com/a"]]), "INVALID_SHARE")
        XCTAssertEqual(code(["files": ["file://files.example/tmp/a.jpg"]]), "INVALID_SHARE")
        XCTAssertEqual(code(["text": String(repeating: "x", count: 5001)]), "INVALID_SHARE")
        XCTAssertEqual(code(["files": (0 ..< 11).map { "file:///tmp/\($0).txt" }]), "INVALID_SHARE")
    }

    private func code(_ value: [String: Any]) -> String? {
        do {
            _ = try SharePayload.prepare(value)
            return nil
        } catch let error as ShareFailure {
            return error.code
        } catch {
            return nil
        }
    }
}
