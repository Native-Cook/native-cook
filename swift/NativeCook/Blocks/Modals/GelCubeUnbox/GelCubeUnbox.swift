import SwiftUI

/// The approved React Native LightBoxReveal, with its original 402 × 874 point coordinate system.
struct GelCubeUnbox: View {
    static let loop = GelCubeUnboxMotion.loop
    var autoPlay: Bool
    var tint: String
    var open: Bool?
    var progress: Binding<Double>?
    var sound: Bool
    var title: String
    var subtitle: String
    var sealed: GelCubeUnboxCopy
    var opened: GelCubeUnboxCopy
    var size: Double
    var lidWeight: Double
    var tokens: GelCubeUnboxTokens
    var onOpenChange: ((Bool) -> Void)?
    var onProgress: ((Double) -> Void)?
    var onAction: ((String) -> Void)?
    var onClose: (() -> Void)?
    var children: AnyView?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @ScaledMetric(relativeTo: .body) private var fontScale = 1.0
    @State private var driver: GelCubeUnboxDriver
    @State private var revision = 0
    @GestureState private var gestureActive = false
    @State private var gesturePending = false
    @AccessibilityFocusState private var sheetFocused: Bool

    init(autoPlay: Bool = false, tint: String = "#2F63D8", open: Bool? = nil, defaultOpen: Bool = false,
         progress: Binding<Double>? = nil,
         sound: Bool = false, title: String = "Congratulations!\nYou got a Lumen Box",
         subtitle: String = "Deals are waiting for you inside", sealed: GelCubeUnboxCopy = .sealed,
         opened: GelCubeUnboxCopy = .opened, size: Double = 215, lidWeight: Double = 1.4,
         tokens: GelCubeUnboxTokens = GelCubeUnboxTokens(), onOpenChange: ((Bool) -> Void)? = nil,
         onProgress: ((Double) -> Void)? = nil, onAction: ((String) -> Void)? = nil,
         onClose: (() -> Void)? = nil, children: AnyView? = nil) {
        self.autoPlay = autoPlay; self.tint = tint; self.open = open; self.progress = progress; self.sound = sound
        self.title = title; self.subtitle = subtitle; self.sealed = sealed; self.opened = opened
        self.size = size; self.lidWeight = lidWeight; self.tokens = tokens
        self.onOpenChange = onOpenChange; self.onProgress = onProgress; self.onAction = onAction
        self.onClose = onClose; self.children = children
        _driver = State(initialValue: GelCubeUnboxDriver(defaultOpen: open ?? defaultOpen, progress: progress?.wrappedValue))
        _ = GelCubeUnboxResources.register
    }

    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let index = args.firstIndex(of: "-gelCubeUnboxTime"), index + 1 < args.count,
           let value = Double(args[index + 1]), value.isFinite, value >= 0 { return value }
        #endif
        return nil
    }

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let width = geo.size.width + insets.leading + insets.trailing
            let height = geo.size.height + insets.top + insets.bottom
            let g = GelCubeUnboxGeometry(width: width, height: height, top: insets.top, bottom: insets.bottom)
            let palette = GelCubeUnboxResources.palette(tint)
            TimelineView(.animation(paused: scenePhase != .active || pinnedTime != nil || reduceMotion || !driver.needsFrames)) { timeline in
                let _ = revision
                let _ = configure(at: timeline.date)
                let scene = pinnedTime.map { GelCubeUnboxMotion.GelCubeUnboxScene.film($0, reduced: reduceMotion) }
                    ?? driver.frame(at: timeline.date)
                stage(scene, g, palette, insets.top, insets.bottom)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active, at: Date()); revision &+= 1 }
        .onChange(of: gestureActive) {
            if !gestureActive && gesturePending {
                driver.end(velocityY: 0, at: Date(), cancelled: true)
                gesturePending = false; revision &+= 1
            }
        }
    }

    private func configure(at date: Date) {
        driver.onOpenChange = onOpenChange; driver.onProgress = onProgress
        let pulse = $revision
        driver.onActivityChange = { pulse.wrappedValue &+= 1 }
        if let progress { driver.onExternalProgress = { progress.wrappedValue = $0 } }
        else { driver.onExternalProgress = nil }
        driver.configure(auto: autoPlay && pinnedTime == nil, reduced: reduceMotion, sound: sound,
                         lidWeight: lidWeight, open: open, externalProgress: progress?.wrappedValue, at: date)
    }

    private func stage(_ scene: GelCubeUnboxMotion.GelCubeUnboxScene, _ g: GelCubeUnboxGeometry,
                       _ palette: [Double], _ top: Double, _ bottom: Double) -> some View {
        let semantic = (reduceMotion ? scene.textProgress : scene.progress) >= 0.8, copy = semantic ? opened : sealed
        return ZStack(alignment: .topLeading) {
            GelCubeUnboxFrame(scene: scene, geometry: g, palette: palette, showClose: onClose != nil,
                              showDetails: children == nil, reduced: reduceMotion, size: size, tokens: tokens)
            if fontScale > 1.1 {
                flow(scene, g, palette, top, bottom, copy)
            } else {
                GelCubeUnboxLabels(scene: scene, geometry: g, title: title, subtitle: subtitle,
                                   sealed: sealed, opened: opened, tokens: tokens, reduced: reduceMotion,
                                   showDetails: children == nil, fontScale: fontScale)
                actionButton(copy.action, semantic)
                    .frame(width: 370 * g.scale, height: max(44, 58 * g.scale))
                    .scaleEffect(scene.actionScale)
                    .offset(x: g.x(16), y: g.y(497))
                if let children {
                    children.frame(width: 354 * g.scale, alignment: .topLeading)
                        .offset(x: g.x(24), y: g.y(629))
                } else {
                    Color.clear.frame(width: 402 * g.scale, height: 278 * g.scale)
                        .contentShape(Rectangle())
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel([copy.chip, copy.sheetTitle, copy.sheetBody].joined(separator: ". "))
                        .accessibilityFocused($sheetFocused)
                        .offset(x: g.x(0), y: g.y(596))
                }
            }
            heroTouch(g, semantic)
            if let close = onClose {
                Button(action: close) { Color.clear.contentShape(Rectangle()) }
                    .buttonStyle(.plain).accessibilityLabel("Close reward")
                    .frame(width: max(44, 44 * g.scale), height: max(44, 44 * g.scale))
                    .offset(x: g.x(340), y: g.y(44))
            }
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
        .clipped()
    }

    private func actionButton(_ label: String, _ semantic: Bool) -> some View {
        Button {
            configure(at: Date())
            driver.action(at: Date())
            if semantic { sheetFocused = true }
            onAction?(semantic ? "open" : "sealed")
            revision &+= 1
        } label: { Color.clear.contentShape(Rectangle()) }
        .buttonStyle(GelCubeUnboxActionStyle { down in
            configure(at: Date()); driver.pressAction(down, at: Date()); revision &+= 1
        })
        .accessibilityLabel(label)
    }

    private func heroTouch(_ g: GelCubeUnboxGeometry, _ semantic: Bool) -> some View {
        Color.clear.contentShape(Rectangle())
            .frame(width: 220 * g.scale, height: 252 * g.scale)
            .gesture(DragGesture(minimumDistance: 0)
                .updating($gestureActive) { _, active, _ in active = true }
                .onChanged { value in
                    guard !reduceMotion else { return }
                    configure(at: value.time)
                    gesturePending = true
                    if !driver.fingerDown { driver.begin(at: value.time) }
                    driver.move(dy: value.translation.height / g.scale, at: value.time)
                    revision &+= 1
                }
                .onEnded { value in
                    gesturePending = false
                    configure(at: value.time)
                    if reduceMotion { driver.toggle(at: value.time) }
                    else { driver.end(velocityY: value.velocity.height / g.scale, at: value.time) }
                    revision &+= 1
                })
            .accessibilityElement(children: .ignore).accessibilityAddTraits(.isButton)
            .accessibilityLabel(semantic ? "Close your Lumen Box" : "Open your Lumen Box")
            .accessibilityHint("Drag upward to lift the lid, or activate to open.")
            .accessibilityValue(semantic ? "Expanded" : "Collapsed")
            .accessibilityAction { configure(at: Date()); driver.toggle(at: Date()); revision &+= 1 }
            .offset(x: g.x(86), y: g.y(96))
    }

    private func flow(_ scene: GelCubeUnboxMotion.GelCubeUnboxScene, _ g: GelCubeUnboxGeometry,
                      _ palette: [Double], _ top: Double, _ bottom: Double, _ copy: GelCubeUnboxCopy) -> some View {
        let width = max(1, g.width - 48 - 32), semantic = (reduceMotion ? scene.textProgress : scene.progress) >= 0.8
        let page = Color(.sRGB, red: palette[3], green: palette[4], blue: palette[5], opacity: 0.96)
        return ScrollView {
            VStack(spacing: 16) {
                flowText(title, 25, 32, width, tokens.ink, g.scale).accessibilityAddTraits(.isHeader)
                flowText(subtitle, 17, 25, width, tokens.mute, g.scale)
                ZStack {
                    flowText(copy.action, 21, nil, width - 24, tokens.ink, g.scale).padding(12)
                    actionButton(copy.action, semantic)
                }
                .frame(minHeight: 58)
                .background(GelCubeUnboxResources.color(0xF4FAFF, 0.14), in: .rect(cornerRadius: 29))
                .scaleEffect(scene.actionScale)
                .padding(.vertical, 8)
                if let children { children }
                else {
                    VStack(spacing: 0) {
                        flowText(copy.chip, 15, 24, width, tokens.ink, g.scale).padding(.bottom, 12)
                        flowText(copy.sheetTitle, 25, 32, width, tokens.ink, g.scale)
                        flowText(copy.sheetBody, 17, 25, width, tokens.mute, g.scale)
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel([copy.chip, copy.sheetTitle, copy.sheetBody].joined(separator: ". "))
                    .accessibilityFocused($sheetFocused)
                }
            }
            .padding(16)
            .background(page, in: .rect(cornerRadius: 48))
            .padding(.horizontal, 24)
            .padding(.top, max(top, g.y(348) + 24))
            .padding(.bottom, bottom + 24)
        }
        .frame(width: g.width, height: g.height)
    }
    private func flowText(_ text: String, _ size: Double, _ line: Double?, _ width: Double, _ ink: UInt32, _ scale: Double) -> some View {
        GelCubeUnboxPrint(text: text, pointSize: size * fontScale, width: width,
                         lineHeight: line.map { $0 * fontScale }, tracking: -0.5 * scale,
                         ink: GelCubeUnboxResources.color(ink), fontName: tokens.font)
            .accessibilityLabel(text)
    }

}

struct GelCubeUnboxActionStyle: ButtonStyle {
    var onPress: (Bool) -> Void
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of: configuration.isPressed) { onPress(configuration.isPressed) }
    }
}
