import SwiftUI
import UIKit

/// Vela yearbook. A folder turns into liquid and the new year. Drag to reveal.
struct LiquidFolderYear: View {
    static let loop = 8.8
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
        if reducedMotion { return 4.8 }
        if touching { return heldTime }
        if let tapStartedAt {
            let age=max(0,now.timeIntervalSince(tapStartedAt))
            if age < (4.8 - tapFrom) / 2.8461538461538463 { return tapFrom + age * 2.8461538461538463 }
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
                    let scale = min(size.width / 393, size.height / 852)
                    context.translateBy(x: (size.width - 393 * scale) / 2, y: (size.height - 852 * scale) / 2)
                    context.scaleBy(x: scale, y: scale)
                    Self.draw(context, time: time(clock.date), recovery: recovery(clock.date))
                }
                .background {
                    Self.color(0x030305)
                    Rectangle().fill(.white)
                        .frame(width:393,height:852)
                        .colorEffect(ShaderLibrary.liquidFolderYearSurface(.float(Float(time(clock.date))), .float(Float(recovery(clock.date)))))
                        .scaleEffect(min(geometry.size.width/393,geometry.size.height/852))
                        .position(x:geometry.size.width/2,y:geometry.size.height/2)
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
                    heldTime = min(5.2, max(0, touchOrigin + value.translation.width / geometry.size.width * 5.2))
                }
                .onEnded { value in
                    guard !reducedMotion, previewTime == nil else { return }
                    let now=Date()
                    if abs(value.translation.width) < 8 {
                        tapFrom = touchOrigin >= 1.1 && touchOrigin < 4.6 ? touchOrigin : 1.1
                        tapStartedAt=now
                        heldTime=4.8
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
            .accessibilityLabel("Vela yearbook. A folder turns into liquid and the new year. Drag to reveal.")
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
        let active=ease((t-1.4)/0.5)*(1-ease((t-4.6)/0.5))*r
        for i in 0..<24 {
            let phase=(t/loop*3+hash(Double(i)+4)).truncatingRemainder(dividingBy:1)
            let a=Double(i)*2.39996
            let r=58+85*phase,x=196.5+cos(a)*r,y=405+sin(a)*r*0.65
            fill(context,circle(x,y,0.6+1.2*hash(Double(i)+5)),i%3==0 ? 0x7EF9EA:0xE9A0FF,active*pow(sin(phase * .pi),2))
        }
        label("VELA / YEARBOOK",196.5,122,11,color(0x908899),.regular,context:context)
        label("Make room for more.",196.5,191,27,color(0xF4EEF8),.bold,context:context)
        label("A new year. A whole new kind of canvas.",196.5,228,14,color(0x908899),context:context)
        label("New possibilities",196.5,568,13,color(0xF4EEF8),.bold,context:context)
        label("Let’s start something new.",196.5,599,13,color(0xA79AAE),context:context)
        stroke(context,rect(49,687,295,52,26),0x6E4282,1)
        label("Create your yearbook",196.5,713,15,color(0xEEDDF5),.bold,context:context)
        label("IDEAS HAVE A WAY OF FINDING YOU",196.5,776,11,color(0x908899),.regular,context:context)
    }

}
