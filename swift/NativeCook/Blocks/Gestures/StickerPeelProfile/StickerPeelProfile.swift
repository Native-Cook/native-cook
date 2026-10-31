import SwiftUI

struct StickerPeelProfile: View {
    static let loop = StickerPeelProfileMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var clock = StickerPeelProfileClock()
    private var pinned: Double? {
        #if DEBUG
        if let v = UserDefaults.standard.object(forKey:"stickerPeelProfileTime") {
            return (v as? NSNumber)?.doubleValue ?? Double("\(v)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
                let size = full.size
                let res = StickerPeelProfileResources.shared
                ZStack(alignment:.topLeading) {
                    Image(uiImage:res.page(size,top,bottom)).resizable().frame(width:size.width,height:size.height).accessibilityHidden(true)
                    TimelineView(.animation(paused:pinned != nil || clock.holding || phase != .active || (!(autoPlay && !reduced) && !clock.awake))) { timeline in
                        let _ = clock.pulse
                        let ss = pinned != nil || phase == .active
                            ? clock.sample(Date(),size.width,top,autoPlay,reduced,pinned)
                            : (clock.stickers.isEmpty ? StickerPeelProfileMotion.home(size.width,top) : clock.stickers)
                        Canvas(opaque:false,colorMode:.nonLinear,rendersAsynchronously:false) { c,_ in
                            StickerPeelProfileArt.draw(c,ss,clock.order)
                        }
                    }
                    if pinned == nil { StickerPeelProfileTouchView(clock:clock,reduced:reduced) }
                    Button(action:clock.close) {
                        Image(uiImage:res.closeImage).resizable().frame(width:56,height:56)
                    }
                    .buttonStyle(StickerPeelProfileCloseStyle())
                    .accessibilityLabel("Close")
                    .position(x:size.width/2,y:size.height-bottom-14-28)
                    // Match the RN screen's read-only elements, behind the sticker surface.
                    Color.clear.frame(width:160,height:30).accessibilityElement().accessibilityLabel("Rio Vega")
                        .position(x:size.width/2,y:top+229).allowsHitTesting(false)
                    Color.clear.frame(width:120,height:20).accessibilityElement().accessibilityLabel("@riovega")
                        .position(x:size.width/2,y:top+214+StickerPeelProfilePage.height(24,"Bold")+12).allowsHitTesting(false)
                    Color.clear.frame(width:130,height:44).accessibilityElement().accessibilityLabel("1 friend")
                        .position(x:size.width/2,y:top+214+StickerPeelProfilePage.height(24,"Bold")+2+StickerPeelProfilePage.height(14)+18+22).allowsHitTesting(false)
                    Color.clear.frame(width:230,height:26).accessibilityElement().accessibilityLabel("Music Compatibility")
                        .position(x:149,y:top+343).allowsHitTesting(false)
                    Color.clear.frame(width:200,height:20).accessibilityElement().accessibilityLabel("Discover your score!")
                        .position(x:134,y:top+365).allowsHitTesting(false)
                    Color.clear.frame(width:52,height:60).accessibilityElement().accessibilityLabel("?")
                        .position(x:size.width-70,y:top+356).allowsHitTesting(false)
                    Color.clear.frame(width:160,height:22).accessibilityElement().accessibilityLabel("Recently played")
                        .position(x:98,y:top+421).allowsHitTesting(false)
                    Color.clear.frame(width:80,height:22).accessibilityElement().accessibilityLabel("See more")
                        .position(x:size.width-58,y:top+421).allowsHitTesting(false)
                    Color.clear.frame(width:42,height:42).accessibilityElement().accessibilityLabel("Message").accessibilityAddTraits(.isButton).position(x:size.width-91,y:top+31).allowsHitTesting(false)
                    Color.clear.frame(width:42,height:42).accessibilityElement().accessibilityLabel("More").accessibilityAddTraits(.isButton).position(x:size.width-39,y:top+31).allowsHitTesting(false)
                    ForEach(0..<3) { i in
                        Color.clear.frame(width:size.width-36,height:52).accessibilityElement()
                            .accessibilityLabel(["Glass Garden by Mira Sol, 4 hours ago","Night Bus Home by Ode Kim, 4 hours ago","Fizz - Remix by Tavi, Rook, 4 hours ago"][i])
                            .position(x:size.width/2,y:top+410+StickerPeelProfilePage.height(15,"SemiBold")+14+Double(i)*66+26).allowsHitTesting(false)
                    }
                }
                .frame(width:size.width,height:size.height,alignment:.topLeading)
            }.ignoresSafeArea()
        }
        .background(StickerPeelProfileResources.color(0x151517).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { clock.prepare(reduced) }
        .onDisappear { clock.suspend() }
        .onChange(of:phase) { _,p in if p == .active { clock.prepare(reduced) } else { clock.suspend() } }
        .onChange(of:reduced) { _,r in clock.suspend(); clock.prepare(r) }
    }
}
