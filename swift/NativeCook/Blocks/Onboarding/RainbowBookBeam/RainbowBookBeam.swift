import SwiftUI
import UIKit

/// Oria reading. Stories open worlds. Drag upward to open the book.
struct RainbowBookBeam: View {
    static let loop = 7.6
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var heldTime = 0.0
    @State private var releasedAt: Date?
    @State private var touching = false
    @State private var touchOrigin = 0.0
    @State private var tapStartedAt: Date?
    @State private var tapFrom = 0.0

    private func time(_ now: Date) -> Double {
        if let previewTime { return previewTime.truncatingRemainder(dividingBy: Self.loop) }
        if reducedMotion { return 3.6 }
        if touching { return heldTime }
        if let tapStartedAt {
            let age=max(0,now.timeIntervalSince(tapStartedAt))
            if age < (3.6 - tapFrom) / 2.125 { return tapFrom + age * 2.125 }
        }
        if let releasedAt {
            let elapsed = now.timeIntervalSince(releasedAt)
            if elapsed < 2 { return heldTime }
            if elapsed < 2.6 { return heldTime }
            return autoPlay ? (elapsed - 2.6).truncatingRemainder(dividingBy: Self.loop) : 0
        }
        return autoPlay ? max(0, now.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop) : 0
    }

    // Settle the selected physical state, never rewind through the story's phases.
    private func recovery(_ now: Date) -> Double {
        if reducedMotion || previewTime != nil { return 1 }
        guard !touching, let releasedAt else { return 1 }
        let age = now.timeIntervalSince(releasedAt)
        return age < 2.6 ? 1 - Self.ease((age - 2) / 0.6) : 1
    }

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(paused: reducedMotion || previewTime != nil || scenePhase != .active || (!autoPlay && releasedAt == nil && !touching))) { clock in
                Canvas { context, size in
                    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.color(0x040305)))
                    let scale = min(size.width / 393, size.height / 852)
                    context.translateBy(x: (size.width - 393 * scale) / 2, y: (size.height - 852 * scale) / 2)
                    context.scaleBy(x: scale, y: scale)
                    Self.draw(context, time: time(clock.date), recovery: recovery(clock.date))
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { value in
                    guard !reducedMotion, previewTime == nil else { return }
                    if !touching {
                        heldTime = time(Date())
                        touchOrigin = heldTime
                        tapStartedAt = nil
                        touching = true
                    }
                    heldTime = min(4.8, max(0, touchOrigin - value.translation.height / 240 * 3.6))
                }
                .onEnded { value in
                    guard !reducedMotion, previewTime == nil else { return }
                    let now=Date()
                    if abs(value.translation.height) < 8 {
                        tapFrom = touchOrigin >= 1.05 && touchOrigin < 3.4 ? touchOrigin : 1.05
                        tapStartedAt=now
                        heldTime=3.6
                        releasedAt=now
                    } else { releasedAt=now }
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    touching = false
                })
            .task(id: releasedAt) {
                guard !autoPlay, let release = releasedAt else { return }
                try? await Task.sleep(for: .seconds(max(0,release.timeIntervalSinceNow)+2.6))
                guard !Task.isCancelled, releasedAt == release, !touching else { return }
                releasedAt = nil
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Oria reading. Stories open worlds. Drag upward to open the book.")
            .accessibilityAction(named: "Replay effect") { epoch = Date(); releasedAt = nil; tapStartedAt = nil }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
    }

    private static func ease(_ value: Double) -> Double {
        let u = min(1, max(0, value)); return u * u * (3 - 2 * u)
    }
    private static func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
    private static func label(_ text: String, _ x: Double, _ y: Double, _ size: Double, _ ink: Color, _ weight: Font.Weight = .regular, context: GraphicsContext) {
        context.draw(Text(text).font(.system(size: size, weight: weight)).foregroundColor(ink), at: CGPoint(x: x, y: y))
    }


    private static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path { Path(ellipseIn: CGRect(x:x-r,y:y-r,width:r*2,height:r*2)) }
    private static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path { Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerRadius:r) }
    private static func polygon(_ points: [CGPoint]) -> Path { Path { p in guard let first=points.first else{return};p.move(to:first);for point in points.dropFirst(){p.addLine(to:point)};p.closeSubpath() } }
    private static func fill(_ c: GraphicsContext, _ p: Path, _ hex: UInt32, _ a: Double = 1) { c.fill(p,with:.color(color(hex).opacity(max(0,min(1,a))))) }
    private static func stroke(_ c: GraphicsContext, _ p: Path, _ hex: UInt32, _ w: Double, _ a: Double = 1) { c.stroke(p,with:.color(color(hex).opacity(max(0,min(1,a)))),style:StrokeStyle(lineWidth:w,lineCap:.round,lineJoin:.round)) }
    private static func glow(_ c: GraphicsContext, _ p: Path, _ hex: UInt32, _ blur: Double, _ a: Double) { c.drawLayer { layer in layer.addFilter(.blur(radius:blur));fill(layer,p,hex,a) } }
    private static func gradient(_ c: GraphicsContext, _ p: Path, _ colors: [UInt32], _ start: CGPoint, _ end: CGPoint, _ a: Double = 1) { c.fill(p,with:.linearGradient(Gradient(colors:colors.map{color($0).opacity(a)}),startPoint:start,endPoint:end)) }
    private static func hash(_ x: Double) -> Double { let v=sin(x*12.9898)*43758.5453;return v-floor(v) }

    private static func beamFill(_ c: GraphicsContext, _ p: Path, _ hex: UInt32, _ top: Double, _ alpha: Double) {
        let ramp=Gradient(stops:[.init(color:color(0xFFF4D1).opacity(alpha),location:0),.init(color:color(hex).opacity(alpha),location:0.86),.init(color:color(hex).opacity(0),location:1)])
        c.fill(p,with:.linearGradient(ramp,startPoint:CGPoint(x:196.5,y:495),endPoint:CGPoint(x:196.5,y:top)))
    }
    private static func beamEdge(_ c:GraphicsContext,_ path:Path,_ top:Double,_ beam:Double) {
        let ramp=Gradient(stops:[.init(color:color(0xFFF1D8).opacity(0.16*beam),location:0),.init(color:color(0xFFE0F5).opacity(0.32*beam),location:0.84),.init(color:color(0xFFE0F5).opacity(0),location:1)])
        let ink=GraphicsContext.Shading.linearGradient(ramp,startPoint:CGPoint(x:196.5,y:495),endPoint:CGPoint(x:196.5,y:top))
        c.drawLayer { layer in layer.addFilter(.blur(radius:2));layer.stroke(path,with:ink,style:StrokeStyle(lineWidth:2.6,lineCap:.round,lineJoin:.round)) }
        c.stroke(path,with:ink,style:StrokeStyle(lineWidth:0.65,lineCap:.round,lineJoin:.round))
    }
    private static func draw(_ context: GraphicsContext, time t: Double, recovery r: Double) {
        let phase=t/loop * .pi*2
        let openingBase=ease((t-1.2)/0.72)*(1-ease((t-5.55)/0.85))
        let age=max(0,t-1.2)
        let recoil=0.24*sin(age*12)*exp(-age*2.9)*ease(age/0.1)*(1-ease((age-1.15)/0.35))
        let opening=max(0,openingBase+recoil)*r
        let beam=ease((t-1.72)/0.72)*(1-ease((t-4.8)/0.9))*r
        label("ORIA / A NEW CHAPTER",196.5,106,11,color(0xA497A4),.regular,context:context)
        label("Stories open worlds.",196.5,173,27,color(0xF8EEF5),.bold,context:context)
        label("Follow your curiosity, one page at a time.",196.5,210,14,color(0xA497A4),context:context)
        let reflected=Path(ellipseIn:CGRect(x:108.5,y:548,width:176,height:18))
        glow(context,reflected,0xE64BB0,12,0.04+beam*0.18)
        if beam>0.001 {
            let palette:[UInt32]=[0xFF3C77,0xE63DAA,0xE748C2,0xFFEAA0,0xF997D9]
            var edges:[Path]=[]
            for band in 0..<5 {
                var points:[CGPoint]=[],reflections:[CGPoint]=[]
                var edge=Path()
                for side in 0...1 { for step in 0...96 {
                    let v=Double(side==0 ? step:96-step)/96*beam
                    let width=(3+70*pow(v,0.8))*(1+0.045*sin(phase*4-v*6))
                    let boundary=Double(band+(side==0 ? 0:1))-2.5
                    let sway=sin(v*20-phase*14)*5*pow(v,0.8)+10*v*v*sin(phase*2)
                    let x=196.5+sway+boundary/2.5*width+sin(v*26-phase*14+boundary*0.8)*1.7*v
                    points.append(CGPoint(x:x,y:495-238*v))
                    if side==1 {if step==0 {edge.move(to:CGPoint(x:x,y:495-238*v))}else{edge.addLine(to:CGPoint(x:x,y:495-238*v))}}
                    reflections.append(CGPoint(x:196.5+(x-196.5)*0.65*(1-0.55*v),y:519+238*v*0.19))
                } }
                let path=polygon(points),mirror=polygon(reflections)
                if band<4 {edges.append(edge)}
                glow(context,mirror,palette[band],4,0.09*r)
                context.fill(mirror,with:.linearGradient(Gradient(colors:[color(palette[band]).opacity(0.2*r),color(palette[band]).opacity(0)]),startPoint:CGPoint(x:196.5,y:519),endPoint:CGPoint(x:196.5,y:566)))
                glow(context,path,palette[band],13,0.27*beam)
                context.fill(path,with:.linearGradient(Gradient(stops:[.init(color:color(0xFFF4D1).opacity(0.88*beam),location:0),.init(color:color(palette[band]).opacity(0.88*beam),location:0.84),.init(color:color(palette[band]).opacity(0),location:1)]),startPoint:CGPoint(x:196.5,y:495),endPoint:CGPoint(x:196.5,y:495-238*beam)))
            }
            for edge in edges {beamEdge(context,edge,495-238*beam,beam)}
            for i in 0..<12 {
                let v=(Double(i)*0.173+t/loop*3).truncatingRemainder(dividingBy:1)
                let sway=sin(v*20-phase*14)*5*pow(v,0.8)+10*v*v*sin(phase*2)
                let x=196.5+sway+(hash(Double(i)+3)-0.5)*(5+118*pow(v,0.8)),y=495-238*v
                let radius=1.4+2.6*hash(Double(i)+54)
                let alpha=ease(v/0.08)*(1-ease((v-0.85)/0.15))*ease((beam-v)/0.12)*r
                glow(context,circle(x,y,radius*2),0xFFF7E9,6,alpha*0.7)
                fill(context,circle(x,y,radius),0xFFF7E9,alpha)
            }
        }
        var book=context
        let anticipate=sin(.pi*min(1,max(0,(t-1.02)/0.22)))
        book.translateBy(x:196.5,y:500);book.rotate(by:.degrees(-12*(1-opening)-6*anticipate*anticipate*r));book.translateBy(x:-196.5,y:-500)
        let openAlpha=ease(opening/0.22),closed=1-openAlpha
        if closed>0.001 {
            let solid=polygon([CGPoint(x:188.5,y:412),CGPoint(x:206.5,y:415),CGPoint(x:206.5,y:507),CGPoint(x:188.5,y:504)])
            gradient(book,solid,[0xFFB826,0xFF773A,0xAF368F],CGPoint(x:188,y:412),CGPoint(x:207,y:507),closed)
            stroke(book,solid,0xFFD256,0.9,closed)
            fill(book,polygon([CGPoint(x:188.5,y:412),CGPoint(x:191.5,y:413),CGPoint(x:191.5,y:505),CGPoint(x:188.5,y:504)]),0xA52E8E,closed)
        }
        if openAlpha>0.001 {
            let angle=opening * .pi/2,span=8*cos(angle)+83*sin(angle),tip=500-88*cos(angle)-18*sin(angle)
            for side in [-1.0,1.0] {
                let cover=Path { p in
                    p.move(to:CGPoint(x:196.5,y:501));p.addQuadCurve(to:CGPoint(x:196.5+side*span,y:tip),control:CGPoint(x:196.5+side*span*0.54,y:491-45*cos(angle)))
                    p.addLine(to:CGPoint(x:196.5+side*(span+3*sin(angle)),y:tip+8));p.addQuadCurve(to:CGPoint(x:196.5,y:510),control:CGPoint(x:196.5+side*span*0.55,y:506-35*cos(angle)));p.closeSubpath()
                }
                glow(book,cover,0xFF5B67,12,0.22*openAlpha)
                gradient(book,cover,[0x7D267F,0xB82C9D,0xF36663],CGPoint(x:196.5+side*span,y:tip),CGPoint(x:196.5,y:510),openAlpha)
                stroke(book,cover,0xFF996B,0.7,openAlpha*0.7)
            }
            // Three independent leaves lag the covers and curl through the hinge.
            for layer in 0..<3 {
                let j=Double(layer)
                let leaf=ease((t-1.26-j*0.055)/0.72)*(1-ease((t-5.30-j*0.06)/0.82))*r
                let a=leaf * .pi/2,w=8*cos(a)+81*sin(a),tip=500-88*cos(a)-18*sin(a)-j*1.4
                let curl=pow(sin(leaf * .pi),2)*(7+j*3)*sin(phase*8+j)
                for side in [-1.0,1.0] {
                    let page=Path { p in
                        p.move(to:CGPoint(x:196.5,y:498-j*0.6))
                        p.addQuadCurve(to:CGPoint(x:196.5+side*w,y:tip-5),control:CGPoint(x:196.5+side*w*0.48,y:489-56*cos(a)-10*sin(a)-curl))
                        p.addLine(to:CGPoint(x:196.5+side*(w+2*sin(a)),y:tip+3))
                        p.addQuadCurve(to:CGPoint(x:196.5,y:504-j*0.5),control:CGPoint(x:196.5+side*w*0.55,y:503-39*cos(a)-3*sin(a)));p.closeSubpath()
                    }
                    gradient(book,page,layer==2 ? [0xFFD457,0xFF9A3F,0xFF583B]:[0xFF9E33,0xE96C38,0xB94B69],CGPoint(x:196.5+side*w,y:tip-5),CGPoint(x:196.5,y:504),openAlpha)
                    stroke(book,page,0xFFD256,0.65,openAlpha*(0.4+j*0.2))
                }
            }
            let spine=Path { p in p.move(to:CGPoint(x:188.5,y:505));p.addQuadCurve(to:CGPoint(x:204.5,y:505),control:CGPoint(x:196.5,y:517)) }
            stroke(book,spine,0x69BBD1,1.3,0.7*openAlpha)
        }
        label("One page. Endless places.",196.5,621,17,color(0xF8EEF5),.bold,context:context)
        fill(context,rect(42,700,309,54,27),0xF5D5EA)
        label("Open your world",196.5,727,15,color(0x40152F),.bold,context:context)
        label("A LITTLE WONDER, EVERY DAY",196.5,783,11,color(0xA497A4),.regular,context:context)
    }
}
