// SPDX-License-Identifier: GPL-3.0-or-later
import Foundation

private let englishText: [String: String] = {
    guard let url = Bundle.main.url(forResource: "Translations", withExtension: "json"),
          let data = try? Data(contentsOf: url),
          let entries = try? JSONDecoder().decode([String: String].self, from: data) else { return [:] }
    return entries
}()

func tr(_ text: String, _ language: String) -> String {
    guard language != "zh" else { return text }
    if let translated = englishText[text] { return translated }
    for (prefix, translated) in [("启动失败：", "Could not start: "), ("读取 VPN 配置失败：", "Could not load VPN settings: ")] {
        if text.hasPrefix(prefix) { return translated + tr(String(text.dropFirst(prefix.count)), language) }
    }
    return text
}
