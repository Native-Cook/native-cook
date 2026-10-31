import SwiftUI
import UIKit

struct MicCordOnboarding: View {
    static let loop = 8.0
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @StateObject private var clock = MicCordOnboardingClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey:"micCordOnboardingTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let size = full.size
                let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
                ZStack(alignment:.topLeading) {
                    MicCordOnboardingResources.color(0x0c0b0f)
                    TimelineView(.animation(paused:pinned != nil || scene != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
                        let _ = clock.configure(size.width, top, pinned, reduced)
                        let _ = clock.sample(timeline.date, autoPlay && !reduced, reduced)
                        Canvas(opaque:false,colorMode:.nonLinear,rendersAsynchronously:false) { c, dimensions in
                            MicCordOnboardingArt.scene(c,dimensions,top,clock.time,clock.rope)
                        }
                    }
                    .accessibilityLabel("A pink microphone hanging on its cable. Drag it to swing it.")
                    .accessibilityAddTraits(.isImage)
                    .overlay {
                        if pinned == nil {
                            MicCordOnboardingTouch { state, point, velocity in clock.touch(state,point,velocity,reduced) }
                                .accessibilityHidden(true)
                        }
                    }
                    MicCordOnboardingCopy()
                        .frame(width:size.width-48,height:114)
                        .offset(x:24,y:size.height*0.56)
                        .accessibilityAddTraits(.isHeader)
                        .allowsHitTesting(false)
                    HStack(spacing:8) {
                        Capsule().fill(MicCordOnboardingResources.color(0xff63dd)).frame(width:18,height:6)
                        Circle().fill(.white.opacity(0.3)).frame(width:6,height:6)
                        Circle().fill(.white.opacity(0.3)).frame(width:6,height:6)
                    }
                    .frame(width:size.width)
                    .offset(y:size.height*0.56+144)
                    .accessibilityHidden(true)
                    Button { if pinned == nil { clock.cta(reduced) } } label: {
                        Text("Start Listening")
                            .font(.custom(MicCordOnboardingResources.shared.buttonFont.fontName,fixedSize:17))
                            .foregroundStyle(.white)
                            // Match RN's fractional glyph origin and baseline at 3x.
                            .offset(x:-1.0/3.0,y:1.0/3.0)
                            .frame(width:240,height:56)
                            .background(LinearGradient(colors:[MicCordOnboardingResources.color(0xffa3d7),MicCordOnboardingResources.color(0xff4fd8),MicCordOnboardingResources.color(0xc46bff)],startPoint:.topLeading,endPoint:.bottomTrailing))
                            .clipShape(Capsule())
                    }
                    .buttonStyle(MicCordOnboardingButtonStyle())
                    .accessibilityLabel("Start listening")
                    .offset(x:(size.width-240)/2,y:size.height-bottom-34-56)
                    .allowsHitTesting(pinned == nil)
                }
                .frame(width:size.width,height:size.height,alignment:.topLeading)
                .clipped()
            }.ignoresSafeArea()
        }
        .background(MicCordOnboardingResources.color(0x0c0b0f).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .persistentSystemOverlays(.hidden)
        .onAppear { clock.active(true,reduced) }
        .onDisappear { clock.active(false,reduced) }
        .onChange(of:scene) { _, value in clock.active(value == .active,reduced) }
        .onChange(of:reduced) { _, value in clock.active(scene == .active,value) }
        .onChange(of:autoPlay) { _, _ in clock.active(scene == .active,reduced) }
    }
}

struct MicCordOnboardingCopy: View {
    var body: some View {
        VStack(spacing:0) {
            (Text("A Quick & ").foregroundColor(.white) + Text("Easy Way").foregroundColor(MicCordOnboardingResources.color(0xff63dd)))
                .frame(height:38)
            Text("to Make Your Own").frame(height:38)
            Text("Podcast").frame(height:38)
        }
        .font(.custom(MicCordOnboardingResources.shared.titleFont.fontName,fixedSize:30))
        .kerning(-0.3)
        .foregroundStyle(.white)
        .multilineTextAlignment(.center)
        .offset(x:-1.0/3.0,y:1.0/3.0)
        .accessibilityElement(children:.ignore)
        .accessibilityLabel("A Quick & Easy Way\nto Make Your Own\nPodcast")
    }
}
struct MicCordOnboardingButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1) }
}

struct MicCordOnboardingTouch: UIViewRepresentable {
    var onTouch: (UIGestureRecognizer.State, CGPoint, CGPoint) -> Void
    func makeUIView(context: Context) -> MicCordOnboardingTouchView {
        let view = MicCordOnboardingTouchView(); view.onTouch = onTouch; return view
    }
    func updateUIView(_ uiView: MicCordOnboardingTouchView, context: Context) { uiView.onTouch = onTouch }
}
// RN Pan.minDistance(0): begin on contact, using UIKit's native pan velocity on release.
final class MicCordOnboardingPanRecognizer: UIPanGestureRecognizer {
    override func touchesBegan(_ touches:Set<UITouch>, with event:UIEvent) {
        super.touchesBegan(touches,with:event)
        if state == .possible { state = .began }
    }
}
final class MicCordOnboardingTouchView: UIView, UIGestureRecognizerDelegate {
    var onTouch: ((UIGestureRecognizer.State, CGPoint, CGPoint) -> Void)?
    override init(frame: CGRect) {
        super.init(frame:frame); backgroundColor = .clear; isOpaque = false
        let gesture = MicCordOnboardingPanRecognizer(target:self,action:#selector(handle(_:)))
        gesture.cancelsTouchesInView = false
        gesture.delegate = self
        addGestureRecognizer(gesture)
    }
    required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer) -> Bool { true }
    @objc private func handle(_ gesture:UIPanGestureRecognizer) {
        onTouch?(gesture.state,gesture.location(in:self),gesture.velocity(in:self))
    }
}
