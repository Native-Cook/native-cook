// Louver Theme Switch — a theme change where the whole screen turns like a louver.
//
// The reusable piece. Give it a screen builder that draws your screen for a theme; it keeps both themes
// mounted as real views, one over the other, snapshots each once (and again when `contentKey`
// changes), and during a change one Metal shader (LouverThemeSwitch.metal) turns fourteen slats in
// perspective, each with the light screen on its front and the dark one on its back. At rest the Metal
// view is cleared to transparent and the live screen takes touches.
//
// autoPlay: loop = 8 s. A ghost finger taps Dark, the slats turn top to bottom, the dark screen holds,
// the finger taps Light and they turn back; frame 8 s equals frame 0. The control, or a vertical drag
// that scrubs the turn and commits or springs back on release, takes over at once; 2 s after the last
// touch the screen returns to light and the loop restarts at t = 0. Reduced motion: the theme changes
// at once, no turn and no loop.
//
// Thread model: one display link on the main thread advances the turn, moves the ghost and draws the
// Metal view while a turn is under way; SwiftUI hears only when the turn crosses its middle.

import MetalKit
import SwiftUI
import UIKit

enum LouverTheme: Hashable {
    case light, dark
}

/// what a screen is told about itself
struct LouverScreen {
    let theme: LouverTheme
    /// ask for a theme; the louver turns to it
    let select: (LouverTheme) -> Void
    /// changes when this screen has gone out of sight: give a native control `.id(resetKey)` so it shows this screen's theme again
    let resetKey: Int
    /// set on the light screen only: where the control is, so the autoplay's finger knows where to tap
    let report: ((CGRect) -> Void)?
}

extension View {
    /// put this on the theme control, so the autoplay's finger knows where to tap
    func louverControl(_ screen: LouverScreen) -> some View {
        onGeometryChange(for: CGRect.self, of: { $0.frame(in: .global) }) { rect in
            screen.report?(rect)
        }
    }
}

struct LouverThemeSwitch<Content: View>: View {
    static var loop: Double { LouverThemeSwitchMotion.loop }

    private let autoPlay: Bool
    private let contentKey: AnyHashable
    private let screen: (LouverScreen) -> Content
    private let onThemeChange: ((LouverTheme) -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var side: LouverTheme = .light

    init(autoPlay: Bool = true, contentKey: AnyHashable = 0, onThemeChange: ((LouverTheme) -> Void)? = nil, @ViewBuilder screen: @escaping (LouverScreen) -> Content) {
        self.autoPlay = autoPlay
        self.contentKey = contentKey
        self.screen = screen
        self.onThemeChange = onThemeChange
    }

    var body: some View {
        LouverThemeSwitchStage(autoPlay: autoPlay, reduced: reduced, contentKey: contentKey, screen: screen) { next in
            side = next
            onThemeChange?(next)
        }
        .ignoresSafeArea()
        .background(Color(hex: 0x0E0F15).ignoresSafeArea())
        .preferredColorScheme(side == .dark ? .dark : .light)
    }
}

private struct LouverThemeSwitchStage<Content: View>: UIViewControllerRepresentable {
    let autoPlay: Bool
    let reduced: Bool
    let contentKey: AnyHashable
    let screen: (LouverScreen) -> Content
    let onSide: (LouverTheme) -> Void

    func makeUIViewController(context: Context) -> LouverThemeSwitchController {
        LouverThemeSwitchController { AnyView(screen($0)) }
    }

    func updateUIViewController(_ controller: LouverThemeSwitchController, context: Context) {
        controller.build = { AnyView(screen($0)) }
        controller.onSide = onSide
        controller.configure(autoPlay: autoPlay, reduced: reduced, contentKey: contentKey)
    }

    static func dismantleUIViewController(_ controller: LouverThemeSwitchController, coordinator: ()) {
        controller.stop()
    }
}

/// the display link's target, so the link does not keep the controller alive
private final class LouverThemeSwitchLinkProxy {
    weak var target: AnyObject?
    let fire: (AnyObject, CADisplayLink) -> Void
    init(target: AnyObject, fire: @escaping (AnyObject, CADisplayLink) -> Void) {
        self.target = target
        self.fire = fire
    }
    @objc func step(_ link: CADisplayLink) {
        if let target { fire(target, link) } else { link.invalidate() }
    }
}

final class LouverThemeSwitchController: UIViewController, UIGestureRecognizerDelegate, MTKViewDelegate {
    private typealias M = LouverThemeSwitchMotion
    private enum Mode { case auto, touch, returning }
    private enum Run { case none, linear, spring }

    var build: (LouverScreen) -> AnyView
    var onSide: ((LouverTheme) -> Void)?

    private var autoPlay = true
    private var reduced = false
    private var contentKey: AnyHashable?
    private var canTurn: Bool { !reduced && pipeline != nil }
    private var auto: Bool { autoPlay && canTurn }

    // views
    private var lightHost: UIHostingController<AnyView>!
    private var darkHost: UIHostingController<AnyView>!
    private let metal = MTKView()
    private let ghost = UIView()
    private var link: CADisplayLink?

    // Metal
    private let device = MTLCreateSystemDefaultDevice()
    private var queue: MTLCommandQueue?
    private var pipeline: MTLRenderPipelineState?
    private var sampler: MTLSamplerState?
    private var pages: (light: MTLTexture, dark: MTLTexture)?
    private var snapshotTask: DispatchWorkItem?
    private var snappedSize = CGSize.zero
    /// the Metal view shows a turn (false once it has been cleared at rest)
    private var covering = false

    // the turn
    private var clock = 0.0
    private var lastStamp: CFTimeInterval?
    private var mode = Mode.auto
    private var autoStart = 0.0
    private var ready = false
    private var amount = 0.0
    private var dir = 1.0
    private var side = 0
    private var resets = (light: 0, dark: 0)
    private var down = false
    private var grabFrom = 0.0
    private var lastTouch = 0.0
    private var running = Run.none
    private var runAt = 0.0
    private var runFrom = 0.0
    private var runTo = 0.0
    private var runSpeed = 0.0
    private var runFor = 0.0
    /// the control in this view's coordinates
    private var control = CGRect.zero
    private let haptic = UIImpactFeedbackGenerator(style: .light)

    private static var ghostSize: CGFloat { 44 }

    init(build: @escaping (LouverScreen) -> AnyView) {
        self.build = build
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.055, green: 0.059, blue: 0.082, alpha: 1)
        view.clipsToBounds = true

        lightHost = UIHostingController(rootView: build(screen(.light)))
        darkHost = UIHostingController(rootView: build(screen(.dark)))
        lightHost.overrideUserInterfaceStyle = .light
        darkHost.overrideUserInterfaceStyle = .dark
        for host in [darkHost!, lightHost!] {
            addChild(host)
            host.view.frame = view.bounds
            host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            host.view.backgroundColor = .clear
            view.addSubview(host.view)
            host.didMove(toParent: self)
        }
        darkHost.view.isUserInteractionEnabled = false

        setUpMetal()
        metal.frame = view.bounds
        metal.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(metal)

        setUpGhost()
        view.addSubview(ghost)

        let pan = UIPanGestureRecognizer(target: self, action: #selector(scrub(_:)))
        pan.maximumNumberOfTouches = 1
        pan.delegate = self
        view.addGestureRecognizer(pan)

        let proxy = LouverThemeSwitchLinkProxy(target: self) { target, link in
            (target as? LouverThemeSwitchController)?.tick(link)
        }
        let link = CADisplayLink(target: proxy, selector: #selector(LouverThemeSwitchLinkProxy.step(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
        link.add(to: .main, forMode: .common)
        self.link = link
    }

    func stop() {
        link?.invalidate()
        link = nil
        snapshotTask?.cancel()
    }

    // MARK: configuration

    func configure(autoPlay: Bool, reduced: Bool, contentKey: AnyHashable) {
        self.autoPlay = autoPlay
        if reduced != self.reduced {
            self.reduced = reduced
            if reduced {
                mode = .touch
                running = .none
                ghost.alpha = 0
            }
        }
        let changed = contentKey != self.contentKey
        self.contentKey = contentKey
        refresh()
        if changed { scheduleSnapshot() }
    }

    private func screen(_ theme: LouverTheme) -> LouverScreen {
        LouverScreen(
            theme: theme,
            select: { [weak self] next in self?.select(next) },
            resetKey: theme == .light ? resets.light : resets.dark,
            report: theme == .light ? { [weak self] rect in self?.controlMoved(rect) } : nil
        )
    }

    private func refresh() {
        guard isViewLoaded else { return }
        lightHost.rootView = build(screen(.light))
        darkHost.rootView = build(screen(.dark))
    }

    private func controlMoved(_ rect: CGRect) {
        control = view.window.map { view.convert(rect, from: $0.coordinateSpace) } ?? rect
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if view.bounds.size != snappedSize, view.bounds.width > 0 { scheduleSnapshot() }
    }

    // MARK: snapshots

    /// let native controls lay out and finish animating first, like the RN block
    private func scheduleSnapshot() {
        guard isViewLoaded, !reduced else { return }
        snapshotTask?.cancel()
        let task = DispatchWorkItem { [weak self] in self?.snapshot() }
        snapshotTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.38, execute: task)
    }

    private func snapshot() {
        guard let device, view.window != nil, view.bounds.width > 0 else { return }
        let bounds = view.bounds
        let format = UIGraphicsImageRendererFormat()
        format.scale = view.window?.screen.scale ?? view.traitCollection.displayScale
        format.opaque = true
        format.preferredRange = .standard
        let renderer = UIGraphicsImageRenderer(bounds: bounds, format: format)
        func shot(_ host: UIHostingController<AnyView>) -> CGImage? {
            renderer.image { _ in _ = host.view.drawHierarchy(in: bounds, afterScreenUpdates: true) }.cgImage
        }
        guard let light = shot(lightHost), let dark = shot(darkHost) else { return }
        let loader = MTKTextureLoader(device: device)
        let options: [MTKTextureLoader.Option: Any] = [
            .SRGB: false,
            .textureUsage: MTLTextureUsage.shaderRead.rawValue,
            .textureStorageMode: MTLStorageMode.private.rawValue,
        ]
        guard let lightTexture = try? loader.newTexture(cgImage: light, options: options),
              let darkTexture = try? loader.newTexture(cgImage: dark, options: options) else { return }
        pages = (lightTexture, darkTexture)
        snappedSize = bounds.size
        ready = true
    }

    // MARK: the frame

    private func tick(_ link: CADisplayLink) {
        let stamp = link.timestamp
        let dt = min(0.05, max(0, lastStamp.map { stamp - $0 } ?? (1.0 / 60)))
        lastStamp = stamp
        clock += dt
        let now = clock
        var ghostAlpha = 0.0
        var ghostPress = 0.0
        var ghostOnDark = true

        if mode == .auto {
            if !ready || !auto {
                autoStart = now
            } else {
                let t = (now - autoStart).truncatingRemainder(dividingBy: M.loop)
                amount = M.filmAmount(t)
                dir = M.filmDir(t)
                let g = M.ghostAt(t)
                ghostAlpha = g.alpha
                ghostPress = g.press
                ghostOnDark = g.onDark
            }
        } else if running != .none {
            let tau = now - runAt
            var x: Double
            let done: Bool
            if running == .linear {
                x = runFrom + (runTo - runFrom) * M.seg(tau, 0, max(runFor, 0.001))
                done = tau >= runFor
            } else {
                x = M.clamp01(M.settleAt(runFrom, runSpeed, runTo, tau))
                done = abs(x - runTo) < 0.002 && tau > 0.05
            }
            if done { x = runTo }
            amount = x
            if done {
                running = .none
                if mode == .returning {
                    autoStart = now
                    mode = .auto
                }
            }
        } else if mode == .touch, auto, !down, now - lastTouch >= M.idle {
            if amount <= M.rest {
                autoStart = now
                mode = .auto
            } else {
                dir = -1
                runFrom = amount
                runTo = 0
                runFor = M.tapTurn * amount
                runAt = now
                running = .linear
                mode = .returning
            }
        }

        let nextSide = amount > 0.5 ? 1 : 0
        if nextSide != side {
            showSide(nextSide)
            if mode != .auto { haptic.impactOccurred() }
        }

        // the ghost
        ghost.alpha = ghostAlpha
        if ghostAlpha > 0 {
            let s = 1 - 0.16 * ghostPress
            ghost.center = CGPoint(x: control.minX + control.width * (ghostOnDark ? 0.75 : 0.25), y: control.midY)
            ghost.transform = CGAffineTransform(scaleX: s, y: s)
        }

        // the turn: the Metal view covers the live screens until the turn rests on the side on top; at
        // rest it is cleared to transparent (never hidden, so it shows its first slat frame at once)
        let atRest = amount <= M.rest || amount >= 1 - M.rest
        let turning = !(atRest || pages == nil || reduced)
        if turning || covering {
            covering = turning
            metal.draw()
        }
    }

    private func showSide(_ next: Int) {
        side = next
        // the screen going out of sight gets a fresh control
        if next == 1 { resets.light += 1 } else { resets.dark += 1 }
        let top = next == 1 ? darkHost! : lightHost!
        let under = next == 1 ? lightHost! : darkHost!
        view.insertSubview(top.view, aboveSubview: under.view)
        top.view.isUserInteractionEnabled = true
        under.view.isUserInteractionEnabled = false
        refresh()
        onSide?(next == 1 ? .dark : .light)
    }

    // MARK: touch

    private func select(_ theme: LouverTheme) {
        let to = theme == .dark ? 1.0 : 0.0
        guard canTurn else {
            // no turn: the theme changes at once
            mode = .touch
            amount = to
            return
        }
        mode = .touch
        lastTouch = clock
        let from = amount
        guard from != to else { return }
        if from <= M.rest || from >= 1 - M.rest { dir = to > from ? 1 : -1 }
        runFrom = from
        runTo = to
        runFor = M.tapTurn * abs(to - from)
        runAt = clock
        running = .linear
    }

    func gestureRecognizerShouldBegin(_ gesture: UIGestureRecognizer) -> Bool {
        guard canTurn, pages != nil, let pan = gesture as? UIPanGestureRecognizer else { return false }
        let v = pan.velocity(in: view)
        let d = pan.translation(in: view)
        // vertical drags only (RN: active past 12 pt vertically, failed past 18 pt sideways)
        return abs(v.y) + abs(d.y) > abs(v.x) + abs(d.x)
    }

    @objc private func scrub(_ pan: UIPanGestureRecognizer) {
        let height = max(view.bounds.height, 1)
        switch pan.state {
        case .began:
            let from = amount
            mode = .touch
            running = .none
            down = true
            grabFrom = from
            lastTouch = clock
            if from <= M.rest || from >= 1 - M.rest { dir = from < 0.5 ? 1 : -1 }
        case .changed:
            lastTouch = clock
            let ty = Double(pan.translation(in: view).y)
            amount = M.clamp01(grabFrom + dir * abs(ty) / (M.scrub * height))
        case .ended:
            let ty = Double(pan.translation(in: view).y)
            let vy = Double(pan.velocity(in: view).y)
            let away = ty >= 0 ? vy : -vy
            let speed = dir * away / (M.scrub * height)
            let at = amount
            runFrom = at
            runSpeed = max(-5, min(5, speed))
            runTo = at + speed * M.project > 0.5 ? 1 : 0
            runAt = clock
            running = .spring
            down = false
            lastTouch = clock
        case .cancelled, .failed:
            if down, amount > M.rest, amount < 1 - M.rest {
                // cancelled mid-drag: go back to where it began
                runFrom = amount
                runSpeed = 0
                runTo = grabFrom > 0.5 ? 1 : 0
                runAt = clock
                running = .spring
            }
            down = false
            lastTouch = clock
        default:
            break
        }
    }

    // MARK: Metal

    private func setUpMetal() {
        guard let device, let library = device.makeDefaultLibrary() else {
            assertionFailure("Louver Theme Switch: Metal library unavailable")
            return
        }
        metal.device = device
        metal.colorPixelFormat = .bgra8Unorm
        metal.framebufferOnly = true
        metal.isOpaque = false
        metal.layer.isOpaque = false
        metal.backgroundColor = .clear
        metal.clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
        metal.isUserInteractionEnabled = false
        // present with the UIKit transaction, so the slats and the swap of the live screens under them land in one frame
        (metal.layer as? CAMetalLayer)?.presentsWithTransaction = true
        metal.isPaused = true
        metal.enableSetNeedsDisplay = false
        metal.delegate = self
        // the snapshots and the shader's colours are sRGB numbers
        (metal.layer as? CAMetalLayer)?.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        queue = device.makeCommandQueue()
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = library.makeFunction(name: "louverThemeSwitchVertex")
        descriptor.fragmentFunction = library.makeFunction(name: "louverThemeSwitchTurn")
        descriptor.colorAttachments[0].pixelFormat = .bgra8Unorm
        do {
            pipeline = try device.makeRenderPipelineState(descriptor: descriptor)
        } catch {
            assertionFailure("Louver Theme Switch: Metal set-up failed: \(error)")
        }
        let linear = MTLSamplerDescriptor()
        linear.minFilter = .linear
        linear.magFilter = .linear
        linear.sAddressMode = .clampToEdge
        linear.tAddressMode = .clampToEdge
        sampler = device.makeSamplerState(descriptor: linear)
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {}

    func draw(in view: MTKView) {
        guard view.bounds.width > 0, let command = queue?.makeCommandBuffer() else { return }
        guard let drawable = view.currentDrawable, let descriptor = view.currentRenderPassDescriptor,
              let encoder = command.makeRenderCommandEncoder(descriptor: descriptor) else { return }
        guard covering, let pages, let pipeline, let sampler else {
            // at rest: a transparent frame, the live screens show through
            encoder.endEncoding()
            command.commit()
            command.waitUntilScheduled()
            drawable.present()
            return
        }
        let size = view.bounds.size
        var uniforms: [SIMD4<Float>] = [
            SIMD4(Float(size.width), Float(size.height), Float(view.drawableSize.width / size.width), 0),
            SIMD4(Float(amount), Float(dir), 0, 0),
            SIMD4(0.055, 0.059, 0.082, 0),
        ]
        encoder.setRenderPipelineState(pipeline)
        encoder.setFragmentBytes(&uniforms, length: MemoryLayout<SIMD4<Float>>.stride * 3, index: 0)
        encoder.setFragmentTexture(pages.light, index: 0)
        encoder.setFragmentTexture(pages.dark, index: 1)
        encoder.setFragmentSamplerState(sampler, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.commit()
        command.waitUntilScheduled()
        drawable.present()
    }

    // MARK: the ghost finger

    /// reads on both themes: a pale core inside a dark ring
    private func setUpGhost() {
        let size = Self.ghostSize
        ghost.frame = CGRect(x: 0, y: 0, width: size, height: size)
        ghost.isUserInteractionEnabled = false
        ghost.alpha = 0
        ghost.layer.cornerRadius = size / 2
        ghost.backgroundColor = UIColor(red: 120 / 255, green: 118 / 255, blue: 112 / 255, alpha: 0.2)
        ghost.layer.borderWidth = 1.5
        ghost.layer.borderColor = UIColor(red: 120 / 255, green: 118 / 255, blue: 112 / 255, alpha: 0.55).cgColor
        ghost.layer.shadowColor = UIColor(red: 20 / 255, green: 18 / 255, blue: 16 / 255, alpha: 1).cgColor
        ghost.layer.shadowOpacity = 0.22
        ghost.layer.shadowRadius = 6
        ghost.layer.shadowOffset = CGSize(width: 0, height: 2)
        ghost.layer.shadowPath = UIBezierPath(ovalIn: ghost.bounds).cgPath
        let core = UIView(frame: CGRect(x: (size - 14) / 2, y: (size - 14) / 2, width: 14, height: 14))
        core.layer.cornerRadius = 7
        core.backgroundColor = UIColor(red: 120 / 255, green: 118 / 255, blue: 112 / 255, alpha: 0.55)
        ghost.addSubview(core)
        ghost.isAccessibilityElement = false
    }
}
