import SwiftUI
import UIKit

/// Value-for-value port of the approved RN assistant: one refracting SDF, a cached page, and live overlays.
struct IslandGlassOrb: View {
    static let loop = 10.0
    var autoPlay = true
    var level: Double?
    var reply = "Two events tomorrow. First at 6:00."
    var replyHoldMs = 6000.0
    var onListen: (() -> Void)?, onSubmit: (() -> Void)?, onCancel: (() -> Void)?, onReplyEnd: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var engine = IslandGlassOrbEngine()
    @State private var dragging = false
    @GestureState private var gestureDown = false
    private typealias M = IslandGlassOrbMotion
    private typealias A = IslandGlassOrbArtwork

    init(autoPlay: Bool = true, level: Double? = nil, reply: String = "Two events tomorrow. First at 6:00.", replyHoldMs: Double = 6000,
         onListen: (() -> Void)? = nil, onSubmit: (() -> Void)? = nil, onCancel: (() -> Void)? = nil, onReplyEnd: (() -> Void)? = nil) {
        self.autoPlay = autoPlay; self.level = level; self.reply = reply; self.replyHoldMs = replyHoldMs
        self.onListen = onListen; self.onSubmit = onSubmit; self.onCancel = onCancel; self.onReplyEnd = onReplyEnd
        _ = A.register
    }
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "islandGlassOrbTime") {
            if let n = value as? NSNumber { return n.doubleValue }
            if let s = value as? String { return Double(s) }
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { geo in
            let scale = geo.size.width / 402
            let height = geo.size.height / scale
            let top = (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.windows.first(where: { $0.isKeyWindow })?.safeAreaInsets.top ?? geo.safeAreaInsets.top
            let oy = top >= 54 || top == 0 ? 0 : top + 4 - 11
            let art = A.get(height: height, scale: scale * UIScreen.main.scale, reply: reply)
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: scenePhase != .active || engine.pin != nil)) { timeline in
                let f = sample(timeline.date, height: height)
                Canvas(rendersAsynchronously: false) { ctx, _ in
                    ctx.draw(Image(uiImage: art.page), in: CGRect(x: 0, y: 0, width: 402, height: height))
                    relight(&ctx, f, art)
                    overlays(&ctx, f, art)
                    // The gallery host retains the system status bar. Draw only the assistant's mic cue.
                    ctx.fill(Path(ellipseIn: CGRect(x: 387,y: 23,width: 6,height: 6)), with: .color(Color(.sRGB,red: 1,green: 122/255,blue: 26/255,opacity: f["mic"]!)))
                    ctx.fill(Path(CGRect(x: 0, y: 0, width: 402, height: height)), with: .shader(glass(f, oy: oy, art: art)))
                    foreground(&ctx, f, oy: oy, art: art)
                }
                .frame(width: 402, height: height, alignment: .topLeading)
                .drawingGroup(opaque: true, colorMode: .nonLinear)
                .scaleEffect(scale, anchor: .topLeading)
                .frame(width: geo.size.width, height: geo.size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .updating($gestureDown) { _, state, _ in state = true }
                    .onChanged { value in
                        if !dragging {
                            dragging = true
                            engine.begin(CGPoint(x: value.startLocation.x / scale, y: value.startLocation.y / scale), oy: oy)
                        }
                        engine.move(CGPoint(x: value.location.x / scale, y: value.location.y / scale), scale: scale)
                    }
                    .onEnded { value in
                        engine.end(velocity: CGSize(width: value.velocity.width / scale, height: value.velocity.height / scale)); dragging = false
                    })
                .accessibilityLabel("Nori assistant. Hold the island to listen, pull the drop or pluck its spectrum, tap to submit. Tap the thinking pill to cancel or swipe the reply to dismiss.")
            }
        }
        .background(Color(.sRGB, red: 145 / 255, green: 158 / 255, blue: 144 / 255))
        .ignoresSafeArea()
        .statusBarHidden(false)
        .preferredColorScheme(.dark)
        .onChange(of: scenePhase) { _, phase in if phase != .active { engine.cancelGesture(); dragging = false }; engine.suspend() }
        .onChange(of: gestureDown) { _, down in if !down && dragging { engine.cancelGesture(); dragging = false } }
    }
    private func sample(_ date: Date, height: Double) -> M.Frame {
        engine.configure(autoPlay: autoPlay, reduced: reduced, level: level, hold: replyHoldMs / 1000)
        engine.onListen = onListen; engine.onSubmit = onSubmit; engine.onCancel = onCancel; engine.onReplyEnd = onReplyEnd
        engine.configurePin(pinned)
        engine.height = height
        engine.step(date.timeIntervalSinceReferenceDate)
        return engine.frame()
    }
    private func glass(_ f: M.Frame, oy: Double, art: A) -> Shader {
        let b = M.shapes(f, oy: oy).box, d = M.shapes(f, oy: oy).drop
        let m = f["m"]!, mc = M.clamp(m), ban = M.clamp(f["ban"]!), amp = f["amp"]!
        let args: [Shader.Argument] = [
            .float4(Float(b.minX), Float(b.minY), Float(b.width), Float(b.height)),
            .float4(Float(d.midX), Float(d.midY), Float(max(d.width / 2, 1)), Float(max(d.height / 2, 1))),
            .float(Float(M.mix(2.6, 2, f["free"]!))), .float(Float(max(m, 0))), .float(Float(f["kJoin"]! * M.smooth(0, 0.25, m))),
            .float4(201, Float(37 + oy), Float(f["neck"]! * mc / 2 > 0.05 ? f["neck"]! * mc * 0.6 + 1 : 0), Float(f["neck"]! * mc / 2)),
            .float(Float(ban)), .float(Float(amp)), .float(Float(f["ampLag"]!)),
            .float4(Float(f["lineX"]!), Float(f["lineY"]! + oy), Float(f["lineLen"]! * (1 + 0.08 * amp)), 3),
            .float(Float(engine.lobeTime)), .float2(Float(f["pluckX"]!), Float(f["pluckD"]!)),
            .float3(Float(f["arcA"]!), Float(f["arcW"]!), Float(f["arcHue"]!)), .float(1),
            .float2(Float(0.3 * mc * mc), Float(max(0.88 * mc, 0.25 * M.clamp(f["pill"]!) * (1 - ban), 0.45 * ban))),
            .float3(Float(M.mix(0.25 * mc, 0.22 * mc, f["free"]!) + 0.22 * ban), Float(M.mix(M.mix(6, 8, ban), 10, f["free"]!)), Float(M.mix((0.1 + 0.25 * M.clamp(f["causticAmp"]!)) * mc, 0.28 * (0.85 + 0.3 * M.clamp(f["causticAmp"]!)) * mc, f["free"]!))),
            .float3(Float(M.mix(M.mix(18, 24, ban), 26, f["free"]!)), Float(M.mix(30, 40, f["free"]!)), Float(M.mix(5, 7, f["free"]!))), .float(Float(f["cap"]!)), .float(Float(f["free"]!)),
            .image(Image(uiImage: art.page)), .image(Image(uiImage: art.blur)), .float(Float(art.height))
        ]
        return Shader(function: ShaderFunction(library: .default, name: "islandGlassOrb"), arguments: args)
    }
    private func text(_ ctx: inout GraphicsContext, _ run: A.TextRun, x: Double, cy: Double, alpha: Double = 1) {
        var c = ctx; c.opacity *= alpha; c.translateBy(x: x, y: cy); c.fill(run.path, with: .color(A.ink()))
    }
    private func relight(_ ctx: inout GraphicsContext, _ f: M.Frame, _ art: A) {
        let a = A.artScale(art.height)
        for mode in 0...1 {
            if mode == 0 && f["bandA"]! + f["floorA"]! <= 0.001 { continue }
            var c = ctx; c.translateBy(x: 201 * (1-a), y: 276 * (1-a)); c.scaleBy(x: a, y: a)
            c.blendMode = mode == 0 ? .color : .plusLighter
            let shader = Shader(function: ShaderFunction(library: .default, name: "islandGlassOrbRelight"), arguments: [
                .float4(Float(f["bandY"]!), Float(f["bandA"]!), Float(f["floorY"]!), Float(f["floorA"]!)),
                .float4(Float(f["bandHue"]!), Float(f["floorHue"]!), Float(mode), Float(M.clamp(f["pill"]!) * (1-M.clamp(f["ban"]!)))),
                .float(Float(engine.lobeTime)), .float(Float(M.clamp(f["causticAmp"]!))),
                .image(Image(uiImage: art.mattes[0])), .image(Image(uiImage: art.mattes[1])), .image(Image(uiImage: art.mattes[2]))])
            c.fill(Path(CGRect(x: 0,y: 230,width: 402,height: 520)), with: .shader(shader))
        }
    }
    private func overlays(_ ctx: inout GraphicsContext, _ f: M.Frame, _ art: A) {
        for (x, key) in [(28.0, "rimL"), (212.0, "rimR")] {
            var c = ctx; c.opacity = f[key]!
            c.stroke(Path(roundedRect: CGRect(x: x + 0.5, y: 79.5, width: 161, height: 160), cornerRadius: 23.5), with: .linearGradient(Gradient(colors: [.white.opacity(0.25), .white.opacity(0)]), startPoint: CGPoint(x: 0, y: 79), endPoint: CGPoint(x: 0, y: 175.6)), lineWidth: 1)
        }
        var row = ctx
        let r = f["row"]!, k = M.clamp(r), s = 1 + 0.04 * r
        row.translateBy(x: 232, y: 190); row.scaleBy(x: s, y: s); row.translateBy(x: -232, y: -190)
        let bar = Color(.sRGB, red: M.mix(247, 100, k) / 255, green: M.mix(247, 244, k) / 255, blue: M.mix(245, 249, k) / 255)
        row.fill(Path(roundedRect: CGRect(x: 232, y: 172, width: 2, height: 35), cornerRadius: 1), with: .color(bar))
        text(&row, art.text["events"]!, x: 240, cy: 181)
        // RN's two range spans share one line; centre using that line's 13-point metrics.
        text(&row, art.text["range"]!, x: 240, cy: 200, alpha: M.mix(0.6, 1, k))
        let align = art.text["range"]!.baseline - art.text["pm"]!.baseline
        text(&row, art.text["pm"]!, x: 240 + art.text["range"]!.width, cy: 200 + align, alpha: M.mix(0.6, 1, k))
        if f["spillA"]! > 0 {
            var c = ctx; c.blendMode = .screen; c.opacity = f["spillA"]!; c.addFilter(.blur(radius: 34))
            c.fill(Path(ellipseIn: CGRect(x: 80, y: f["spillY"]! - 80, width: 240, height: 160)), with: .color(spectrum(f["arcHue"]!)))
        }
    }
    private func foreground(_ ctx: inout GraphicsContext, _ f: M.Frame, oy: Double, art: A) {
        if f["spinA"]! > 0 {
            ctx.drawLayer { c in
                c.opacity = f["spinA"]!; c.addFilter(.blur(radius: f["spinBlur"]!))
                let cx = M.shapes(f, oy: oy).box.minX + 30, head = ((f["spinT"]!.truncatingRemainder(dividingBy: 1) + 1).truncatingRemainder(dividingBy: 1)) * 6
                for i in 0..<6 {
                    let angle = Double(i) / 6 * 2 * .pi - .pi / 2
                    let behind = ((head - Double(i)).truncatingRemainder(dividingBy: 6) + 6).truncatingRemainder(dividingBy: 6)
                    c.fill(Path(ellipseIn: CGRect(x: cx + 10.5 * cos(angle) - 2.5, y: 40 + oy + 10.5 * sin(angle) - 2.5, width: 5, height: 5)), with: .color(A.ink(0.3 + 0.7 * max(0, 1 - behind / 3.2))))
                }
            }
        }
        for (i, word) in art.words.enumerated() {
            let k = M.out(M.seg(f["textT"]!, 0.09 * Double(i), 0.09 * Double(i) + 0.3)), op = k * (1 - f["textOut"]!)
            if op <= 0 { continue }
            ctx.drawLayer { c in
                c.opacity = op; c.addFilter(.blur(radius: 8 * (1 - k) + 8 * f["textOut"]!))
                text(&c, word.run, x: word.x, cy: word.y + oy + 4 * (1 - k))
            }
        }
    }
    private func spectrum(_ x: Double) -> Color {
        let values: [[Double]] = [[255, 90, 31], [245, 205, 150], [100, 244, 249], [43, 91, 255], [255, 45, 138]]
        let u = ((x.truncatingRemainder(dividingBy: 1) + 1).truncatingRemainder(dividingBy: 1)) * 5, i = Int(floor(u)), f = M.smooth(0, 1, u - floor(u))
        let a = values[i % 5], b = values[(i + 1) % 5]
        return Color(.sRGB, red: M.mix(a[0], b[0], f).rounded() / 255, green: M.mix(a[1], b[1], f).rounded() / 255, blue: M.mix(a[2], b[2], f).rounded() / 255)
    }
}
