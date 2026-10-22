// SPDX-License-Identifier: GPL-3.0-or-later
import CoreImage.CIFilterBuiltins
import Foundation
import ImageIO
import Vision

enum QRImageCodec {
    static func render(_ payload: String) throws -> CGImage {
        let filter = CIFilter.qrCodeGenerator(); filter.message = Data(payload.utf8); filter.correctionLevel = "M"
        guard let code = filter.outputImage else { throw SettingsError.message("无法生成入网码") }
        let bounds = code.extent.insetBy(dx: -4, dy: -4)
        let background = CIImage(color: .white).cropped(to: bounds)
        let output = code.composited(over: background).transformed(by: CGAffineTransform(scaleX: 8, y: 8))
        guard let image = CIContext().createCGImage(output, from: output.extent) else { throw SettingsError.message("无法生成入网码") }
        return image
    }
    static func decode(_ data: Data) throws -> [String] {
        guard data.count <= 30_000_000, let source = CGImageSourceCreateWithData(data as CFData, nil),
              let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceThumbnailMaxPixelSize: 2400,
                kCGImageSourceCreateThumbnailWithTransform: true
              ] as CFDictionary) else { throw SettingsError.message("无法读取图片或图片过大") }
        let request = VNDetectBarcodesRequest(); request.symbologies = [.qr]
        try VNImageRequestHandler(cgImage: image).perform([request])
        return Array(Set((request.results ?? []).compactMap(\.payloadStringValue)))
    }
}
