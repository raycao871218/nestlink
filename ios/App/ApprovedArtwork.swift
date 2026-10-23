import SwiftUI

/// Static Chinese lettering from the approved drawing, inside accessible native UI.
/// Arbitrary text, English and large accessibility sizes retain real font layout.
struct ApprovedLabel: View {
    let text: String
    let language: String
    var height: CGFloat = 24
    @Environment(\.dynamicTypeSize) private var typeSize
    private var art: (String, CGFloat)? {
        switch text {
        case "家庭网络": return ("ApprovedFamily", 171.0/45.0)
        case "互联网": return ("ApprovedInternet", 203.0/49.0)
        case "可用": return ("ApprovedAvailable", 65.0/49.0)
        case "当前网络": return ("ApprovedCurrent", 123.0/39.0)
        case "家庭 NAS": return ("ApprovedHomeNas", 165.0/46.0)
        case "通过 ZeroTier 安全连接": return ("ApprovedProfileHint", 300.0/36.0)
        case "连接指南": return ("ApprovedGuide", 178.0/40.0)
        case "配置、授权与故障排查": return ("ApprovedGuideHint", 280.0/35.0)
        case "断开连接": return ("ApprovedDisconnect", 184.0/59.0)
        case "连接": return ("ApprovedConnect", 100.0/59.0)
        default: return nil
        }
    }
    var body: some View {
        if language == "zh", !typeSize.isAccessibilitySize, let (name, ratio) = art {
            Image(name).resizable().scaledToFit().frame(width: height*ratio, height: height).accessibilityLabel(text)
        } else {
            Text(tr(text, language)).font(.ink(height/1.25))
        }
    }
}

struct ApprovedSymbol: View {
    let name: String
    var size: CGFloat = 30
    var body: some View {
        Image("Approved"+name).resizable().scaledToFit().frame(width: size, height: size).accessibilityHidden(true)
    }
}
