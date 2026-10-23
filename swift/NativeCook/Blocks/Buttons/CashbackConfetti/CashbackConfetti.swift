import SwiftUI
import UIKit

/// Perks rewards. Claim your cashback. Drag to celebrate.
struct CashbackConfetti: View {
    static let loop = 6.0
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
        if reducedMotion { return 2.2 }
        if touching { return heldTime }
        if let tapStartedAt {
            let age=max(0,now.timeIntervalSince(tapStartedAt))
            if age < (3.1 - tapFrom) / 2.05 { return tapFrom + age * 2.05 }
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
                    context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Self.color(0x050207)))
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
                    heldTime = min(3.0, max(0, touchOrigin + value.translation.width / geometry.size.width * 3.0))
                }
                .onEnded { value in
                    guard !reducedMotion, previewTime == nil else { return }
                    let now=Date()
                    if abs(value.translation.width) < 8 {
                        tapFrom = touchOrigin >= 1.05 && touchOrigin < 2.6 ? touchOrigin : 1.05
                        tapStartedAt=now
                        heldTime=3.1
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
            .accessibilityLabel("Perks rewards. Claim your cashback. Drag to celebrate.")
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

    private static func draw(_ context: GraphicsContext, time t: Double, recovery r: Double) {
        gradient(context,rect(0,0,393,852,0),[0x050207,0x0B030F,0x241027],CGPoint(x:196,y:100),CGPoint(x:196,y:852))
        let event=ease((t-1.2)/0.35)*(1-ease((t-3.0)/0.5))*r
        let caption=ease((t-1.75)/0.4)*(1-ease((t-3.9)/0.6))*r
        label("PERKS / REWARDS",196.5,126,11,color(0x997B9E),.regular,context:context)
        label("A little extra.",196.5,190,29,color(0xF1DBF5),.bold,context:context)
        label("From your everyday moments.",196.5,229,14,color(0x997B9E),context:context)
        for wave in 0..<2 {
            let u=(t-1.52-Double(wave)*0.16)/1.35
            if u>0 && u<1 {
                let q=1-pow(1-u,3),alpha=ease(u/0.09)*pow(1-u,2)*0.62*r
                stroke(context,rect(133.5-48*q,337-22*q,126+96*q,46+44*q,23+22*q),0xC453CE,0.8,alpha)
            }
        }
        let anticipation=sin(.pi*min(1,max(0,(t-1.05)/0.2)))
        let age=max(0,t-1.25)
        // A compressed anticipation, then an underdamped impulse with zero end velocity.
        let impulse=sin(age*17)*exp(-age*5.2)*ease(age/0.08)*(1-ease((age-0.75)/0.3))
        let kick=(-0.025*anticipation*anticipation+0.15*impulse)*r
        var pill=context;pill.translateBy(x:196.5,y:360);pill.scaleBy(x:1+kick,y:1+kick);pill.translateBy(x:-196.5,y:-360)
        glow(pill,rect(134,337,125,46,23),0xAA2DB7,16,0.11+0.16*event)
        fill(pill,rect(133.5,337,126,46,23),0x100512)
        stroke(pill,rect(133.5,337,126,46,23),0xA448AD,1.2)
        var note=pill;note.translateBy(x:154,y:360);note.rotate(by:.degrees(-14-9*impulse*r));note.translateBy(x:-154,y:-360)
        fill(note,rect(140,343,28,35,2),0x7B258D);fill(note,rect(142,341,24,33,1.5),0xF7C0FA)
        stroke(note,rect(145,344,18,27,0.5),0xC16CCE,0.5)
        label("$",154,358,20,color(0x80278F),.bold,context:note)
        let value=45*ease((t-1.25)/0.7)*(1-ease((t-3.9)/0.6))*r
        var amount=pill;amount.clip(to:rect(174,344,74,32,0))
        let expanded=ease(value/10)
        label("$",199-9*expanded,360,22,color(0xDE57E8),.bold,context:amount)
        for column in 0..<2 {
            let turns=column==0 ? floor(value/10)+ease((value.truncatingRemainder(dividingBy:10)-9)) : value
            let digit=Int(floor(turns))%10,offset=turns-floor(turns),x=column==0 ? 205.0 : 218.0
            for row in 0...1 {
                let number=(digit+row)%10
                if column==0 && number==0 { continue }
                label(String(number),x,360+(Double(row)-offset)*32,22,color(0xDE57E8),.bold,context:amount)
            }
        }
        for i in 0..<22 {
            let seed=hash(Double(i)+2),q=(t-1.43-Double(i%5)*0.028)/(1.25+0.25*seed)
            if q>0 && q<1 {
                let side=i%2==0 ? -1.0:1.0,u=(1-exp(-3*q))/(1-exp(-3.0))
                let x=196.5+side*(29+(77+38*seed)*u)
                let y=360-(78+45*seed)*q+(100+36*hash(Double(i)+31))*q*q
                let alpha=ease(q/0.055)*(1-ease((q-0.62)/0.38))*r
                let flutter=cos(q*(12+9*seed)+seed*6.28),w=(3+3*seed)*(0.18+0.82*abs(flutter)),h=8+5*hash(Double(i)+23)
                var conf=context;conf.translateBy(x:x,y:y);conf.rotate(by:.radians(side*(q*4+seed*2)))
                let path=Path { p in
                    p.move(to:CGPoint(x:-w/2,y:-h/2));p.addQuadCurve(to:CGPoint(x:w/2,y:-h/2),control:CGPoint(x:0,y:-h/2-3*flutter))
                    p.addLine(to:CGPoint(x:w/2,y:h/2));p.addQuadCurve(to:CGPoint(x:-w/2,y:h/2),control:CGPoint(x:0,y:h/2-3*flutter));p.closeSubpath()
                }
                gradient(conf,path,flutter>0 ? [0xFEA8FF,0xBF3BD0,0x6E217F] : [0x7C278F,0xDD65E8,0xA839BA],CGPoint(x:-w,y:0),CGPoint(x:w,y:0),alpha)
                stroke(conf,path,0xFFD3FF,0.35,alpha*max(0,flutter)*0.65)
            }
        }
        label("LIFETIME CASHBACK",196.5,445-16*caption,11,color(0x997B9E),.regular,context:context)
        label("$1,367 earned so far",196.5,476-16*caption,14,color(0xE1ABE6),.regular,context:context)
        fill(context,rect(42,652,309,54,27),0xE9B7EF)
        label("Claim your cashback",196.5,679,15,color(0x36103E),.bold,context:context)
        label("GOOD THINGS ADD UP",196.5,745,11,color(0x997B9E),.regular,context:context)
    }
}
