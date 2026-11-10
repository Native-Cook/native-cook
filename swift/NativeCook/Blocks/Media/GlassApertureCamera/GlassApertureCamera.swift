import SwiftUI

struct GlassApertureCamera: View {
    static let loop = GlassApertureCameraMotion.loop
    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @Environment(\.displayScale) private var scale
    @StateObject private var clock = GlassApertureCameraClock()
    private var pinned: Double? {
        #if DEBUG
        if let v = UserDefaults.standard.object(forKey: "glassApertureCameraTime") {
            return (v as? NSNumber)?.doubleValue ?? Double("\(v)")
        }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { safe in
            GeometryReader { proxy in
                let l = GlassApertureCameraTheme.Layout(proxy.size,safe.safeAreaInsets.top,safe.safeAreaInsets.bottom)
                TimelineView(.animation(paused:pinned != nil || phase != .active || (!(autoPlay && !reduced) && !clock.running))) { timeline in
                    let f = clock.sample(timeline.date,autoPlay,reduced,pinned)
                    ZStack(alignment:.topLeading) {
                        // The photo and GPU viewfinder share top-aligned point coordinates.
                        GlassApertureCameraResources.shared.sceneImage.resizable()
                            .frame(width:l.w,height:l.photoH)
                            .frame(width:l.w,height:l.viewH,alignment:.topLeading).clipped()
                            .allowsHitTesting(false).accessibilityHidden(true)
                        GlassApertureCameraShaderView(layout:l,frame:f,scale:scale)
                            .frame(width:l.w,height:l.viewH,alignment:.topLeading)
                        Canvas(opaque:false,colorMode:.nonLinear) { context, _ in
                            GlassApertureCameraArt.draw(context,l,f,reduced,scale)
                        }.allowsHitTesting(false).accessibilityHidden(true)
                        GlassApertureCameraPhotoView(layout:l,frame:f,reduced:reduced)
                            .allowsHitTesting(false).accessibilityHidden(true)
                        Canvas(opaque:false,colorMode:.nonLinear) { context, _ in
                            GlassApertureCameraArt.drawFooter(context,l,f,reduced,scale)
                        }.allowsHitTesting(false).accessibilityHidden(true)
                        Canvas(opaque:false,colorMode:.nonLinear) { context, _ in
                            GlassApertureCameraArt.drawOverlay(context,l,f)
                        }.allowsHitTesting(false).accessibilityHidden(true)
                        if pinned == nil { GlassApertureCameraTouchSurface(layout:l,clock:clock,reduced:reduced) }
                        accessibility(l,f)
                    }
                    .frame(width:l.w,height:l.h,alignment:.topLeading)
                    .clipped()
                }
            }.ignoresSafeArea()
        }
        .background(.black)
        .preferredColorScheme(.dark)
        .onAppear { clock.reset(autoPlay,reduced) }
        .onDisappear { clock.active(false) }
        .onChange(of:phase) { _, p in clock.active(p == .active) }
        .onChange(of:reduced) { _, _ in clock.reset(autoPlay,reduced) }
        .onChange(of:autoPlay) { _, _ in clock.reset(autoPlay,reduced) }
    }
    private func accessibility(_ l:GlassApertureCameraTheme.Layout,_ f:GlassApertureCameraMotion.Frame) -> some View {
        ZStack(alignment:.topLeading) {
            Color.clear.frame(width:l.w,height:l.viewH).position(x:l.w/2,y:l.viewH/2)
                .accessibilityElement().accessibilityAddTraits(.isImage)
                .accessibilityLabel("Viewfinder, a street at dusk, aperture f \(GlassApertureCameraMotion.label(f.fnum))")
                .accessibilityHint("Pinch to set the aperture, tap to focus")
            ForEach(Array(zip([48.0,148.0,250.0,349.0],["Favourites","One shot","Burst of 5","Edit"])),id:\.0) { x, title in
                Color.clear.frame(width:x == 250 ? 56 : 44,height:44).position(x:x*l.sx+(x == 250 ? 6 : 0),y:l.Y(607))
                    .accessibilityElement().accessibilityLabel(title)
            }
            Color.clear.frame(width:40,height:40).position(x:82*l.sx,y:l.shutterY)
                .accessibilityElement().accessibilityAddTraits(.isButton).accessibilityLabel("Flash")
            Button { if pinned == nil { clock.shoot(reduced) } } label: { Color.clear }
                .frame(width:52,height:52).position(x:l.shutterX,y:l.shutterY).accessibilityLabel("Take photo")
            Color.clear.frame(width:40,height:40).position(x:316*l.sx,y:l.shutterY)
                .accessibilityElement().accessibilityAddTraits(.isButton).accessibilityLabel("Flip camera")
            Color.clear.frame(width:68,height:30).position(x:l.irisX,y:l.irisY-l.irisR-29+(1-f.iris)*6)
                .accessibilityElement().accessibilityLabel("ƒ\(GlassApertureCameraMotion.label(f.fnum))")
                .accessibilityHidden(f.iris < 0.001)
            if f.sent < 0.999 {
                Color.clear.frame(width:l.w,height:16).position(x:l.w/2,y:l.viewH+25)
                    .accessibilityElement().accessibilityLabel("Friends @mira")
                Color.clear.frame(width:l.w,height:15).position(x:l.w/2,y:l.viewH+45.5)
                    .accessibilityElement().accessibilityLabel("Sharable with other apps")
            }
            if f.sent > 0.5 {
                Color.clear.frame(width:l.w,height:16).position(x:l.w/2,y:l.viewH+35+(1-f.sent)*6)
                    .accessibilityElement().accessibilityLabel("Sent to 4 friends")
            }
        }.allowsHitTesting(false)
    }
}
