import CoreText
import SwiftUI
import UIKit

struct GlassFolderBurstDemo: View {
    var body: some View {
        GlassFolderBurst(autoPlay: true)
    }
}

struct GlassFolderBurst: View {
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        GlassFolderBurstAssets.register()
        _ = GlassFolderBurstAssets.covers
    }

    var body: some View {
        GeometryReader { proxy in
            let layout = GlassFolderBurstLayout(width: proxy.size.width,
                height: proxy.size.height + proxy.safeAreaInsets.top + proxy.safeAreaInsets.bottom,
                top: proxy.safeAreaInsets.top, bottom: proxy.safeAreaInsets.bottom)
            GlassFolderBurstScene(layout: layout, autoPlay: autoPlay, reduced: reduceMotion)
                .frame(width: layout.width, height: layout.height)
                .offset(y: -proxy.safeAreaInsets.top)
                .id("\(layout.width):\(layout.height):\(layout.headerTop):\(layout.dockY):\(reduceMotion)")
        }
        .background(.black)
        .statusBarHidden()
    }
}

enum GlassFolderBurstAssets {
    static let titles = ["Glass Hours", "Night Ferry", "Slow Voltage", "Paper Moons", "Undertow", "Kerosene Sky", "Soft Machines", "Harbor Lights", "After the Rain"]
    static let artists = ["Ivory Kessel", "Mara Voss", "Okano & Reed", "The Halden Set", "Sable Arc", "Juno Vale", "Calder Lune", "Wren Atlas", "Nilo Ferreira"]
    static let seconds = [222, 245, 198, 236, 261, 213, 227, 252, 189]
    static let regular = "ArchivoExpanded-Regular"
    static let black = "ArchivoExpanded-Black"
    static let covers: [Image] = {
        guard let url = Bundle.main.url(forResource: "GlassFolderBurst-covers", withExtension: "png"),
              let atlas = UIImage(contentsOfFile: url.path)?.cgImage else { return [] }
        return (0..<9).compactMap { index in
            let rect = CGRect(x: (index % 3) * 256, y: (index / 3) * 256, width: 256, height: 256)
            guard let tile = atlas.cropping(to: rect) else { return nil }
            return Image(decorative: tile, scale: 1)
        }
    }()
    private static let registered: Void = {
        for name in [regular, black] {
            guard let url = Bundle.main.url(forResource: "GlassFolderBurst-" + name, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
    static func register() { _ = registered }
    static func subtitle(_ count: Int) -> String {
        let minutes = Int((Double(seconds.prefix(count).reduce(0, +)) / 60).rounded())
        return "\(count) tracks · \(minutes) min"
    }
    static func color(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
            green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: opacity)
    }
    static func text(_ context: GraphicsContext, _ text: String, x: Double, top: Double, height: Double,
                     size: Double, bold: Bool = false, color: Color, centered: Bool = false) {
        let name = bold ? black : regular
        let font = UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
        let resolved = context.resolve(Text(text).font(.custom(name, fixedSize: size)).foregroundStyle(color))
        let bounds = CGSize(width: 2000, height: 100)
        let width = resolved.measure(in: bounds).width
        let baseline = top + (height + font.capHeight) / 2
        context.draw(resolved, at: CGPoint(x: x - (centered ? width / 2 : 0), y: baseline - resolved.firstBaseline(in: bounds)), anchor: .topLeading)
    }
}

/// SwiftUI may defer Canvas drawing; all layers retain values from one simulation tick.
private struct GlassFolderBurstFrame {
    let sim: GlassFolderBurstSimulation
    let reduced: Bool
    let expanded: Bool
    let pressed: Int
    let rowPress: Double
    let selectedMix: [Double]
    let selected: Int
    let landed: Int
    let dock: Double
    let fade: Double
    let clock: Double

    init(_ motion: GlassFolderBurstMotion) {
        sim = motion.sim
        reduced = motion.reduced
        expanded = motion.expanded
        pressed = motion.pressed
        rowPress = motion.springs[10]
        selectedMix = motion.selectedMix
        selected = motion.selected
        landed = motion.landed
        dock = motion.dock
        fade = motion.fade
        clock = motion.reduced ? 0 : motion.idleTime
    }
}

private struct GlassFolderBurstScene: View {
    let layout: GlassFolderBurstLayout
    @State private var motion: GlassFolderBurstMotion
    @GestureState private var touchingScreen = false
    @Environment(\.scenePhase) private var scenePhase

    init(layout: GlassFolderBurstLayout, autoPlay: Bool, reduced: Bool) {
        self.layout = layout
        _motion = State(initialValue: GlassFolderBurstMotion(count: layout.count, reduced: reduced, autoPlay: autoPlay))
    }

    var body: some View {
        TimelineView(.animation(paused: scenePhase != .active)) { timeline in
            let _ = advance(timeline.date)
            let folder = motion.folder(layout)
            let orb = motion.orb(layout)
            let placement = folder.placement
            let frame = GlassFolderBurstFrame(motion)
            let visibleRows = (0..<layout.count).filter { frame.landed & (1 << $0) != 0 }
            let selected = frame.selected
            ZStack(alignment: .topLeading) {
                GlassFolderBurstSurface(layout: layout, frame: frame, folder: folder, orb: orb, clock: frame.clock)
                    .opacity(frame.fade)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                ForEach(visibleRows, id: \.self) { index in
                    let rect = layout.slot(index)
                    Button { motion.send(.select(index)) } label: { Color.clear.contentShape(Rectangle()) }
                        .buttonStyle(GlassFolderBurstPressStyle(motion: motion, index: index))
                        .frame(width: rect.width, height: rect.height)
                        .position(x: rect.midX, y: rect.midY)
                        .accessibilityLabel("\(GlassFolderBurstAssets.titles[index]), \(GlassFolderBurstAssets.artists[index]), \(GlassFolderBurstAssets.seconds[index] / 60) minutes \(GlassFolderBurstAssets.seconds[index] % 60) seconds")
                        .accessibilityValue(selected == index ? "Playing" : "Stopped")
                        .accessibilityAddTraits(selected == index ? [.isSelected] : [])
                }
                Button { motion.send(.toggle) } label: { Color.clear.contentShape(Rectangle()) }
                    .buttonStyle(GlassFolderBurstPressStyle(motion: motion, index: -1))
                    .frame(width: 144 * folder.scale + 24, height: 104 * folder.scale + 24)
                    .position(x: placement.x, y: folder.cy)
                    .accessibilityLabel("Low Tide, \(layout.count) tracks")
                    .accessibilityValue(frame.expanded ? "Expanded" : "Collapsed")
                    .accessibilityHint(frame.expanded ? "Puts the tracks away" : "Shows the tracks")
                Text("Low Tide")
                    .opacity(0.001)
                    .allowsHitTesting(false)
                    .accessibilityAddTraits(.isHeader)
                    .frame(width: layout.width - 100, height: 24)
                    .position(x: layout.width / 2, y: layout.restY - 142 + (layout.headerTop - layout.restY + 154) * frame.dock)
            }
            .frame(width: layout.width, height: layout.height)
            .contentShape(Rectangle())
            .simultaneousGesture(DragGesture(minimumDistance: 0)
                .updating($touchingScreen) { _, touching, _ in touching = true })
        }
        .onChange(of: scenePhase) { _, _ in motion.pauseClock() }
        .onChange(of: touchingScreen) { _, touching in
            motion.send(touching ? .touchStart : .touchEnd)
        }
    }

    private func advance(_ date: Date) {
        motion.advance(to: date, layout: layout)
        if !motion.autoPlay || (motion.paused && !motion.recovering) {
            if motion.events & 2 != 0 { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
            if motion.events & 4 != 0 { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
            if motion.events & 8 != 0 { UISelectionFeedbackGenerator().selectionChanged() }
            if motion.events & 16 != 0 { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
        }
        motion.events = 0
    }
}

private struct GlassFolderBurstPressStyle: ButtonStyle {
    let motion: GlassFolderBurstMotion
    let index: Int
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of: configuration.isPressed) { _, down in
            if index < 0 { motion.send(down ? .folderDown : .folderUp) }
            else { motion.send(down ? .rowDown(index) : .rowUp(index)) }
        }
    }
}

private struct GlassFolderBurstSurface: View {
    let layout: GlassFolderBurstLayout
    let frame: GlassFolderBurstFrame
    let folder: GlassFolderBurstFolder
    let orb: GlassFolderBurstOrb
    let clock: Double

    var body: some View {
        let p = folder.placement
        ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                Rectangle().fill(.white)
                    .colorEffect(ShaderLibrary.glassFolderBurstBackground(
                        .float2(layout.width, layout.height), .float2(p.x, p.y), .float(p.sy), .float(folder.flow), .float(clock),
                        .float2(frame.sim.bloomX == 0 ? layout.width / 2 : frame.sim.bloomX,
                                frame.sim.bloomY == 0 ? layout.dockY - 180 : frame.sim.bloomY),
                        .float(max(frame.sim.bloomStrength, frame.expanded ? 0.12 : 0)),
                        .float2(max(p.x - 50 * p.sx, min(p.x + 50 * p.sx, orb.x)), storedLight)))
                Rectangle().fill(.white)
                    .colorEffect(ShaderLibrary.glassFolderBurstBack(
                        .float2(p.x, p.y), .float2(p.sx, p.sy), .float(folder.open), .float(folder.flow)))
                Rectangle().fill(.white)
                    .colorEffect(ShaderLibrary.glassFolderBurstOrb(
                        .float2(orb.x, orb.y), .float(orb.radius), .float(orb.glow),
                        .float(orb.beat), .float(orb.mark), .float(clock)))
                ForEach(Array((0..<layout.count).reversed()), id: \.self) { index in
                    let pose = GlassFolderBurstPose(row: frame.sim.rows[index], index: index, layout: layout,
                        folder: folder, reduced: frame.reduced,
                        press: frame.pressed == index ? 1 - 0.03 * frame.rowPress : 1)
                    GlassFolderBurstTrack(index: index, width: layout.width - 48, pose: pose,
                        active: frame.selectedMix[index], clock: clock, reduced: frame.reduced)
                        .frame(width: layout.width - 48 + 100, height: 152)
                        .colorEffect(ShaderLibrary.glassFolderBurstRowColor())
                        .projectionEffect(pose.projection(width: layout.width - 48, padding: 50))
                        .opacity(pose.opacity)
                }
            }
            .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            .drawingGroup(opaque: true, colorMode: .nonLinear)
            .layerEffect(ShaderLibrary.glassFolderBurstFront(
                .float2(p.x, p.y), .float2(p.sx, p.sy), .float(folder.open), .float(folder.press),
                .float(folder.flow), .float(clock), .float4(orb.x, orb.y, orb.radius, orb.glow),
                .float(orb.beat), .float(orb.mark)), maxSampleOffset: CGSize(width: 64, height: 64))
            .colorEffect(ShaderLibrary.glassFolderBurstDisplayColor())
            header
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
    }

    private var storedLight: Double {
        let p = folder.placement
        let qx = (orb.x - p.x) / p.sx
        let qy = (orb.y - p.y) / p.sy
        let radius = max(0.01, orb.radius / p.sy)
        let start = -28 - 3 * folder.open
        let u = GlassFolderBurstMath.clamp((qx - start) / (-6 - start))
        let top = -46 + 12 * folder.open + 12 * u * u * (3 - 2 * u)
        return GlassFolderBurstMath.smooth((qy - top + radius) / (2 * radius))
            * (1 - GlassFolderBurstMath.smooth((abs(qx) - 60) / (6 + radius)))
            * (1 - GlassFolderBurstMath.smooth((qy - 52) / radius)) * orb.glow
    }

    private var header: some View {
        Canvas { context, _ in
            let titleFont = UIFont(name: GlassFolderBurstAssets.black, size: 20) ?? .systemFont(ofSize: 20)
            let titleWidth = ("Low Tide" as NSString).size(withAttributes: [.font: titleFont]).width
            let x = (layout.width - titleWidth) / 2
            let titleY = layout.restY - 154
            GlassFolderBurstAssets.text(context, "Low Tide", x: x + (76 - x) * frame.dock,
                top: titleY + (layout.headerTop - titleY) * frame.dock, height: 24, size: 20, bold: true,
                color: GlassFolderBurstAssets.color(0xF2FFF7))
            let total = frame.selectedMix.reduce(0, +)
            for index in -1..<layout.count {
                var text = index == -1 ? GlassFolderBurstAssets.subtitle(layout.count) : "Now playing · " + GlassFolderBurstAssets.titles[index]
                let font = UIFont(name: GlassFolderBurstAssets.regular, size: 12) ?? .systemFont(ofSize: 12)
                if index >= 0 && (text as NSString).size(withAttributes: [.font: font]).width > layout.width - 100 {
                    text = GlassFolderBurstAssets.titles[index]
                }
                let width = (text as NSString).size(withAttributes: [.font: font]).width
                let startX = (layout.width - width) / 2
                let startY = layout.restY - 126
                var line = context
                line.opacity = index == -1 ? max(0, 1 - total) : frame.selectedMix[index]
                GlassFolderBurstAssets.text(line, text, x: startX + (76 - startX) * frame.dock,
                    top: startY + (layout.headerTop + 24 - startY) * frame.dock, height: 16, size: 12,
                    color: GlassFolderBurstAssets.color(0xF2FFF7, opacity: 0.58))
            }
        }
    }
}

#Preview {
    GlassFolderBurstDemo()
}
