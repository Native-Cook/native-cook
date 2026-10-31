import SwiftUI

struct VoiceOrbScanner: View {
    static let loop = VoiceOrbScannerMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @StateObject private var clock = VoiceOrbScannerClock()
    private var pinned: Double? {
        #if DEBUG
        if let v = UserDefaults.standard.object(forKey: "voiceOrbScannerTime") {
            return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            let top = safe.safeAreaInsets.top
            let bottom = safe.safeAreaInsets.bottom
            GeometryReader { full in
                TimelineView(.animation(paused: pinned != nil || scenePhase != .active)) { timeline in
                    let f = clock.sample(timeline.date, autoPlay, reduced, pinned)
                    scene(full.size, top, bottom, f)
                }
            }.ignoresSafeArea()
        }
        .background(VoiceOrbScannerResources.color(0x17121f))
        .preferredColorScheme(.dark)
        .persistentSystemOverlays(.hidden)
        .onAppear { clock.prepare(reduced) }
        .onDisappear { clock.suspend() }
        .onChange(of: scenePhase) { _, value in if value == .active { clock.prepare(reduced) } else { clock.suspend() } }
        .onChange(of: reduced) { _, value in clock.prepare(value) }
    }
    private func label(_ s: String, _ face: String, _ size: Double, _ color: Color = .white) -> some View {
        Text(s).font(VoiceOrbScannerResources.font(face, size)).foregroundStyle(color)
            .frame(height: VoiceOrbScannerResources.line(face, size)).fixedSize(horizontal: true, vertical: false)
    }
    private func scene(_ size: CGSize, _ top: Double, _ bottom: Double, _ f: VoiceOrbScannerMotion.Frame) -> some View {
        let w = size.width, h = size.height, cx = w/2, oy = top+270, r = min(118,w*0.29)
        let cw = (w-64)/3, cy = oy+r+100
        let phase = f.phase
        return ZStack(alignment: .topLeading) {
            VoiceOrbScannerResources.color(0x17121f)
            label("✕", "Inter-Medium", 15).offset(x:-1/displayScale,y:-1).frame(width: 38,height: 38)
                .background(.white.opacity(26.0/255), in: Circle()).position(x:w-39,y:top+27)
                .accessibilityLabel("Close").accessibilityAddTraits(.isButton)
            VStack(spacing: 10) {
                label("AI Voice Scanning", "Outfit-Medium", 22).offset(x:-1/displayScale)
                HStack(spacing: 6) {
                    Circle().fill(phase == 2 ? VoiceOrbScannerResources.color(0x8ff0b4) : phase == 1 ? VoiceOrbScannerResources.color(0xff6fb7) : .white.opacity(128.0/255)).frame(width:7,height:7)
                    label(phase == 2 ? "Completed" : phase == 1 ? "Listening…" : "Ready", "Inter-Medium", 12)
                }.padding(.horizontal,10).frame(height:26).background(.white.opacity(26.0/255),in:Capsule())
            }.frame(width:w).offset(y:top+56)
            label("Hold the orb and sing a note", "Inter-Regular", 14, .white.opacity(0.6))
                .frame(width:w).offset(y:oy+r+30-1/displayScale).opacity(1-VoiceOrbScannerMotion.smooth(f.S*2))
            label("Voice resume", "Outfit-Medium", 17).frame(width:w).offset(y:cy-32-1/displayScale)
            ForEach(0..<3, id: \.self) { i in
                card(i, cw, cy, f).offset(x:24+Double(i)*(cw+8),y:cy)
            }
            VoiceOrbScannerArt(size: size, top: top, frame: f, reduced: reduced, autoPlay: autoPlay)
                .allowsHitTesting(false).accessibilityHidden(true)
            VoiceOrbScannerTouch { began in
                guard pinned == nil else { return }
                if began { clock.begin(reduced) } else { clock.end() }
            }.frame(width:2*r+40,height:2*r+40).scaleEffect(f.held ? 0.97 : 1).position(x:cx,y:oy)
                .accessibilityElement().accessibilityAddTraits(.isButton)
                .accessibilityLabel(phase == 2 ? "Scan again" : "Hold to sing and scan your voice")
            Button { if pinned == nil { clock.rescan(reduced) } } label: {
                VStack(alignment:.leading,spacing:3) {
                    label("Your voice, your playlist", "Outfit-Medium", 18, VoiceOrbScannerResources.color(0x1b1326))
                    label("Songs picked for your range · tap to scan again", "Inter-Regular", 12.5, VoiceOrbScannerResources.color(0x1b1326,0.65))
                }.offset(y:1/displayScale).frame(maxWidth:.infinity,alignment:.leading).padding(.horizontal,18).frame(height:76)
                    .background(VoiceOrbScannerResources.color(0xd9c9ff),in:RoundedRectangle(cornerRadius:22,style:.circular))
            }.buttonStyle(VoiceOrbScannerButtonStyle()).frame(width:w-40).offset(x:20,y:h-bottom-94)
                .accessibilityLabel("Your voice, your playlist. Scan again")
        }.frame(width:w,height:h,alignment:.topLeading).allowsHitTesting(pinned == nil)
    }
    private func card(_ i: Int, _ width: Double, _ y: Double, _ f: VoiceOrbScannerMotion.Frame) -> some View {
        let names = ["Voice tier", "Vocal range", "Breath"], values = ["Soprano", "2 octaves", "2.2 sec"]
        let k = VoiceOrbScannerMotion.smooth((f.S-0.55-Double(i)*0.08)/0.3)
        return ZStack(alignment:.topLeading) {
            RoundedRectangle(cornerRadius:20,style:.circular).fill(VoiceOrbScannerResources.color(0x1c1626,0.92))
            RoundedRectangle(cornerRadius:20,style:.circular).strokeBorder(.white.opacity(20.0/255),lineWidth:1)
            Circle().fill(.white.opacity(13.0/255)).frame(width:34,height:34).offset(x:10,y:12)
            VStack(alignment:.leading,spacing:4) {
                label(names[i],"Inter-Regular",12,.white.opacity(0.6)).offset(y:1/displayScale)
                label(values[i],"Inter-SemiBold",16)
            }.frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.bottomLeading).padding(13)
        }.frame(width:width,height:128).scaleEffect(VoiceOrbScannerMotion.mix(0.94,1,k)).offset(y:(1-k)*14).opacity(k)
            .accessibilityElement(children:.ignore).accessibilityLabel("\(names[i]): \(values[i])").accessibilityHidden(f.phase != 2)
    }
}

struct VoiceOrbScannerButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.scaleEffect(configuration.isPressed ? 0.98 : 1) }
}
