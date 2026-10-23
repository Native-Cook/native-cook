import SwiftUI
import Metal
import MetalKit
import UIKit
import QuartzCore

final class KeycapMoodInputModel: ObservableObject {
    @Published var choices = [0, 0, 0, 0]
    @Published var fanOpen = false
    var simulation: KeycapMoodInputSimulation?
    var autoPlay = false
    var reduced = false

    func configure(size: CGSize) {
        let layout = KeycapMoodInputLayout(width: size.width, height: size.height)
        guard layout.width > 0 && layout.height > 0 else { return }
        if let simulation {
            simulation.layout = layout
            simulation.reduced = reduced
        } else {
            simulation = KeycapMoodInputSimulation(layout: layout, reduced: reduced, autoPlay: autoPlay)
        }
    }

    func action(_ action: KeycapMoodInputSimulation.Action) {
        simulation?.action(action)
        syncAccessibility()
        playEvents()
    }

    func pointer(down: Bool, point: CGPoint, cancelled: Bool = false) {
        simulation?.input(down: down, point: SIMD2(Double(point.x), Double(point.y)), cancelled: cancelled)
        syncAccessibility()
        playEvents()
    }

    func syncAccessibility() {
        guard let simulation else { return }
        if choices != simulation.choices { choices = simulation.choices }
        if fanOpen != simulation.fanOpen { fanOpen = simulation.fanOpen }
    }

    func playEvents() {
        guard let simulation else { return }
        for (event, _) in simulation.eventLog {
            if event & 7 != 0 { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            if event & 8 != 0 { UIImpactFeedbackGenerator(style: .rigid).impactOccurred() }
            if event == 16 { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
            if event == 32 { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
            if event == 64 { UISelectionFeedbackGenerator().selectionChanged() }
        }
    }
}

struct KeycapMoodInput: View {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var model = KeycapMoodInputModel()

    var body: some View {
        GeometryReader { geometry in
            let layout = KeycapMoodInputLayout(width: geometry.size.width, height: geometry.size.height)
            ZStack {
                Color.black
                KeycapMoodInputMetalView(model: model)
                    .accessibilityHidden(true)
                ForEach(0..<4, id: \.self) { index in
                    let centre = layout.centre(index)
                    let label = ["Subject", "Verb", "Intensity", "Mood"][index]
                    let value = index == 3 ? KeycapMoodInputPalette.names[model.choices[index]]
                        : KeycapMoodInputPalette.words[index][model.choices[index]]
                    Color.clear
                        .frame(width: layout.keySize + 24 * layout.u, height: layout.pitch)
                        .position(x: centre.x, y: centre.y + (layout.pitch - layout.keySize) / 2 - 4 * layout.u)
                        .allowsHitTesting(false)
                        .accessibilityElement()
                        .accessibilityLabel(label)
                        .accessibilityValue(value)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAdjustableAction { direction in
                            switch direction {
                            case .increment: model.action(.next(index))
                            case .decrement: model.action(.previous(index))
                            @unknown default: break
                            }
                        }
                        .accessibilityAction(named: "Activate") { model.action(.next(index)) }
                        .accessibilityAction(named: "Show moods") {
                            if index == 3 { model.action(.toggleFan) }
                        }
                }
                if model.fanOpen {
                    ForEach(0..<6, id: \.self) { index in
                        let mood = KeycapMoodInputPalette.fanOrder[index]
                        let centre = layout.mini(index)
                        Color.clear
                            .frame(width: 52 * layout.u, height: 52 * layout.u)
                            .position(x: centre.x, y: centre.y)
                            .allowsHitTesting(false)
                            .accessibilityElement()
                            .accessibilityLabel(KeycapMoodInputPalette.names[mood].capitalized)
                            .accessibilityAddTraits(mood == model.choices[3] ? [.isButton, .isSelected] : .isButton)
                            .accessibilityAction { model.action(.selectMood(mood)) }
                    }
                }
            }
            .onAppear {
                model.autoPlay = autoPlay
                model.reduced = reduceMotion
                model.configure(size: geometry.size)
            }
            .onChange(of: geometry.size) { _, size in model.configure(size: size) }
            .onChange(of: reduceMotion) { _, reduced in model.reduced = reduced; model.simulation?.reduced = reduced }
        }
        .ignoresSafeArea()
        .background(Color.black)
    }

}

private struct KeycapMoodInputMetalView: UIViewRepresentable {
    @ObservedObject var model: KeycapMoodInputModel

    func makeUIView(context: Context) -> KeycapMoodInputTouchView {
        let view = KeycapMoodInputTouchView(frame: .zero, device: MTLCreateSystemDefaultDevice())
        view.model = model
        view.colorPixelFormat = .bgra8Unorm_srgb
        view.framebufferOnly = true
        view.isPaused = false
        view.enableSetNeedsDisplay = false
        view.preferredFramesPerSecond = 60
        view.clearColor = MTLClearColorMake(0, 0, 0, 1)
        if let renderer = KeycapMoodInputRenderer(view: view, model: model) {
            context.coordinator.renderer = renderer
            view.delegate = renderer
        }
        let recognizer = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handle(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.delegate = context.coordinator
        view.addGestureRecognizer(recognizer)
        return view
    }

    func updateUIView(_ view: KeycapMoodInputTouchView, context: Context) {
        view.model = model
        model.configure(size: view.bounds.size)
    }

    func makeCoordinator() -> Coordinator { Coordinator(model: model) }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        let model: KeycapMoodInputModel
        var renderer: KeycapMoodInputRenderer?

        init(model: KeycapMoodInputModel) { self.model = model }

        @objc func handle(_ recognizer: UILongPressGestureRecognizer) {
            let point = recognizer.location(in: recognizer.view)
            switch recognizer.state {
            case .began, .changed: model.pointer(down: true, point: point)
            case .ended: model.pointer(down: false, point: point)
            case .cancelled, .failed: model.pointer(down: false, point: point, cancelled: true)
            default: break
            }
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
    }
}

private final class KeycapMoodInputTouchView: MTKView {
    weak var model: KeycapMoodInputModel?
}

private final class KeycapMoodInputRenderer: NSObject, MTKViewDelegate {
    private let model: KeycapMoodInputModel
    private let commandQueue: MTLCommandQueue
    private let pipeline: MTLRenderPipelineState
    private let textures: [MTLTexture]
    private var lastTime: CFTimeInterval?
    private var revealStart: CFTimeInterval?

    init?(view: MTKView, model: KeycapMoodInputModel) {
        guard let device = view.device,
              let library = device.makeDefaultLibrary(),
              let vertex = library.makeFunction(name: "keycapMoodInputVertex"),
              let fragment = library.makeFunction(name: "keycapMoodInputFragment"),
              let queue = device.makeCommandQueue() else { return nil }
        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.vertexFunction = vertex
        descriptor.fragmentFunction = fragment
        descriptor.colorAttachments[0].pixelFormat = view.colorPixelFormat
        guard let pipeline = try? device.makeRenderPipelineState(descriptor: descriptor) else { return nil }
        let loader = MTKTextureLoader(device: device)
        let assets = ["keycap-opaque", "keycap-glass-light", "keycap-glass", "keycap-mini", "well-glow"]
        var loaded: [MTLTexture] = []
        for (index, name) in assets.enumerated() {
            guard let url = Bundle.main.url(forResource: name, withExtension: "png"),
                  let texture = try? loader.newTexture(URL: url, options: [
                    .SRGB: index == 2,
                    .textureUsage: NSNumber(value: MTLTextureUsage.shaderRead.rawValue)
                  ]) else { return nil }
            loaded.append(texture)
        }
        self.model = model
        commandQueue = queue
        self.pipeline = pipeline
        textures = loaded
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        model.configure(size: view.bounds.size)
    }

    func draw(in view: MTKView) {
        let now = CACurrentMediaTime()
        if revealStart == nil { revealStart = now }
        model.configure(size: view.bounds.size)
        guard let simulation = model.simulation else { return }
        if let lastTime {
            simulation.step(milliseconds: (now - lastTime) * 1000)
            if !simulation.eventLog.isEmpty {
                model.syncAccessibility()
                model.playEvents()
            }
        }
        lastTime = now
        guard let drawable = view.currentDrawable,
              let pass = view.currentRenderPassDescriptor,
              let command = commandQueue.makeCommandBuffer(),
              let encoder = command.makeRenderCommandEncoder(descriptor: pass) else { return }
        var uniforms = [Float](repeating: 0, count: 272)
        pack(simulation, reveal: min(1, (now - (revealStart ?? now)) / 0.24), into: &uniforms)
        encoder.setRenderPipelineState(pipeline)
        uniforms.withUnsafeBytes { bytes in
            encoder.setFragmentBytes(bytes.baseAddress!, length: bytes.count, index: 0)
        }
        for (index, texture) in textures.enumerated() { encoder.setFragmentTexture(texture, index: index) }
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        command.present(drawable)
        command.commit()
    }

    private func pack(_ state: KeycapMoodInputSimulation, reveal: Double, into u: inout [Float]) {
        let layout = state.layout
        u[0] = Float(layout.width); u[1] = Float(layout.height)
        u[2] = Float(layout.u); u[3] = Float(layout.y0)
        u[4] = Float(layout.keySize); u[5] = Float(layout.pitch)
        u[6] = state.reduced ? 1 : 0; u[7] = Float(reveal)
        u[8] = Float(state.scene.x); u[9] = Float(state.scene.y); u[10] = Float(state.scene.z)
        u[11] = Float(state.burst); u[12] = Float(state.ring)
        u[13] = Float(state.charge); u[14] = Float(state.dim)
        let from = KeycapMoodInputPalette.linear[state.mood.from]
        let to = KeycapMoodInputPalette.linear[state.mood.to]
        u[15] = Float(from.x); u[16] = Float(from.y); u[17] = Float(from.z)
        u[18] = Float(to.x); u[19] = Float(to.y); u[20] = Float(to.z)
        u[21] = Float(state.mood.progress)
        u[22] = Float(state.mood.origin.x / layout.u); u[23] = Float(state.mood.origin.y / layout.u)
        u[24] = state.mood.radial ? 1 : 0
        u[25] = Float(state.choices[3]); u[26] = state.fanOpen ? 1 : 0
        for key in 0..<4 {
            let base = 32 + key * 16
            u[base] = Float(state.x[key]); u[base + 1] = Float(state.rim[key])
            u[base + 2] = Float(state.choices[key])
            u[base + 3] = Float(state.legends[key].from)
            u[base + 4] = Float(state.legends[key].to)
            u[base + 5] = Float(state.legends[key].time)
            u[base + 6] = Float(state.legends[key].mode)
            let before = KeycapMoodInputGlyphs.rows(key: key, choice: state.legends[key].from)
            let after = KeycapMoodInputGlyphs.rows(key: key, choice: state.legends[key].to)
            for row in 0..<10 {
                u[128 + key * 20 + row] = before[row]
                u[128 + key * 20 + 10 + row] = after[row]
            }
        }
        for mini in 0..<6 {
            u[96 + mini * 4] = Float(state.out[mini])
            u[97 + mini * 4] = Float(state.miniX[mini])
        }
        for mood in 0..<6 {
            let rows = KeycapMoodInputGlyphs.rows(key: 3, choice: mood)
            for row in 0..<10 { u[208 + mood * 10 + row] = rows[row] }
        }
    }
}
