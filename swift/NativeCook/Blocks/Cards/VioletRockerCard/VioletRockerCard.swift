import SwiftUI
import UIKit

private struct VioletRockerCardFrame {
    var n: Double = 0
    var c: Double = 0
    var scale: Double = 1.223
    var originX: Double = 187
    var originY: Double = 431
    var snap: Double = 0
    var time: Double? = nil
    var flat = false
    var cameraZ: Double? = nil
    var cameraX: Double? = nil
    var rock: Double { flat ? 0 : -9 + 18*n + snap }
}

private enum VioletRockerCardMath {
    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi,max(lo,x)) }
    static func smooth(_ x: Double) -> Double { let u=clamp(x); return u*u*(3-2*u) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { smooth((t-a)/(b-a)) }
    static func automatic(_ time: Double) -> VioletRockerCardFrame {
        let t=max(0,time).truncatingRemainder(dividingBy:VioletRockerCard.loop)
        let events=[1.0,2.2,3.25,4.45,5.45,6.65,7.65,8.85]
        var n=0.0, extra=0.0
        func snap(_ a: Double, _ b: Double) -> Double {
            let u=clamp((t-a)/(b-a))
            return 1.2*16*u*u*(1-u)*(1-u)*sin(2*Double.pi*u)
        }
        for (index,start) in events.enumerated() {
            let direction=index.isMultiple(of:2) ? 1.0 : -1.0
            n+=direction*seg(t,start-0.09,start+0.09)
            extra+=direction*snap(start-0.09,start+0.09)
        }
        if t<4.45 { return .init(n:n,snap:extra,time:t) }
        if t<6.65 { return .init(n:n,scale:2.7,originX:-40,originY:350,snap:extra,time:t,cameraZ:90,cameraX:20) }
        let c=1-seg(t,9.1,9.6)
        return .init(n:n,c:c,scale:1.223+0.577*c,originX:187-13*c,originY:431-5*c,snap:extra,time:t)
    }
    static func color(_ day: UInt32, _ night: UInt32, _ n: Double) -> Color {
        let q=clamp(n)
        func channel(_ shift: UInt32) -> Double { (Double((day>>shift)&255)*(1-q)+Double((night>>shift)&255)*q)/255 }
        return Color(.sRGB,red:channel(16),green:channel(8),blue:channel(0),opacity:1)
    }
    static func spring(_ age: Double, from: Double, to: Double, velocity: Double) -> Double {
        if age>=0.64 { return to }
        let t=max(0,age), omega=2*Double.pi/0.32, zeta=0.78
        let damp=omega*zeta, wd=omega*sqrt(1-zeta*zeta), delta=from-to
        let x=exp(-damp*t)*(delta*cos(wd*t)+(velocity+damp*delta)/wd*sin(wd*t))
        return clamp(to+x*(1-seg(t,0.48,0.64)),-1.0/15,16.0/15)
    }
    static func contains(_ point: CGPoint) -> Bool {
        let x=abs(point.x), y=abs(point.y)
        return x<=50 && y<=130 && (y<=80 || x*x+(y-80)*(y-80)<=2500)
    }
}

/// Exact planar projection of a rocker slice; u/v are the source view's 0...100/0...260 coordinates.
private struct VioletRockerCardProjection {
    let a: Double, b: Double, d: Double, e: Double, g: Double, h: Double
    let tx: Double, ty: Double, w: Double
    let originX: Double, originY: Double
    init(frame: VioletRockerCardFrame, rock: Double, depth: Double) {
        originX=frame.originX;originY=frame.originY
        let az=(frame.cameraZ ?? (55*(1-frame.c)))*Double.pi/180, ac=(frame.cameraX ?? (50.2*(1-frame.c)))*Double.pi/180
        let ar=rock*Double.pi/180, cz=cos(az), sz=sin(az), cc=cos(ac), sc=sin(ac), cr=cos(ar), sr=sin(ar)
        let scale=frame.scale
        a=scale*cz; b = -scale*sz*cr
        d=scale*cc*sz; e=scale*(cc*cz*cr+sc*sr)
        g = -sc*sz/900; h = -(sc*cz*cr-cc*sr)/900
        tx = -50*a-130*b
        ty = -50*d-130*e-scale*sc*depth
        w=1-cc*depth/900-50*g-130*h
    }
    var transform: ProjectionTransform {
        var p=ProjectionTransform()
        p.m11=a; p.m12=d; p.m13=g
        p.m21=b; p.m22=e; p.m23=h
        p.m31=tx; p.m32=ty; p.m33=w
        return p
    }
    func point(_ local: CGPoint) -> CGPoint {
        let u=local.x+50, v=local.y+130, divisor=g*u+h*v+w
        return CGPoint(x:originX+(a*u+b*v+tx)/divisor,y:originY+(d*u+e*v+ty)/divisor)
    }
    func local(_ screen: CGPoint) -> CGPoint? {
        let x=screen.x-originX, y=screen.y-originY
        let aa=a-x*g, bb=b-x*h, dd=d-y*g, ee=e-y*h
        let xx=x*w-tx, yy=y*w-ty, determinant=aa*ee-bb*dd
        guard abs(determinant)>0.000001 else { return nil }
        return CGPoint(x:(xx*ee-bb*yy)/determinant-50,y:(aa*yy-xx*dd)/determinant-130)
    }
}

/// A physical emitting rocker. Face and extrusion use one camera; markers stay on the ground plane.
struct VioletRockerCard: View {
    static let loop = 10.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var epoch=Date()
    @State private var visible=false
    @State private var manual=false
    @State private var held=false
    @State private var dragged=false
    @State private var manualCamera=0.0
    @State private var manualScale=1.223
    @State private var manualOriginX=187.0
    @State private var manualOriginY=431.0
    @State private var manualSnap=0.0
    @State private var manualCameraZ=55.0
    @State private var manualCameraX=50.2
    @State private var originalOn=false
    @State private var heldNight=0.0
    @State private var releaseFrom=0.0
    @State private var releaseTarget=0.0
    @State private var releaseVelocity=0.0
    @State private var releaseDate=Date()
    @State private var lastTouch=Date()
    @State private var moving=false
    @State private var serial=0
    @State private var reducedNight=1.0
    @GestureState private var gestureActive=false

    private func frame(_ now: Date) -> VioletRockerCardFrame {
        if reduced { return .init(n:reducedNight,c:1,scale:1.8,originX:174,originY:426,flat:true) }
        if manual {
            let n=held ? heldNight : VioletRockerCardMath.spring(now.timeIntervalSince(releaseDate),from:releaseFrom,to:releaseTarget,velocity:releaseVelocity)
            let recovery=held || !autoPlay ? 0 : VioletRockerCardMath.smooth((now.timeIntervalSince(lastTouch)-2)/0.6)
            return .init(n:n*(1-recovery),c:manualCamera*(1-recovery),scale:manualScale+(1.223-manualScale)*recovery,
                         originX:manualOriginX+(187-manualOriginX)*recovery,originY:manualOriginY+(431-manualOriginY)*recovery,
                         snap:manualSnap*(held ? 1 : 1-VioletRockerCardMath.seg(now.timeIntervalSince(releaseDate),0,0.32))*(1-recovery),
                         cameraZ:manualCameraZ+(55-manualCameraZ)*recovery,
                         cameraX:manualCameraX+(50.2-manualCameraX)*recovery)
        }
        return VioletRockerCardMath.automatic(previewTime ?? (autoPlay ? now.timeIntervalSince(epoch) : 0))
    }
    var body: some View {
        GeometryReader { geo in
            let scale=geo.size.width/393
            let top=max(0,(geo.size.height-852*scale)/2)
            TimelineView(.animation(paused: !visible || scene != .active || reduced || held || previewTime != nil || (!autoPlay && !held && !moving))) { clock in
                let f=frame(clock.date)
                VioletRockerCardArt(frame:f)
                    .frame(width:393,height:852,alignment:.topLeading)
                    .scaleEffect(scale,anchor:.topLeading)
                    .offset(y:top)
                    .frame(width:geo.size.width,height:geo.size.height,alignment:.topLeading)
                    .background(VioletRockerCardMath.color(0xC2C2C2,0x111113,f.n))
                    .contentShape(Rectangle())
                    .gesture(gesture(scale:scale,top:top),including:previewTime == nil ? .all : .none)
                    .accessibilityElement(children:.ignore)
                    .accessibilityLabel("Ambient light")
                    .accessibilityValue(f.n>=0.5 ? "On" : "Off")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { select(f.n>=0.5 ? 0 : 1) }
                    .accessibilityAdjustableAction { direction in
                        switch direction { case .increment: select(1); case .decrement: select(0); @unknown default: break }
                    }
                    .preferredColorScheme(f.n>0.5 ? .dark : .light)
            }
        }
        .ignoresSafeArea()
        .onAppear { visible=true;epoch=Date() }
        .onDisappear { visible=false;reset() }
        .onChange(of:scene) { _, value in if value != .active { reset() } else { epoch=Date() } }
        .onChange(of:reduced) { _, _ in reset() }
        .onChange(of:autoPlay) { _, _ in reset() }
        .onChange(of:previewTime) { _, _ in reset() }
        .onChange(of:gestureActive) { _, active in
            if !active && held {
                Task { @MainActor in
                    await Task.yield()
                    if !gestureActive && held { finish(target:heldNight>=0.5 ? 1 : 0,velocity:0,haptic:false) }
                }
            }
        }
        .task(id:serial) {
            guard manual && !held && !reduced else { return }
            do { try await Task.sleep(for:.seconds(autoPlay ? 2.6 : 0.66)) } catch { return }
            guard manual && !held else { return }
            if autoPlay { reset() } else { moving=false }
        }
    }
    private func reset() {
        manual=false; held=false; dragged=false; moving=false; epoch=Date(); reducedNight=1
    }
    private func select(_ target: Double) {
        guard previewTime == nil else { return }
        if reduced { reducedNight=target; return }
        let f=frame(Date())
        manual=true; held=false; manualCamera=f.c;manualScale=f.scale;manualOriginX=f.originX;manualOriginY=f.originY
        manualSnap=f.snap;manualCameraZ=f.cameraZ ?? 55*(1-f.c);manualCameraX=f.cameraX ?? 50.2*(1-f.c)
        releaseFrom=f.n; releaseTarget=target; releaseVelocity=0
        releaseDate=Date(); lastTouch=releaseDate; moving=true;serial+=1
        if (f.n>=0.5) != (target>=0.5) { UIImpactFeedbackGenerator(style:.light).impactOccurred() }
    }
    private func finish(target: Double, velocity: Double, haptic: Bool) {
        guard held else { return }
        let changed=originalOn != (target>=0.5)
        releaseFrom=heldNight;releaseTarget=target;releaseVelocity=VioletRockerCardMath.clamp(velocity,-1.8,1.8)
        held=false;releaseDate=Date();lastTouch=releaseDate;moving=true;serial+=1
        if haptic && changed { UIImpactFeedbackGenerator(style:.light).impactOccurred() }
    }
    private func gesture(scale: Double, top: Double) -> some Gesture {
        func canonical(_ point: CGPoint) -> CGPoint { CGPoint(x:point.x/scale,y:(point.y-top)/scale) }
        return DragGesture(minimumDistance:0)
            .updating($gestureActive) { _, active, _ in active=true }
            .onChanged { value in
                guard !reduced else { return }
                if !held {
                    let f=frame(Date()), projection=VioletRockerCardProjection(frame:f,rock:f.rock,depth:18)
                    guard let p=projection.local(canonical(value.startLocation)),VioletRockerCardMath.contains(p) else { return }
                    manual=true;held=true;dragged=false;manualCamera=f.c;manualScale=f.scale
                    manualOriginX=f.originX;manualOriginY=f.originY;manualSnap=f.snap;manualCameraZ=f.cameraZ ?? 55*(1-f.c);manualCameraX=f.cameraX ?? 50.2*(1-f.c);originalOn=f.n>=0.5;heldNight=f.n;moving=true;serial+=1
                }
                let travel=hypot(value.translation.width/scale,value.translation.height/scale)
                if travel>3 { dragged=true }
                if dragged {
                    let f=frame(Date()), projection=VioletRockerCardProjection(frame:f,rock:f.rock,depth:18)
                    if let p=projection.local(canonical(value.location)) { heldNight=VioletRockerCardMath.clamp((p.y+95)/190) }
                }
                lastTouch=Date()
            }
            .onEnded { value in
                let f=frame(Date()), projection=VioletRockerCardProjection(frame:f,rock:f.rock,depth:18)
                if reduced {
                    guard let p=projection.local(canonical(value.startLocation)),VioletRockerCardMath.contains(p) else { return }
                    reducedNight=p.y<0 ? 0 : 1
                    return
                }
                guard held else { return }
                let end=canonical(value.location)
                if !dragged {
                    let p=projection.local(end)
                    finish(target:(p?.y ?? 0)<0 ? 0 : 1,velocity:0,haptic:true)
                } else {
                    let velocity=value.velocity
                    let predicted=CGPoint(x:end.x+velocity.width/scale*0.10,y:end.y+velocity.height/scale*0.10)
                    let mapped=projection.local(end).map { VioletRockerCardMath.clamp(($0.y+95)/190) } ?? heldNight
                    let projected=projection.local(predicted).map { VioletRockerCardMath.clamp(($0.y+95)/190) } ?? heldNight
                    finish(target:projected>=0.5 ? 1 : 0,velocity:(projected-mapped)/0.10,haptic:true)
                }
            }
    }
}

private struct VioletRockerCardArt: View {
    let frame: VioletRockerCardFrame
    private static let day=UIImage(named:"VioletRockerCard-rocker-day.png").flatMap { $0.preparingForDisplay() ?? $0 }
    private static let night=UIImage(named:"VioletRockerCard-rocker-night.png").flatMap { $0.preparingForDisplay() ?? $0 }
    private var n: Double { VioletRockerCardMath.clamp(frame.n) }
    private var ground: ProjectionTransform { VioletRockerCardProjection(frame:frame,rock:0,depth:0).transform }
    private func projected<V: View>(_ view: V, depth: Double, rock: Double? = nil) -> some View {
        view.frame(width:100,height:260,alignment:.topLeading)
            .projectionEffect(VioletRockerCardProjection(frame:frame,rock:rock ?? frame.rock,depth:depth).transform)
            .offset(x:frame.originX,y:frame.originY)
    }
    var body: some View {
        ZStack(alignment:.topLeading) {
            VioletRockerCardMath.color(0xC2C2C2,0x111113,n)
            groundLight
            projected(Capsule().fill(.black.opacity(0.12-0.04*n)).blur(radius:22),depth:0,rock:0).offset(y:10)
            projected(Capsule().fill(.black.opacity(0.25)).blur(radius:3),depth:0,rock:0).offset(y:1.5)
            projected(Capsule().stroke(VioletRockerCardMath.color(0x8C8C8C,0x080809,n),lineWidth:0.5),depth:0,rock:0)
            ForEach(0..<20,id:\.self) { index in
                let fraction=Double(index)/19
                projected(Capsule().fill(sideGradient(fraction)),depth:fraction*18,rock:frame.rock*fraction)
            }
            projected(Capsule().stroke(Color(red:0.70,green:0.18,blue:1).opacity(0.75*n),lineWidth:2).blur(radius:4),depth:18)
            projected(face,depth:18)
            projected(Capsule().stroke(LinearGradient(colors:[VioletRockerCardMath.color(0xDDD9DD,0xF7CAFF,n).opacity(0.28+0.62*n),VioletRockerCardMath.color(0xBD69E6,0x8934D4,n).opacity(0.18)],startPoint:.top,endPoint:.bottom),lineWidth:0.6),depth:18)
            markers
        }
        .frame(width:393,height:852,alignment:.topLeading)
        .clipped()
        .accessibilityHidden(true)
    }
    private func sideGradient(_ fraction: Double) -> LinearGradient {
        let back=0.52+0.48*fraction
        return LinearGradient(colors:[
            VioletRockerCardMath.color(0x45138F,0xF7CAFF,n).opacity(back),
            VioletRockerCardMath.color(0x8934D4,0xDB8EFF,n).opacity(back),
            VioletRockerCardMath.color(0xC15CF1,0x45138F,n).opacity(back)
        ],startPoint:.top,endPoint:.bottom)
    }
    private var face: some View {
        ZStack(alignment:.topLeading) {
            if let image=Self.day {
                Image(uiImage:image).resizable().interpolation(.high)
                    .frame(width:231*100/187,height:600*260/526)
                    .offset(x:-22*100/187,y:-40*260/526).opacity(1-n)
            }
            if let image=Self.night {
                Image(uiImage:image).resizable().interpolation(.high)
                    .frame(width:231*100/189,height:600*260/525)
                    .offset(x:-21*100/189,y:-40*260/525).opacity(n).blendMode(.plusLighter)
            }
        }
        .frame(width:100,height:260,alignment:.topLeading)
        .clipShape(Capsule()).compositingGroup()
    }
    private var markers: some View {
        ZStack(alignment:.topLeading) {
            Text("ER").font(.system(size:18,weight:.regular)).foregroundStyle(VioletRockerCardMath.color(0x727272,0xF1F1F1,n))
                .frame(width:52,height:28).position(x:119,y:11)
            Text("SÜ").font(.system(size:18,weight:.regular)).foregroundStyle(VioletRockerCardMath.color(0x727272,0xF1F1F1,n))
                .frame(width:52,height:28).position(x:119,y:260)
            ForEach(0..<19,id:\.self) { index in
                let brightness=dot(index)
                Circle().fill(VioletRockerCardMath.color(0xA9ADB3,0xF1F1F1,brightness))
                    .overlay(Circle().stroke(.black.opacity(0.10*(1-n)),lineWidth:0.35))
                    .frame(width:2.4,height:2.4).position(x:119,y:33+Double(index)*197/18)
            }
        }
        .frame(width:180,height:300,alignment:.topLeading)
        .projectionEffect(ground)
        .offset(x:frame.originX,y:frame.originY)
    }
    private func dot(_ index: Int) -> Double {
        let base=index==18 ? 0.25 : 0.04
        guard let t=frame.time else { return base+(1-base)*n }
        var active=0.0
        for (event,start) in [1.0,2.2,3.25,4.45,5.45,6.65,7.65,8.85].enumerated() {
            let delay=0.008+Double(18-index)*0.004
            active+=(event.isMultiple(of:2) ? 1.0 : -1.0)*VioletRockerCardMath.seg(t,start-0.09+delay,start-0.09+delay+0.1)
        }
        return base+(1-base)*active
    }
    private var groundLight: some View {
        let lifted=130*(1-2*n)
        let p=VioletRockerCardProjection(frame:frame,rock:frame.rock,depth:8).point(CGPoint(x:0,y:lifted))
        let radius=(84+11*frame.c)*min(1.7,frame.scale/1.223)
        return ZStack {
            Ellipse().fill(RadialGradient(colors:[VioletRockerCardMath.color(0x8C2DE5,0x8C2DE5,n).opacity(0.12+0.83*n),.clear],center:.center,startRadius:0,endRadius:radius))
                .frame(width:radius*2.4,height:radius*1.8).blur(radius:12+8*n)
            Ellipse().fill(RadialGradient(colors:[VioletRockerCardMath.color(0xC15CF1,0xF7CAFF,n).opacity(0.08+0.65*n),.clear],center:.center,startRadius:0,endRadius:35))
                .frame(width:90,height:70).blur(radius:8)
        }
        .frame(width:radius*2.4,height:radius*1.8)
        .position(x:p.x,y:p.y)
    }
}
