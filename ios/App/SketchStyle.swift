import SwiftUI

let sketchInk = Color(red: 0.298, green: 0.153, blue: 0.094)
let sketchAccent = Color(red: 1, green: 0.827, blue: 0.298)
let sketchGreen = Color(red: 0.863, green: 0.914, blue: 0.737)

/// Fixed, subtly uneven pen strokes; redraws do not make the outline jitter.
struct SketchContour: Shape {
    var drift: CGFloat = 0.7
    func path(in rect: CGRect) -> Path {
        let r = rect.insetBy(dx: 3, dy: 3)
        let x = r.minX, y = r.minY, w = r.width, h = r.height
        let k = min(12, min(w, h) / 4)
        var p = Path()
        p.move(to: CGPoint(x: x+k, y: y+drift))
        p.addCurve(to: CGPoint(x: x+w-k, y: y), control1: CGPoint(x: x+w*0.30, y: y-1.2), control2: CGPoint(x: x+w*0.67, y: y+2))
        p.addQuadCurve(to: CGPoint(x: x+w, y: y+k), control: CGPoint(x: x+w+drift, y: y))
        p.addCurve(to: CGPoint(x: x+w-1, y: y+h-k), control1: CGPoint(x: x+w-1.5, y: y+h*0.35), control2: CGPoint(x: x+w+drift, y: y+h*0.67))
        p.addQuadCurve(to: CGPoint(x: x+w-k, y: y+h), control: CGPoint(x: x+w, y: y+h+drift))
        p.addCurve(to: CGPoint(x: x+k, y: y+h-1), control1: CGPoint(x: x+w*0.66, y: y+h-1), control2: CGPoint(x: x+w*0.32, y: y+h+1.3))
        p.addQuadCurve(to: CGPoint(x: x, y: y+h-k), control: CGPoint(x: x-drift, y: y+h))
        p.addCurve(to: CGPoint(x: x, y: y+k), control1: CGPoint(x: x+1.5, y: y+h*0.64), control2: CGPoint(x: x-drift, y: y+h*0.32))
        p.addQuadCurve(to: CGPoint(x: x+k, y: y+drift), control: CGPoint(x: x, y: y-1))
        p.closeSubpath()
        return p
    }
}

struct SketchUnderline: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 3, y: r.midY))
        p.addCurve(to: CGPoint(x: r.maxX-3, y: r.midY-1), control1: CGPoint(x: r.width*0.25, y: 2), control2: CGPoint(x: r.width*0.7, y: r.maxY-2))
        return p
    }
}

struct SketchPaper: View {
    var fill: Color = .white
    var body: some View {
        Image(fill == sketchAccent ? "ApprovedButton" : "ApprovedPanel")
            .resizable(capInsets: EdgeInsets(top: 14, leading: 14, bottom: 14, trailing: 14), resizingMode: .stretch)
            .allowsHitTesting(false)
    }
}

extension View {
    func stickerSurface(_ fill: Color) -> some View { background(SketchPaper(fill: fill)) }
}

struct SketchPressStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(enabled ? (configuration.isPressed ? 0.65 : 1) : 0.4)
    }
}

struct SketchSettingsButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.ink(19)).foregroundStyle(sketchInk)
            .padding(.horizontal, 14).padding(.vertical, 12)
            .stickerSurface(sketchAccent.opacity(0.6))
            .opacity(enabled ? (configuration.isPressed ? 0.65 : 1) : 0.4)
    }
}

struct SketchFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration.padding(.horizontal, 12).padding(.vertical, 12)
            .stickerSurface(Color(red: 1, green: 0.993, blue: 0.965))
    }
}

extension Font {
    static func ink(_ size: CGFloat, relativeTo style: Font.TextStyle = .body) -> Font {
        .custom("HelveticaNeue", size: size, relativeTo: style)
    }
}
