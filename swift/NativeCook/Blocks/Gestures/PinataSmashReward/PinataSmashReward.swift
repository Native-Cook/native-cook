import SwiftUI

/// The approved React Native daily piñata, using the same art, mesh, closed-form story and driver.
struct PinataSmashReward: View {
    let autoPlay: Bool
    static let loop = 8.4
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var engine = PinataSmashRewardEngine()
    @State private var rmAnimating = false
    @State private var rmGeneration = 0
    @State private var gesturePending = false
    @GestureState private var gestureActive = false

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = PinataSmashRewardResources.register
    }
    private var pin: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of:"-pinataSmashRewardTime"), i+1 < args.count,
           let t = Double(args[i+1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { geometry in
            let insets = geometry.safeAreaInsets
            let size = CGSize(width:geometry.size.width+insets.leading+insets.trailing,
                              height:geometry.size.height+insets.top+insets.bottom)
            stage(size,insets.top)
                .frame(width:size.width,height:size.height,alignment:.topLeading)
                .offset(x:-insets.leading,y:-insets.top)
        }
        .preferredColorScheme(.light)
        .onChange(of:autoPlay && !reduced) { engine.reset(at:Date()) }
        .onChange(of:scenePhase) { engine.setActive(scenePhase == .active,at:Date()) }
        .onChange(of:gestureActive) {
            if !gestureActive && gesturePending { finish(.zero) }
        }
        .task(id:scenePhase == .active ? rmGeneration : -1) {
            guard rmAnimating && scenePhase == .active else { return }
            do { try await Task.sleep(for:.milliseconds(400)); rmAnimating = false } catch { }
        }
    }
    private func finish(_ velocity: CGSize) {
        gesturePending = false
        if engine.end(velocity,reduced:reduced,at:Date()) { rmAnimating = true; rmGeneration += 1 }
    }
    private func stage(_ size: CGSize, _ inset: Double) -> some View {
        let s = size.width/402, dy = (size.height/s-874)/2, top = inset/s-62
        let geo = PinataSmashRewardResources.geo(top,dy)
        return ZStack(alignment:.topLeading) {
            Color.white
            TimelineView(.animation(paused:scenePhase != .active || (reduced && !rmAnimating) || (!reduced && pin != nil))) { timeline in
                let f = engine.frame(at:timeline.date,geo:geo,auto:autoPlay && !reduced,reduced:reduced,pin:pin)
                PinataSmashRewardCanvas(frame:f,width:size.width,height:size.height,scale:s,dy:dy,top:top)
            }
            .allowsHitTesting(false)
            Color.clear.contentShape(Rectangle())
                .frame(width:size.width,height:size.height,alignment:.topLeading)
                .gesture(DragGesture(minimumDistance:0)
                    .updating($gestureActive) { _,active,_ in active = true }
                    .onChanged { value in
                        if !engine.fingerDown {
                            gesturePending = true
                            engine.begin(.init(x:value.startLocation.x/s,y:value.startLocation.y/s-dy),reduced:reduced,at:Date())
                        }
                        engine.move(.init(x:value.location.x/s,y:value.location.y/s-dy),reduced:reduced)
                    }
                    .onEnded { value in finish(.init(width:value.velocity.width/s,height:value.velocity.height/s)) })
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Daily piñata. Keep tapping the piñata to break it open.")
                .accessibilityHint("Hits the piñata.")
                .accessibilityAction { if !reduced { engine.accessibilityHit() } }
        }
        .frame(width:size.width,height:size.height,alignment:.topLeading)
        .clipped()
    }
}
