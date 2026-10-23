// SPDX-License-Identifier: GPL-3.0-or-later
import XCTest

final class IPadLayoutTests: XCTestCase {
    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testHomeSettingsNavigationAndLanguage() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-developer", "YES"]
        XCUIDevice.shared.orientation = .portrait
        app.launch()
        let settings = app.buttons["home.settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        settings.tap()
        let initialLanguage = app.segmentedControls["settings.language"]
        XCTAssertTrue(initialLanguage.waitForExistence(timeout: 5))
        initialLanguage.buttons["简体中文"].tap()
        XCTAssertTrue(app.navigationBars["连接设置"].waitForExistence(timeout: 5))
        app.buttons["settings.cancel"].tap()
        XCTAssertEqual(app.scrollViews.count, 0, "Home must have no vertical scrolling or bounce")
        let connect = app.buttons["home.connect"]
        XCTAssertTrue(connect.isHittable)
        let before = connect.frame
        app.swipeUp()
        XCTAssertEqual(connect.frame, before, "Swiping must not move the connection controls")
        capture("home-portrait-zh")
        settings.tap()
        let language = app.segmentedControls["settings.language"]
        XCTAssertTrue(language.waitForExistence(timeout: 5))
        XCTAssertTrue(language.buttons["简体中文"].isSelected)
        capture("settings-portrait-initial-zh")
        language.buttons["English"].tap()
        XCTAssertTrue(language.buttons["English"].isSelected)
        XCTAssertTrue(app.navigationBars["Connection settings"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["settings.save"].isHittable)
        capture("settings-portrait-initial-en")
        app.buttons["settings.cancel"].tap()
        app.terminate()
        XCUIDevice.shared.orientation = .portrait
        app.launch()
        XCTAssertTrue(settings.waitForExistence(timeout: 10))
        settings.tap()
        XCTAssertTrue(app.buttons["settings.save"].waitForExistence(timeout: 5))
        capture("settings-portrait-en")
        let network = app.textFields["settings.network"]
        network.tap()
        network.typeText("0123456789abcdef")
        language.buttons["简体中文"].tap()
        XCTAssertEqual(network.value as? String, "0123456789abcdef", "Language changes must preserve the connection draft")
        capture("settings-portrait-zh")
        language.buttons["English"].tap()
        app.buttons["settings.save"].tap()
        XCTAssertTrue(app.alerts["Check connection settings"].waitForExistence(timeout: 5))
        app.alerts.buttons["Close"].tap()
        app.swipeUp()
        XCTAssertTrue(app.buttons["settings.guide"].waitForExistence(timeout: 5))
        app.buttons["settings.guide"].tap()
        capture("settings-guide-en")
        app.swipeDown()
        app.buttons["settings.moons"].tap()
        XCTAssertTrue(app.navigationBars["Moon helpers"].waitForExistence(timeout: 5))
        capture("moons-en")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["settings.cancel"].tap()
        XCTAssertTrue(connect.isHittable)
        XCTAssertLessThan(app.frame.width, app.frame.height)
        capture("home-portrait-en")
        app.buttons["home.diagnostics"].tap()
        XCTAssertTrue(app.navigationBars["Developer tools"].waitForExistence(timeout: 5))
        capture("diagnostics-en")
    }
    func testHomeRoutingSettingsAndDraft() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-language", "en"]
        XCUIDevice.shared.orientation = .portrait
        app.launch()
        XCTAssertTrue(app.buttons["home.settings"].waitForExistence(timeout: 10))
        app.buttons["home.settings"].tap()
        let remoteNetwork = app.textFields["settings.network"]
        remoteNetwork.tap(); remoteNetwork.typeText("0123456789abcdef")
        let remoteGateway = app.textFields["settings.gateway"]
        remoteGateway.tap(); remoteGateway.typeText("10.1.2.1")
        app.collectionViews.firstMatch.swipeUp()
        app.buttons["settings.homeRouting"].tap()
        let enabled = app.switches["homeRouting.enabled"]
        XCTAssertTrue(enabled.waitForExistence(timeout: 5))
        XCTAssertEqual(enabled.value as? String, "0", "Existing installations must keep automatic switching off")
        enabled.switches.firstMatch.tap()
        let readNetwork = app.buttons["homeRouting.readNetwork"]
        XCTAssertTrue(readNetwork.waitForExistence(timeout: 5))
        readNetwork.tap()
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: readNetwork)
        waitForExpectations(timeout: 12)
        capture("home-network-read-result")
        let ssid = app.textFields["homeRouting.ssid"]
        for _ in 0..<3 {
            if ssid.exists && ssid.isHittable { break }
            app.collectionViews.firstMatch.swipeUp()
        }
        XCTAssertTrue(ssid.waitForExistence(timeout: 5))
        capture("home-routing-empty-en")
        ssid.tap()
        if let value = ssid.value as? String, value != "Exactly as shown in the Wi-Fi list" {
            ssid.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: value.count))
        }
        ssid.typeText("NestLink Home Test")
        let nas = app.textFields["homeRouting.nas"]
        for _ in 0..<5 {
            if nas.exists && nas.isHittable { break }
            let form = app.collectionViews.firstMatch
            form.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7)).press(forDuration: 0.05, thenDragTo: form.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45)))
        }
        nas.tap(); nas.typeText("192.168.1.66")
        XCTAssertFalse(app.switches["homeRouting.confirmed"].exists)
        capture("home-local-proxy-settings-en")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["settings.homeRouting"].tap()
        readNetwork.tap()
        expectation(for: NSPredicate(format: "enabled == true"), evaluatedWith: readNetwork)
        waitForExpectations(timeout: 12)
        for _ in 0..<3 {
            if ssid.exists && ssid.isHittable { break }
            app.collectionViews.firstMatch.swipeUp()
        }
        XCTAssertEqual(app.textFields["homeRouting.ssid"].value as? String, "NestLink Home Test")
        XCTAssertEqual(app.textFields["homeRouting.nas"].value as? String, "192.168.1.66")
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["settings.cancel"].tap()
        XCTAssertEqual(app.scrollViews.count, 0)
    }
    func testHomeAtAccessibilityTextSize() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        XCUIDevice.shared.orientation = .portrait
        app.launch()
        let connect = app.buttons["home.connect"]
        XCTAssertTrue(connect.waitForExistence(timeout: 10))
        XCTAssertTrue(connect.isHittable)
        XCTAssertEqual(app.scrollViews.count, 0)
        let lan = app.descendants(matching: .any)["home.lanReady"]
        XCTAssertTrue(lan.exists)
        XCTAssertFalse(connect.frame.intersects(lan.frame))
        XCTAssertTrue(app.frame.contains(connect.frame))
        XCTAssertTrue(app.frame.contains(lan.frame))
        capture("home-accessibility-portrait")
        app.terminate()
    }

    func testLandscapeLayoutWhenDeviceRotates() throws {
        let app = XCUIApplication()
        XCUIDevice.shared.orientation = .landscapeLeft
        app.launch()
        XCTAssertTrue(app.buttons["home.settings"].waitForExistence(timeout: 10))
        defer { app.terminate() }
        guard app.frame.width >= 900 && app.frame.width > app.frame.height else {
            throw XCTSkip("The simulator reported landscape orientation but retained a portrait app window; landscape needs a rotation-capable session or a physical iPad.")
        }
        XCTAssertGreaterThan(app.buttons["home.connect"].frame.minX, app.frame.midX)
        XCTAssertEqual(app.scrollViews.count, 0)
        capture("home-landscape")
    }
}
