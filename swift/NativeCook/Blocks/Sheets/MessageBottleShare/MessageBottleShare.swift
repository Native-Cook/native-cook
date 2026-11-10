import SwiftUI

struct MessageBottleShare: View {
    static let loop = MessageBottleShareMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = MessageBottleShareClock()
    private let ink = Color(.sRGB, red: 42/255, green: 34/255, blue: 56/255, opacity: 1)
    private let ctaInk = Color(.sRGB, red: 251/255, green: 244/255, blue: 238/255, opacity: 1)
    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = MessageBottleShareResources.registered
    }
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "messageBottleShareTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top, bottom = proxy.safeAreaInsets.bottom
            let l = MessageBottleShareMotion.Layout(proxy.size.width, proxy.size.height+top+bottom, top, bottom)
            TimelineView(.animation(paused: pinned != nil || scenePhase != .active || (reduced && !clock.fading))) { timeline in
                let s = clock.sample(timeline.date, l, autoPlay && !reduced, reduced, pinned)
                ZStack(alignment: .topLeading) {
                    MessageBottleShareSea(layout: l, frame: s.frame, time: s.time)
                        .frame(width: l.w, height: l.h)
                        .accessibilityHidden(true)
                    if pinned == nil {
                        MessageBottleShareTouch { state, translation, velocity in
                            clock.touch(state, translation, velocity, l, reduced)
                        }
                        .frame(width: l.scale*2.6, height: l.scale*1.6)
                        .offset(x: l.homeX-l.scale*1.3, y: l.homeY-l.scale*0.8)
                        .allowsHitTesting(!s.frame.sent)
                        .accessibilityElement(children: .ignore)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityLabel("Bottle with your letter")
                        .accessibilityHint("Throws the bottle out to sea and sends the letter")
                        .accessibilityAction { clock.activateBottle(l, reduced) }
                    }
                    Button {} label: {
                        Text("Cancel").font(.custom("Inter24pt-Regular", fixedSize: 17)).foregroundStyle(ink)
                            .offset(y: 1.0/3).padding(.horizontal, 8).frame(height: 44)
                            .contentShape(Rectangle().inset(by: -4))
                    }
                    .buttonStyle(MessageBottleSharePressStyle(reduced: reduced))
                    .offset(x: 8, y: l.top)
                    // RN pixel-rounds the text line boxes and excludes the serif's extra leading.
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Cast your letter").font(.custom("InstrumentSerif-Regular", fixedSize: 42))
                            .frame(height: 48).offset(y: -10.0/3).accessibilityAddTraits(.isHeader)
                        Text("To Maya · 214 words · written at the lake").font(.custom("Inter24pt-Regular", fixedSize: 14))
                            .foregroundStyle(ink.opacity(0.62)).frame(height: 20)
                    }
                    .foregroundStyle(ink).opacity(s.unsentOpacity)
                    .offset(x: 24, y: l.titleTop+s.unsentY).accessibilityHidden(s.frame.sent)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("On its way").font(.custom("InstrumentSerif-Regular", fixedSize: 42))
                            .frame(height: 48).offset(y: -10.0/3).accessibilityAddTraits(.isHeader)
                        Text("Maya will find it at dawn.").font(.custom("Inter24pt-Regular", fixedSize: 14))
                            .foregroundStyle(ink.opacity(0.62)).frame(height: 20)
                    }
                    .foregroundStyle(ink).opacity(s.sentOpacity)
                    .offset(x: 24, y: l.titleTop+s.sentY).accessibilityHidden(!s.frame.sent)
                    Button { clock.press(l, reduced) } label: {
                        HStack(spacing: 8) {
                            MessageBottleShareIcon(sent: s.frame.sent).stroke(ctaInk, style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                                .frame(width: 18, height: 18)
                            Text(s.frame.sent ? "Write Another" : "Cast Off").font(.custom("Inter-SemiBold", fixedSize: 16))
                                .foregroundStyle(ctaInk)
                                .offset(x: s.frame.sent ? 0 : -1.0/3, y: -1.0/3)
                        }
                        .frame(width: l.w-48, height: 56)
                        .background(ink, in: Capsule())
                        .contentShape(Capsule())
                    }
                    .buttonStyle(MessageBottleSharePressStyle(reduced: reduced))
                    .offset(x: 24, y: l.ctaTop)
                    .accessibilityLabel(s.frame.sent ? "Write Another" : "Cast Off")
                    Text("or throw the bottle").font(.custom("Inter24pt-Regular", fixedSize: 13))
                        .foregroundStyle(.white.opacity(0.82)).frame(width: l.w, height: 20)
                        .opacity(s.unsentOpacity).offset(y: l.hintTop+s.unsentY+1.0/3)
                        .accessibilityHidden(s.frame.sent)
                    Canvas { context, _ in
                        let r = 22*(1-0.12*s.frame.fingerPress)
                        let path = Path(ellipseIn: CGRect(x: s.frame.fingerX-r, y: s.frame.fingerY-r, width: r*2, height: r*2))
                        context.fill(path, with: .color(ink.opacity(0.12*s.frame.finger)))
                        context.stroke(path, with: .color(.white.opacity(0.6*s.frame.finger)), lineWidth: 1.5)
                    }
                    .allowsHitTesting(false).accessibilityHidden(true)
                }
                .frame(width: l.w, height: l.h, alignment: .topLeading)
                .clipped().allowsHitTesting(pinned == nil)
            }
            .offset(y: -top)
        }
        .preferredColorScheme(.light)
        .onAppear {
            _ = MessageBottleShareResources.registered
            clock.reset(autoPlay, reduced)
        }
        .onChange(of: scenePhase) { _, _ in clock.active() }
        .onChange(of: reduced) { _, value in clock.reset(autoPlay, value) }
        .onChange(of: autoPlay) { _, value in clock.reset(value, reduced) }
    }
}
struct MessageBottleShareIcon: Shape {
    let sent: Bool
    func path(in rect: CGRect) -> Path {
        var p = Path()
        func move(_ x: Double, _ y: Double) { p.move(to: CGPoint(x: x*rect.width/18, y: y*rect.height/18)) }
        func line(_ x: Double, _ y: Double) { p.addLine(to: CGPoint(x: x*rect.width/18, y: y*rect.height/18)) }
        if sent { move(9,3); line(9,15); move(3,9); line(15,9) }
        else { move(3,15); line(15,3); move(7,3); line(15,3); line(15,11) }
        return p
    }
}
struct MessageBottleSharePressStyle: ButtonStyle {
    let reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1)
            .opacity(configuration.isPressed && reduced ? 0.72 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.15), value: configuration.isPressed)
    }
}
