import SwiftUI

struct WovenSpeakerPlayer: View {
    static let loop = WovenSpeakerPlayerMotion.loop
    var autoPlay: Bool
    var onOpenQueue: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @State private var driver = WovenSpeakerPlayerDriver()
    @GestureState private var gestureActive = false
    @State private var gestureZone = 0
    @State private var pillAt: Date?
    @State private var pillFailed = false
    @State private var titleActivated = false
    @State private var titleFailed = false
    @State private var revision = 0
    @State private var reduceUpdates = false

    init(autoPlay: Bool = false, onOpenQueue: (() -> Void)? = nil) {
        self.autoPlay = autoPlay; self.onOpenQueue = onOpenQueue
        _ = WovenSpeakerPlayerResources.register
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-wovenSpeakerPlayerTime"), i+1 < args.count,
           let t = Double(args[i+1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    var body: some View {
        let _ = revision
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width+insets.leading+insets.trailing,height: geo.size.height+insets.top+insets.bottom)
            let layout = WovenSpeakerPlayerLayout(size,insets.top,insets.bottom)
            stage(layout).offset(x: -insets.leading,y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onAppear { driver.onOpenQueue = onOpenQueue }
        .task(id: pillAt) {
            guard let start = pillAt else { return }
            do {
                try await Task.sleep(for: .milliseconds(600))
                if gestureZone == 3 && pillAt == start && !pillFailed {
                    pillFailed = true; driver.pill(false,at: Date())
                }
            } catch { /* Finalizing the touch cancels the recognizer's timeout. */ }
        }
        .onChange(of: autoPlay) { driver.reset() }
        .onChange(of: reduceMotion) { driver.reset(); reduceUpdates = false }
        .onChange(of: scenePhase) { driver.suspend() }
        .onChange(of: gestureActive) {
            guard !gestureActive && gestureZone != 0 else { return }
            switch gestureZone {
            case 1: driver.end(.zero,at: Date(),cancelled: true)
            case 2: driver.endTitle(0,at: Date())
            case 3: driver.pill(false,at: Date()); pillAt = nil
            default: break
            }
            gestureZone = 0; revision += 1
        }
    }
    private func stage(_ l: WovenSpeakerPlayerLayout) -> some View {
        TimelineView(.animation(paused: scenePhase != .active || pinnedTime != nil || (reduceMotion && !reduceUpdates))) { timeline in
            let k = driver.frame(at: timeline.date,autoPlay: autoPlay,reduce: reduceMotion,pin: pinnedTime)
            ZStack(alignment: .topLeading) {
                WovenSpeakerPlayerFrame(look: k,layout: l,pixels: displayScale,index: driver.index,slide: driver.slide,pillPress: driver.pillPress)
                    .allowsHitTesting(false).accessibilityHidden(true)
                dome(l,k)
                Color.clear.contentShape(Rectangle())
                    .frame(width: 380*l.scale,height: 88*l.scale)
                    .position(x: l.cx,y: l.y(590)+16*l.scale)
                    .gesture(titleGesture())
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityLabel(k.glyph > 0.5 ? WovenSpeakerPlayerResources.titles[driver.index] : "Press play.")
                    .accessibilityHint(k.glyph > 0.5 ? "Swipe left or right to change track" : "")
                Color.clear.contentShape(Rectangle())
                    .frame(width: 124*l.scale+8,height: 48*l.scale)
                    .position(x: l.cx,y: l.y(692))
                    .gesture(pillGesture())
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel(k.glyph > 0.5 ? "Open queue" : "Learn more")
                    .accessibilityAction { if k.glyph > 0.5 { onOpenQueue?() } }
            }
            .frame(width: l.width,height: l.height,alignment: .topLeading)
            .clipped()
            .background(WovenSpeakerPlayerResources.color(0x131313))
            .coordinateSpace(name: "WovenSpeakerPlayerScreen")
            .onChange(of: timeline.date) {
                if reduceMotion && !driver.reducedFramesNeeded { reduceUpdates = false }
            }
        }
    }
    private func dome(_ l: WovenSpeakerPlayerLayout, _ k: WovenSpeakerPlayerLook) -> some View {
        Color.clear.contentShape(Rectangle())
            .frame(width: 340*l.scale,height: 340*l.scale)
            .position(x: l.cx,y: l.cy)
            .gesture(DragGesture(minimumDistance: 0,coordinateSpace: .named("WovenSpeakerPlayerScreen"))
                .updating($gestureActive) { _,s,_ in s = true }
                .onChanged { v in
                    if reduceMotion { reduceUpdates = true }
                    func local(_ p: CGPoint) -> CGPoint { CGPoint(x: (p.x-l.cx)/l.scale,y: (p.y-l.cy)/l.scale) }
                    if gestureZone == 0 { gestureZone = 1; driver.begin(local(v.startLocation),at: v.time) }
                    driver.move(local(v.location),translation: CGSize(width: v.translation.width/l.scale,height: v.translation.height/l.scale),at: v.time)
                }
                .onEnded { v in
                    driver.end(CGSize(width: v.translation.width/l.scale,height: v.translation.height/l.scale),at: v.time); gestureZone = 0
                })
            .accessibilityElement(children: .contain)
            .accessibilityLabel("Woven speaker")
            .overlay {
                ZStack {
                    Color.clear.accessibilityElement().accessibilityLabel("Track position")
                        .accessibilityValue(positionWords(k.pos)+" of 3 minutes 20 seconds")
                        .accessibilityHint("Turn the dome to scrub the track. Swipe up or down to seek five seconds.")
                        .accessibilityAdjustableAction { dir in
                            driver.seek(dir == .increment ? 5 : -5,at: Date()); reduceUpdates = true; revision += 1
                        }
                    accessibilityButton(k.glyph > 0.5 ? "Pause" : "Play",width: 56*l.scale) { driver.toggle(at: Date()); reduceUpdates = true; revision += 1 }
                    accessibilityButton("Previous track",width: 44*l.scale) { driver.skip(-1,at: Date()); reduceUpdates = true; revision += 1 }.offset(x: -67*l.scale)
                    accessibilityButton("Next track",width: 44*l.scale) { driver.skip(1,at: Date()); reduceUpdates = true; revision += 1 }.offset(x: 67*l.scale)
                }.allowsHitTesting(false)
            }
    }
    private func accessibilityButton(_ label: String, width: Double, action: @escaping () -> Void) -> some View {
        Color.clear.frame(width: width,height: width).accessibilityElement().accessibilityLabel(label)
            .accessibilityAddTraits(.isButton).accessibilityAction(.default, action)
    }
    private func positionWords(_ pos: Double) -> String {
        let p = Int(pos.rounded()), m = p/60, s = p%60
        return "\(m) \(m == 1 ? "minute" : "minutes") \(s) \(s == 1 ? "second" : "seconds")"
    }
    private func titleGesture() -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _,s,_ in s = true }
            .onChanged { v in
                if reduceMotion { reduceUpdates = true }
                if gestureZone == 0 {
                    gestureZone = 2; titleActivated = false; titleFailed = false
                    driver.beginTitle(at: v.time)
                }
                if !titleActivated && abs(v.translation.height) > 18 { titleFailed = true }
                if !titleFailed && abs(v.translation.width) > 8 { titleActivated = true }
                if titleActivated { driver.moveTitle(v.translation.width,at: v.time) }
            }
            .onEnded { v in
                if gestureZone == 2 { driver.endTitle(v.velocity.width,at: v.time) }; gestureZone = 0
            }
    }
    private func pillGesture() -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _,s,_ in s = true }
            .onChanged { v in
                if reduceMotion { reduceUpdates = true }
                if gestureZone == 0 {
                    gestureZone = 3; pillAt = v.time; pillFailed = false; driver.pill(true,at: v.time)
                }
                if !pillFailed && hypot(v.translation.width,v.translation.height) > 10 {
                    pillFailed = true; driver.pill(false,at: v.time)
                }
            }
            .onEnded { v in
                let success = !pillFailed && v.time.timeIntervalSince(pillAt ?? v.time) <= 0.6 && hypot(v.translation.width,v.translation.height) < 10
                driver.pill(false,success: success,at: v.time); gestureZone = 0; pillAt = nil
            }
    }
}
