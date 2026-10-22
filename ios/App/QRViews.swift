// SPDX-License-Identifier: GPL-3.0-or-later
import AVFoundation
import CoreImage.CIFilterBuiltins
import PhotosUI
import SwiftUI
import Vision

struct ScanJoinView: View {
    let language: String
    let onPayload: (QRPayload) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var photo: PhotosPickerItem?
    @State private var error: String?
    @State private var processing = false
    private func accept(_ code: String) {
        do { onPayload(try QRPayload.parse(code)) }
        catch { self.error = error.localizedDescription }
    }
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                CameraScanner(language: language, onCode: accept)
                    .frame(maxHeight: 420).clipShape(RoundedRectangle(cornerRadius: 22))
                Text(tr("将入网二维码放入相机画面，或从图片识别。", language)).multilineTextAlignment(.center)
                PhotosPicker(selection: $photo, matching: .images) {
                    Label(tr("从图片识别", language), systemImage: "photo")
                }.buttonStyle(.bordered).disabled(processing)
                if processing { ProgressView() }
                if let error { Text(tr(error, language)).foregroundStyle(.red).font(.callout) }
                Text(tr("识别在设备本地完成，不上传图片。拒绝相机权限仍可从图片识别。", language)).font(.footnote).foregroundStyle(.secondary)
                Spacer()
            }.padding(24).background(nestCream)
                .navigationTitle(tr("扫码入网", language)).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("取消", language)) { dismiss() } } }
                .onChange(of: photo) { _, item in
                    guard let item else { return }; processing = true; error = nil
                    Task {
                        defer { processing = false; photo = nil }
                        do {
                            guard let data = try await item.loadTransferable(type: Data.self), data.count <= 30_000_000 else {
                                throw SettingsError.message("无法读取图片或图片过大")
                            }
                            let codes = try await Task.detached {
                                try QRImageCodec.decode(data)
                            }.value
                            guard codes.count == 1, let code = codes.first else { throw SettingsError.message("请选择只包含一个入网二维码的图片") }
                            accept(code)
                        } catch { self.error = error.localizedDescription }
                    }
                }
        }
    }
}

private struct CameraScanner: UIViewControllerRepresentable {
    let language: String
    let onCode: (String) -> Void
    func makeUIViewController(context: Context) -> CameraController { CameraController(language: language, onCode: onCode) }
    func updateUIViewController(_ uiViewController: CameraController, context: Context) {}
}

private final class CameraController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "app.nestlink.scanner")
    private var preview: AVCaptureVideoPreviewLayer?
    private var visible = false
    private var configured = false
    private var detected = false
    private let language: String
    private let onCode: (String) -> Void
    private let label = UILabel()
    init(language: String, onCode: @escaping (String) -> Void) { self.language = language; self.onCode = onCode; super.init(nibName: nil, bundle: nil) }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = .black
        label.text = tr("正在准备相机", language); label.textColor = .white; label.numberOfLines = 0; label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(label)
        NSLayoutConstraint.activate([label.centerXAnchor.constraint(equalTo: view.centerXAnchor), label.centerYAnchor.constraint(equalTo: view.centerYAnchor), label.widthAnchor.constraint(lessThanOrEqualTo: view.widthAnchor, multiplier: 0.85)])
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated); visible = true
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configureCamera()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] allowed in
                DispatchQueue.main.async { if allowed { self?.configureCamera() } else { self?.showUnavailable() } }
            }
        default: showUnavailable()
        }
    }
    private func showUnavailable() { label.text = tr("相机不可用，请使用下方的从图片识别。", language) }
    private func configureCamera() {
        guard visible else { return }
        queue.async {
            if !self.configured {
                guard let camera = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: camera), self.session.canAddInput(input) else {
                    DispatchQueue.main.async { self.showUnavailable() }; return
                }
                self.session.beginConfiguration(); self.session.addInput(input)
                let output = AVCaptureMetadataOutput()
                guard self.session.canAddOutput(output) else { self.session.commitConfiguration(); DispatchQueue.main.async { self.showUnavailable() }; return }
                self.session.addOutput(output); output.setMetadataObjectsDelegate(self, queue: .main); output.metadataObjectTypes = [.qr]
                self.session.commitConfiguration(); self.configured = true
            }
            DispatchQueue.main.async {
                guard self.visible else { return }
                if self.preview == nil {
                    let layer = AVCaptureVideoPreviewLayer(session: self.session); layer.videoGravity = .resizeAspectFill
                    self.view.layer.insertSublayer(layer, at: 0); self.preview = layer; self.view.setNeedsLayout()
                }
                self.label.isHidden = true
                self.queue.async { self.session.startRunning() }
            }
        }
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews(); preview?.frame = view.bounds
        if let connection = preview?.connection {
            let angle: CGFloat
            switch view.window?.windowScene?.interfaceOrientation {
            case .landscapeLeft: angle = 0
            case .landscapeRight: angle = 180
            case .portraitUpsideDown: angle = 270
            default: angle = 90
            }
            if connection.isVideoRotationAngleSupported(angle) { connection.videoRotationAngle = angle }
        }
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated); visible = false
        let session = session; queue.async { session.stopRunning() }
    }
    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard visible, !detected, let code = metadataObjects.compactMap({ ($0 as? AVMetadataMachineReadableCodeObject)?.stringValue }).first else { return }
        detected = true; onCode(code)
        // An invalid code can be replaced by another scan without closing the camera.
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in self?.detected = false }
    }
}

struct JoinCodeView: View {
    let payload: String
    let language: String
    @Environment(\.dismiss) private var dismiss
    private var image: UIImage? {
        guard let cg = try? QRImageCodec.render(payload) else { return nil }
        return UIImage(cgImage: cg)
    }
    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                if let image {
                    Image(uiImage: image).interpolation(.none).resizable().scaledToFit().frame(maxWidth: 320, maxHeight: 320).padding(24).background(.white)
                }
                Text(tr("用另一台设备扫描，确认后填入设置。", language)).multilineTextAlignment(.center)
                Text(tr("仅包含网络、NAS 和 Moon 参数，不包含设备身份私钥或管理 Token。", language)).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                ShareLink(item: payload) { Label(tr("分享入网信息", language), systemImage: "square.and.arrow.up") }
                Spacer()
            }.padding(28).background(nestCream)
                .navigationTitle(tr("显示入网码", language)).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("完成", language)) { dismiss() } } }
        }
    }
}
