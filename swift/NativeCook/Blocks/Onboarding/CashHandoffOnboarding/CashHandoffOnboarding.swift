import SwiftUI
import UIKit

struct CashHandoffOnboarding: View {
    static let loop = CashHandoffOnboardingMotion.LOOP
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var clock = CashHandoffOnboardingClock()
    @State private var touching = false
    @GestureState private var gestureActive = false
    private let resources = CashHandoffOnboardingResources.shared
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey: "cashHandoffOnboardingTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let l = CashHandoffOnboardingMotion.Layout(width: full.size.width, height: full.size.height, top: safe.safeAreaInsets.top, bottom: safe.safeAreaInsets.bottom)
                let artGeometry = CashHandoffOnboardingArt.Geometry(l)
                ZStack(alignment: .topLeading) {
                    Color.white
                    UnevenRoundedRectangle(bottomLeadingRadius: 34, bottomTrailingRadius: 34, style: .circular)
                        .fill(CashHandoffOnboardingResources.color(0xc391f4))
                        .frame(width: l.width, height: l.panel)
                    VStack(alignment: .leading, spacing: 4) {
                        // RN clips Anton to lineHeight 72 and raises its baseline by 12 pt.
                        Text("RECEIVE").font(.custom("Anton-Regular", fixedSize: 64)).tracking(0.5)
                            .foregroundStyle(.white).offset(y: -12).frame(height: 72).clipped()
                            .accessibilityAddTraits(.isHeader)
                        Text("Accept money directly from friends or family\nwithout the usual bank delays.")
                            .font(.custom("Inter-Medium", fixedSize: 14))
                            .lineSpacing(20 - (UIFont(name: "Inter-Medium", size: 14)?.lineHeight ?? 17))
                            .foregroundStyle(.white.opacity(0.92)).fixedSize(horizontal: false, vertical: true).frame(height: 40)
                    }
                    .frame(width: l.width - 48, alignment: .leading).offset(x: 24, y: l.top + 40)
                    .allowsHitTesting(false)
                    TimelineView(.animation(paused: pinned != nil || scenePhase != .active || clock.parked || (!(autoPlay && !reduced) && !clock.running))) { timeline in
                        let sample = clock.sample(timeline.date, l, autoPlay && !reduced, pinned)
                        Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                            CashHandoffOnboardingArt.draw(context, l, sample.scene, sample.time, sample.now, artGeometry)
                        }
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .local)
                            .updating($gestureActive) { _, active, _ in active = true }
                            .onChanged { value in
                                guard pinned == nil else { return }
                                if !touching { touching = true; clock.begin(value.startLocation, reduced) }
                                clock.move(value.location)
                            }
                            .onEnded { _ in guard pinned == nil else { return }; touching = false; clock.end(l, reduced) })
                        .onChange(of: gestureActive) { _, active in
                            if !active && touching && pinned == nil { touching = false; clock.end(l, reduced) }
                        }
                        .accessibilityElement(children: .ignore)
                        .accessibilityAddTraits(.isButton)
                        .accessibilityLabel("A hand holding out banknotes to an open hand. Drag the notes across to send them.")
                    }.frame(width: l.width, height: l.panel)
                    HStack(spacing: 5) {
                        Capsule().fill(CashHandoffOnboardingResources.color(0xc9c9cf)).frame(width: 4, height: 16)
                        Capsule().fill(CashHandoffOnboardingResources.color(0x2a2a2e)).frame(width: 4, height: 26)
                        Capsule().fill(CashHandoffOnboardingResources.color(0xc9c9cf)).frame(width: 4, height: 16)
                    }.frame(width: l.width, height: 26).offset(y: l.panel + 34).allowsHitTesting(false)
                    VStack(spacing: 16) {
                        Button(action: { clock.cta() }) {
                            Text("Get Started").font(.custom("Inter-SemiBold", fixedSize: 16))
                                .foregroundStyle(.white).frame(maxWidth: .infinity).frame(height: 56)
                                .background(CashHandoffOnboardingResources.color(0x2c2c30), in: Capsule())
                        }.buttonStyle(CashHandoffOnboardingButtonStyle()).accessibilityLabel("Get started")
                        (Text("Already have an account? ").font(.custom("Inter-Medium", fixedSize: 14)).foregroundColor(CashHandoffOnboardingResources.color(0x3a3a40)) + Text("Log in").font(.custom("Inter-SemiBold", fixedSize: 14)).foregroundColor(CashHandoffOnboardingResources.color(0x111111)))
                    }
                    .frame(width: l.width - 40)
                    .frame(height: 89, alignment: .top)
                    .offset(x: 20, y: l.height - l.bottom - 16 - 89)
                }.frame(width: l.width, height: l.height, alignment: .topLeading)
            }.ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { clock.active(scenePhase == .active, reduced, pinned != nil) }
        .onDisappear { clock.active(false, reduced, pinned != nil) }
        .onChange(of: scenePhase) { _, phase in clock.active(phase == .active, reduced, pinned != nil) }
        .onChange(of: reduced) { _, value in clock.active(scenePhase == .active, value, pinned != nil) }
    }
}
struct CashHandoffOnboardingButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View { configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1) }
}
