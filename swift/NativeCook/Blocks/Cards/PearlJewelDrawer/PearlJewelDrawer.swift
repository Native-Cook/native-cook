import SwiftUI
import UIKit

struct PearlJewelDrawer: View {
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
    private let accent = Color(hex: 0x7C2544)
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
                    Color(hex: 0xECEBE6)
                    HStack {
                        Image(systemName: "square.stack").font(.system(size: 15,weight: .medium))
                        Text("FORME / LITTLE TREASURES").font(.system(size: 10,weight: .semibold)).tracking(1.8)
                        Spacer()
                        Text("SILVER").font(.system(size: 10,design: .monospaced)).opacity(0.55)
                    }.frame(width: 337).position(x:196.5,y:32)
                    Text("Keep the\nlittle things.").font(.system(size:38,weight:.semibold,design: .serif)).tracking(-1.2)
                        .multilineTextAlignment(.center).lineSpacing(-1).frame(width:350).position(x:196.5,y:118)
                    Text("A quiet place for your favorites.").font(.system(size:13)).opacity(0.6).position(x:196.5,y:186)
                    artwork(p).frame(width:337,height:330).contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:1).updating($gestureActive) { _,active,_ in active=true }.onChanged { value in
                            if !touching { start=pose(.now);touching=true;detent=false }
                            let next = min(1,max(0,start + -value.translation.height / (180*scale)))
                            manual=next
                            if next > 0.85 && !detent { detent=true;UIImpactFeedbackGenerator(style:.light).impactOccurred() }
                        }.onEnded { _ in touching=false;release = .now })
                        .accessibilityElement(children:.ignore).accessibilityLabel("Pearl Jewel Drawer")
                        .accessibilityValue(p > 0.7 ? "Three silver treasures revealed":"Lid closed")
                        .accessibilityHint("Drag the lid upward")
                        .accessibilityAdjustableAction { direction in manual=direction == .increment ? 1:0;release = .now }
                        .position(x:196.5,y:385)
                    detail.position(x:196.5,y:591)
                    Button { activate() } label: {
                        HStack(spacing:9) { Image(systemName: activated ? "checkmark":"arrow.up.right");Text(activated ? "Reveal collection" : "Open keepsake").fontWeight(.semibold) }
                            .font(.system(size:15)).frame(width:337,height:54)
                            .background(accent,in:RoundedRectangle(cornerRadius:18))
                            .foregroundStyle(Color.white)
                    }.buttonStyle(PearlJewelDrawerPressStyle(reduced: reduced)).position(x:196.5,y:680)
                    Text("DRAG THE LID UPWARD").font(.system(size:9,weight:.medium)).tracking(1.6).opacity(0.45).position(x:196.5,y:735)
                }.foregroundStyle(ink).frame(width:393,height:760,alignment:.topLeading)
                    .scaleEffect(scale).frame(width:geo.size.width,height:geo.size.height)
            }
        }.background(Color(hex:0xECEBE6).ignoresSafeArea()).preferredColorScheme(.light)
        .task(id:release) {
            guard manual != nil,!touching else { return }
            do { try await Task.sleep(for:.seconds(2.45)) } catch { return }
            guard !touching else { return };manual=nil;epoch = .now
        }
        .onChange(of:gestureActive) { _,active in if !active && touching { touching=false;release = .now } }
        .onChange(of:scenePhase) { _,value in if value == .active { manual=nil;touching=false;epoch = .now } }
        .onChange(of:reduced) { _,_ in manual=nil;touching=false;epoch = .now }
    }
    private func activate() { activated=true; manual=1;release = .now;UISelectionFeedbackGenerator().selectionChanged() }
    private func label(_ text:String,at point:CGPoint,size:CGFloat,color:Color,context:GraphicsContext,weight:Font.Weight = .regular) {
        context.draw(Text(text).font(.system(size:size,weight:weight)).foregroundStyle(color),at:point)
    }
    
    private func artwork(_ p:Double) -> some View {
        Canvas { c,_ in
            c.translateBy(x:30.3,y:49)
            c.scaleBy(x:0.82,y:0.82)
            let silver=Gradient(colors:[Color(hex:0xFEFEFA),Color(hex:0xB7BAB5),Color(hex:0xECEDE7)])
            let base=Path(roundedRect:CGRect(x:65,y:61,width:207,height:258),cornerRadius:32)
            var tray=c;tray.addFilter(.shadow(color:.black.opacity(0.2),radius:18,x:0,y:12));tray.addFilter(.shadow(color:.black.opacity(0.25),radius:2,x:0,y:2));tray.fill(base,with:.linearGradient(silver,startPoint:CGPoint(x:65,y:70),endPoint:CGPoint(x:272,y:305)))
            c.stroke(base,with:.color(.white.opacity(0.95)),lineWidth:1.5)
            let interior=Path(roundedRect:CGRect(x:76,y:72,width:185,height:234),cornerRadius:25)
            c.fill(interior,with:.linearGradient(Gradient(colors:[Color(hex:0x565A53),Color(hex:0x858B7F)]),startPoint:CGPoint(x:76,y:72),endPoint:CGPoint(x:261,y:306)))
            var jewels=c;jewels.translateBy(x:0,y:-5*p)
            let hoop=Path(ellipseIn:CGRect(x:105,y:254,width:44,height:44));jewels.stroke(hoop,with:.color(Color(hex:0xDFE2DA)),lineWidth:3);jewels.stroke(hoop,with:.color(.white.opacity(0.7)),lineWidth:1)
            let pendant=Path(ellipseIn:CGRect(x:164,y:222,width:38,height:38));jewels.stroke(pendant,with:.color(Color(hex:0xECEEE4)),lineWidth:3)
            for i in 0..<16 { let a=Double(i)*2*Double.pi/16;let x=183+cos(a)*26,y=241+sin(a)*26;var leaf=jewels;leaf.translateBy(x:x,y:y);leaf.rotate(by:.radians(a));leaf.fill(Path(ellipseIn:CGRect(x:-5,y:-2,width:10,height:4)),with:.linearGradient(silver,startPoint:CGPoint(x:-5,y:0),endPoint:CGPoint(x:5,y:0))) }
            for point in [CGPoint(x:214,y:279),CGPoint(x:231,y:287)] { jewels.fill(Path(ellipseIn:CGRect(x:point.x-6,y:point.y-6,width:12,height:12)),with:.radialGradient(Gradient(colors:[.white,Color(hex:0xBEC3BA),Color(hex:0x737A70)]),center:CGPoint(x:point.x-2,y:point.y-2),startRadius:0,endRadius:9)) }
            var lid=c;lid.translateBy(x:168.5,y:190-108*p);lid.rotate(by:.degrees(4*p));lid.translateBy(x:-168.5,y:-190)
            lid.addFilter(.shadow(color:.black.opacity(0.18),radius:4+10*p,x:2,y:4+8*p))
            lid.fill(base,with:.linearGradient(silver,startPoint:CGPoint(x:62,y:61),endPoint:CGPoint(x:280,y:319)))
            var brushed=lid;brushed.clip(to:base)
            for i in 0..<53 { var line=Path();line.move(to:CGPoint(x:65,y:63+Double(i)*5));line.addLine(to:CGPoint(x:272,y:60+Double(i)*5));brushed.stroke(line,with:.color(.white.opacity(i%2==0 ? 0.12:0.04)),lineWidth:0.7) }
            lid.stroke(base,with:.color(.white.opacity(0.9)),lineWidth:1.5)
            lid.fill(Path(ellipseIn:CGRect(x:130.5,y:152,width:76,height:76)),with:.color(accent))
            lid.stroke(Path(ellipseIn:CGRect(x:151,y:172,width:34,height:34)),with:.color(Color(hex:0x4D172A)),lineWidth:6)
            label("FORME",at:CGPoint(x:168.5,y:263),size:9,color:ink.opacity(0.6),context:lid,weight:.medium)
        }
    }

    private var detail: some View { HStack(spacing:24) { VStack(spacing:6) { Text("03").font(.system(size:22,design:.serif));Text("TREASURES").font(.system(size:8)).tracking(1.5) };Rectangle().fill(ink.opacity(0.15)).frame(width:1,height:33);VStack(spacing:6) { Text("Silver").font(.system(size:22,design:.serif));Text("COLLECTION").font(.system(size:8)).tracking(1.5) } } }
}

private struct PearlJewelDrawerPressStyle: ButtonStyle {
    let reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed && !reduced ? 0.97:1)
            .opacity(configuration.isPressed ? 0.8:1)
            .animation(.easeOut(duration:0.13),value:configuration.isPressed)
    }
}
