import SwiftUI
import UIKit

struct OrbitImport: View {
    static let loop: Double = 10
    var autoPlay = false
    var previewTime: Double? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var epoch = Date()
    @State private var touched: Date? = nil
    @State private var isTouching = false
    @State private var manual = 0.0
    @State private var dragBase: Double? = nil
    @State private var releaseVelocity = 0.0
    @State private var tapTarget: Double? = nil
    private let names = ["Vela", "Moss", "Frame", "Folio", "Aster", "Vault"]
    private let glyphs = ["V", "m", "▶", "F", "+", "◈"]
    private let prices = ["$ 10.99", "$ 12.00", "$ 10.00", "$ 120.00", "$ 8.00", "$ 35.00"]
    private let colors: [Color] = [.init(red:0.75,green:0.22,blue:0.28),.init(red:0.2,green:0.9,blue:0.45),.init(red:0.97,green:0.2,blue:0.32),.init(red:0.92,green:0.91,blue:0.84),.init(red:0.62,green:0.6,blue:0.86),.init(red:0.76,green:0.63,blue:0.38)]
    private func e(_ a: Double, _ b: Double, _ t: Double) -> Double { let x=min(1,max(0,(t-a)/(b-a)));return x*x*x*(10+x*(-15+6*x)) }
    private func time(_ date: Date) -> Double {
        if let previewTime { return previewTime.truncatingRemainder(dividingBy:Self.loop) }
        if reduceMotion { return 8.2 }
        if let touched { let d=date.timeIntervalSince(touched);if isTouching{return manual};if d<2.7 { if let tapTarget { return manual+(tapTarget-manual)*e(0,0.7,d) };return min(9.3,max(0,manual+releaseVelocity*0.16*(1-exp(-d/0.16)))) };return autoPlay ? (d-2.7).truncatingRemainder(dividingBy:Self.loop) : 0 }
        return autoPlay ? max(0,date.timeIntervalSince(epoch)).truncatingRemainder(dividingBy:Self.loop) : 0
    }
    private func input(_ v: Double) { manual=min(9.2,max(0,v));touched=Date() }
    private func returnWeight(_ date: Date) -> Double { guard previewTime == nil, !reduceMotion, !isTouching, let touched else { return 1 };let d=date.timeIntervalSince(touched);return d<2.7 ? 1-e(2,2.7,d) : 1 }
    private func sphere(_ t: Double, _ weight: Double) -> [(x: Double,y: Double,z: Double)] {
        let ay=(t-1.4)*1.8, ax=0.45+(t-1.4)*0.65
        let radius=72*e(1.4,1.9,t)*(1-e(4.05,4.45,t))*weight
        return (0..<48).map { i in
            let seed=Double(i)*2.39996323, by=1-2*(Double(i)+0.5)/48
            let ring=sqrt(max(0,1-by*by)), bx=cos(seed)*ring, bz=sin(seed)*ring
            let rx=bx*cos(ay)+bz*sin(ay), rz = -bx*sin(ay)+bz*cos(ay)
            let ry=by*cos(ax)-rz*sin(ax), depth=by*sin(ax)+rz*cos(ax)
            let perspective=240/(240-depth*72)
            return (196+rx*radius*perspective,365+ry*radius*0.85*perspective,depth)
        }.sorted { $0.z < $1.z }
    }
    private func icon(_ index: Int, size: Double) -> some View {
        Text(glyphs[index]).font(.system(size:size*0.5,weight:.bold)).foregroundStyle(index == 3 ? Color.black : Color.white)
            .frame(width:size,height:size).background(colors[index],in:Circle())
            .overlay(Circle().stroke(.white.opacity(0.1),lineWidth:1))
    }
    private func button(_ title: String, light: Bool = true) -> some View {
        HStack(spacing:9) { Image(systemName:"tray.and.arrow.down.fill").font(.system(size:14));Text(title).font(.system(size:14,weight:.semibold)) }
            .foregroundStyle(light ? Color.black : Color.white).frame(width:321,height:52)
            .background(light ? Color(red:0.98,green:0.98,blue:0.96) : Color.white.opacity(0.055),in:RoundedRectangle(cornerRadius:12))
            .overlay(RoundedRectangle(cornerRadius:12).stroke(.white.opacity(0.07),lineWidth:1))
    }
    var body: some View {
        GeometryReader { geo in
            TimelineView(.animation(paused:previewTime != nil || reduceMotion || (!autoPlay && touched == nil))) { tick in
                let t=time(tick.date),s=geo.size.width/393,h=geo.size.height/s
                let weight=returnWeight(tick.date), reset=1-weight, slide=reset*(h+80)
                let start=max(1-e(1.3,1.65,t)+e(8.7,9.3,t),reset)
                let processing=e(1.4,1.9,t)*(1-e(4.2,4.6,t))
                let listing=e(4.2,4.6,t)*(1-e(6.65,7.15,t))
                let calendar=e(6.4,6.8,t)*(1-e(8.7,9.3,t))
                let headerExit=1-e(0,0.45,reset)
                let importHeader=max(1-e(4.2,4.4,t)+e(9,9.3,t),e(0.55,1,reset))
                let listHeader=e(4.4,4.6,t)*(1-e(6.45,6.65,t))*headerExit
                let calendarHeader=e(6.65,6.85,t)*(1-e(8.7,9,t))*headerExit
                ZStack(alignment:.topLeading) {
                    Color(red:0.14,green:0.14,blue:0.14)
                    RoundedRectangle(cornerRadius:34).fill(Color(red:0.22,green:0.22,blue:0.22))
                        .overlay(RoundedRectangle(cornerRadius:34).stroke(.white.opacity(0.08),lineWidth:1))
                        .frame(width:373,height:h-105).offset(x:10,y:70)
                    Image(systemName:"chevron.left").font(.system(size:13,weight:.semibold)).foregroundStyle(.white.opacity(0.8))
                        .frame(width:34,height:34).background(.white.opacity(0.055),in:Circle()).offset(x:26,y:88)
                    ZStack {
                        Text("Import from Folio").opacity(importHeader)
                        Text("Subscriptions").opacity(listHeader)
                        Text("October 2026").opacity(calendarHeader)
                    }.font(.system(size:15,weight:.semibold)).foregroundStyle(.white.opacity(0.94)).frame(width:260).offset(x:66,y:98)
                    ZStack(alignment:.topLeading) {
                        Text("F").font(.system(size:48,weight:.bold,design:.serif)).foregroundStyle(.white)
                            .frame(width:82,height:82).background(Color(red:0.08,green:0.08,blue:0.08),in:RoundedRectangle(cornerRadius:21))
                            .overlay(RoundedRectangle(cornerRadius:21).stroke(.white.opacity(0.12),lineWidth:1)).rotationEffect(.degrees(-5)).offset(x:155,y:228)
                        Image(systemName:"arrow.triangle.2.circlepath").font(.system(size:18,weight:.medium)).foregroundStyle(Color(red:0.8,green:0.72,blue:0.33))
                            .frame(width:34,height:34).background(.black.opacity(0.5),in:Circle()).offset(x:218,y:288)
                        VStack(spacing:9) {
                            Text("Step 1").font(.system(size:13,weight:.semibold))
                            Text("Enable public access").font(.system(size:13))
                            Text("Share → General access → Anyone").font(.system(size:12)).opacity(0.65)
                        }.foregroundStyle(.white.opacity(0.74)).frame(width:321).offset(x:36,y:365)
                        VStack(spacing:9) { Text("Step 2").font(.system(size:13,weight:.semibold));Text("Copy the link to your Folio page").font(.system(size:13)) }
                            .foregroundStyle(.white.opacity(0.74)).frame(width:321).offset(x:36,y:452)
                        button("Paste from Clipboard").offset(x:36,y:h-183)
                        Text("Cancel").font(.system(size:13)).foregroundStyle(.white.opacity(0.42)).frame(width:321).offset(x:36,y:h-110)
                    }.opacity(start).offset(y: -24*reset*(1-reset))
                    ZStack(alignment:.topLeading) {
                        Canvas { context,_ in
                            for point in sphere(t,weight) {
                                let x=point.x,y=point.y,depth=(point.z+1)/2
                                let r=1+2.8*depth, alpha=0.18+0.8*depth
                                var glow=context;glow.addFilter(.blur(radius:3.5))
                                glow.fill(Path(ellipseIn:CGRect(x:x-r*2,y:y-r*2,width:r*4,height:r*4)),with:.color(.white.opacity(alpha*0.25)))
                                var core=context;core.addFilter(.blur(radius:1.4*(1-depth)))
                                core.fill(Path(ellipseIn:CGRect(x:x-r,y:y-r,width:r*2,height:r*2)),with:.color(.white.opacity(alpha)))
                            }
                        }.frame(width:393,height:h)
                        Text("Processing").font(.system(size:20,weight:.semibold)).foregroundStyle(.white).frame(width:393).offset(y:510)
                        Text("Analyzing with AI…").font(.system(size:14)).foregroundStyle(.white.opacity(0.42)).frame(width:393).offset(y:543)
                    }.opacity(processing).offset(y:slide)
                    ZStack(alignment:.topLeading) {
                        Text("6 subscriptions found").font(.system(size:11)).foregroundStyle(.white.opacity(0.4)).frame(width:393).offset(y:124)
                        ForEach(0..<6,id:\.self) { i in
                            let selected=e(5+Double(i)*0.4,5.3+Double(i)*0.4,t)
                            let enter=e(4.25+Double(i)*0.045,4.65+Double(i)*0.045,t)
                            let exit=e(6.65+Double(i)*0.035,7+Double(i)*0.035,t)
                            HStack(spacing:12) {
                                ZStack { Circle().stroke(.white.opacity(0.2),lineWidth:1).frame(width:20,height:20);Image(systemName:"checkmark.circle.fill").font(.system(size:21)).opacity(i < 3 ? selected : 0) }
                                icon(i,size:40).opacity(i<3 ? 1-e(6.3+Double(i)*0.12,6.35+Double(i)*0.12,t) : 1)
                                VStack(alignment:.leading,spacing:7) { Text(names[i]).font(.system(size:15,weight:.medium));Text(prices[i]).font(.system(size:12)).opacity(0.48) }
                                Spacer()
                                VStack(alignment:.trailing,spacing:8) { Text("\(4+i*3) Oct 2026").font(.system(size:10)).opacity(0.5);Text(i==3 || i==5 ? "Yearly" : "Monthly").font(.system(size:9,weight:.medium)).padding(.horizontal,6).padding(.vertical,3).background(.white.opacity(0.055),in:Capsule()).opacity(0.6) }
                            }.foregroundStyle(.white).padding(.horizontal,12).frame(width:333,height:68)
                                .background(.white.opacity(0.045),in:RoundedRectangle(cornerRadius:12))
                                .opacity(enter*(1-exit)).offset(x:30,y:170+Double(i)*76+24*(1-enter)+220*exit)
                        }
                        button(t>6.1 ? "Import 3 Subscriptions" : t>5.7 ? "Import 2 Subscriptions" : t>5.3 ? "Import 1 Subscription" : "Import Subscriptions",light:t>5.3).opacity(1-calendar).offset(x:36,y:h-123)
                    }.opacity(listing).offset(y:slide)
                    ZStack(alignment:.topLeading) {
                        Text("YOUR MONTH, IN VIEW").font(.system(size:10,weight:.medium)).tracking(1.8).foregroundStyle(.white.opacity(0.4)).offset(x:35,y:180)
                        ForEach(0..<7,id:\.self) { col in
                            Text(["M","T","W","T","F","S","S"][col]).font(.system(size:11,weight:.medium)).foregroundStyle(.white.opacity(0.4)).position(x:58+Double(col)*46,y:216)
                        }
                        Canvas { context,_ in
                            for col in 0...7 { let x=35+Double(col)*46;var p=Path();p.move(to:CGPoint(x:x,y:238));p.addLine(to:CGPoint(x:x,y:628));context.stroke(p,with:.color(.white.opacity(0.055)),lineWidth:1) }
                            for row in 0...6 { let y=238+Double(row)*65;var p=Path();p.move(to:CGPoint(x:35,y:y));p.addLine(to:CGPoint(x:357,y:y));context.stroke(p,with:.color(.white.opacity(0.055)),lineWidth:1) }
                            var route=Path();route.move(to:CGPoint(x:105,y:320));route.addCurve(to:CGPoint(x:287,y:451),control1:CGPoint(x:290,y:290),control2:CGPoint(x:160,y:430));route.addCurve(to:CGPoint(x:151,y:568),control1:CGPoint(x:355,y:495),control2:CGPoint(x:115,y:510));context.stroke(route.trimmedPath(from:0,to:e(6.7,7.8,t)*weight),with:.color(.white.opacity(0.3)),style:StrokeStyle(lineWidth:1.3,dash:[3,5]))
                        }.frame(width:393,height:h)
                        ForEach(1...30,id:\.self) { day in Text("\(day)").font(.system(size:10)).foregroundStyle(.white.opacity(0.25)).position(x:44+Double((day+2)%7)*46,y:252+Double((day+2)/7)*65) }
                        Text("3 subscriptions added").font(.system(size:13,weight:.medium)).foregroundStyle(.white.opacity(0.72)).frame(width:321).opacity(e(7.6,7.95,t)).offset(x:36,y:h-145)
                        button("Import complete").opacity(e(7.6,7.95,t)).offset(x:36,y:h-123)
                    }.opacity(calendar).offset(y:slide)
                    ForEach(0..<3,id:\.self) { i in
                        let delay=Double(i)*0.12,p=e(6.35+delay,7.45+delay,t)*weight,q=1-p
                        let tx=[105.0,287.0,151.0][i],ty=[320.0,451.0,568.0][i],sy=204+Double(i)*76
                        let x=q*q*q*94+3*q*q*p*(50+Double(i)*60)+3*q*p*p*(tx+80)+p*p*p*tx
                        let y=q*q*q*sy+3*q*q*p*(sy+90)+3*q*p*p*(ty-80)+p*p*p*ty
                        let land=e(7.35+delay,7.8+delay,t)
                        icon(i,size:40-4*p).scaleEffect(1+0.08*sin(.pi*land)).rotationEffect(.degrees(6*sin(.pi*p)))
                            .shadow(color:colors[i].opacity(0.25),radius:12*p).position(x:x,y:y+slide)
                            .opacity(e(6.3+delay,6.35+delay,t)*(1-e(8.7,9.3,t)))
                    }
                }.frame(width:393,height:h,alignment:.topLeading).scaleEffect(s,anchor:.topLeading).frame(width:geo.size.width,height:geo.size.height,alignment:.topLeading).clipped()
                    .contentShape(Rectangle()).gesture(DragGesture(minimumDistance:8).onChanged { v in
                        if dragBase == nil { dragBase=time(Date());tapTarget=nil;releaseVelocity=0 }
                        isTouching=true;input((dragBase ?? 0)-v.translation.height/(75*s))
                    }.onEnded { v in releaseVelocity=min(3,max(-3,-(v.predictedEndTranslation.height-v.translation.height)/(75*s*0.25)));dragBase=nil;isTouching=false;touched=Date() })
                    .onTapGesture { tapTarget=t<1.5 ? 2.4 : t<4.5 ? 5.8 : t<6.8 ? 8.1 : 9.3;releaseVelocity=0;input(t);UIImpactFeedbackGenerator(style:.light).impactOccurred() }
                    .accessibilityElement(children:.ignore).accessibilityLabel("Import subscriptions from Folio. Tap to continue, or drag to scrub the import preview.")
            }
        }.ignoresSafeArea().preferredColorScheme(.dark)
            .task(id:touched) {
                guard !autoPlay, previewTime == nil, let completedTouch=touched else { return }
                try? await Task.sleep(for:.seconds(2.7))
                guard !Task.isCancelled, !isTouching, touched==completedTouch else { return }
                touched=nil;releaseVelocity=0;tapTarget=nil
            }
    }
}
