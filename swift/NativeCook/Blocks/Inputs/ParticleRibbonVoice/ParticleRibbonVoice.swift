import SwiftUI

/// The approved RN block: the same live particle sheet, voice story, transcript, and hold-to-talk surface.
struct ParticleRibbonVoice: View {
    var autoPlay = false
    var sound = true
    var touchable = true
    var control = true
    var tapToToggle = false
    static let loop = 9.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @ScaledMetric(relativeTo:.body) private var textMultiplier = 1.0
    @State private var driver = ParticleRibbonVoiceDriver()
    @State private var typography = ParticleRibbonVoiceTypography()

    init(autoPlay: Bool = false, sound: Bool = true, touchable: Bool = true, control: Bool = true, tapToToggle: Bool = false) {
        self.autoPlay = autoPlay; self.sound = sound; self.touchable = touchable; self.control = control; self.tapToToggle = tapToToggle
        _ = ParticleRibbonVoiceResources.register
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of:"-particleRibbonVoiceTime"), i+1<args.count,
           let t = Double(args[i+1]), t.isFinite, t>=0 { return t }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width:geo.size.width+insets.leading+insets.trailing,height:geo.size.height+insets.top+insets.bottom)
            let g = ParticleRibbonVoiceLayout(width:size.width,height:size.height,top:insets.top,bottom:insets.bottom)
            ZStack(alignment:.topLeading) {
                TimelineView(.animation(paused:scenePhase != .active)) { timeline in
                    let s = driver.snapshot(at:timeline.date,auto:autoPlay,reduce:reduceMotion,pin:pinnedTime)
                    ParticleRibbonVoiceFrame(frame:s.frame,cap:s.cap,ask:s.ask,clock:s.clock,textAlpha:s.textAlpha,
                                             layout:g,typography:typography,textMultiplier:textMultiplier,control:control)
                        .accessibilityElement(children:.ignore)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityLabel("Voice assistant. \(s.mode == 0 ? "Hey Mara, How can I help you?":ParticleRibbonVoiceResources.states[s.mode])")
                        .accessibilityHint(tapToToggle ? "Double tap to start or stop listening":"Press and hold to talk. Drag along the ribbon to part it.")
                        .accessibilityValue(ParticleRibbonVoiceResources.states[s.mode])
                        .accessibilityAction(.default) { driver.toggle() }
                        .accessibilityAction(named:Text(s.mode == 1 ? "Stop listening":"Start listening")) { driver.toggle() }
                }
                ParticleRibbonVoiceTouches(driver:driver,layout:g)
                    .frame(width:size.width,height:size.height,alignment:.topLeading)
                    .accessibilityHidden(true)
            }
            .frame(width:size.width,height:size.height,alignment:.topLeading)
            .clipped()
            .offset(x:-insets.leading,y:-insets.top)
        }
        .preferredColorScheme(.dark)
        .onAppear { configure() }
        .onChange(of:autoPlay) { driver.reset(auto:autoPlay,reduce:reduceMotion) }
        .onChange(of:reduceMotion) { driver.reset(auto:autoPlay,reduce:reduceMotion) }
        .onChange(of:sound) { driver.sound = sound }
        .onChange(of:touchable) { driver.touchable = touchable }
        .onChange(of:tapToToggle) { driver.tapToToggle = tapToToggle }
        .onChange(of:scenePhase) { driver.setActive(scenePhase == .active,at:Date()) }
        .onDisappear { driver.stop() }
    }
    private func configure() {
        driver.sound = sound; driver.touchable = touchable; driver.tapToToggle = tapToToggle; driver.pinned = pinnedTime
    }
}
