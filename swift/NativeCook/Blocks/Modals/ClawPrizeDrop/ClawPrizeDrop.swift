import SwiftUI

struct ClawPrizeDrop: View {
    static let loop = ClawPrizeDropMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var scale
    @StateObject private var clock = ClawPrizeDropClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey:"clawPrizeDropTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { outer in
            let top = outer.safeAreaInsets.top
            GeometryReader { proxy in
                let g = ClawPrizeDropMotion.Geo(proxy.size,top)
                let layers = ClawPrizeDropResources.shared.scene(proxy.size,top,scale)
                TimelineView(.animation(paused:pinned != nil || scenePhase != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
                    let o = clock.sample(timeline.date,g,autoPlay,reduced,pinned)
                    Canvas(opaque:true,colorMode:.nonLinear) { c, size in
                        c.draw(layers.0,in:CGRect(origin:.zero,size:size))
                        c.draw(layers.3,in:CGRect(origin:.zero,size:size))
                        ClawPrizeDropArt.draw(c,g,o,reduced)
                        c.draw(layers.4,in:CGRect(origin:.zero,size:size))
                        c.draw(layers.1,in:CGRect(origin:.zero,size:size))
                        if o.coupon > 0 {
                            let cw = g.w-32, cy = g.y+g.h-6+14+42
                            var coupon = c; coupon.opacity = o.coupon
                            coupon.translateBy(x:32+12+cw/2,y:cy+(1-o.coupon)*20)
                            coupon.scaleBy(x:0.9+0.1*o.coupon,y:0.9+0.1*o.coupon)
                            let prize = pinned != nil ? 2 : clock.won
                            coupon.draw(ClawPrizeDropResources.shared.coupon(prize,cw,scale),in:CGRect(x:-cw/2,y:-29,width:cw,height:58))
                        }
                        let s = clock.pressed ? 0.96 : 1.0
                        let bx = g.x-10+(g.w+20)-14-32, by = g.y+g.h-6+142
                        c.draw(layers.2,in:CGRect(x:bx-32*s,y:by-32*s,width:64*s,height:64*s))
                    }
                    .overlay(alignment:.topLeading) {
                        if pinned == nil { touch(g) }
                    }
                }
                .frame(width:proxy.size.width,height:proxy.size.height,alignment:.topLeading)
            }.ignoresSafeArea()
        }
        .background(ClawPrizeDropResources.color(0x8fd3ff).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onChange(of:scenePhase) { _, _ in clock.resetDate() }
    }
    private func touch(_ g: ClawPrizeDropMotion.Geo) -> some View {
        ZStack(alignment:.topLeading) {
            Color.clear.frame(width:g.w,height:g.h).contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance:10).onChanged { clock.pan($0.translation.width,g) }.onEnded { _ in clock.endPan() })
                .accessibilityElement().accessibilityLabel("Claw. Drag sideways to move it over a prize")
                .accessibilityAdjustableAction { _ in } // RN supplies the trait, without increment/decrement handlers.
                .accessibilitySortPriority(70)
                .offset(x:g.x,y:g.y)
            Button {} label: { Color.clear.frame(width:44,height:44).contentShape(Circle()) }
                .accessibilityLabel("Close").accessibilitySortPriority(100).offset(x:16,y:g.y-150+8)
            Color.clear.frame(width:100,height:36).accessibilityElement().accessibilityLabel("1,200 coins")
                .accessibilitySortPriority(90).offset(x:g.w+56-116,y:g.y-150+12)
            Color.clear.frame(width:g.w,height:52).accessibilityElement()
                .accessibilityLabel("Grabbit").accessibilityAddTraits(.isStaticText).accessibilitySortPriority(80)
                .offset(x:g.x,y:g.y-86)
            Color.clear.frame(width:g.w-64,height:22).accessibilityElement()
                .accessibilityLabel("Slide the claw, then grab · 3 tries left")
                .accessibilityAddTraits(.isStaticText).accessibilitySortPriority(50)
                .offset(x:32,y:g.y+g.h-6+131)
            if clock.rewardVisibleForAccessibility {
                Color.clear.frame(width:g.w-32,height:58)
                    .accessibilityElement().accessibilityLabel("\(ClawPrizeDropMotion.names[clock.won]). \(ClawPrizeDropMotion.rewards[clock.won])")
                    .accessibilityAddTraits(.isStaticText).accessibilitySortPriority(60)
                    .offset(x:44,y:g.y+g.h+21)
            }
            Button { clock.grab(g) } label: { Color.clear.frame(width:64,height:64).contentShape(Circle()) }
                .buttonStyle(ClawPrizeDropPressStyle(clock:clock))
                .accessibilityLabel("Grab").accessibilitySortPriority(40).offset(x:g.x-10+g.w+20-14-64,y:g.y+g.h-6+110)
        }
        .frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.topLeading)
    }
}
private struct ClawPrizeDropPressStyle: ButtonStyle {
    let clock: ClawPrizeDropClock
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of:configuration.isPressed) { _, pressed in clock.pressed = pressed }
    }
}
