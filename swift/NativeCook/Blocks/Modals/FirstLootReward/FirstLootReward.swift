import SwiftUI

struct FirstLootReward:View {
    static let loop=FirstLootRewardMotion.loop
    var autoPlay=false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var clock=FirstLootRewardClock()
    private var pinned:Double? {
        #if DEBUG
        if let v=UserDefaults.standard.object(forKey:"firstLootRewardTime") {
            return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0
        }
        #endif
        return nil
    }
    var body:some View {
        GeometryReader { safe in
            let top=safe.safeAreaInsets.top, bottom=safe.safeAreaInsets.bottom
            GeometryReader { full in
                TimelineView(.animation(paused:pinned != nil || reduced || clock.sleeping || phase != .active || (!autoPlay && !clock.running))) { timeline in
                    let f=clock.sample(timeline.date,autoPlay && !reduced,pinned)
                    let art=FirstLootRewardArt(w:full.size.width,h:full.size.height,top:top,bottom:bottom)
                    ZStack(alignment:.topLeading) {
                        FirstLootRewardResources.color(0x090909)
                        if f.sceneA > 0.01 {
                            Rectangle().fill(.white).colorEffect(ShaderLibrary.firstLootRewardRays(.float2(art.bx,art.by),.float(f.sceneA),.float2(f.tiltX,f.tiltY)))
                        }
                        Canvas(opaque:false,colorMode:.nonLinear) { c,_ in art.draw(c,f) }
                        FirstLootRewardControls(w:full.size.width,h:full.size.height,top:top,bottom:bottom,frame:f)
                            .allowsHitTesting(false).accessibilityHidden(true)
                        if pinned == nil {
                            FirstLootRewardTouch(top:top,bottom:bottom,clock:clock).accessibilityHidden(true)
                            accessibilityTargets(full.size,top,bottom)
                        }
                        FirstLootRewardResources.color(0x090909).opacity(f.fade).allowsHitTesting(false)
                    }
                    .frame(width:full.size.width,height:full.size.height,alignment:.topLeading)
                }
            }.ignoresSafeArea()
        }
        .background(FirstLootRewardResources.color(0x090909)).preferredColorScheme(.dark)
        .onAppear { clock.active(true,reduced,pinned != nil) }
        .onDisappear { clock.active(false,reduced,pinned != nil) }
        .onChange(of:phase) { _,v in clock.active(v == .active,reduced,pinned != nil) }
        .onChange(of:reduced) { _,v in clock.reset(); clock.active(phase == .active,v,pinned != nil) }
    }
    private func accessibilityTargets(_ size:CGSize,_ top:Double,_ bottom:Double) -> some View {
        ZStack(alignment:.topLeading) {
            Color.clear.frame(width:220,height:220).contentShape(Rectangle())
                .accessibilityElement().accessibilityLabel("Loot chest badge").accessibilityHint("Opens the chest.")
                .accessibilityAddTraits([.isImage,.isButton]).accessibilityAction { clock.activate(1) }
                .offset(x:size.width/2-110,y:top+72)
            Color.clear.frame(width:size.width-40,height:68).accessibilityElement()
                .accessibilityLabel("You just earned your first Loot.").accessibilityAddTraits(.isHeader)
                .offset(x:20,y:top+351)
            Color.clear.frame(width:size.width-72,height:40).accessibilityElement()
                .accessibilityLabel("Nice start! Keep playing, keep winning, and watch your rewards stack up.")
                .offset(x:36,y:top+434)
            Color.clear.frame(width:132,height:23).accessibilityElement()
                .accessibilityLabel("Announcement")
                .offset(x:36,y:size.height-bottom-211)
            Color.clear.frame(width:size.width-72,height:22).accessibilityElement()
                .accessibilityLabel("Something big is coming 🎉")
                .offset(x:36,y:size.height-bottom-171)
            Color.clear.frame(width:size.width-72,height:36).accessibilityElement()
                .accessibilityLabel("Ember Rift drops October 24. Get ready to play, compete, and earn exclusive Loot.")
                .offset(x:36,y:size.height-bottom-144)
            ForEach(0..<2) { i in
                Color.clear.frame(width:(size.width-48)/2,height:52).accessibilityElement()
                    .accessibilityLabel(i == 0 ? "Keep playing" : "View loot").accessibilityAddTraits(.isButton)
                    .accessibilityAction { clock.activate(i+2) }
                    .offset(x:20+Double(i)*((size.width-48)/2+8),y:size.height-bottom-68)
            }
        }.allowsHitTesting(false)
    }
}
