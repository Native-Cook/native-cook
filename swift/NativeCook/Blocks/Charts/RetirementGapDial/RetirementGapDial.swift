import SwiftUI
import UIKit

/// A goal's missing arc closes when its white marker catches the red stop.
struct RetirementGapDial: View {
    static let loop: Double = 8
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var epoch = Date()
    @State private var lastTouch: Date? = nil
    @State private var manual: Double = 0
    @State private var dragStart: Double? = nil
    @State private var caught = false
    @State private var resolveAt: Date? = nil
    @State private var resolveFrom: Double = 0
    @State private var releaseFrom = 0.0
    @State private var releaseVelocity = 0.0
    @State private var sampleAt = Date()
    @State private var sampleValue = 0.0
    @State private var dragAngle = 0.0

    private static let red = Color(red: 0.961, green: 0.133, blue: 0.184)
    private var safeInsets: UIEdgeInsets {
        UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows).first(where: \.isKeyWindow)?.safeAreaInsets ?? .zero
    }
    private static func clamp(_ v: Double) -> Double { min(1, max(0, v)) }
    private static func smooth(_ x: Double) -> Double { let v = clamp(x); return v * v * (3 - 2 * v) }
    // Hermite arrival carries velocity into the damped catch instead of stopping before impact.
    private static func arrive(_ q: Double) -> Double {
        let x = clamp(q); return smooth(x) + 2.4 * (x*x*x - x*x)
    }
    private static func catchOffset(_ dt: Double, velocity: Double = 1.045*2.4/1.78) -> Double {
        let x = max(0, dt)
        return velocity / 17 * exp(-7.5*x) * sin(17*x) * (1-smooth((x-0.5)/0.3))
    }
    private static func phase(_ time: Double) -> Double {
        let t = time.truncatingRemainder(dividingBy: loop)
        if t < 1.1 { return 0 }
        if t < 1.32 { return -0.045 * smooth((t-1.1)/0.22) }
        if t < 3.1 { return -0.045 + 1.045 * arrive((t-1.32)/1.78) }
        if t < 3.9 { return 1 + catchOffset(t-3.1) }
        if t < 5.2 { return 1 }
        if t < 6.8 { return 1 - smooth((t-5.2)/1.6) }
        return 0
    }
    private static func rubber(_ value: Double) -> Double {
        if value < 0 { return -0.08 * (1-exp(value/0.08)) }
        if value > 1 { return 1 + 0.08 * (1-exp(-(value-1)/0.08)) }
        return value
    }
    private func progress(_ date: Date) -> Double {
        if let previewTime { return Self.phase(previewTime) }
        if dragStart != nil { return manual }
        if reduced { return lastTouch == nil ? 0 : manual }
        if let resolveAt {
            let dt = date.timeIntervalSince(resolveAt)
            if dt < 0.7 { return resolveFrom + (1-resolveFrom)*Self.arrive(dt/0.7) }
            if dt < 1.5 { return 1 + Self.catchOffset(dt-0.7,velocity:(1-resolveFrom)*2.4/0.7) }
        }
        if let lastTouch {
            let dt = date.timeIntervalSince(lastTouch)
            if dt < 0.65 {
                let displacement = releaseFrom-manual
                let tail = (displacement+(releaseVelocity+16*displacement)*dt)*exp(-16*dt)
                return manual + tail*(1-Self.smooth((dt-0.45)/0.2))
            }
            if dt < 2 { return manual }
            if dt < 2.6 { return manual*(1-Self.smooth((dt-2)/0.6)) }
            return autoPlay ? Self.phase(dt-2.6) : 0
        }
        return autoPlay ? Self.phase(date.timeIntervalSince(epoch)) : 0
    }
    private func line(_ a: Double, _ b: Double, center: CGPoint, radius: Double) -> Path {
        Path { p in p.addArc(center: center, radius: radius, startAngle: .degrees(a), endAngle: .degrees(b), clockwise: false) }
    }
    private func label(_ text: String, _ size: Double, _ weight: Font.Weight = .regular, _ color: Color = Color(white: 0.08)) -> Text {
        Text(text).font(.system(size: size, weight: weight)).foregroundColor(color)
    }
    var body: some View {
        GeometryReader { geo in
            let s = geo.size.width / 393
            let h = geo.size.height / s
            let top = max(geo.safeAreaInsets.top, safeInsets.top) / s
            let cy = h - 220
            TimelineView(.animation(minimumInterval: 1.0 / 60, paused: reduced || previewTime != nil || (!autoPlay && lastTouch == nil))) { timeline in
                let value = progress(timeline.date)
                let amount = Self.clamp(value)
                let done = Self.smooth((amount-0.90)/0.10)
                let goalCover = Self.smooth((amount-0.86)/0.10)
                let impact = min(1,max(0,value-1)/0.05)
                let angle = -109.5 + 19.5 * value
                let a = angle * .pi / 180
                let thumb = CGPoint(x: 196.5 + 226 * cos(a), y: cy + 226 * sin(a))
                let goal = CGPoint(x: 196.5, y: cy - 226)
                ZStack(alignment: .topLeading) {
                    Color(white: 222.0 / 255)
                    Canvas { context, _ in
                        let center = CGPoint(x: 196.5, y: cy)
                        let redArc = line(min(angle, -90.01), -90, center: center, radius: 226)
                        context.stroke(line(-171, -9, center: center, radius: 226), with: .color(Color(white: 0.78)), style: StrokeStyle(lineWidth: 51, lineCap: .round))
                        context.drawLayer { glow in
                            glow.addFilter(.blur(radius: 23))
                            glow.stroke(redArc, with: .color(Self.red.opacity(0.33 * (1 - amount))), style: StrokeStyle(lineWidth: 69, lineCap: .round))
                        }
                        var guide = Path()
                        guide.move(to: CGPoint(x: goal.x, y: cy - 275)); guide.addLine(to: CGPoint(x: goal.x, y: cy - 77))
                        context.stroke(guide, with: .color(Color.black.opacity(0.055 + 0.04 * amount)), lineWidth: 1)
                        var ray = Path(); ray.move(to: CGPoint(x: 196.5 + 282 * cos(a), y: cy + 282 * sin(a))); ray.addLine(to: CGPoint(x: 196.5 + 88 * cos(a), y: cy + 88 * sin(a)))
                        context.stroke(ray, with: .color(Color.black.opacity(0.10)), style: StrokeStyle(lineWidth: 1.4, dash: [6, 6]))
                        if goalCover < 1 { context.stroke(redArc, with: .color(Self.red.opacity(1-goalCover)), style: StrokeStyle(lineWidth: 51, lineCap: .round)) }
                        context.fill(Path(ellipseIn: CGRect(x: goal.x - 17, y: goal.y - 17, width: 34, height: 34)), with: .color(Color(red: 0.72, green: 0.09, blue: 0.14).opacity(1-goalCover)))
                        context.stroke(line(-171, angle, center: center, radius: 226), with: .color(Color(white: 0.966)), style: StrokeStyle(lineWidth: 51, lineCap: .round))
                        context.stroke(line(-171, angle - 0.5, center: center, radius: 250.5), with: .color(.white.opacity(0.7)), style: StrokeStyle(lineWidth: 0.7, lineCap: .round))
                        if done > 0 {
                            context.fill(Path(ellipseIn: CGRect(x:goal.x-17,y:goal.y-17,width:34,height:34)),with:.color(Color(white:0.966).opacity(goalCover)))
                            context.stroke(Path(ellipseIn:CGRect(x:goal.x-20-impact*5,y:goal.y-20-impact*5,width:40+impact*10,height:40+impact*10)),with:.color(.white.opacity(done*impact*0.6)),lineWidth:1.5)
                        }
                        let tw = 34*(1+impact*0.18), th = 34*(1-impact*0.14)
                        context.fill(Path(ellipseIn:CGRect(x:thumb.x-tw/2,y:thumb.y-th/2,width:tw,height:th)),with:.color(Color(white:0.87)))
                        var arrowContext=context;arrowContext.translateBy(x:thumb.x,y:thumb.y);arrowContext.rotate(by:.degrees(angle+109.5))
                        arrowContext.draw(label("↗",25,.semibold,Color(white:0.16)),at:.zero)
                        context.draw(label("Retirement", 29, .semibold), at: CGPoint(x: 24, y: top + 116), anchor: .leading)
                        let current = amount < 0.005 ? "Current ₹ 89 L" : String(format: "Current ₹ %.2f L", 89 + 1.17 * amount)
                        context.draw(label(current, 17, .regular, Color(white: 0.31)), at: CGPoint(x: 24, y: top + 157), anchor: .leading)
                        var doneContext=context;doneContext.opacity=done
                        doneContext.draw(label("Back on track",18,.semibold),at:CGPoint(x:196.5,y:cy-34+4*(1-done)))
                        if done < 1 {
                            var context=context;context.opacity=1-done
                            let prefix = label("Off-Track by", 18, .semibold)
                            let suffix = label(String(format: "₹ %.2f L", 1.17 * (1 - amount)), 18, .semibold, Self.red)
                            let prefixWidth = context.resolve(prefix).measure(in: CGSize(width: 393, height: 40)).width
                            let suffixWidth = context.resolve(suffix).measure(in: CGSize(width: 393, height: 40)).width
                            let x = (393 - prefixWidth - suffixWidth - 6) / 2
                            context.draw(prefix, at: CGPoint(x: x, y: cy - 34 - 4*done), anchor: .leading)
                            context.draw(suffix, at: CGPoint(x: x + prefixWidth + 6, y: cy - 34 - 4*done), anchor: .leading)
                        }
                        context.draw(label("Target ₹ 90.17 L", 16, .regular, Color(white: 0.32)), at: CGPoint(x: 196.5, y: cy))
                    }
                    .frame(width: 393, height: h)
                    Circle().fill(Color.clear).frame(width: 51, height: 51).position(thumb)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Retirement goal marker")
                        .accessibilityValue("\(Int(amount * 100)) percent of the gap closed")
                        .accessibilityHint("Drag along the dial or adjust to close the gap")
                        .accessibilityAdjustableAction { direction in
                            let step = direction == .increment ? 0.2 : -0.2
                            manual = Self.clamp(amount + step); releaseFrom=manual;releaseVelocity=0;lastTouch = Date(); resolveAt = nil
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        }
                    Button {
                        let started = Date()
                        resolveFrom = amount; manual = 1; releaseFrom=1;releaseVelocity=0;resolveAt = started; lastTouch = started
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    } label: {
                        Text("Resolve").font(.system(size: 17, weight: .semibold)).foregroundColor(Color(red: 0.15, green: 0.55, blue: 0.96))
                            .frame(width: 98, height: 44).background(Color(white: 0.815), in: Capsule())
                    }.position(x: 196.5, y: cy + 64)
                    Image(systemName: "chevron.left").font(.system(size: 22, weight: .regular)).foregroundColor(Color(white: 0.25)).position(x: 28, y: top + 38).accessibilityHidden(true)
                    label("Drag the marker to close the gap", 11, .regular, Color.black.opacity(0.45)).position(x: 196.5, y: h - max(max(geo.safeAreaInsets.bottom, safeInsets.bottom) / s, 16) - 22)
                }
                .frame(width: 393, height: h, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 4).onChanged { v in
                    let now=Date()
                    let degrees=atan2(v.location.y-cy,v.location.x-196.5)*180 / .pi
                    if dragStart == nil {
                        manual=value;dragStart=value;dragAngle=degrees;caught=false;resolveAt=nil
                        sampleAt=now;sampleValue=value;releaseVelocity=0
                    }
                    let next=Self.rubber((dragStart ?? 0)+(degrees-dragAngle)/19.5)
                    let dt=now.timeIntervalSince(sampleAt)
                    if dt>0.002 { releaseVelocity=0.35*releaseVelocity+0.65*min(3,max(-3,(next-sampleValue)/dt)) }
                    manual=next;sampleValue=next;sampleAt=now;lastTouch=now
                    if manual>=0.99 && !caught {caught=true;UIImpactFeedbackGenerator(style:.light).impactOccurred()}
                }.onEnded { _ in
                    releaseFrom=manual
                    manual=Self.clamp(manual+min(0.08,max(-0.08,releaseVelocity*0.12)))
                    dragStart=nil;lastTouch=Date()
                })
                .scaleEffect(s, anchor: .topLeading)
            }
        }.task(id: lastTouch) {
            guard !autoPlay,let stamp=lastTouch else {return}
            try? await Task.sleep(for:.seconds(2.6))
            guard !Task.isCancelled,lastTouch==stamp,!(dragStart != nil) else {return}
            lastTouch=nil;manual=0;resolveAt=nil
        }.ignoresSafeArea().preferredColorScheme(.light)
    }
}
