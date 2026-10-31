import SwiftUI

struct PixelTrophyUnlock: View {
    static let loop = PixelTrophyUnlockMotion.LOOP
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @StateObject private var clock = PixelTrophyUnlockClock()
    private var pinned: Double? {
        #if DEBUG
        if let value = UserDefaults.standard.object(forKey:"pixelTrophyUnlockTime") {
            return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { full in
                let art = PixelTrophyUnlockArt(size:full.size,top:safe.safeAreaInsets.top,bottom:safe.safeAreaInsets.bottom)
                // Warm the immutable artwork before the animation clock starts.
                let _ = art.resources.background(full.size,safe.safeAreaInsets.top)
                TimelineView(.animation(paused:pinned != nil || phase != .active || clock.idle || (!autoPlay || reduced || clock.a0 > -50) && !clock.running)) { timeline in
                    let k = clock.sample(timeline.date,autoPlay && !reduced,reduced,pinned)
                    Canvas(opaque:true,colorMode:.nonLinear,rendersAsynchronously:false) { c,_ in
                        art.draw(c,k,clock.shake,clock.now-clock.shakeAt)
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { p in
                        guard pinned == nil else { return }
                        let index = (0..<8).first { i in let q = art.position(i);return p.x>q.x && p.x<q.x+80 && p.y>q.y && p.y<q.y+98 }
                        clock.tap(index,reduced)
                    }
                    .overlay {
                        PixelTrophyUnlockCopy(size:full.size,top:safe.safeAreaInsets.top,bottom:safe.safeAreaInsets.bottom,earned:k.earned)
                    }
                }
                .frame(width:full.size.width,height:full.size.height,alignment:.topLeading)
            }.ignoresSafeArea()
        }
        .background(Color(red:14/255,green:14/255,blue:16/255).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .persistentSystemOverlays(.hidden)
        .onAppear { _ = PixelTrophyUnlockResources.shared;clock.active(phase == .active,reduced,pinned != nil) }
        .onDisappear { clock.active(false,reduced,pinned != nil) }
        .onChange(of:phase) { _,v in clock.active(v == .active,reduced,pinned != nil) }
        .onChange(of:reduced) { _,v in clock.active(phase == .active,v,pinned != nil) }
        .onChange(of:autoPlay) { _,_ in clock.active(phase == .active,reduced,pinned != nil) }
    }
}
