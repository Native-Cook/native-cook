import SwiftUI
import UIKit

struct PixelWingFlight: View {
    static let loop: Double = 8
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var epoch = Date()
    @State private var touched: Date? = nil
    @State private var manual = 0.0
    @State private var dragStart = 0.0
    @State private var isTouching = false
    @State private var paused = false
    @State private var held = 0.0
    @State private var pauseBegan: Date? = nil
    @State private var coast = 0.0
    @State private var sampleAt = Date()
    @State private var sampleManual = 0.0
    @State private var velocity = 0.0
    @State private var heldReturn = 0.0
    @State private var manualWingOffset = 0.0
    @State private var heldWingOffset = 0.0
    private static let cloud = UIImage(named: "PixelWingFlight-cloud.png")
    private static let grain = UIImage(named: "PixelWingFlight-grain.png")
    private var safeInsets: UIEdgeInsets {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).first(where: \.isKeyWindow)?.safeAreaInsets ?? .zero
    }
    private static func smooth(_ v: Double) -> Double { let x = min(1,max(0,v)); return x*x*(3-2*x) }
    // Integrate a smooth speed ramp so the first and last wingbeats settle without a snap.
    private static func flap(_ t: Double) -> Double {
        if t < 0.8 || t >= 7.2 { return 0 }
        let u=t-0.8
        func integral(_ x:Double)->Double {x*x*x-0.5*x*x*x*x}
        let cycles:Double
        if u<0.4 {cycles=0.2*integral(u/0.4)}
        else if u>6 {cycles=3-0.2*integral((6.4-u)/0.4)}
        else {cycles=0.5*(u-0.2)}
        return cycles.truncatingRemainder(dividingBy:1)
    }
    private func recovery(_ now:Date)->Double {
        if previewTime != nil || reduced {return 0}
        if paused {return heldReturn}
        guard !isTouching,let touched else {return 0}
        let dt=now.timeIntervalSince(touched)
        return dt<2.6 ? Self.smooth((dt-2)/0.6) : 0
    }
    private func wingOffset(_ now:Date)->Double {
        if previewTime != nil || reduced {return 0}
        if paused {return heldWingOffset}
        if isTouching {return manualWingOffset}
        if let touched,now.timeIntervalSince(touched)<2.6 {return manualWingOffset}
        return 0
    }
    private func time(_ now: Date) -> Double {
        if let previewTime { return previewTime.truncatingRemainder(dividingBy: Self.loop) }
        if reduced { return 0 }
        if isTouching { return manual }
        if paused { return held }
        if let touched {
            let dt = now.timeIntervalSince(touched)
            if dt < 2.6 { return (manual+coast*(1-exp(-dt/0.1))+Self.loop).truncatingRemainder(dividingBy:Self.loop) }
            return autoPlay ? (dt-2.6).truncatingRemainder(dividingBy: Self.loop) : 0
        }
        return autoPlay ? now.timeIntervalSince(epoch).truncatingRemainder(dividingBy: Self.loop) : 0
    }
    private func text(_ value: String, size: Double, weight: Font.Weight = .regular, opacity: Double = 1) -> Text {
        Text(value).font(.system(size: size, weight: weight)).foregroundColor(.white.opacity(opacity))
    }
    var body: some View {
        GeometryReader { geo in
            let scale = geo.size.width/393
            let h = Double(geo.size.height/scale)
            let top = Double(max(geo.safeAreaInsets.top, safeInsets.top)/scale)
            let bottom = max(Double(max(geo.safeAreaInsets.bottom, safeInsets.bottom)/scale), 20)
            TimelineView(.animation(minimumInterval: 1.0/60, paused: reduced || previewTime != nil || paused || (!autoPlay && touched == nil))) { timeline in
                let t = time(timeline.date)
                let returning=recovery(timeline.date)
                let position=Self.flap((t+wingOffset(timeline.date)+Self.loop).truncatingRemainder(dividingBy:Self.loop))*Double(PixelWingFlightPoses.paths.count)
                let pose=Int(position)%PixelWingFlightPoses.paths.count
                let next=(pose+1)%PixelWingFlightPoses.paths.count
                let mix=Self.smooth(position-Double(pose))
                let dy = (h - 1280*393/720)/2
                ZStack(alignment: .topLeading) {
                    LinearGradient(colors: [Color(red: 0.22, green: 0.21, blue: 0.72),Color(red: 0.20, green: 0.23, blue: 0.78),Color(red: 0.19, green: 0.20, blue: 0.68)], startPoint: .topLeading, endPoint: .bottomTrailing)
                    if let grain=Self.grain { Image(uiImage: grain).resizable().frame(width:393,height:h).opacity(0.7) }
                    Canvas { context,_ in
                        let bird=PixelWingFlightPoses.paths[pose],second=PixelWingFlightPoses.paths[next],rest=PixelWingFlightPoses.paths[0]
                        context.drawLayer { layer in
                            layer.translateBy(x:0,y:dy);layer.blendMode = .plusLighter
                            layer.fill(bird,with:.color(Color(white:0.97).opacity((1-mix)*(1-returning))))
                            layer.fill(second,with:.color(Color(white:0.97).opacity(mix*(1-returning))))
                            layer.fill(rest,with:.color(Color(white:0.97).opacity(returning)))
                        }
                        let b=bird.boundingRect,n=second.boundingRect,z=rest.boundingRect
                        let minY=(Double(b.minY)*(1-mix)+Double(n.minY)*mix)*(1-returning)+Double(z.minY)*returning
                        let maxY=(Double(b.maxY)*(1-mix)+Double(n.maxY)*mix)*(1-returning)+Double(z.maxY)*returning
                        let left=86.0,right=306.0,y1=minY+dy-9,y2=maxY+dy+9
                        var lines=Path()
                        lines.move(to:CGPoint(x:left,y:0));lines.addLine(to:CGPoint(x:left,y:h))
                        lines.move(to:CGPoint(x:right,y:0));lines.addLine(to:CGPoint(x:right,y:h))
                        lines.move(to:CGPoint(x:0,y:y1));lines.addLine(to:CGPoint(x:393,y:y1))
                        lines.move(to:CGPoint(x:0,y:y2));lines.addLine(to:CGPoint(x:393,y:y2))
                        context.stroke(lines,with:.color(.white.opacity(0.48)),lineWidth:0.65)
                        for i in 0..<14 {
                            let x=Double((i*97+29)%393),y=Double((i*173+137)%720)+dy
                            let colors:[Color]=[.white,.green,.orange,.cyan]
                            context.fill(Path(CGRect(x:x,y:y,width:i%3==0 ? 2:1,height:i%3==0 ? 2:1)),with:.color(colors[i%4].opacity(0.7)))
                        }
                        if let cloud=Self.cloud {
                            let image=Image(uiImage:cloud)
                            for i in 0..<6 {
                                let k=Double(i),w=170+k.truncatingRemainder(dividingBy:3)*38
                                let start=Double(i)*0.173
                                let speed=i%2==0 ? 2.0:1.0
                                var u=(t/Self.loop*speed+start).truncatingRemainder(dividingBy:1)
                                let delta=start-u-(start-u).rounded()
                                u=(u+delta*returning+1).truncatingRemainder(dividingBy:1)
                                let x=443-(493+w)*u
                                let y=dy+120+k*92+22*((1-returning)*sin(2 * .pi * t/Self.loop + k)+returning*sin(k))
                                var layer=context;layer.opacity=i%2==0 ? 0.44:0.24
                                layer.draw(image,in:CGRect(x:x,y:y,width:w,height:w*0.375))
                            }
                        }
                        var caption=context;caption.translateBy(x:317,y:(y1+y2)/2+25);caption.rotate(by:.degrees(-90))
                        caption.draw(text("Grow wings and fly",size:11,weight:.medium).italic(),at:.zero)
                    }.frame(width:393,height:h)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Flight canvas")
                        .accessibilityHint("Drag horizontally to steer the wings")
                    HStack {
                        text("AER / FIELD NOTES",size:10,weight:.medium,opacity:0.8).tracking(2)
                        Spacer()
                        text("01 / FLIGHT",size:10,weight:.medium,opacity:0.7).tracking(1)
                    }.frame(width:345).position(x:196.5,y:top+28)
                    text("A little more sky.",size:25,weight:.medium).frame(width:345,alignment:.leading).position(x:196.5,y:h-bottom-112)
                    text("A quiet moment, above it all.",size:12,opacity:0.68).frame(width:345,alignment:.leading).position(x:196.5,y:h-bottom-82)
                    text("DRAG TO FLY",size:9,weight:.medium,opacity:0.65).tracking(1.8).frame(width:345,alignment:.leading).position(x:196.5,y:h-bottom-29)
                    Button {
                        if paused {
                            let duration = Date().timeIntervalSince(pauseBegan ?? Date())
                            epoch = epoch.addingTimeInterval(duration)
                            if let touched { self.touched = touched.addingTimeInterval(duration) }
                            paused = false
                        } else { held = t; heldReturn=returning;heldWingOffset=wingOffset(timeline.date);pauseBegan = Date(); paused = true }
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    } label: {
                        Image(systemName:paused ? "play.fill":"pause.fill").font(.system(size:14,weight:.medium)).foregroundColor(.white).frame(width:48,height:48).overlay(Circle().stroke(.white.opacity(0.45),lineWidth:1))
                    }.position(x:345,y:h-bottom-31).accessibilityLabel(paused ? "Resume flight":"Pause flight")
                }.frame(width:393,height:h,alignment:.topLeading)
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance:4).onChanged { v in
                        let now=Date()
                        if !isTouching {manualWingOffset=3+2*Self.flap((t+wingOffset(now)+Self.loop).truncatingRemainder(dividingBy:Self.loop))-t;dragStart=t;manual=t;isTouching=true;sampleAt=now;sampleManual=t;velocity=0;coast=0}
                        paused=false
                        let raw=dragStart+v.translation.width/393*2
                        let dt=now.timeIntervalSince(sampleAt)
                        if dt>0.002 {velocity=0.35*velocity+0.65*min(3,max(-3,(raw-sampleManual)/dt))}
                        sampleManual=raw;sampleAt=now;manual=(raw+Self.loop).truncatingRemainder(dividingBy:Self.loop);touched=now
                    }.onEnded { _ in coast=min(0.36,max(-0.36,velocity*0.12));isTouching=false;touched=Date() })
                    .scaleEffect(scale,anchor:.topLeading)
            }
        }.task(id: touched) {
            guard !autoPlay,let stamp=touched else {return}
            try? await Task.sleep(for:.seconds(max(0,2.6-Date().timeIntervalSince(stamp))))
            guard !Task.isCancelled,touched==stamp,!paused,!(isTouching) else {return}
            touched=nil;manual=0;coast=0
        }.ignoresSafeArea().preferredColorScheme(.dark)
    }
}
