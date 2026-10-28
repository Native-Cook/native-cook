import SwiftUI
import UIKit

struct MothLinkBloom: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var manual: Double? = nil
    @State private var touching = false
    @GestureState private var gestureActive = false
    @State private var start = 0.0
    @State private var release = Date.distantPast
    @State private var activated = false
    @State private var selection = 0
    @State private var detent = false
    private let ink = Color(hex: 0x272832)
    private let accent = Color(hex: 0x6655F5)
    private func ease(_ value: Double) -> Double { let u = min(1, max(0, value)); return u*u*(3-2*u) }
    private func pose(_ date: Date) -> Double {
        if reduced { return 1 }
        if let manual { return touching ? manual : manual*(1-ease((date.timeIntervalSince(release)-2)/0.45)) }
        let t = previewTime ?? (autoPlay ? max(0,date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop) : 0)
        return ease((t-1.4)/1.8)*(1-ease((t-4.8)/1.8))
    }
    var body: some View {
        GeometryReader { geo in
            let scale = min(geo.size.width/393, geo.size.height/760)
            TimelineView(.animation(paused: reduced || scenePhase != .active || previewTime != nil || (!autoPlay && manual == nil))) { timeline in
                let p = pose(timeline.date)
                ZStack(alignment: .topLeading) {
                    Color(hex: 0xF6F5FF)
                    HStack {
                        Image(systemName: "network").font(.system(size: 15,weight: .medium))
                        Text("LUMA / PRIVATE LINK").font(.system(size: 10,weight: .semibold)).tracking(1.8)
                        Spacer()
                        Text("PRIVATE").font(.system(size: 10,design: .monospaced)).opacity(0.55)
                    }.frame(width: 337).position(x:196.5,y:32)
                    Text("A quieter\nconnection.").font(.system(size:38,weight:.semibold,design: .default)).tracking(-1.2)
                        .multilineTextAlignment(.center).lineSpacing(-1).frame(width:350).position(x:196.5,y:118)
                    Text("One touch. A little more peace.").font(.system(size:13)).opacity(0.6).position(x:196.5,y:186)
                    artwork(p).frame(width:337,height:330).contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:1).updating($gestureActive) { _,active,_ in active=true }.onChanged { value in
                            if !touching { start=pose(.now);touching=true;detent=false }
                            let next = min(1,max(0,start + -value.translation.height / (180*scale)))
                            manual=next
                            if next > 0.85 && !detent { detent=true;UIImpactFeedbackGenerator(style:.light).impactOccurred() }
                        }.onEnded { _ in touching=false;release = .now })
                        .accessibilityElement(children:.ignore).accessibilityLabel("Moth Link Bloom")
                        .accessibilityValue(p > 0.7 ? "Wings spread":"Wings folded")
                        .accessibilityHint("Drag upward to spread wings")
                        .accessibilityAdjustableAction { direction in manual=direction == .increment ? 1:0;release = .now }
                        .position(x:196.5,y:385)
                    detail.position(x:196.5,y:591)
                    Button { activate() } label: {
                        HStack(spacing:9) { Image(systemName: activated ? "checkmark":"arrow.up.right");Text(activated ? "Disconnect" : "Connect").fontWeight(.semibold) }
                            .font(.system(size:15)).frame(width:337,height:54)
                            .background(accent,in:RoundedRectangle(cornerRadius:18))
                            .foregroundStyle(Color.white)
                    }.buttonStyle(MothLinkBloomPressStyle(reduced: reduced)).position(x:196.5,y:680)
                    Text("DRAG UPWARD TO SPREAD WINGS").font(.system(size:9,weight:.medium)).tracking(1.6).opacity(0.45).position(x:196.5,y:735)
                }.foregroundStyle(ink).frame(width:393,height:760,alignment:.topLeading)
                    .scaleEffect(scale).frame(width:geo.size.width,height:geo.size.height)
            }
        }.background(Color(hex:0xF6F5FF).ignoresSafeArea()).preferredColorScheme(.light)
        .task(id:release) {
            guard manual != nil,!touching else { return }
            do { try await Task.sleep(for:.seconds(2.45)) } catch { return }
            guard !touching else { return };manual=nil;epoch = .now
        }
        .onChange(of:gestureActive) { _,active in if !active && touching { touching=false;release = .now } }
        .onChange(of:scenePhase) { _,value in if value == .active { manual=nil;touching=false;epoch = .now } }
        .onChange(of:reduced) { _,_ in manual=nil;touching=false;epoch = .now }
    }
    private func activate() { activated.toggle(); manual=activated ? 1:0;release = .now;UISelectionFeedbackGenerator().selectionChanged() }
    private func label(_ text:String,at point:CGPoint,size:CGFloat,color:Color,context:GraphicsContext,weight:Font.Weight = .regular) {
        context.draw(Text(text).font(.system(size:size,weight:weight)).foregroundStyle(color),at:point)
    }
    
    private func artwork(_ p:Double) -> some View {
        Canvas { c,_ in
            let center=CGPoint(x:168.5,y:155)
            c.fill(Path(ellipseIn:CGRect(x:39-p*9,y:26-p*9,width:259+p*18,height:259+p*18)),with:.radialGradient(Gradient(colors:[accent.opacity(0.17),accent.opacity(0)]),center:center,startRadius:80,endRadius:145))
            let disc=Path(ellipseIn:CGRect(x:63.5,y:50,width:210,height:210))
            c.addFilter(.shadow(color:accent.opacity(0.2),radius:14,x:0,y:12))
            c.fill(disc,with:.linearGradient(Gradient(colors:[Color(hex:0xA99BFF),accent,Color(hex:0x5145DC)]),startPoint:CGPoint(x:80,y:50),endPoint:CGPoint(x:255,y:260)))
            c.stroke(disc,with:.color(.white.opacity(0.65)),lineWidth:1.5)
            for side in [-1.0,1.0] {
                let spread=0.42+p*0.58
                var wing=Path();wing.move(to:center)
                wing.addCurve(to:CGPoint(x:center.x+side*70*spread,y:92-p*4),control1:CGPoint(x:center.x+side*22*spread,y:124),control2:CGPoint(x:center.x+side*53*spread,y:109))
                wing.addCurve(to:CGPoint(x:center.x+side*25*spread,y:174),control1:CGPoint(x:center.x+side*68*spread,y:157),control2:CGPoint(x:center.x+side*53*spread,y:170))
                wing.closeSubpath();c.fill(wing,with:.color(.white.opacity(0.95)))
                var lower=Path();lower.move(to:CGPoint(x:center.x,y:157))
                lower.addQuadCurve(to:CGPoint(x:center.x+side*48*spread,y:209),control:CGPoint(x:center.x+side*58*spread,y:178))
                lower.addQuadCurve(to:CGPoint(x:center.x,y:170),control:CGPoint(x:center.x+side*7*spread,y:204));lower.closeSubpath()
                c.fill(lower,with:.color(Color(hex:0xE7E1FF)))
                var vein=Path();vein.move(to:center);vein.addLine(to:CGPoint(x:center.x+side*54*spread,y:114));c.stroke(vein,with:.color(accent.opacity(0.25)),lineWidth:1)
                var antenna=Path();antenna.move(to:CGPoint(x:center.x,y:137));antenna.addQuadCurve(to:CGPoint(x:center.x+side*18,y:117),control:CGPoint(x:center.x+side*19,y:134));c.stroke(antenna,with:.color(.white),lineWidth:1.5)
            }
            c.fill(Path(roundedRect:CGRect(x:165.5,y:133,width:6,height:48),cornerRadius:3),with:.color(.white))
            label(activated ? "CONNECTED":"READY WHEN YOU ARE",at:CGPoint(x:168.5,y:304),size:10,color:accent,context:c,weight:.semibold)
        }
    }

    private var detail: some View { HStack(spacing:10) { ForEach(0..<2,id:\.self) { i in Button { selection=i;manual=pose(.now);release = .now;UISelectionFeedbackGenerator().selectionChanged() } label: { VStack(spacing:7) { Text(i==0 ? "North / 24 ms":"West / 38 ms").font(.system(size:12,weight:.semibold));Text(selection==i ? "SELECTED":"AVAILABLE").font(.system(size:8)).tracking(1.2).opacity(0.55) }.frame(width:160,height:64).background(selection==i ? accent.opacity(0.12):Color.white.opacity(0.7),in:RoundedRectangle(cornerRadius:18)) }.buttonStyle(.plain).accessibilityAddTraits(selection==i ? .isSelected:[]) } } }
}

private struct MothLinkBloomPressStyle: ButtonStyle {
    let reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed && !reduced ? 0.97:1)
            .opacity(configuration.isPressed ? 0.8:1)
            .animation(.easeOut(duration:0.13),value:configuration.isPressed)
    }
}
