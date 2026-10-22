// Package the existing NestLink artwork as an opaque iOS app icon.
import Foundation
import CoreGraphics
import ImageIO

let root = URL(fileURLWithPath: #filePath).standardizedFileURL.deletingLastPathComponent().deletingLastPathComponent()
let source = root.appendingPathComponent("branding/nestlink-icon.png")
let folder = root.appendingPathComponent("ios/App/Assets.xcassets/AppIcon.appiconset")
func failure(_ message: String) -> NSError {
    NSError(domain: "NestLink.Icon", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
}
guard let input = CGImageSourceCreateWithURL(source as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(input, 0, nil) else {
    throw failure("Cannot load artwork at \(source.path)")
}
guard let context = CGContext(data: nil, width: 1024, height: 1024,
                              bitsPerComponent: 8, bytesPerRow: 4096,
                              space: CGColorSpaceCreateDeviceRGB(),
                              bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
    throw failure("Cannot create icon bitmap")
}
context.setFillColor(CGColor(red: 0.99, green: 0.97, blue: 0.94, alpha: 1))
context.fill(CGRect(x: 0, y: 0, width: 1024, height: 1024))
context.interpolationQuality = .high
context.draw(image, in: CGRect(x: 0, y: 0, width: 1024, height: 1024))
try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
guard let result = context.makeImage(),
      let output = CGImageDestinationCreateWithURL(folder.appendingPathComponent("AppIcon.png") as CFURL, "public.png" as CFString, 1, nil) else {
    throw failure("Cannot create PNG output")
}
CGImageDestinationAddImage(output, result, nil)
guard CGImageDestinationFinalize(output) else { throw failure("Cannot write icon") }
print("Generated 1024×1024 opaque NestLink app icon")
