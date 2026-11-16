import SwiftUI
import UIKit

/// The RN block's default flower canvas, glass volume, menu and touch choreography.
struct GlassPourMenu: View {
    static let loop = GlassPourMenuMotion.loop
    var autoPlay: Bool
    var items: [GlassPourMenuItem]
    var sound: Bool
    var haptics: Bool
    var dragToSelect: Bool
    var anchor: CGPoint?
    var placement: GlassPourMenuPlacement
    var onSelect: (String) -> Void
    var onOpenChange: (Bool) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.sizeCategory) private var sizeCategory
    @State private var driver = GlassPourMenuDriver()
    @GestureState private var touching = false
    @State private var gesturePending = false
    @AccessibilityFocusState private var focused: String?
    typealias M = GlassPourMenuMotion

    init(autoPlay: Bool = false, items: [GlassPourMenuItem] = GlassPourMenuItem.defaults,
         sound: Bool = false, haptics: Bool = true, dragToSelect: Bool = true,
         anchor: CGPoint? = nil, placement: GlassPourMenuPlacement = .auto,
         onSelect: @escaping (String) -> Void = { _ in }, onOpenChange: @escaping (Bool) -> Void = { _ in }) {
        precondition((2...7).contains(items.count), "GlassPourMenu requires 2–7 items")
        self.autoPlay = autoPlay; self.items = items; self.sound = sound; self.haptics = haptics
        self.dragToSelect = dragToSelect; self.anchor = anchor; self.placement = placement
        self.onSelect = onSelect; self.onOpenChange = onOpenChange
        _ = GlassPourMenuResources.register
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-glassPourMenuTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size, insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.light) // dark status bar ink on the pale page (RN: StatusBar style="dark")
        .onChange(of: touching) {
            if !touching && gesturePending {
                driver.end(velocityY: 0, cancelled: true); gesturePending = false
            }
        }
        .onChange(of: scenePhase) { driver.resetClock() }
        .onChange(of: driver.menuOpen) {
            if !driver.isAutomatic { focused = driver.menuOpen ? items.first(where: { !$0.disabled })?.key : "more" }
        }
    }
    private func stage(_ size: CGSize, _ insets: EdgeInsets) -> some View {
        // RN PixelRatio.getFontScale() is the content-size multiplier, not UIFontMetrics' text-style curve.
        let fontScale = GlassPourMenuFontScale.value(sizeCategory)
        let fontSize = 17 * fontScale
        let g = M.Geometry(half: max(160, min(250, size.width - 32)) / 2,
                           pitch: max(48, fontSize * 1.35 + 16), fontSize: fontSize, count: items.count)
        let usable = size.height - insets.top - insets.bottom
        let defaultY = max(insets.top + 330 * usable / 778, insets.top + 130)
        let above = placement == .above || (placement == .auto && anchor != nil
            && size.height - (anchor?.y ?? 0) - insets.bottom < g.bottom
            && (anchor?.y ?? 0) - insets.top > g.bottom)
        let dir = above ? -1.0 : 1.0
        let low = above ? g.bottom : -g.beadY + g.beadR
        let high = above ? -g.beadY + g.beadR : g.bottom
        let origin = CGPoint(x: max(g.half + 8, min(size.width - g.half - 8, anchor?.x ?? size.width / 2)),
                             y: max(insets.top + low + 8, min(size.height - insets.bottom - high - 8, anchor?.y ?? defaultY)))
        return ZStack(alignment: .topLeading) {
            GlassPourMenuResources.color(0xF4E1DE)
            TimelineView(.animation(paused: scenePhase != .active || (reduceMotion && !driver.fading)
                                    || (!reduceMotion && pinnedTime != nil && !driver.pinCancelled))) { timeline in
                let f = driver.frame(at: timeline.date, pin: pinnedTime)
                GlassPourMenuFrame(frame: f, geometry: g, items: items, origin: origin, direction: dir,
                                   size: size, reduced: reduceMotion, fade: driver.fade,
                                   automatic: driver.isAutomatic || (pinnedTime != nil && !driver.pinCancelled))
            }
            Color.clear.contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0)
                    .updating($touching) { _, state, _ in state = true }
                    .onChanged { v in
                        configure(g)
                        gesturePending = true
                        if !driver.fingerDown {
                            driver.begin(CGPoint(x: v.startLocation.x - origin.x, y: (v.startLocation.y - origin.y) * dir))
                        }
                        driver.move(CGPoint(x: v.location.x - origin.x, y: (v.location.y - origin.y) * dir), dragToSelect: dragToSelect)
                    }
                    .onEnded { v in
                        gesturePending = false
                        driver.end(velocityY: v.velocity.height * dir)
                    })
                .accessibilityHidden(true)
            accessibility(origin, dir, g)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .contain)
        .accessibilityAction(.escape) { driver.activate(-2) }
        .onAppear { configure(g) }
        .onChange(of: reduceMotion) { configure(g) }
        .onChange(of: autoPlay) { configure(g) }
        .onChange(of: sizeCategory) { configure(g) }
        .onChange(of: size) { configure(g) }
    }
    private func configure(_ g: M.Geometry) {
        driver.configure(geometry: g, items: items, auto: autoPlay, reduced: reduceMotion,
                         haptics: haptics, sound: sound, onSelect: onSelect, onOpenChange: onOpenChange)
    }
    private func accessibility(_ o: CGPoint, _ dir: Double, _ g: M.Geometry) -> some View {
        ZStack(alignment: .topLeading) {
            if !driver.menuOpen {
                Button { driver.activate(-1) } label: { Color.clear.frame(width: max(44, g.radius * 2), height: max(44, g.radius * 2)) }
                    .accessibilityLabel("More actions")
                    .accessibilityValue("Collapsed")
                    .accessibilityFocused($focused, equals: "more")
                    .position(o)
            } else {
                Button { driver.activate(-2) } label: { Color.clear.frame(width: max(44, g.beadR * 2), height: max(44, g.beadR * 2)) }
                    .accessibilityLabel("Close menu")
                    .position(x: o.x, y: o.y + dir * g.beadY)
                ForEach(Array(items.enumerated()), id: \.element.id) { i, item in
                    Button { driver.activate(i) } label: { Color.clear.frame(width: g.half * 2, height: max(44, g.pitch)) }
                        .accessibilityLabel(item.label)
                        .disabled(item.disabled)
                        .accessibilityFocused($focused, equals: item.key)
                        .position(x: o.x, y: o.y + dir * g.rows[i])
                }
            }
        }
        .buttonStyle(.plain)
        .allowsHitTesting(false)
    }
}

enum GlassPourMenuPlacement { case auto, above, below }

/// Values of RCTAccessibilityManager's iOS multipliers (RN's PixelRatio font scale).
enum GlassPourMenuFontScale {
    static func value(_ category: ContentSizeCategory) -> Double {
        switch category {
        case .extraSmall: return 0.823
        case .small: return 0.882
        case .medium: return 0.941
        case .large: return 1
        case .extraLarge: return 1.118
        case .extraExtraLarge: return 1.235
        case .extraExtraExtraLarge: return 1.353
        case .accessibilityMedium: return 1.786
        case .accessibilityLarge: return 2.143
        case .accessibilityExtraLarge: return 2.643
        case .accessibilityExtraExtraLarge: return 3.143
        case .accessibilityExtraExtraExtraLarge: return 3.571
        @unknown default: return 1
        }
    }
}
