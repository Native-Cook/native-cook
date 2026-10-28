import SwiftUI
import UIKit

struct TasselGiftCertificate: View {
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
    private let accent = Color(hex: 0x731C38)
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
                    Color(hex: 0xEEE8E1)
                    HStack {
                        Image(systemName: "gift").font(.system(size: 15,weight: .medium))
                        Text("ATELIER / A SMALL GIFT").font(.system(size: 10,weight: .semibold)).tracking(1.8)
                        Spacer()
                        Text("0148").font(.system(size: 10,design: .monospaced)).opacity(0.55)
                    }.frame(width: 337).position(x:196.5,y:32)
                    Text("Something\njust for you.").font(.system(size:38,weight:.semibold,design: .serif)).tracking(-1.2)
                        .multilineTextAlignment(.center).lineSpacing(-1).frame(width:350).position(x:196.5,y:118)
                    Text("A keepsake for a moment of your own.").font(.system(size:13)).opacity(0.6).position(x:196.5,y:186)
                    artwork(p).frame(width:337,height:330).contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance:1).updating($gestureActive) { _,active,_ in active=true }.onChanged { value in
                            if !touching { start=pose(.now);touching=true;detent=false }
                            let next = min(1,max(0,start + value.translation.width / (180*scale)))
                            manual=next
                            if next > 0.85 && !detent { detent=true;UIImpactFeedbackGenerator(style:.light).impactOccurred() }
                        }.onEnded { _ in touching=false;release = .now })
                        .accessibilityElement(children:.ignore).accessibilityLabel("Tassel Gift Certificate")
                        .accessibilityValue(p > 0.7 ? "Tassel fanned":"Tassel ready to pull")
                        .accessibilityHint("Drag sideways on the tassel")
                        .accessibilityAdjustableAction { direction in manual=direction == .increment ? 1:0;release = .now }
                        .position(x:196.5,y:385)
                    detail.position(x:196.5,y:591)
                    Button { activate() } label: {
                        HStack(spacing:9) { Image(systemName: activated ? "checkmark":"arrow.up.right");Text(activated ? "Saved to keepsakes" : "Save keepsake").fontWeight(.semibold) }
                            .font(.system(size:15)).frame(width:337,height:54)
                            .background(accent,in:RoundedRectangle(cornerRadius:18))
                            .foregroundStyle(Color.white)
                    }.buttonStyle(TasselGiftCertificatePressStyle(reduced: reduced)).position(x:196.5,y:680)
                    Text("DRAG SIDEWAYS ON THE TASSEL").font(.system(size:9,weight:.medium)).tracking(1.6).opacity(0.45).position(x:196.5,y:735)
                }.foregroundStyle(ink).frame(width:393,height:760,alignment:.topLeading)
                    .scaleEffect(scale).frame(width:geo.size.width,height:geo.size.height)
            }
        }.background(Color(hex:0xEEE8E1).ignoresSafeArea()).preferredColorScheme(.light)
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
            var backing=c;backing.translateBy(x:168,y:190);backing.rotate(by:.degrees(-7+p*1.5));backing.translateBy(x:-168,y:-190)
            backing.addFilter(.shadow(color:.black.opacity(0.13),radius:14,x:0,y:10))
            backing.fill(Path(roundedRect:CGRect(x:56,y:55,width:225,height:262),cornerRadius:3),with:.color(accent))
            var paper=c;paper.translateBy(x:168,y:190);paper.rotate(by:.degrees(2+p*1.5));paper.translateBy(x:-168,y:-190)
            let rect=Path(roundedRect:CGRect(x:58,y:75,width:220,height:233),cornerRadius:3)
            paper.fill(rect,with:.color(Color(hex:0xFFFCF5)));paper.stroke(rect,with:.color(Color.white.opacity(0.7)),lineWidth:1)
            for i in 0..<12 { paper.fill(Path(ellipseIn:CGRect(x:65+Double(i)*18,y:300,width:8,height:8)),with:.color(accent)) }
            label("GIFT",at:CGPoint(x:168,y:135),size:36,color:ink,context:paper,weight:.semibold)
            label("FOR A LITTLE MOMENT",at:CGPoint(x:168,y:167),size:9,color:accent,context:paper,weight:.semibold)
            label("TO     Someone wonderful",at:CGPoint(x:166,y:216),size:10,color:ink.opacity(0.7),context:paper)
            var rule=Path();rule.move(to:CGPoint(x:81,y:235));rule.addLine(to:CGPoint(x:254,y:235));paper.stroke(rule,with:.color(ink.opacity(0.15)),lineWidth:1)
            label("A keepsake, chosen with care.",at:CGPoint(x:168,y:262),size:9,color:ink.opacity(0.55),context:paper)
            label("CERTIFICATE     0148",at:CGPoint(x:168,y:289),size:8,color:accent,context:paper)
            let eye=CGPoint(x:243,y:82),knot=CGPoint(x:243+40*p,y:40-12*p)
            c.stroke(Path(ellipseIn:CGRect(x:236,y:75,width:14,height:14)),with:.color(Color(hex:0xA56C41)),lineWidth:3)
            var cord=Path();cord.move(to:eye);cord.addQuadCurve(to:knot,control:CGPoint(x:268+34*p,y:59+12*p));c.stroke(cord,with:.color(accent),style:StrokeStyle(lineWidth:4,lineCap:.round))
            for i in 0..<24 {
                let n=Double(i)-11.5,end=CGPoint(x:knot.x+n*(1.25+1.3*p),y:4+sin(Double(i)*0.7)*5)
                var thread=Path();thread.move(to:knot);thread.addQuadCurve(to:end,control:CGPoint(x:knot.x+n*0.3+19*p,y:19))
                c.stroke(thread,with:.color(i%3==0 ? Color(hex:0xB64460):accent),style:StrokeStyle(lineWidth:i%3==0 ? 1.3:0.85,lineCap:.round))
            }
            c.fill(Path(ellipseIn:CGRect(x:knot.x-5,y:knot.y-5,width:10,height:10)),with:.color(accent))
        }
    }

    private var detail: some View { VStack(spacing:8) { Text(activated ? "Saved to your keepsakes":"A small gesture. A lasting memory.").font(.system(size:17,design:.serif));Text("THOUGHTFULLY CHOSEN  /  NO. 0148").font(.system(size:8)).tracking(1.7).opacity(0.5) } }
}

private struct TasselGiftCertificatePressStyle: ButtonStyle {
    let reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed && !reduced ? 0.97:1)
            .opacity(configuration.isPressed ? 0.8:1)
            .animation(.easeOut(duration:0.13),value:configuration.isPressed)
    }
}
