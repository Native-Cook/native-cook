import SwiftUI

struct FilamentBulbDimmer:View {
    static let loop=FilamentBulbDimmerMotion.LOOP
    var autoPlay=false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @Environment(\.displayScale) private var scale
    @StateObject private var clock=FilamentBulbDimmerClock()
    private var pinned:Double? {
        #if DEBUG
        if let v=UserDefaults.standard.object(forKey:"filamentBulbDimmerTime") { return (v as? NSNumber)?.doubleValue ?? Double("\(v)") }
        #endif
        return nil
    }
    var body:some View {
        GeometryReader { proxy in
            let top=proxy.safeAreaInsets.top,bottom=proxy.safeAreaInsets.bottom
            let l=FilamentBulbDimmerTheme.Layout(w:proxy.size.width,h:proxy.size.height+top+bottom,top:top,bottom:bottom)
            TimelineView(.animation(paused:pinned != nil || phase != .active || (reduced && !clock.running))) { timeline in
                let f=clock.sample(timeline.date,auto:autoPlay,reduced:reduced,pinned:pinned,layout:l)
                ZStack(alignment:.topLeading) {
                    FilamentBulbDimmerMetal(layout:l,frame:f,scale:scale,reduced:reduced,pinned:pinned).accessibilityHidden(true)
                    Canvas { c,_ in FilamentBulbDimmerArt(layout:l,frame:f,drawBack:pinned != nil).draw(c) }.allowsHitTesting(false).accessibilityHidden(true)
                    light(l,f).allowsHitTesting(false).accessibilityHidden(true)
                    Canvas { c,_ in
                        let p=l.finger(f),r=22*(1-0.12*f.fingerPress),shape=Path(ellipseIn:CGRect(x:p.x-r,y:p.y-r,width:2*r,height:2*r))
                        c.fill(shape,with:.color(.white.opacity(0.12*f.finger)));c.stroke(shape,with:.color(.white.opacity(0.45*f.finger)),lineWidth:1.5)
                    }.allowsHitTesting(false).accessibilityHidden(true)
                    if pinned==nil {
                        FilamentBulbDimmerTouch { state,p,translation in clock.touch(state,p,translation,l,reduced) }.accessibilityHidden(true)
                        accessibility(l,f)
                    }
                }.frame(width:l.w,height:l.h,alignment:.topLeading).clipped()
            }.frame(width:l.w,height:l.h,alignment:.topLeading).offset(y:-top)
        }
        .background(FilamentBulbDimmerTheme.color(0x262630).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear { _=FilamentBulbDimmerResources.shared;clock.reset(autoPlay,reduced) }
        .onChange(of:phase) { _,_ in clock.active() }
        .onChange(of:reduced) { _,_ in clock.configure(autoPlay,reduced) }
        .onChange(of:autoPlay) { _,_ in clock.configure(autoPlay,reduced) }
    }
    private func light(_ l:FilamentBulbDimmerTheme.Layout,_ f:FilamentBulbDimmerMotion.Frame)->some View {
        let p=l.pose(f),lc=FilamentBulbDimmerTheme.lightColor(f.heat,f.colour),g=FilamentBulbDimmerMotion.lum(f.heat)*2.2
        func rect(_ r:CGRect)->Shader.Argument { .float4(Float(r.minX),Float(r.minY),Float(r.width),Float(r.height)) }
        return Rectangle().fill(.white).frame(width:l.w,height:l.h)
            .colorEffect(ShaderLibrary.filamentBulbDimmerLight(.float4(Float(p.fil.x),Float(p.fil.y),Float(2.4*l.R),0),.float4(Float(pow(lc.x,1/2.2)*g),Float(pow(lc.y,1/2.2)*g),Float(pow(lc.z,1/2.2)*g),0),rect(l.card),rect(l.chipL),rect(l.chipR),rect(l.tab)))
    }
    private func hit(_ rect:CGRect)->some View { Color.clear.frame(width:rect.width,height:rect.height).contentShape(Rectangle()).offset(x:rect.minX,y:rect.minY) }
    @ViewBuilder private func accessibility(_ l:FilamentBulbDimmerTheme.Layout,_ f:FilamentBulbDimmerMotion.Frame)->some View {
        hit(CGRect(x:l.anchorX-130,y:l.restY-12,width:260,height:3.74*l.R+8)).allowsHitTesting(false)
            .accessibilityRepresentation {
                Toggle("Living Room Lamp",isOn:Binding(get:{ f.on==1 },set:{ _ in clock.takeOver();clock.toggle(false) }))
                    .accessibilityHint("Pull down to switch, or swing it")
            }
        hit(l.toggle).allowsHitTesting(false)
            .accessibilityRepresentation {
                Toggle("Lamp power",isOn:Binding(get:{ f.on==1 },set:{ _ in clock.powerSwitch(reduced) }))
            }
        hit(CGRect(x:l.cx-l.rOut-10,y:l.cy-l.rOut-10,width:l.rOut*2+20,height:l.rOut+l.leg+44)).allowsHitTesting(false)
            .accessibilityElement().accessibilityLabel("Intensity").accessibilityValue("\(Int((f.level*100).rounded())) percent")
            .accessibilityAdjustableAction { clock.adjust($0 == .increment ? 0.1 : -0.1) }
        ForEach(0..<5,id:\.self) { i in
            hit(CGRect(x:FilamentBulbDimmerTheme.xs[i]*l.sx-22,y:l.dotsY-22,width:44,height:44)).allowsHitTesting(false)
                .accessibilityElement().accessibilityLabel("\(FilamentBulbDimmerTheme.names[i]) light")
                .accessibilityAddTraits(Int(f.trail.rounded())==i ? [.isButton,.isSelected]:.isButton)
                .accessibilityAction { clock.choose(Double(i),reduced) }
        }
        hit(CGRect(x:0,y:l.y(65)-11,width:l.w,height:22)).allowsHitTesting(false)
            .accessibilityElement().accessibilityLabel("Lamp Details").accessibilityAddTraits(.isHeader)
        Button {} label: {
            Canvas { c,_ in
                var d=c;d.translateBy(x:16,y:12)
                d.stroke(FilamentBulbDimmerResources.back,with:.color(FilamentBulbDimmerTheme.color(0xE9E5EB)),style:StrokeStyle(lineWidth:2,lineCap:.round,lineJoin:.round))
            }.frame(width:44,height:44).contentShape(Rectangle())
        }
            .buttonStyle(FilamentBulbDimmerBackStyle(reduced:reduced)).accessibilityLabel("Back").offset(x:43*l.sx-22,y:l.y(65)-22)
    }
}
