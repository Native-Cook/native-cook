import SwiftUI
import UIKit

private struct ContactFileStackOutline: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: 16)); p.addQuadCurve(to: CGPoint(x: 12, y: 4), control: .zero)
        p.addLine(to: CGPoint(x: 80, y: 4)); p.addLine(to: CGPoint(x: 112, y: 28))
        p.addLine(to: CGPoint(x: r.width - 12, y: 28)); p.addQuadCurve(to: CGPoint(x: r.width, y: 40), control: CGPoint(x: r.width, y: 28))
        p.addLine(to: CGPoint(x: r.width - 8, y: r.height - 10)); p.addQuadCurve(to: CGPoint(x: r.width - 20, y: r.height), control: CGPoint(x: r.width - 8, y: r.height))
        p.addLine(to: CGPoint(x: 20, y: r.height)); p.addQuadCurve(to: CGPoint(x: 8, y: r.height - 10), control: CGPoint(x: 8, y: r.height))
        p.closeSubpath(); return p
    }
}
struct ContactFileStack: View {
    static let loop: Double = 9
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date()
    @State private var touched: Date? = nil
    @State private var isTouching = false
    @State private var manual = 0.0
    @State private var dragBase: Double? = nil
    @State private var releaseVelocity = 0.0
    @State private var tapRun = false
    private let names = ["Ari m.", "Arden p.", "Asa m.", "Aster n.", "Bryn", "Bowie p.", "Caro m.", "Cedar p.", "Cleo m.", "Cove p.", "Dara l.", "Demi p.", "Drew n.", "Eden r."]
    private let bg = Color(red: 0.816, green: 0.816, blue: 0.773)
    private let paper = Color(red: 0.765, green: 0.769, blue: 0.725)
    private let edge = Color(red: 0.522, green: 0.525, blue: 0.486)
    private func e(_ a: Double, _ b: Double, _ t: Double) -> Double { let x = min(1, max(0, (t-a)/(b-a))); return x*x*x*(10+x*(-15+6*x)) }
    private func spring(_ u: Double) -> Double { u + 0.4 * sin(.pi * u) * sin(.pi * u / 2) }
    private func time(_ date: Date) -> Double {
        if let previewTime { return previewTime.truncatingRemainder(dividingBy: Self.loop) }
        if reduceMotion { return 5 }
        if let touched { let d=date.timeIntervalSince(touched); if isTouching { return manual }; if d<2.7 { return min(8.3,max(0,manual+(tapRun ? min(3.4,d*3) : releaseVelocity*0.16*(1-exp(-d/0.16))))) }; return autoPlay ? (d-2.7).truncatingRemainder(dividingBy: Self.loop) : 0 }
        return autoPlay ? max(0,date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy: Self.loop) : 0
    }
    private func input(_ v: Double) { manual=min(8.3,max(0,v)); touched=Date() }
    private func returnWeight(_ date: Date) -> Double { guard previewTime == nil, !reduceMotion, !isTouching, let touched else { return 1 }; let d=date.timeIntervalSince(touched); return d<2.7 ? 1-e(2,2.7,d) : 1 }
    private func folder(_ name: String, _ letter: String?, width: Double, height: Double) -> some View {
        ZStack(alignment: .topLeading) {
            if letter != nil {
                UnevenRoundedRectangle(topLeadingRadius: 6, topTrailingRadius: 8).fill(Color(red:0.06,green:0.065,blue:0.05))
                    .frame(width: 95,height: 45).offset(x:width-95,y:13)
                
            }
            ContactFileStackOutline().fill(paper).overlay(ContactFileStackOutline().stroke(edge,lineWidth:0.9))
            if let letter { Text(letter).font(.system(size:12,weight:.medium)).foregroundStyle(bg).offset(x:width-43,y:14) }
            Text(name).font(.system(size:13,weight:.medium)).foregroundStyle(Color.black.opacity(0.88)).offset(x:10,y:10)
            Path { p in p.move(to:CGPoint(x:5,y:29));p.addLine(to:CGPoint(x:width-7,y:29)) }.stroke(.white.opacity(0.25),lineWidth:1)
        }.frame(width:width,height:height,alignment:.topLeading)
    }
    private func card(_ i: Int) -> some View {
        ZStack(alignment:.topLeading) {
            Color(red:0.98,green:0.975,blue:0.95)
            VStack(alignment:.leading,spacing:7) {
                Text("Bryn Hale").font(.system(size:15,weight:.semibold))
                Text(i == 0 ? "Creative direction" : i == 1 ? "A note for later" : "Studio / 04").font(.system(size:10)).opacity(0.6)
                Spacer()
                Text(verbatim: "bryn@quiet-studio.example").font(.system(size:9)).opacity(0.5)
            }.padding(15).foregroundStyle(Color.black)
            Rectangle().fill(i == 1 ? Color(red:0.93,green:0.45,blue:0.22) : Color(red:0.36,green:0.12,blue:0.87))
                .frame(width:30,height:30).overlay(Rectangle().stroke(.white.opacity(0.4),lineWidth:2).padding(5)).offset(x:184,y:14)
        }.frame(width:232,height:126).shadow(color:.black.opacity(0.1),radius:2,y:2).shadow(color:.black.opacity(0.16),radius:16,y:12)
    }
    private func printPhoto(_ i: Int) -> some View {
        ZStack(alignment:.topLeading) {
            Color(red:0.99,green:0.99,blue:0.96)
            LinearGradient(colors:[Color(red:0.3,green:0.5,blue:0.57),Color(red:0.78,green:0.77,blue:0.62)],startPoint:.top,endPoint:.bottom).frame(width:78,height:78).offset(x:6,y:6)
            Path { p in p.move(to:CGPoint(x:6,y:70));p.addLine(to:CGPoint(x:32,y:36));p.addLine(to:CGPoint(x:48,y:60));p.addLine(to:CGPoint(x:64,y:44));p.addLine(to:CGPoint(x:84,y:78));p.closeSubpath() }.fill(Color(red:0.2,green:0.25,blue:0.2))
        }.frame(width:90,height:110).shadow(color:.black.opacity(0.15),radius:6,y:7).rotationEffect(.degrees(i == 0 ? 12 : -10))
    }
    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation(paused:previewTime != nil || reduceMotion || (!autoPlay && touched == nil))) { tick in
                let t=time(tick.date), s=geo.size.width/393, h=geo.size.height/s
                let weight=returnWeight(tick.date)
                let compressed=e(1.3,2.1,t)*(1-e(7.8,8.4,t))*weight
                let open=e(2.2,3,t)*(1-e(7,7.8,t))*weight
                let drop=e(3.7,4.4,t)*(1-e(5.8,6.5,t))*weight
                let stackY=240+240*compressed, stride=39-17*compressed
                ZStack(alignment:.topLeading) {
                    bg
                    Text("contact files").font(.system(size:14,weight:.semibold)).offset(x:25,y:84)
                    ForEach(0..<14,id:\.self) { i in
                            let hide=e(2.2+Double(i)*0.025,2.9+Double(i)*0.025,t)*(1-e(7,7.8,t))*weight
                            folder(names[i], [0,4,6,10].contains(i) ? (i<4 ? "A" : i<6 ? "B" : i<10 ? "C" : "D") : nil,width:353,height:138)
                                .offset(x:20,y:stackY+Double(i)*stride+(i == 4 ? 0 : 180*hide)).opacity(i == 4 ? 1-open : 1-hide).zIndex(Double(i))
                    }
                    ZStack(alignment:.topLeading) {
                        folder("Bryn",nil,width:349,height:138+107*open).offset(y:312*drop)
                        ForEach((0..<3).reversed(),id:\.self) { i in
                            let delay=Double(i)*0.055
                            let lift=spring(e(3+delay,3.85+delay,t))*(1-e(6.05+delay,6.95+delay,t))*weight
                            let fan=e(3.35+delay,4.1+delay,t)*(1-e(6.05+delay,6.95+delay,t))*weight
                            card(i).rotationEffect(.degrees(-2+(-10+Double(i)*8.5)*fan))
                                .offset(x:62+Double(i)*5+Double(i)*18*fan,y:32-140*lift+Double(i)*7-Double(i)*12*fan)
                        }
                        RoundedRectangle(cornerRadius:12).fill(paper)
                            .overlay(RoundedRectangle(cornerRadius:12).stroke(edge,lineWidth:0.9))
                            .overlay(RoundedRectangle(cornerRadius:12).stroke(.white.opacity(0.15),lineWidth:1).padding(1))
                            .frame(width:349,height:110+70*open)
                            .rotation3DEffect(.degrees(22*open*(1-drop)),axis:(x:1,y:0,z:0),anchor:.top,perspective:0.35)
                            .offset(y:28+35*open+drop*312)
                    }.frame(width:349,height:245,alignment:.topLeading)
                        .offset(x:20+2*open,y:(stackY+4*stride)*(1-open)+338*open)
                        .compositingGroup().opacity(open).zIndex(20)
                    ForEach(0..<2,id:\.self) { i in
                        let delay=Double(i)*0.07
                        let prints=e(3.7+delay,4.5+delay,t)*(1-e(5.8+delay,6.5+delay,t))*weight
                        printPhoto(i).scaleEffect(0.82+0.18*prints).rotationEffect(.degrees((i == 0 ? 16 : -16)*(1-prints)))
                            .offset(x:230+Double(i)*19,y:540-85*spring(prints)+Double(i)*21).opacity(prints).zIndex(21)
                    }
                    Text("14 PEOPLE / ONE PLACE").font(.system(size:9,weight:.medium,design:.monospaced)).tracking(1.2)
                        .foregroundStyle(.black.opacity(0.35)).offset(x:25,y:h-53).opacity(1-open)
                }.frame(width:393,height:h,alignment:.topLeading).scaleEffect(s,anchor:.topLeading)
                    .frame(width:geo.size.width,height:geo.size.height,alignment:.topLeading).clipped()
                    .contentShape(Rectangle()).gesture(DragGesture(minimumDistance:8).onChanged { v in
                        if dragBase == nil { dragBase=time(Date());tapRun=false;releaseVelocity=0 }
                        isTouching=true;input((dragBase ?? 0)-v.translation.height/(90*s))
                    }.onEnded { v in releaseVelocity=min(3,max(-3,-(v.predictedEndTranslation.height-v.translation.height)/(90*s*0.25)));dragBase=nil;isTouching=false;touched=Date() })
                    .onTapGesture { tapRun=true;releaseVelocity=0;input(1.3);UIImpactFeedbackGenerator(style:.light).impactOccurred() }
                    .accessibilityElement(children:.ignore).accessibilityLabel("Contact files. Tap a file to open Bryn's cards, or drag to unfold the stack.")
            }
        }.ignoresSafeArea().preferredColorScheme(.light)
            .task(id: touched) {
                guard !autoPlay, previewTime == nil, let completedTouch = touched else { return }
                try? await Task.sleep(for: .seconds(2.7))
                guard !Task.isCancelled, !isTouching, touched == completedTouch else { return }
                touched=nil;tapRun=false;releaseVelocity=0
            }
    }
}
