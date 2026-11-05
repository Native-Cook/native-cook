import SwiftUI

struct AuroraTeammatesOnboarding: View {
    static let loop=9.0
    var autoPlay=false
    var onComplete:(()->Void)?
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var clock=AuroraTeammatesOnboardingClock()
    private var pinned:Double? {
        #if DEBUG
        if let value=UserDefaults.standard.object(forKey:"auroraTeammatesOnboardingTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)")
        }
        #endif
        return nil
    }
    var body:some View {
        GeometryReader { proxy in
            let top=proxy.safeAreaInsets.top,bottom=proxy.safeAreaInsets.bottom
            let l=AuroraTeammatesOnboardingTheme.Layout(W:proxy.size.width,H:proxy.size.height+top+bottom)
            TimelineView(.animation(paused:pinned != nil || phase != .active || (reduced && !clock.running))) { timeline in
                let _=AuroraTeammatesOnboardingResources.shared.prepare(l)
                let _ = { clock.onComplete=onComplete }()
                let sample=clock.sample(timeline.date,autoPlay,reduced,pinned,l)
                let sc=sample.scene
                ZStack(alignment:.topLeading) {
                    AuroraTeammatesOnboardingLayers(l:l,sc:sc)
                    Canvas(opaque:false,colorMode:.nonLinear) { c,_ in AuroraTeammatesOnboardingArt(l:l,sc:sc).drawFlare(c) }
                    Canvas(opaque:false,colorMode:.nonLinear) { c,_ in AuroraTeammatesOnboardingArt(l:l,sc:sc,reduced:reduced,press:sample.press).draw(c) }
                    if !reduced { AuroraTeammatesOnboardingMotes(layout:l,scene:sc).allowsHitTesting(false) }
                    if pinned==nil {
                        AuroraTeammatesOnboardingTouch { state,p,delta,velocity in clock.touch(state,p,delta,velocity,l,reduced) }.accessibilityHidden(true)
                    }
                }
                .accessibilityElement(children:.ignore)
                .accessibilityLabel(AuroraTeammatesOnboardingTheme.accessibility[clock.page])
                .accessibilityValue("Page \(clock.page+1) of 3")
                .accessibilityAdjustableAction { direction in
                    guard pinned==nil else { return }
                    clock.step(direction == .increment ? 1:-1,reduced)
                }
                .overlay(alignment:.topLeading) {
                    if pinned==nil {
                        Button { clock.next(reduced) } label: { Color.clear }
                            .accessibilityLabel("Next").accessibilityHint("Shows the next onboarding page")
                            .frame(width:l.buttonW,height:l.buttonH)
                            .position(x:l.cx,y:l.H-sc[181]-l.buttonH/2)
                            .allowsHitTesting(false)
                    }
                }
                .frame(width:l.W,height:l.H,alignment:.topLeading).clipped()
            }
            .frame(width:l.W,height:l.H,alignment:.topLeading).offset(y:-top)
        }
        .background(AuroraTeammatesOnboardingTheme.color(0x0B0D45).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { clock.onComplete=onComplete;clock.configure(reduced,phase == .active,pinned != nil) }
        .onDisappear { clock.configure(reduced,false,pinned != nil) }
        .onChange(of:phase) { _,value in clock.configure(reduced,value == .active,pinned != nil) }
        .onChange(of:reduced) { _,value in clock.configure(value,phase == .active,pinned != nil) }
    }
}
