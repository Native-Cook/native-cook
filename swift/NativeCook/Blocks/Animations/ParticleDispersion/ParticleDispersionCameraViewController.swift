import AVFoundation
import UIKit

final class ParticleDispersionCameraViewController: UIViewController, UIGestureRecognizerDelegate {
  private enum ParticleDispersionPhase { case preview, capturing, analyzing, effect, result, error }
  private let canvas = ParticleDispersionDispersionView()
  private let camera = ParticleDispersionCameraService()
  private let processor = ParticleDispersionSubjectProcessor()
  private let processing = DispatchQueue(label: "particle.processing", qos: .userInitiated)
  private let preview = AVCaptureVideoPreviewLayer()
  private let dateLabel = UILabel()
  private let motionButton = UIButton(type: .system)
  private let dock = UIVisualEffectView(effect: UIBlurEffect(style: .systemMaterialLight))
  private let instruction = UILabel()
  private let primary = ParticleDispersionShutterButton()
  private let left = UIButton(type: .system)
  private let right = UIButton(type: .system)
  private let spinner = UIActivityIndicatorView(style: .medium)
  private var phase = ParticleDispersionPhase.preview
  private var scene: ParticleDispersionEffectScene?
  private var requestID = UUID().uuidString
  private var reduceMotionOverride = false
  private var cameraReady = false
  private var errorMessage = ""
  private var active = true
  private var zoomFactor: CGFloat = 1
  private var pinchStart: CGFloat = 1

  override var prefersStatusBarHidden: Bool { true }
  override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .portrait }

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = .black
    preview.session = camera.session
    preview.videoGravity = .resizeAspectFill
    view.layer.addSublayer(preview)
    let focusGesture = UITapGestureRecognizer(target: self, action: #selector(focusCamera(_:)))
    let zoomGesture = UIPinchGestureRecognizer(target: self, action: #selector(zoomCamera(_:)))
    for gesture in [focusGesture, zoomGesture] {
      gesture.delegate = self
      gesture.cancelsTouchesInView = false
      view.addGestureRecognizer(gesture)
    }
    view.addSubview(canvas)
    canvas.onComplete = { [weak self] in self?.setPhase(.result) }
    canvas.onError = { [weak self] error in self?.showError(error) }
    dateLabel.font = UIFont(name: "Georgia", size: 20)
    dateLabel.textColor = .white
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US")
    formatter.dateFormat = "MMM d"
    dateLabel.text = formatter.string(from: Date())
    dateLabel.isAccessibilityElement = false
    view.addSubview(dateLabel)

    for button in [motionButton] {
      button.tintColor = .white
      button.titleLabel?.font = .systemFont(ofSize: 12, weight: .medium)
      button.backgroundColor = UIColor.black.withAlphaComponent(0.28)
      button.layer.cornerRadius = 16
      view.addSubview(button)
    }
    motionButton.accessibilityIdentifier = "reduced-motion-toggle"
    motionButton.accessibilityLabel = "Reduced motion"
    motionButton.addTarget(self, action: #selector(toggleMotion), for: .touchUpInside)

    dock.clipsToBounds = true
    dock.layer.cornerRadius = 52
    dock.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
    dock.contentView.backgroundColor = UIColor(red: 0.97, green: 0.96, blue: 0.98, alpha: 0.80)
    view.addSubview(dock)
    let texture = ParticleDispersionDockTexture()
    texture.autoresizingMask = [.flexibleWidth, .flexibleHeight]
    dock.contentView.addSubview(texture)
    instruction.font = .systemFont(ofSize: 17)
    instruction.textColor = UIColor(white: 0.53, alpha: 1)
    instruction.numberOfLines = 2
    instruction.textAlignment = .center
    instruction.accessibilityIdentifier = "effect-status"
    instruction.accessibilityTraits = .updatesFrequently
    dock.contentView.addSubview(instruction)
    dock.contentView.addSubview(primary)
    primary.addTarget(self, action: #selector(primaryAction), for: .touchUpInside)
    for button in [left, right] {
      button.backgroundColor = UIColor(red: 0.87, green: 0.86, blue: 0.87, alpha: 1)
      button.tintColor = .white
      button.layer.cornerRadius = 27
      dock.contentView.addSubview(button)
    }
    right.addTarget(self, action: #selector(retake), for: .touchUpInside)
    spinner.hidesWhenStopped = true
    spinner.color = UIColor(white: 0.55, alpha: 1)
    primary.addSubview(spinner)
    NotificationCenter.default.addObserver(self, selector: #selector(suspend),
                                          name: UIApplication.willResignActiveNotification, object: nil)
    NotificationCenter.default.addObserver(self, selector: #selector(resume),
                                          name: UIApplication.didBecomeActiveNotification, object: nil)
    startCamera()
    updateControls()
  }

  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    let width = view.bounds.width
    let height = view.bounds.height
    preview.frame = view.bounds
    canvas.frame = view.bounds
    let top = view.safeAreaInsets.top + 34
    dateLabel.frame = CGRect(x: 24, y: top, width: 140, height: 28)
    motionButton.frame = CGRect(x: width - 174, y: top - 1, width: 150, height: 32)
    let panelHeight = max(224, 190 + max(view.safeAreaInsets.bottom, 12))
    dock.frame = CGRect(x: 0, y: height - panelHeight, width: width, height: panelHeight)
    dock.contentView.subviews.first?.frame = dock.bounds
    instruction.frame = CGRect(x: 28, y: 25, width: width - 56, height: 44)
    primary.frame = CGRect(x: (width - 106) / 2, y: 82, width: 106, height: 106)
    spinner.center = CGPoint(x: 53, y: 53)
    let rowWidth = min(width, 370)
    left.frame = CGRect(x: (width - rowWidth) / 2 + 26, y: 108, width: 54, height: 54)
    right.frame = CGRect(x: (width + rowWidth) / 2 - 80, y: 108, width: 54, height: 54)
  }

  private var reducedMotion: Bool { reduceMotionOverride || UIAccessibility.isReduceMotionEnabled }

  private func setPhase(_ value: ParticleDispersionPhase) {
    phase = value
    updateControls()
  }

  private func updateControls() {
    let modeChanged = primary.resultMode != (phase == .result)
    motionButton.setTitle("Reduce motion: \(reducedMotion ? "On" : "Off")", for: .normal)
    motionButton.accessibilityValue = reducedMotion ? "On" : "Off"
    motionButton.isEnabled = phase == .preview || phase == .result || phase == .error
    primary.resultMode = phase == .result
    primary.reduced = reducedMotion
    primary.isEnabled = phase == .result || phase == .error || (phase == .preview && cameraReady)
    primary.alpha = primary.isEnabled ? 1 : 0.65
    primary.accessibilityIdentifier = phase == .result ? "replay-button" : (phase == .preview ? "capture-button" : "effect-primary")
    primary.accessibilityLabel = phase == .result ? "Replay effect" : (phase == .error ? "Try again" : "Take photo")
    left.setImage(UIImage(systemName: phase == .result ? "crop" : "chevron.down"), for: .normal)
    left.isEnabled = false
    left.accessibilityLabel = "Crop unavailable in this demo"
    right.setImage(UIImage(systemName: phase == .result ? "xmark" : "photo"), for: .normal)
    right.accessibilityIdentifier = "retake-button"
    right.accessibilityLabel = phase == .result ? "Discard photo and retake" : "Photo library unavailable in this demo"
    right.isEnabled = phase == .result
    let busy = phase == .capturing || phase == .analyzing || phase == .effect
    if busy { spinner.startAnimating() } else { spinner.stopAnimating() }
    switch phase {
    case .preview:
      instruction.text = cameraReady ? "Center your subject in the frame\nTap to capture!" : "Opening camera…\nPlease wait a moment"
    case .capturing: instruction.text = "Hold still for a moment…\nTaking your photo"
    case .analyzing: instruction.text = "Finding your subject…\nEverything stays on your device"
    case .effect: instruction.text = "The background is turning into particles…\nYour effect will be ready in a few seconds"
    case .result: instruction.text = "Tap the checkmark to replay\nTap cancel to take another photo"
    case .error: instruction.text = errorMessage + "\nTap to try again"
    }
    view.accessibilityIdentifier = phase == .result ? "result-screen" : "camera-screen"
    if modeChanged {
      for control in [primary, left, right] {
        let targetAlpha = control.alpha
        control.alpha = 0
        if !reducedMotion { control.transform = CGAffineTransform(scaleX: 0.92, y: 0.92) }
        UIView.animate(withDuration: 0.18, delay: 0, options: [.beginFromCurrentState, .curveEaseOut]) {
          control.alpha = targetAlpha
          control.transform = .identity
        }
      }
    }
  }

  func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
    phase == .preview && cameraReady &&
      gestureRecognizer.location(in: view).y < dock.frame.minY &&
      gestureRecognizer.location(in: view).y > motionButton.frame.maxY + 12
  }

  @objc private func focusCamera(_ gesture: UITapGestureRecognizer) {
    camera.focus(at: preview.captureDevicePointConverted(fromLayerPoint: gesture.location(in: view)))
  }

  @objc private func zoomCamera(_ gesture: UIPinchGestureRecognizer) {
    if gesture.state == .began { pinchStart = zoomFactor }
    zoomFactor = min(max(pinchStart * gesture.scale, 1), 8)
    camera.zoom(to: zoomFactor)
  }

  private func startCamera() {
    cameraReady = false
    updateControls()
    let request = requestID
    switch AVCaptureDevice.authorizationStatus(for: .video) {
    case .authorized: openCamera(request: request)
    case .notDetermined:
      AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
        DispatchQueue.main.async {
          guard let self, self.requestID == request, self.active else { return }
          if granted { self.openCamera(request: request) }
          else { self.cameraPermissionError() }
        }
      }
    default: cameraPermissionError()
    }
  }

  private func openCamera(request: String) {
    camera.start { [weak self] result in
      guard let self, self.requestID == request, self.active else { return }
      switch result {
      case .success:
        self.cameraReady = true
        self.canvas.isHidden = true
        self.scene?.release()
        self.scene = nil
        self.setPhase(.preview)
        if !self.active { self.camera.stop() }
      case .failure(let error): self.showError(error)
      }
    }
  }

  private func cameraPermissionError() {
    errorMessage = "Camera access is disabled. Enable it in Settings."
    setPhase(.error)
  }

  @objc private func primaryAction() {
    UIImpactFeedbackGenerator(style: .soft).impactOccurred()
    if phase == .error {
      if [.denied, .restricted].contains(AVCaptureDevice.authorizationStatus(for: .video)),
         let settings = URL(string: UIApplication.openSettingsURLString) {
        UIApplication.shared.open(settings)
      } else { retake() }
      return
    }
    if phase == .result {
      setPhase(.effect)
      canvas.play(reducedMotion: reducedMotion)
      return
    }
    guard phase == .preview, cameraReady else { return }
    setPhase(.capturing)
    let request = requestID
    camera.capture { [weak self] result in
      guard let self, self.requestID == request else {
        if case .success(let url) = result { try? FileManager.default.removeItem(at: url) }
        return
      }
      switch result {
      case .success(let url): self.process(url, request: request)
      case .failure(let error): self.showError(error)
      }
    }
  }

  private func process(_ source: URL, request: String) {
    setPhase(.analyzing)
    let viewport = view.bounds.size
    let pixelRatio = view.window?.screen.scale ?? traitCollection.displayScale
    let scale = min(pixelRatio, 1600 / max(viewport.width, viewport.height))
    let width = max(2, Int((viewport.width * scale).rounded()) / 2 * 2)
    let height = max(2, Int((viewport.height * scale).rounded()) / 2 * 2)
    processing.async { [weak self] in
      guard let self else { return }
      var owned: [String] = []
      defer { try? FileManager.default.removeItem(at: source) }
      do {
        let frame = try self.processor.prepareEffectFrame(ParticleDispersionPrepareEffectFrameOptions(
          sourceUri: source.absoluteString, requestId: request, targetWidth: width, targetHeight: height))
        let frameURI = frame["frameUri"] as! String
        owned.append(frameURI)
        let mask = try self.processor.segmentEffectFrame(ParticleDispersionSegmentEffectFrameOptions(frameUri: frameURI, requestId: request))
        let maskURI = mask["maskUri"] as! String
        owned.append(maskURI)
        let b = mask["subjectBounds"] as! [String: Double]
        let prepared = ParticleDispersionEffectScene(frame: URL(string: frameURI)!, mask: URL(string: maskURI)!,
          imageSize: CGSize(width: width, height: height),
          subjectBounds: CGRect(x: b["x"]!, y: b["y"]!, width: b["width"]!, height: b["height"]!), ownsFiles: true)
        DispatchQueue.main.async {
          guard self.requestID == request else { prepared.release(); return }
          do {
            try self.canvas.show(prepared)
            self.scene?.release()
            self.scene = prepared
            self.canvas.isHidden = false
            self.camera.stop()
            self.setPhase(.effect)
            self.canvas.play(reducedMotion: self.reducedMotion)
            if !self.active { self.canvas.suspend() }
          } catch { prepared.release(); self.showError(error) }
        }
      } catch {
        try? self.processor.releaseOwnedFiles(owned)
        DispatchQueue.main.async {
          if self.requestID == request { self.showError(error) }
        }
      }
    }
  }

  @objc private func retake() {
    processor.cancel(requestID)
    requestID = UUID().uuidString
    canvas.stop()
    setPhase(.preview)
    startCamera()
  }

  @objc private func toggleMotion() {
    reduceMotionOverride.toggle()
    updateControls()
  }

  private func showError(_ error: Error) {
    canvas.stop()
    errorMessage = error.localizedDescription
    NSLog("[ParticleNative] %@", error.localizedDescription)
    setPhase(.error)
  }

  @objc func suspend() {
    active = false
    canvas.suspend()
    camera.stop()
  }

  @objc func resume() {
    guard viewIfLoaded?.window != nil else { return }
    active = true
    canvas.resume()
    if phase == .preview { startCamera() }
  }

  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    suspend()
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    resume()
  }

  func shutdown() {
    active = false
    NotificationCenter.default.removeObserver(self)
    processor.cancel(requestID)
    requestID = UUID().uuidString
    canvas.onComplete = nil
    canvas.onError = nil
    canvas.stop()
    camera.stop()
    scene?.release()
    scene = nil
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
    processor.cancel(requestID)
    scene?.release()
    camera.stop()
  }
}

private final class ParticleDispersionShutterButton: UIButton {
  var resultMode = false { didSet { setNeedsDisplay() } }
  var reduced = false
  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false
    backgroundColor = .clear
    layer.cornerRadius = 53
    layer.shadowColor = UIColor.black.cgColor
    layer.shadowOpacity = 0.10
    layer.shadowRadius = 13
    layer.shadowOffset = CGSize(width: 0, height: 3)
  }
  required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }
  override var isHighlighted: Bool {
    didSet {
      UIView.animate(withDuration: 0.1) {
        self.transform = self.isHighlighted && !self.reduced ? CGAffineTransform(scaleX: 0.92, y: 0.92) : .identity
      }
    }
  }
  override func draw(_ rect: CGRect) {
    guard let context = UIGraphicsGetCurrentContext() else { return }
    UIColor(red: 1, green: 0.992, blue: 1, alpha: 0.96).setFill()
    UIBezierPath(ovalIn: bounds).fill()
    context.setLineCap(.round)
    context.setLineWidth(5)
    if resultMode {
      context.setStrokeColor(UIColor(red: 0.72, green: 0.69, blue: 0.92, alpha: 1).cgColor)
      context.move(to: CGPoint(x: 37, y: 53))
      context.addLine(to: CGPoint(x: 49, y: 64))
      context.addLine(to: CGPoint(x: 70, y: 42))
      context.strokePath()
    } else {
      let arcs: [(UInt32, CGFloat, CGFloat)] = [(0xF0F885, -112, 50), (0x9FD8BC, -50, 58),
        (0xB5ADE4, 20, 78), (0xBBDCFC, 112, 52), (0xFBC8EC, 176, 54)]
      for (color, start, sweep) in arcs {
        context.setStrokeColor(UIColor(red: CGFloat((color >> 16) & 255) / 255,
          green: CGFloat((color >> 8) & 255) / 255, blue: CGFloat(color & 255) / 255, alpha: 1).cgColor)
        context.addArc(center: CGPoint(x: 53, y: 53), radius: 45, startAngle: start * .pi / 180,
                       endAngle: (start + sweep) * .pi / 180, clockwise: false)
        context.strokePath()
      }
    }
  }
}

private final class ParticleDispersionDockTexture: UIView {
  override init(frame: CGRect) { super.init(frame: frame); backgroundColor = .clear; isUserInteractionEnabled = false }
  required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }
  override func draw(_ rect: CGRect) {
    UIColor(red: 0.41, green: 0.37, blue: 0.41, alpha: 0.10).setFill()
    for row in 0..<11 {
      for column in 0..<20 {
        UIBezierPath(ovalIn: CGRect(x: (CGFloat(column) + 0.5) * bounds.width / 20,
          y: (CGFloat(row) + 0.5) * bounds.height / 11, width: 2, height: 2)).fill()
      }
    }
  }
}
