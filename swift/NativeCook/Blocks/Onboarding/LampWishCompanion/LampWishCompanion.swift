import SwiftUI
import UIKit

struct LampWishCompanion: View {
    static let loop = 8.0
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var manual: Double? = nil
    @GestureState private var gestureActive = false
    @State private var touching = false
    @State private var start = 0.0
    @State private var release = Date.distantPast
    @State private var wished = false
    @State private var thresholdSent = false
    private let ink = Color(hex: 0x6A3D28)
    private func smooth(_ x: Double) -> Double { let u = min(1, max(0, x)); return u*u*(3-2*u) }
    private func pose(_ date: Date) -> Double {
        if reduced { return 0 }
        if let manual { return touching ? manual : manual * (1-smooth((date.timeIntervalSince(release)-2)/0.45)) }
        let t = previewTime ?? (autoPlay ? max(0,date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop) : 0)
        return smooth((t-1.5)/1.7)*(1-smooth((t-4.7)/1.8))
    }
    var body: some View {
        GeometryReader { geo in
            let scale = min(geo.size.width/393, geo.size.height/760)
            TimelineView(.animation(paused: reduced || scenePhase != .active || previewTime != nil || (!autoPlay && manual == nil))) { timeline in
                let p = pose(timeline.date)
                ZStack(alignment: .topLeading) {
                    village.frame(width: 393, height: 300).offset(y: 55)
                    HStack {
                        Text("DAILY LITTLE WONDER").font(.system(size: 11, weight: .bold)).tracking(2)
                        Spacer()
                        Text("01 / 07").font(.system(size: 11, design: .monospaced))
                    }.frame(width: 337).position(x: 196.5, y: 30)
                    cat(p).frame(width: 220, height: 240).position(x: 196.5, y: 295 - 30*p)
                    Text("A little wish,\njust for you.").font(.system(size: 30, weight: .bold, design: .rounded))
                        .multilineTextAlignment(.center).frame(width: 340).position(x: 196.5, y: 455)
                    Text(wished ? "A bright little moment is yours." : "Meet Miso. Every day holds a surprise.")
                        .font(.system(size: 14, design: .rounded)).frame(width: 350).position(x: 196.5, y: 510)
                    lamp(p).frame(width: 175, height: 120)
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance: 1).updating($gestureActive) { _, active, _ in active = true }.onChanged { value in
                            if !touching { start = pose(.now); touching = true; thresholdSent = false }
                            manual = min(1,max(0,start + abs(value.translation.width)/120))
                            if (manual ?? 0) > 0.8 {
                                wished = true
                                if !thresholdSent { thresholdSent = true; UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                            }
                        }.onEnded { _ in touching = false; release = .now })
                        .accessibilityElement(children: .ignore)
                        .accessibilityIdentifier("lamp-wish-lamp")
                        .accessibilityLabel("Wish lamp")
                        .accessibilityHint("Rub horizontally to make a wish")
                        .accessibilityAction { makeWish() }
                        .position(x: 196.5, y: 595)
                    Button(action: makeWish) {
                        HStack(spacing: 9) { Image(systemName: "sparkle"); Text(wished ? "Wish made" : "Rub to make a wish") }
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .frame(width: 305,height: 54).background(Color(hex: 0xFFF8E8),in: Capsule())
                    }.buttonStyle(.plain).position(x: 196.5,y: 698)
                    Text("ONE SMALL MOMENT. ALL YOURS.").font(.system(size: 9,weight: .bold)).tracking(1.8).opacity(0.55).position(x: 196.5,y: 746)
                }.foregroundStyle(ink).frame(width: 393,height: 760,alignment: .topLeading)
                    .scaleEffect(scale).frame(width: geo.size.width,height: geo.size.height)
            }
        }.background(Color(hex: 0xF2D49B).ignoresSafeArea()).preferredColorScheme(.light)
        .task(id: release) {
            guard manual != nil, !touching else { return }
            do { try await Task.sleep(for: .seconds(2.45)) } catch { return }
            guard !touching else { return }; manual = nil; epoch = .now
        }
        .onChange(of: gestureActive) { _, active in
            if !active && touching { touching = false; release = .now }
        }
        .onChange(of: scenePhase) { _, value in if value == .active { manual = nil; touching = false; epoch = .now } }
        .onChange(of: reduced) { _, _ in manual = nil; touching = false; epoch = .now }
    }
    private func makeWish() { wished = true; manual = 1; release = .now; UIImpactFeedbackGenerator(style: .light).impactOccurred() }
    private var village: some View {
        Canvas { c,_ in
            c.fill(Path(CGRect(x:0,y:0,width:393,height:240)),with:.color(Color(hex:0xFAF6CC)))
            for i in 0..<8 {
                let x = Double(i)*59-20
                c.fill(Path(ellipseIn:CGRect(x:x,y:140-Double(i%3)*15,width:82,height:100)),with:.color(Color(hex:0xA5C878)))
            }
            c.fill(Path(roundedRect:CGRect(x:32,y:88,width:95,height:146),cornerRadius:4),with:.color(Color(hex:0xC98D4A)))
            c.fill(Path(CGRect(x:42,y:104,width:75,height:45)),with:.color(Color(hex:0xA5DFD9)))
            c.fill(Path(CGRect(x:28,y:156,width:104,height:17)),with:.color(Color(hex:0xF4C147)))
            for i in 0..<5 { c.fill(Path(CGRect(x:Double(28+i*21),y:156,width:10,height:17)),with:.color(Color(hex:0xB87435))) }
            c.draw(Text("COFFEE").font(.system(size:13,weight:.black,design:.rounded)).foregroundStyle(Color(hex:0x805B65)),at:CGPoint(x:80,y:76))
            c.fill(Path(roundedRect:CGRect(x:282,y:44,width:62,height:190),cornerRadius:7),with:.color(Color(hex:0x9DCED4)))
            for i in 0..<8 { c.fill(Path(CGRect(x:290,y:Double(64+i*20),width:46,height:9)),with:.color(Color(hex:0xC3E5DC))) }
            var roof=Path();roof.move(to:CGPoint(x:270,y:48));roof.addLine(to:CGPoint(x:313,y:4));roof.addLine(to:CGPoint(x:355,y:48));roof.closeSubpath()
            c.fill(roof,with:.color(Color(hex:0xD9E4DE)))
            c.fill(Path(CGRect(x:0,y:229,width:393,height:22)),with:.color(Color(hex:0xFFF8E8)))
            for i in 0..<7 { c.fill(Path(roundedRect:CGRect(x:Double((i*79)%370),y:265+Double(i%2)*18,width:17,height:6),cornerRadius:3),with:.color(Color(hex:0xE9C589))) }
        }.accessibilityHidden(true)
    }
    private func cat(_ p: Double) -> some View {
        ZStack {
            Ellipse().fill(ink.opacity(0.1)).frame(width:145,height:22).offset(y:112+p*20)
            Capsule().fill(Color(hex:0xFFAF30)).frame(width:85,height:36).rotationEffect(.degrees(-35-p*28)).offset(x:80,y:38)
            Capsule().fill(Color(hex:0xFFF8E8)).frame(width:29,height:36).rotationEffect(.degrees(-35-p*28)).offset(x:111,y:17)
            Capsule().fill(Color(hex:0xFFF8E8)).frame(width:38,height:64).rotationEffect(.degrees(12+p*18)).offset(x:-39,y:91)
            Capsule().fill(Color(hex:0xFFF8E8)).frame(width:38,height:64).rotationEffect(.degrees(-12-p*18)).offset(x:39,y:91)
            Ellipse().fill(Color(hex:0xFFAF30)).frame(width:146,height:145).offset(y:39)
            Ellipse().fill(Color(hex:0xFFE1AB)).frame(width:76,height:88).offset(y:44)
            Capsule().fill(Color(hex:0xFFF8E8)).frame(width:36,height:60).rotationEffect(.degrees(35+p*35)).offset(x:-75,y:35-p*12)
            Capsule().fill(Color(hex:0xFFF8E8)).frame(width:36,height:60).rotationEffect(.degrees(-35-p*35)).offset(x:75,y:35-p*12)
            RoundedRectangle(cornerRadius:16).fill(Color(hex:0xFFAF30)).frame(width:65,height:76).rotationEffect(.degrees(-22)).offset(x:-58,y:-91)
            RoundedRectangle(cornerRadius:16).fill(Color(hex:0xFFAF30)).frame(width:65,height:76).rotationEffect(.degrees(22)).offset(x:58,y:-91)
            Ellipse().fill(Color(hex:0xFFF8E8)).frame(width:29,height:43).rotationEffect(.degrees(-22)).offset(x:-58,y:-98)
            Ellipse().fill(Color(hex:0xFFF8E8)).frame(width:29,height:43).rotationEffect(.degrees(22)).offset(x:58,y:-98)
            Ellipse().fill(Color(hex:0xFFAF30)).frame(width:190,height:148).offset(y:-40)
            Ellipse().fill(Color(hex:0xFFF8E8)).frame(width:180,height:89).offset(y:-3)
            ForEach(0..<3,id:\.self) { i in
                Capsule().fill(Color(hex:0xF58B28)).frame(width:18,height:28).rotationEffect(.degrees(Double(i-1)*15)).offset(x:Double(i-1)*27,y:-102)
            }
            HStack(spacing:40) { ForEach(0..<2,id:\.self) { _ in Capsule().fill(ink).frame(width:13,height:15-p*8) } }.offset(y:-44)
            Ellipse().fill(Color(hex:0xE37854)).frame(width:18,height:12).offset(y:-28)
            Ellipse().fill(Color(hex:0xB8482D)).frame(width:37+p*10,height:20+p*9).offset(y:-8)
            Capsule().fill(Color(hex:0x78D6DB)).frame(width:120,height:15).offset(y:33)
            Circle().fill(Color(hex:0xA5EBE9)).frame(width:28,height:28).offset(y:36)
            Circle().fill(.white.opacity(0.7)).frame(width:7,height:7).offset(x:-6,y:30)
        }.scaleEffect(x:1+0.09*sin(p*Double.pi*2),y:1-0.09*sin(p*Double.pi*2),anchor:.bottom)
            .rotationEffect(.degrees(-3+p*6)).accessibilityHidden(true)
    }
    private func lamp(_ p: Double) -> some View {
        ZStack {
            Ellipse().fill(ink.opacity(0.1)).frame(width:170,height:28).offset(y:50)
            ForEach(0..<8,id:\.self) { i in
                Image(systemName:"sparkle").font(.system(size:Double(10+i%3*4))).foregroundStyle(Color(hex:0xFFF8E8))
                    .offset(x:cos(Double(i)*Double.pi/4)*(50+p*40),y:sin(Double(i)*Double.pi/4)*(30+p*42)-20)
                    .opacity(p).scaleEffect(0.5+p*0.5)
            }
            Ellipse().stroke(Color(hex:0xE7C93E),lineWidth:10).frame(width:45,height:45).rotationEffect(.degrees(-25)).offset(x:-57,y:-8)
            Capsule().fill(Color(hex:0xE7C93E)).frame(width:62,height:13).offset(y:40)
            Ellipse().fill(Color(hex:0xFFE64F)).frame(width:105,height:66)
            Path { path in path.move(to:CGPoint(x:113,y:45));path.addQuadCurve(to:CGPoint(x:165,y:40),control:CGPoint(x:148,y:58));path.addLine(to:CGPoint(x:170,y:48));path.addQuadCurve(to:CGPoint(x:123,y:75),control:CGPoint(x:152,y:72));path.closeSubpath() }.fill(Color(hex:0xFFE64F))
            Ellipse().fill(Color(hex:0xFFF18B)).frame(width:50,height:15).offset(y:-31)
            Capsule().fill(Color(hex:0x78D6DB)).frame(width:16,height:21).offset(y:-41)
            Image(systemName:"heart.fill").font(.system(size:30)).foregroundStyle(Color(hex:0x78D6DB)).offset(y:5)
        }.rotationEffect(.degrees(sin(p * Double.pi) * -8))
    }
}
