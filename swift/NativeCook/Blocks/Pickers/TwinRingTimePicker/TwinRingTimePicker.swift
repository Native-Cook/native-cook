import SwiftUI
import UIKit
import CoreText

struct TwinRingTimePicker: View {
    static let loop = 9
    var autoPlay = true
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @StateObject private var driver = TwinRingTimePickerDriver()

    init(autoPlay: Bool = true) {
        self.autoPlay=autoPlay
        TwinRingTimePickerFonts.register()
    }

    var body: some View {
        GeometryReader { safeGeometry in
            GeometryReader { geometry in
                TwinRingTimePickerScene(driver: driver, size: geometry.size, topInset: safeGeometry.safeAreaInsets.top, bottomInset: safeGeometry.safeAreaInsets.bottom, displayScale: displayScale)
            }.ignoresSafeArea()
        }
        .background(TwinRingTimePickerPalette.color(0xEEEDEB).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { driver.configure(autoPlay: autoPlay, reduced: reduced, active: scenePhase == .active) }
        .onChange(of: autoPlay) { _, _ in driver.configure(autoPlay: autoPlay, reduced: reduced, active: scenePhase == .active) }
        .onChange(of: reduced) { _, _ in driver.configure(autoPlay: autoPlay, reduced: reduced, active: scenePhase == .active) }
        .onChange(of: scenePhase) { _, phase in driver.configure(autoPlay: autoPlay, reduced: reduced, active: phase == .active) }
        .onDisappear { driver.stop() }
    }
}

private enum TwinRingTimePickerPalette {
    static func ui(_ hex: Int, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double(hex>>16 & 255)/255, green: Double(hex>>8 & 255)/255, blue: Double(hex & 255)/255, alpha: alpha)
    }
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color { Color(uiColor: ui(hex, alpha)) }
    static func blend(_ a: Int, _ b: Int, _ k: Double) -> Color {
        let r=TwinRingTimePickerMotion.mix(Double(a>>16 & 255), Double(b>>16 & 255), k)/255
        let g=TwinRingTimePickerMotion.mix(Double(a>>8 & 255), Double(b>>8 & 255), k)/255
        let b=TwinRingTimePickerMotion.mix(Double(a & 255), Double(b & 255), k)/255
        return Color(.sRGB, red: r, green: g, blue: b)
    }
}

private enum TwinRingTimePickerFonts {
    static var registered = false
    static func register() {
        guard !registered else { return }; registered=true
        for name in ["Onest-ExtraBold", "InterTight-SemiBold"] {
            if let url=Bundle.main.url(forResource: "TwinRingTimePicker-"+name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }
    static func font(_ size: Double, tight: Bool = false) -> UIFont {
        // The supplied 600-weight Inter Tight file's internal PostScript name is InterTight-Regular.
        // Construct from its actual bytes to avoid a different block's registered face with that name.
        let name=tight ? "InterTight-SemiBold" : "Onest-ExtraBold"
        let url=Bundle.main.url(forResource:"TwinRingTimePicker-"+name, withExtension:"ttf")!
        let graphicsFont=CGFont(CGDataProvider(url:url as CFURL)!)!
        return CTFontCreateWithGraphicsFont(graphicsFont, size, nil, nil) as UIFont
    }
    static var glyphs: [String: UIImage] = [:]
    static var labels: [String: UIImage] = [:]
    static func glyph(_ label: String, scale: Double) -> UIImage {
        let key="\(label)/\(scale)"
        if let image=glyphs[key] { return image }
        let font=font(56) as CTFont
        var chars=Array(label.utf16), ids=Array(repeating: CGGlyph(), count: label.utf16.count)
        CTFontGetGlyphsForCharacters(font, &chars, &ids, ids.count)
        var advances=Array(repeating: CGSize.zero, count: ids.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &advances, ids.count)
        let width=advances.reduce(0) { $0+$1.width }
        let format=UIGraphicsImageRendererFormat(); format.scale=scale
        let image=UIGraphicsImageRenderer(size: CGSize(width: 180, height: 152), format: format).image { context in
            let c=context.cgContext
            c.translateBy(x: 90-width/2, y: 96); c.scaleBy(x: 1, y: -1)
            c.setFillColor(UIColor.white.cgColor)
            var x=0.0
            let positions=advances.map { advance -> CGPoint in defer { x+=advance.width }; return CGPoint(x: x, y: 0) }
            CTFontDrawGlyphs(font, ids, positions, ids.count, c)
        }
        glyphs[key]=image
        return image
    }
    static func text(_ text: String, size: Double, tight: Bool, tracking: Double = 0, width: Double? = nil, lineHeight: Double? = nil, scale: Double) -> UIImage {
        let key="\(text)/\(size)/\(tight)/\(tracking)/\(width ?? 0)/\(lineHeight ?? 0)/\(scale)"
        if let image=labels[key] { return image }
        let f=font(size, tight: tight)
        let attributes: [NSAttributedString.Key: Any] = [.font:f, .kern:tracking, .foregroundColor:TwinRingTimePickerPalette.ui(0x0B0B0B)]
        let rows=text.components(separatedBy: "\n")
        let strings=rows.map { NSAttributedString(string: $0, attributes: attributes) }
        let w=width ?? ceil(strings.map { $0.size().width }.max() ?? 0)
        let h=lineHeight ?? f.lineHeight
        let format=UIGraphicsImageRendererFormat(); format.scale=scale
        let image=UIGraphicsImageRenderer(size: CGSize(width:w, height:h*Double(rows.count)), format:format).image { _ in
            for (i,s) in strings.enumerated() {
                let x=width == nil ? 0 : (w-s.size().width)/2
                s.draw(at: CGPoint(x:x, y:Double(i)*h+(h-f.lineHeight)/2))
            }
        }
        labels[key]=image
        return image
    }
}

private struct TwinRingTimePickerScene: View {
    @ObservedObject var driver: TwinRingTimePickerDriver
    let size: CGSize
    let topInset: Double
    let bottomInset: Double
    let displayScale: Double
    private typealias M = TwinRingTimePickerMotion
    private var scale: Double { size.width/402 }
    private var dy: Double { (size.height/scale-874)/2 }

    var body: some View {
        let f=driver.frame, s=scale, d=dy
        let open=min(max(f.open, 0), 1)
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                drawBase(&context, frame:f, dy:d)
            }
            .allowsHitTesting(false)
            Image(uiImage:TwinRingTimePickerFonts.text("When do you usually\nwake up?", size:29.5, tight:true, tracking:-1/s, width:402, lineHeight:33, scale:displayScale))
                .position(x:201, y:max(156, topInset/s+100)+33)
                .accessibilityLabel("When do you usually wake up?")
            ForEach(0..<18, id:\.self) { index in
                TwinRingTimePickerNumeral(side:index/9, slot:index%9-4, frame:f, dy:d, displayScale:displayScale)
                    .allowsHitTesting(false)
            }
            Canvas { context, _ in
                context.translateBy(x:0, y:d)
                drawForeground(&context, frame:f)
            }.allowsHitTesting(false)
            TwinRingTimePickerMeridiem(k:driver.ampmValue, open:open, dy:d, displayScale:displayScale, unit:s)
                .allowsHitTesting(false)
            TwinRingTimePickerTouchSurface(driver:driver, scale:s)
                .frame(width:402, height:480)
                .offset(y:255+d)
                .accessibilityLabel("Wake-up time. Drag left wheel for hours, right wheel for minutes.")
            Button { driver.confirm() } label: {
                HStack {
                    Image(uiImage:TwinRingTimePickerFonts.text("Continue", size:20, tight:true, scale:displayScale))
                        .renderingMode(.template)
                    Spacer(minLength:0)
                    TwinRingTimePickerArrow(back:false).stroke(TwinRingTimePickerPalette.color(0xFFFBF8), style:StrokeStyle(lineWidth:2.5, lineCap:.round, lineJoin:.round)).frame(width:24, height:14)
                }
                .foregroundStyle(TwinRingTimePickerPalette.color(0xFFFBF8))
                .padding(.horizontal,18)
                .frame(width:349, height:64)
                .contentShape(RoundedRectangle(cornerRadius:22))
            }
            .buttonStyle(TwinRingTimePickerButtonStyle())
            .accessibilityLabel("Continue")
            .scaleEffect(f.cta)
            .offset(x:27, y:min(751+2*d, size.height/s-bottomInset/s-64))
        }
        .frame(width:402, height:size.height/s, alignment:.topLeading)
        .scaleEffect(s, anchor:.topLeading)
        .frame(width:size.width, height:size.height, alignment:.topLeading)
        .background(TwinRingTimePickerPalette.blend(0xEEEDEB, 0xF3ECDF, open))
        .clipped()
    }

    private func drawBase(_ context: inout GraphicsContext, frame f:M.Frame, dy:Double) {
        let backY=max(67, topInset/scale+8)
        context.fill(Path(ellipseIn:CGRect(x:23, y:backY, width:44, height:44)), with:.color(TwinRingTimePickerPalette.color(0xE2E2E2)))
        context.stroke(TwinRingTimePickerArrow(back:true).path(in:CGRect(x:36, y:backY+15, width:18, height:14)), with:.color(TwinRingTimePickerPalette.color(0x0B0B0B)), style:StrokeStyle(lineWidth:2, lineCap:.round, lineJoin:.round))
        let track=CGRect(x:111, y:backY+16, width:180, height:12)
        context.fill(Path(roundedRect:track, cornerRadius:6), with:.color(TwinRingTimePickerPalette.color(0xD6D6D6)))
        context.fill(Path(roundedRect:CGRect(x:111, y:backY+16, width:41+23*f.fill, height:12), cornerRadius:6), with:.color(TwinRingTimePickerPalette.color(0x0B0B0B)))
        context.translateBy(x:0, y:dy)
        for side in 0...1 {
            let cx=side == 0 ? -44-36*max(f.open, 0) : 446+36*max(f.open, 0)
            let color=driver.held == side+1 ? 0xCACACA : 0xD4D4D4
            context.stroke(Path(ellipseIn:CGRect(x:cx-226, y:495-226, width:452, height:452)), with:.color(TwinRingTimePickerPalette.color(color)), lineWidth:38)
            var ticks=Path()
            let turn=(side == 0 ? f.h : -f.m)*Double.pi/10
            for i in 0..<100 {
                let a=Double(i)*Double.pi/50+turn, r=207.0, length=i%5 == 0 ? 12.0 : 6.0
                ticks.move(to:CGPoint(x:cx+cos(a)*r, y:495+sin(a)*r))
                ticks.addLine(to:CGPoint(x:cx+cos(a)*(r+length), y:495+sin(a)*(r+length)))
            }
            context.stroke(ticks, with:.color(TwinRingTimePickerPalette.color(0xB8B8B8)), lineWidth:1.5)
            let mesh=atan2(442.0-495, 201.0-(side == 0 ? -44.0 : 446.0)), r=243.0
            var flash=Path()
            for i in 0...12 {
                let a=mesh-7/r+14/r*Double(i)/12, point=CGPoint(x:cx+cos(a)*r,y:495+sin(a)*r)
                if i == 0 { flash.move(to:point) } else { flash.addLine(to:point) }
            }
            var rim=context; rim.opacity=f.flash
            rim.stroke(flash, with:.color(TwinRingTimePickerPalette.color(0xBEBEBE)), style:StrokeStyle(lineWidth:4,lineCap:.round))
        }
        var seam=Path(); seam.move(to:CGPoint(x:201, y:487)); seam.addLine(to:CGPoint(x:201, y:503))
        var sc=context; sc.opacity=1-min(max(f.open, 0), 1)
        sc.stroke(seam, with:.color(TwinRingTimePickerPalette.color(0xC6C6C6)), lineWidth:1)
    }
    private func drawForeground(_ context:inout GraphicsContext, frame f:M.Frame) {
        for y in [489.0,506] { context.fill(Path(ellipseIn:CGRect(x:196.5, y:y-4.5, width:9, height:9)), with:.color(TwinRingTimePickerPalette.color(0x0B0B0B))) }
        let o=min(max(f.open,0),1)
        var stem=Path(); stem.move(to:CGPoint(x:201,y:465)); stem.addLine(to:CGPoint(x:201,y:481))
        var stalk=context; stalk.opacity=1-M.seg(o,0,0.3)
        stalk.stroke(stem,with:.color(TwinRingTimePickerPalette.color(0xF5B400)),lineWidth:1)
        var pin=context
        pin.translateBy(x:201,y:442-42*(1-pow(1-o,3))-3*f.kick)
        pin.rotate(by:.degrees(90*(f.m-6-(f.h-8))))
        let ps=1+0.25*f.kick; pin.scaleBy(x:ps,y:ps)
        let path=Self.pinion(open:o)
        pin.drawLayer { glow in
            glow.addFilter(.blur(radius:18)); glow.opacity=0.35*M.seg(o,0.3,1)
            glow.fill(Path(ellipseIn:CGRect(x:-46,y:-46,width:92,height:92)),with:.color(TwinRingTimePickerPalette.color(0xF5B400)))
        }
        pin.drawLayer { shadow in
            shadow.translateBy(x:0,y:1); shadow.addFilter(.blur(radius:1))
            shadow.fill(path,with:.color(TwinRingTimePickerPalette.color(0x8A6400,0.18)))
        }
        let k=M.seg(f.h,11.5,12.5)*(f.h<20 ? 1 : 0)
        pin.fill(path,with:.color(TwinRingTimePickerPalette.blend(0xF5B400,0xF29A1E,k)))
        pin.stroke(path,with:.color(TwinRingTimePickerPalette.color(0xFFD24A,0.6)),lineWidth:1)
        pin.opacity=1-M.seg(o,0,0.4)
        pin.fill(Path(ellipseIn:CGRect(x:17.5,y:-1.5,width:3,height:3)),with:.color(TwinRingTimePickerPalette.color(0xC98F00)))
    }
    private static func pinion(open:Double) -> Path {
        var p=Path()
        for i in 0...120 {
            let t=Double(i)/120*2*Double.pi
            let r=M.mix(25,32,open)*(1-(1-open)*0.26*(0.5-0.5*cos(4*t)))
            let point=CGPoint(x:cos(t)*r,y:sin(t)*r)
            if i==0 { p.move(to:point) } else { p.addLine(to:point) }
        }
        p.closeSubpath(); return p
    }
}

private struct TwinRingTimePickerNumeral: View {
    let side:Int, slot:Int
    let frame:TwinRingTimePickerMotion.Frame
    let dy:Double, displayScale:Double
    private typealias M = TwinRingTimePickerMotion
    var body: some View {
        let value=side == 0 ? frame.h : frame.m
        let v=Int(M.rounded(value))+slot, k=Double(v)-value, a=abs(k)
        let scale=a < 1 ? M.mix(1, 0.84, a) : a < 2 ? M.mix(0.84, 0.74, a-1) : a < 3 ? M.mix(0.74, 0.66, a-2) : 0.66
        let blur=a < 1 ? 0 : a < 2 ? M.mix(0, 1.5, a-1) : a < 3 ? M.mix(1.5, 4, a-2) : 4
        let alpha=(a < 3.3 ? 1 : 1-M.seg(a, 3.3, 3.9))*(1-min(a, 1) > 0.5 ? 1 : 1-min(max(frame.open, 0), 1))
        let angle=side == 0 ? -k*18*Double.pi/180 : Double.pi+k*18*Double.pi/180
        let radius=side == 0 ? 155.0 : 150
        let cx=side == 0 ? -44-36*max(frame.open, 0) : 446+36*max(frame.open, 0)
        let x=cx+cos(angle)*radius, y=495+sin(angle)*radius+dy
        let rotation=side == 0 ? angle : angle-Double.pi
        let focusScale=1+0.28*min(max(frame.open, 0), 1)
        let totalScale=scale*M.mix(1, focusScale, 1-min(a, 1))
        let colors=[0x0B0B0B, 0xA8A8A8, 0xC9C9C9, 0xDDDDDD], colorIndex=min(Int(a), 2)
        let tint=TwinRingTimePickerPalette.blend(colors[colorIndex], colors[colorIndex+1], min(a, 3)-Double(colorIndex))
        let speed=side == 0 ? frame.hSpeed : frame.mSpeed
        let sigmaX=blur, sigmaY=blur+min(14, speed*2.4)
        let label=side == 0 ? M.hourLabel(v) : M.minuteLabel(v)
        Image(uiImage:TwinRingTimePickerFonts.glyph(label, scale:displayScale))
            .renderingMode(.template).foregroundStyle(tint)
            .frame(width:180, height:152)
            .layerEffect(ShaderLibrary.twinRingTimePickerBlur(.float(sigmaX), .float2(1, 0)), maxSampleOffset:CGSize(width:52, height:0), isEnabled:sigmaX > 0.01)
            .layerEffect(ShaderLibrary.twinRingTimePickerBlur(.float(sigmaY), .float2(0, 1)), maxSampleOffset:CGSize(width:0, height:72), isEnabled:sigmaY > 0.01)
            .scaleEffect(totalScale).rotationEffect(.radians(rotation))
            .opacity(alpha).position(x:x, y:y)
            .accessibilityHidden(true)
    }
}

private struct TwinRingTimePickerMeridiem: View {
    let k:Double, open:Double, dy:Double, displayScale:Double, unit:Double
    var body: some View {
        let opacity=TwinRingTimePickerMotion.seg(k, 0.3, 0.7)
        let a=open<0.4 ? 1-TwinRingTimePickerMotion.seg(open,0,0.15) : TwinRingTimePickerMotion.seg(open,0.55,0.9)
        let x=open>0.4 ? 184.0 : 338.0, y=open>0.4 ? 535.0 : 483.0
        ZStack(alignment:.topLeading) {
            label("AM").offset(y:12*k).opacity((1-opacity)*a)
            label("PM").offset(y:-12*(1-k)).opacity(opacity*a)
        }.offset(x:x, y:y+dy)
    }
    private func label(_ text:String) -> some View {
        Image(uiImage:TwinRingTimePickerFonts.text(text, size:23, tight:false, tracking:-0.5/unit, scale:displayScale))
            .accessibilityLabel(text)
    }
}

private struct TwinRingTimePickerArrow: Shape {
    var back:Bool
    func path(in rect:CGRect) -> Path {
        var p=Path()
        let points: [[CGPoint]]=back ? [[CGPoint(x:17,y:7),CGPoint(x:1.5,y:7)],[CGPoint(x:7,y:1),CGPoint(x:1,y:7),CGPoint(x:7,y:13)]] : [[CGPoint(x:1.5,y:7),CGPoint(x:22,y:7)],[CGPoint(x:16,y:1.5),CGPoint(x:22,y:7),CGPoint(x:16,y:12.5)]]
        for line in points {
            for (i,v) in line.enumerated() {
                let point=CGPoint(x:rect.minX+v.x*rect.width/(back ? 18:24), y:rect.minY+v.y*rect.height/14)
                if i == 0 { p.move(to:point) } else { p.addLine(to:point) }
            }
        }
        return p
    }
}

private struct TwinRingTimePickerButtonStyle: ButtonStyle {
    func makeBody(configuration:Configuration) -> some View {
        configuration.label.background(TwinRingTimePickerPalette.color(configuration.isPressed ? 0xB23500 : 0xC93C00), in:RoundedRectangle(cornerRadius:22))
    }
}

/// A zero-distance native pan preserves RN's touch-down takeover and point/second release velocity.
private struct TwinRingTimePickerTouchSurface: UIViewRepresentable {
    let driver:TwinRingTimePickerDriver
    let scale:Double
    func makeUIView(context:Context) -> TwinRingTimePickerTouchView { let v=TwinRingTimePickerTouchView(); v.driver=driver; return v }
    func updateUIView(_ view:TwinRingTimePickerTouchView, context:Context) { view.driver=driver; view.unit=scale }
}

private final class TwinRingTimePickerTouchView:UIView {
    weak var driver:TwinRingTimePickerDriver?
    var unit=1.0
    private lazy var pan=UIPanGestureRecognizer(target:self, action:#selector(panned(_:)))
    override init(frame:CGRect) {
        super.init(frame:frame)
        pan.cancelsTouchesInView=false; pan.delaysTouchesBegan=false; pan.delaysTouchesEnded=false
        addGestureRecognizer(pan)
    }
    required init?(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
    override func touchesBegan(_ touches:Set<UITouch>, with event:UIEvent?) {
        guard let touch=touches.first else { return }
        driver?.begin(left:touch.location(in:self).x < bounds.width/2)
    }
    @objc private func panned(_ recognizer:UIPanGestureRecognizer) {
        // UIKit inverts the outer SwiftUI scale, just as the RN handler divides pixels by S.
        switch recognizer.state {
        case .began, .changed: driver?.drag(translation:recognizer.translation(in:self).y)
        case .ended: driver?.end(velocity:recognizer.velocity(in:self).y)
        case .cancelled, .failed: driver?.end(velocity:0)
        default: break
        }
    }
    override func touchesEnded(_ touches:Set<UITouch>, with event:UIEvent?) {
        if pan.state == .possible || pan.state == .failed { driver?.end(velocity:0) }
    }
    override func touchesCancelled(_ touches:Set<UITouch>, with event:UIEvent?) { driver?.end(velocity:0) }
}

private struct TwinRingTimePickerChannel {
    var value:Double
    var velocity=0.0
    private var start=0.0, from=0.0, target=0.0, v0=0.0, duration=0.0
    private var spring:TwinRingTimePickerMotion.Spring?
    private(set) var animating=false
    mutating func set(_ value:Double) { self.value=value; velocity=0; animating=false }
    mutating func animate(to:Double, at:Double, spring:TwinRingTimePickerMotion.Spring, velocity:Double? = nil, reduced:Bool) {
        if reduced { set(to); return }
        if animating && self.spring != nil && target == to { return }
        from=value; target=to; start=at; v0=(animating ? self.velocity : 0)+(velocity ?? 0)
        if (to > value && v0 < 0) || (to < value && v0 > 0) { v0=0 }
        self.spring=spring; animating=true
    }
    mutating func timing(to:Double, at:Double, duration:Double, reduced:Bool) {
        if reduced { set(to); return }
        from=value; target=to; start=at; self.duration=duration; spring=nil; animating=true
    }
    mutating func advance(_ now:Double) {
        guard animating else { return }
        let t=now-start
        if let spring {
            // withSpring defaults to mass 4 in the installed Reanimated 4.5.1.
            // The autoplay's explicit springAt helper above intentionally uses mass 1.
            value=TwinRingTimePickerMotion.springAt(t, from, target, v0, spring, mass:4)
            let epsilon=0.00001
            velocity=(TwinRingTimePickerMotion.springAt(t+epsilon, from, target, v0, spring, mass:4)-value)/epsilon
            let initial=0.5*spring.stiffness*pow(from-target, 2)+2*v0*v0
            let energy=0.5*spring.stiffness*pow(value-target, 2)+2*velocity*velocity
            if initial == 0 || energy/max(initial, 0.000000001) < 6e-9 { set(target) }
        } else {
            let k=min(max(t/duration, 0), 1), eased=k < 0.5 ? 2*k*k : 1-pow(-2*k+2, 2)/2
            value=TwinRingTimePickerMotion.mix(from, target, eased)
            if k >= 1 { set(target) }
        }
    }
}

private final class TwinRingTimePickerDisplayTarget:NSObject {
    weak var owner:TwinRingTimePickerDriver?
    @objc func tick(_ link:CADisplayLink) { owner?.tick(link) }
}

private final class TwinRingTimePickerDriver:ObservableObject {
    private typealias M = TwinRingTimePickerMotion
    @Published var frame = TwinRingTimePickerMotion.Frame()
    @Published var held=0
    @Published var ampmValue=0.0
    private let target=TwinRingTimePickerDisplayTarget()
    private var link:CADisplayLink?, previous=0.0, now=0.0, t=0.0
    private var auto=true, autoPlay=true, reduced=false, active=true, configured=false
    private var h=TwinRingTimePickerChannel(value:8), m=TwinRingTimePickerChannel(value:6)
    private var open=TwinRingTimePickerChannel(value:0), fill=TwinRingTimePickerChannel(value:0), pm=TwinRingTimePickerChannel(value:0)
    private var touching=false, lastTouch=0.0, start=0.0, lastTick=0.0, lastCarry=0.0, lastDetent=8006.0
    private var hPrev=8.0, mPrev=6.0, hSpeed=0.0, mSpeed=0.0
    private var pin:Double?
    private var homing=false
    private var manual=false
    private var successAt:Double?
    init() {
        target.owner=self
        #if DEBUG
        manual=UserDefaults.standard.bool(forKey:"twinRingTimePickerManual")
        if let raw=UserDefaults.standard.object(forKey:"twinRingTimePickerTime"), let value=Double("\(raw)") {
            pin=value; t=value; frame=M.frame(value)
            ampmValue=meridiem(frame.h); pm.set(ampmValue)
        }
        #endif
    }
    deinit { link?.invalidate() }
    func configure(autoPlay:Bool, reduced:Bool, active:Bool) {
        if !configured || self.autoPlay != autoPlay || self.reduced != reduced { auto=autoPlay && !reduced && !manual }
        configured=true; self.autoPlay=autoPlay; self.reduced=reduced; self.active=active
        if let pin { frame=M.frame(pin); ampmValue=meridiem(frame.h) }
        else if !auto { publish() }
        wake()
    }
    func stop() { link?.invalidate(); link=nil; previous=0 }
    private func wake() {
        if link == nil {
            let link=CADisplayLink(target:target, selector:#selector(TwinRingTimePickerDisplayTarget.tick(_:)))
            link.add(to:.main, forMode:.common); self.link=link
        }
        previous=0; link?.isPaused = !active || pin != nil
    }
    private func meridiem(_ h:Double) -> Double { let hour=(Int(M.rounded(h))%24+24)%24; return hour >= 12 ? 1 : 0 }
    func tick(_ link:CADisplayLink) {
        let dt=previous == 0 ? 0.016 : min(max(link.timestamp-previous, 0), 0.05); previous=link.timestamp
        now+=dt
        if dt > 0 {
            hSpeed=M.mix(hSpeed, abs(h.value-hPrev)/dt, 0.5); mSpeed=M.mix(mSpeed, abs(m.value-mPrev)/dt, 0.5)
        }
        hPrev=h.value; mPrev=m.value
        h.advance(now); m.advance(now); open.advance(now); fill.advance(now); pm.advance(now)
        if auto { t=(t+dt).truncatingRemainder(dividingBy:M.loop); frame=M.frame(t) }
        else {
            reactions()
            if !reduced && autoPlay && !manual && !touching && open.value < 0.01 && now-lastTouch > 2 {
                let homed=abs(h.value-8)<0.01 && abs(m.value-6)<0.01 && hSpeed<0.15 && mSpeed<0.15
                if homed && fill.value<0.01 {
                    h.set(8); m.set(6); fill.set(0); hSpeed=0; mSpeed=0; hPrev=8; mPrev=6
                    t=0; homing=false; auto=true; frame=M.frame(0)
                } else if !homed && !homing {
                    homing=true
                    h.animate(to:8,at:now,spring:.glide,reduced:reduced)
                    m.animate(to:6,at:now,spring:.glide,reduced:reduced)
                }
            }
            if !auto { publish() }
        }
        let next=meridiem(frame.h)
        if next != pmTarget { pmTarget=next; pm.animate(to:next, at:now, spring:.snap, reduced:reduced) }
        ampmValue=pm.value
        if let at=successAt, now >= at { UINotificationFeedbackGenerator().notificationOccurred(.success); successAt=nil }
        if !auto && !touching && !h.animating && !m.animating && !open.animating && !fill.animating && !pm.animating && successAt == nil && (!autoPlay || reduced || open.value >= 0.01) { link.isPaused=true; previous=0 }
    }
    private var pmTarget=0.0
    private func publish() { frame=M.Frame(h:h.value, m:m.value, open:open.value, fill:fill.value, hSpeed:hSpeed, mSpeed:mSpeed) }
    private func takeOver() {
        if homing { h.set(h.value); m.set(m.value); homing=false }
        if auto {
            let f=M.frame(t)
            h.set(M.rounded(f.h*1000)/1000); m.set(f.m); open.set(f.open); fill.set(f.fill); auto=false
        }
        hPrev=h.value; mPrev=m.value
        pin=nil; touching=true; lastTouch=now
        lastCarry=floor((m.value+0.5)/12); lastDetent=M.rounded(m.value)*1000+M.rounded(h.value)
        wake()
    }
    func begin(left:Bool) {
        takeOver()
        if open.value > 0.05 { close(); return }
        held=left ? 1 : 2; start=left ? h.value : m.value
        if left { h.set(h.value) } else { m.set(m.value) }
        UIImpactFeedbackGenerator(style:.light).impactOccurred(); publish()
    }
    func drag(translation:Double) {
        guard held != 0 else { return }
        let per=(held == 1 ? 155.0 : 150)*Double.pi*18/180
        if held == 1 { h.set(start+translation/per) } else { m.set(start+translation/per) }
        reactions(); publish()
    }
    func end(velocity:Double) {
        if held != 0 {
            let per=(held == 1 ? 155.0 : 150)*Double.pi*18/180, v=max(-14, min(14, velocity/per))
            if held == 1 { h.animate(to:M.rounded(h.value+v*0.3), at:now, spring:.detent, velocity:v, reduced:reduced) }
            else { m.animate(to:M.rounded(m.value+v*0.3), at:now, spring:.detent, velocity:v, reduced:reduced) }
        }
        release()
    }
    func release() { touching=false; held=0; lastTouch=now; wake() }
    private func reactions() {
        guard !homing else { lastCarry=floor((m.value+0.5)/12); return }
        let detent=M.rounded(m.value)*1000+M.rounded(h.value)
        if detent != lastDetent && now-lastTick >= 0.1 { UISelectionFeedbackGenerator().selectionChanged(); lastTick=now }
        lastDetent=detent
        let carry=floor((m.value+0.5)/12)
        if carry != lastCarry {
            h.animate(to:M.rounded(h.value)+carry-lastCarry, at:now, spring:.weighty, reduced:reduced)
            UIImpactFeedbackGenerator(style:.rigid).impactOccurred()
        }
        lastCarry=carry
    }
    private func close() {
        open.animate(to:0, at:now, spring:.glide, reduced:reduced)
        fill.timing(to:0, at:now, duration:0.5, reduced:reduced)
    }
    func confirm() {
        takeOver()
        if open.value > 0.5 { close() }
        else {
            open.animate(to:1, at:now, spring:.glide, reduced:reduced)
            fill.timing(to:1, at:now, duration:0.7, reduced:reduced)
        }
        if open.value < 0.5 { successAt=now+0.65 }
        release(); publish()
    }
}
