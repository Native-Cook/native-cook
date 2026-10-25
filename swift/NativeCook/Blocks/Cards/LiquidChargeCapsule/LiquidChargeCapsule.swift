import SwiftUI
import UIKit

/// A glass cell whose liquid and submerged bolt share one deterministic wave field.
struct LiquidChargeCapsule: View {
    static let loop = 7.5
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @State private var epoch = Date()
    @State private var lastTouch: Date? = nil
    @State private var drag = CGSize.zero
    @State private var held = false
    @State private var touchStarted = Date()
    @State private var touchPhase = 0.0
    @State private var returnPhase = 0.0
    @GestureState private var panActive = false
    @State private var visible = false

    private func smooth(_ v: Double) -> Double { let x = min(1, max(0, v)); return x*x*(3-2*x) }
    var body: some View {
        GeometryReader { geo in
            let s = geo.size.width / 393
            TimelineView(.animation(paused: !visible || scene != .active || reduced || previewTime != nil || (!autoPlay && lastTouch == nil))) { clock in
                let age = lastTouch.map { clock.date.timeIntervalSince($0) } ?? 99
                let returning = lastTouch != nil && !held
                let gain = held ? 1 : 1 - smooth((age-2)/0.55)
                let t = reduced ? 0 : (previewTime ?? max(0,clock.date.timeIntervalSince(epoch))).truncatingRemainder(dividingBy: Self.loop)
                let progress = smooth((t-0.75)/6.0)
                let automaticPhase = progress * .pi*2
                let livePhase = touchPhase + clock.date.timeIntervalSince(touchStarted) * .pi*2/Self.loop
                let phase = reduced ? 0 : lastTouch != nil ? livePhase*gain+returnPhase*(1-gain) : automaticPhase
                let envelope = 1.0
                ZStack {
                    Color.black
                    LiquidChargeCapsuleArt(envelope: envelope, phase: phase, tilt: Double(drag.width/s)*gain*0.13,
                                           level: Double(drag.height/s)*gain*0.16)
                        .frame(width: 138*s, height: 259*s)
                        .position(x: geo.size.width/2 - 2.5*s, y: geo.size.height/2 + 19*s)
                        .accessibilityLabel("Charge reserve")
                        .accessibilityValue("Half full")
                        .accessibilityHint("Drag to stir the liquid")
                }
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 3).updating($panActive) { _, active, _ in active = true }.onChanged { value in
                    if !held { touchPhase = phase; touchStarted = Date(); lastTouch = Date(); held = true }
                    drag = CGSize(width: min(110*s,max(-110*s,value.translation.width)), height: min(130*s,max(-130*s,value.translation.height)))
                }.onEnded { _ in
                    finishTouch()
                }, including: reduced || previewTime != nil ? .none : .all)
                .onChange(of: panActive) { _, active in if !active && held { finishTouch() } }
                .task(id: returning && age > 2.55) {
                    if returning && age > 2.55 { lastTouch = nil; drag = .zero; epoch = Date() }
                }
            }
        }
        .ignoresSafeArea().preferredColorScheme(.dark)
        .onAppear { visible = true; epoch = Date() }
        .onDisappear { visible = false; lastTouch = nil; held = false }
        .onChange(of: scene) { _, value in if value == .active { epoch = Date() } else { lastTouch = nil; held = false; drag = .zero } }
        .onChange(of: reduced) { _, _ in epoch = Date(); lastTouch = nil; held = false; drag = .zero }
    }
    private func finishTouch() {
        guard held else { return }
        let now = Date()
        let future = touchPhase + (now.timeIntervalSince(touchStarted)+2.55) * .pi*2/Self.loop
        returnPhase = ceil(future/(.pi*2)) * .pi*2
        lastTouch = now; held = false
        if !reduced { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    }
}

private struct LiquidChargeCapsuleArt: View {
    var envelope: Double
    var phase: Double
    var tilt: Double
    var level: Double
    private func col(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, opacity: alpha)
    }
    private static let glass = UIImage(named: "LiquidChargeCapsule-glass-shell.png")
    private static let metal = UIImage(named: "LiquidChargeCapsule-metal-bolt.png")
    var body: some View {
        Canvas { context, size in
            let sx = size.width/140, sy = size.height/262
            context.scaleBy(x: sx, y: sy)
            let shell = Path(roundedRect: CGRect(x: 1, y: 1, width: 138, height: 260), cornerRadius: 69)
            let inside = Path(roundedRect: CGRect(x: 6, y: 6, width: 128, height: 250), cornerRadius: 64)
            context.fill(shell, with: .linearGradient(Gradient(colors: [col(0x121715,0.5), .black, col(0x111713,0.24)]), startPoint: .zero, endPoint: CGPoint(x: 140,y: 262)))
            context.stroke(shell, with: .color(col(0xC6D4CC,0.17)), lineWidth: 0.85)
            var inner = context
            inner.clip(to: inside)
            func surface(_ x: Double) -> Double {
                let u = (x-6)/128
                return 121 + level + 2.3*sin(u * .pi*2) + envelope*(-14*sin(u * .pi*2+phase)+3.2*sin(u * .pi*4-phase*2)) + tilt*(u-0.5)
            }
            var water = Path()
            water.move(to: CGPoint(x: 4,y: surface(4)))
            for n in 1...66 { let x = Double(4+n*2); water.addLine(to: CGPoint(x: x,y: surface(x))) }
            water.addLine(to: CGPoint(x: 136,y: 262)); water.addLine(to: CGPoint(x: 4,y: 262)); water.closeSubpath()
            inner.fill(water, with: .linearGradient(Gradient(stops: [.init(color:col(0x78FF00),location:0),.init(color:col(0x03CA08),location:0.18),.init(color:col(0x00530C),location:0.40),.init(color:col(0x000301),location:1)]),startPoint: CGPoint(x:70,y:111+level),endPoint: CGPoint(x:70,y:260)))
            var top = Path()
            top.move(to: CGPoint(x: 5,y: surface(5)))
            for n in 1...65 { let x = Double(5+n*2); top.addLine(to: CGPoint(x:x,y:surface(x))) }
            for n in stride(from:65,through:0,by:-1) { let x=Double(5+n*2); top.addLine(to:CGPoint(x:x,y:141 + level + 8*sin((x-5)/130 * .pi*2+phase) + tilt*((x-5)/130-0.5))) }
            top.closeSubpath()
            inner.fill(top,with:.linearGradient(Gradient(colors:[col(0x64FA00),col(0xFFFF0A),col(0xD9FF00),col(0x5FFC00)]),startPoint:CGPoint(x:14,y:110),endPoint:CGPoint(x:128,y:139)))
            if let metal = Self.metal {
                inner.draw(Image(uiImage:metal),in:CGRect(x:-8.15,y:3.9,width:154.9,height:280.13))
            }
            var submerged = inner
            var frontWater = Path()
            frontWater.move(to: CGPoint(x: 4,y: 141+level))
            for n in 1...66 { let x=Double(4+n*2); let u=(x-5)/130; frontWater.addLine(to:CGPoint(x:x,y:141+level+8*sin(u * .pi*2+phase)+tilt*(u-0.5))) }
            frontWater.addLine(to:CGPoint(x:136,y:262)); frontWater.addLine(to:CGPoint(x:4,y:262)); frontWater.closeSubpath()
            submerged.clip(to:frontWater)
            submerged.translateBy(x:envelope*sin(phase)*1.2,y:0)
            if let metal = Self.metal {
                var refracted = submerged
                refracted.addFilter(.colorMultiply(col(0xB5FFD0)))
                refracted.draw(Image(uiImage:metal),in:CGRect(x:-8.15,y:3.9,width:154.9,height:280.13))
            }
            submerged.fill(water,with:.linearGradient(Gradient(colors:[col(0x03F000,0.1),col(0x001703,0.5)]),startPoint:CGPoint(x:70,y:120),endPoint:CGPoint(x:70,y:262)))
            for i in 0..<7 {
                let hash = (sin(Double(i+1)*12.9898)*43758.5453).truncatingRemainder(dividingBy:1)
                let x = 25+abs(hash)*70
                let cycle = phase/(.pi*2)+Double(i)*0.173
                let rise = cycle-floor(cycle)
                let alpha = min(1,rise/0.12)*min(1,(1-rise)/0.22)
                let y = 212-rise*67
                let r = 1.1+Double(i%3)*0.55
                inner.fill(Path(ellipseIn:CGRect(x:x,y:y,width:r*2,height:r*2)),with:.radialGradient(Gradient(colors:[col(0x68FF40,envelope*alpha*0.35),col(0x00A313,0)]),center:CGPoint(x:x+r,y:y+r),startRadius:0,endRadius:r*1.4))
            }
            if let glass = Self.glass {
                context.draw(Image(uiImage:glass),in:CGRect(x:-27.2,y:-26.4,width:196.5,height:317))
            }
            let left = Path(roundedRect:CGRect(x:3,y:50,width:4,height:159),cornerRadius:2)
            context.fill(left,with:.linearGradient(Gradient(colors:[col(0xE3F3E8,0),col(0xB1C6B8,0.18),col(0x44E84E,0.27),col(0xA5C7B0,0)]),startPoint:CGPoint(x:4,y:50),endPoint:CGPoint(x:4,y:209)))
            context.stroke(Path(roundedRect:CGRect(x:7,y:7,width:126,height:248),cornerRadius:63),with:.linearGradient(Gradient(colors:[col(0xF3FFF3,0.05),.clear,col(0x1DFB20,0.24),.clear]),startPoint:CGPoint(x:0,y:0),endPoint:CGPoint(x:140,y:262)),lineWidth:1)

        }
    }
}
