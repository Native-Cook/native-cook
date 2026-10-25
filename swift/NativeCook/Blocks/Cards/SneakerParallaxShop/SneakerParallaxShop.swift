import SwiftUI
import UIKit

private struct SneakerParallaxShopFrame {
    var q: Double = 0
    var e: Double = 0
    var detail = 2
    func mixed(_ other: Self, _ t: Double) -> Self {
        .init(q: q+(other.q-q)*t, e: e+(other.e-e)*t, detail: other.detail)
    }
}

private enum SneakerParallaxShopMath {
    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi,max(lo,x)) }
    static func smooth(_ x: Double) -> Double { let u=clamp(x); return u*u*(3-2*u) }
    static func automatic(_ time: Double) -> SneakerParallaxShopFrame {
        let t=max(0,time).truncatingRemainder(dividingBy:SneakerParallaxShop.loop)
        let q=smooth((t-0.67)/0.48)+smooth((t-1.82)/0.53)-2*smooth((t-5.271933)/1.2)
        let e=clamp((t-3)/0.5)*(1-clamp((t-4.5)/0.5))
        return .init(q:q,e:e,detail:2)
    }
    static func spring(_ progress: Double) -> Double {
        if progress >= 1 { return 1 }
        let u=max(0,progress), a=5.15, b=3.59
        let end=1-exp(-a)*(cos(b)+a/b*sin(b))
        return (1-exp(-a*u)*(cos(b*u)+a/b*sin(b*u)))/end
    }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255)
    }
}

/// A shoe stays in one layer as its catalog surface becomes a curved detail header.
struct SneakerParallaxShop: View {
    static let loop = 7.5
    var autoPlay = false
    var previewTime: Double? = nil
    var onBack: (() -> Void)? = nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @Environment(\.dismiss) private var dismiss
    @State private var epoch = Date()
    @State private var visible = false
    @State private var manualFrom: SneakerParallaxShopFrame?
    @State private var manualTarget = SneakerParallaxShopFrame()
    @State private var eventDate = Date()
    @State private var eventDuration = 0.42
    @State private var eventMoving = false
    @State private var lastTouch = Date()
    @State private var held = false
    @State private var panOrigin = SneakerParallaxShopFrame()
    @State private var serial = 0
    @GestureState private var panActive = false
    @State private var favorite = false
    @State private var sizeIndex = 3
    @State private var filter = 0
    @State private var added = false
    @State private var more = false
    @State private var colorway = 0
    @State private var reducedDetail = 2
    @State private var reducedExpanded = true
    private static let names = ["Crimson Lift","Dune Runner","Prism Court"]
    private static let prices = ["₹8,895","₹11,897","₹12,995"]
    private static let colors: [UInt32] = [0xE24C4C,0xFAB960,0x4D80F4]
    private static let catalogSizes = [254.06,256.54,293.08]
    private static let catalogCenters = [CGPoint(x:223.8,y:403),CGPoint(x:218.4,y:410.1),CGPoint(x:197.8,y:391.8)]
    private static let catalogAngles = [8.4,3.5,-31.0]
    private static let detailCenters = [CGPoint(x:187.66,y:179.67),CGPoint(x:185.09,y:169.01),CGPoint(x:175.2,y:172.3)]
    private static let detailAngles = [29.8,24.9,-9.6]
    private static let images = ["red","yellow","gray","blue","olive","black"].map { name in
        UIImage(named:"SneakerParallaxShop-sneaker-\(name).png").flatMap { $0.preparingForDisplay() ?? $0 }
    }
    private var ink: Color { SneakerParallaxShopMath.color(0x202A33) }
    private var muted: Color { SneakerParallaxShopMath.color(0xA5A09D) }
    private func frame(_ now: Date) -> SneakerParallaxShopFrame {
        if reduced { return .init(q:Double(reducedDetail),e:reducedExpanded ? 1 : 0,detail:reducedDetail) }
        if let from=manualFrom {
            let u=eventDuration == 0 ? 1 : SneakerParallaxShopMath.spring(now.timeIntervalSince(eventDate)/eventDuration)
            let current=from.mixed(manualTarget,u)
            let recovery=(held || !autoPlay) ? 0 : SneakerParallaxShopMath.smooth((now.timeIntervalSince(lastTouch)-2)/0.55)
            var result=current
            result.q *= 1-recovery; result.e *= 1-recovery
            return result
        }
        return SneakerParallaxShopMath.automatic(previewTime ?? (autoPlay ? now.timeIntervalSince(epoch) : 0))
    }
    var body: some View {
        GeometryReader { geo in
            let scale=geo.size.width/393
            TimelineView(.animation(paused: !visible || scene != .active || reduced || previewTime != nil || (!autoPlay && !eventMoving))) { clock in
                let f=frame(clock.date)
                let chrome=1-f.e
                let details=SneakerParallaxShopMath.smooth((f.e-0.35)/0.65)
                ZStack(alignment:.topLeading) {
                    SneakerParallaxShopMath.color(0xF7F0EE)
                    catalogChrome(f).opacity(chrome).allowsHitTesting(f.e<0.05).accessibilityHidden(f.e>=0.05)
                    hero(f)
                    detailControls(f).opacity(details).offset(y:12*(1-details)).allowsHitTesting(f.e>0.96).accessibilityHidden(f.e<=0.96)
                    navigation(f)
                }
                .frame(width:393,height:852,alignment:.topLeading).clipped()
                .allowsHitTesting(previewTime == nil)
                .contentShape(Rectangle())
                .gesture(pan(scale:scale), including: reduced || previewTime != nil ? .none : .all)
                .scaleEffect(scale,anchor:.topLeading)
                .offset(y:max(0,(geo.size.height-852*scale)/2))
                .frame(width:geo.size.width,height:geo.size.height,alignment:.topLeading)
                .preferredColorScheme(f.e>0.5 ? .dark : .light)
            }
        }
        .background(SneakerParallaxShopMath.color(0xF7F0EE)).ignoresSafeArea()
        .onAppear { visible=true; epoch=Date() }
        .onDisappear { visible=false; reset() }
        .onChange(of:scene) { _, value in if value != .active { reset() } else { epoch=Date() } }
        .onChange(of:reduced) { _, _ in reset() }
        .onChange(of:autoPlay) { _, _ in reset() }
        .onChange(of:previewTime) { _, _ in reset() }
        .onChange(of:panActive) { _, active in if !active && held { settlePan(projected:0,vertical:0,haptic:false) } }
        .task(id:serial) {
            guard manualFrom != nil && !held else { return }
            if !autoPlay {
                do { try await Task.sleep(for:.seconds(max(0.02,eventDuration+0.02))) } catch { return }
                guard !held, manualFrom != nil else { return }; eventMoving=false
                return
            }
            do { try await Task.sleep(for:.seconds(2.55)) } catch { return }
            guard !held, manualFrom != nil else { return }; reset()
        }
        .accessibilityAction(named:"Next shoe") { chooseAdjacent(1) }
        .accessibilityAction(named:"Previous shoe") { chooseAdjacent(-1) }
        .accessibilityAction(named:"Open product details") { open(Int(frame(Date()).q.rounded())) }
    }
    private func chooseAdjacent(_ direction:Int) {
        let f=frame(Date()), index=(Int(f.q.rounded())+direction+3)%3
        if reduced { reducedDetail=index }
        interact(.init(q:Double(index),e:f.e,detail:index))
    }
    private func reset() {
        held=false; eventMoving=false; manualFrom=nil; epoch=Date(); favorite=false; sizeIndex=3; filter=0; added=false; more=false; colorway=0; reducedDetail=2; reducedExpanded=true
    }
    private func interact(_ target: SneakerParallaxShopFrame, duration: Double = 0.42) {
        let now=Date(), current=frame(now)
        if reduced {
            reducedDetail=min(2,max(0,target.e>0.5 ? target.detail : Int(target.q.rounded())))
            reducedExpanded=target.e>0.5
        }
        held=false; manualFrom=current; manualTarget=target; eventDate=now; eventDuration=duration; eventMoving=duration>0; lastTouch=now; serial+=1
    }
    private func touchControl() { interact(frame(Date()),duration:0) }
    private func open(_ index: Int) {
        let i=min(2,max(0,index))
        if i != frame(Date()).detail { colorway=0; favorite=false; more=false; added=false }
        reducedDetail=i
        interact(.init(q:Double(i),e:1,detail:i),duration:0.55)
        if !reduced { UIImpactFeedbackGenerator(style:.light).impactOccurred() }
    }
    private func pan(scale: Double) -> some Gesture {
        DragGesture(minimumDistance:6)
            .updating($panActive) { _, active, _ in active=true }
            .onChanged { value in
                if !held {
                    let current=frame(Date()); panOrigin=current; manualFrom=current; manualTarget=current; held=true; serial+=1
                }
                let dx=Double(value.translation.width)/scale, dy=Double(value.translation.height)/scale
                var f=panOrigin
                if panOrigin.e>0.5 && abs(dy)>abs(dx) { f.e=SneakerParallaxShopMath.clamp(panOrigin.e-dy/280) }
                else if panOrigin.e<0.5 {
                    let proposed=panOrigin.q-dx/349
                    let edge=SneakerParallaxShopMath.clamp(proposed,0,2)
                    let over=(proposed-edge)*349
                    f.q=edge+(over/(1+abs(over)/120))/349
                }
                manualTarget=f; eventDuration=0; eventDate=Date(); lastTouch=Date()
            }
            .onEnded { value in
                settlePan(projected:Double(value.predictedEndTranslation.width-value.translation.width)/scale*0.18,vertical:Double(value.translation.height)/scale,haptic:true)
            }
    }
    private func settlePan(projected: Double, vertical: Double, haptic: Bool) {
        guard held else { return }
        var f=manualTarget
        if panOrigin.e>0.5 { f.e=vertical>80 ? 0 : 1; f.q=Double(f.detail) }
        else { f.q=SneakerParallaxShopMath.clamp((f.q-projected/349).rounded(),0,2) }
        let changed=Int(f.q.rounded()) != Int(panOrigin.q.rounded()) || (f.e>0.5) != (panOrigin.e>0.5)
        interact(f)
        if haptic && changed { UIImpactFeedbackGenerator(style:.light).impactOccurred() }
    }
    private func text(_ value:String,_ size:CGFloat,_ weight:Font.Weight = .regular,_ color:Color? = nil) -> some View {
        Text(value).font(.system(size:size,weight:weight)).foregroundStyle(color ?? ink)
    }
    @ViewBuilder private func image(_ index:Int) -> some View {
        if Self.images.indices.contains(index),let image=Self.images[index] { Image(uiImage:image).resizable().interpolation(.high).scaledToFit().accessibilityHidden(true) }
    }
    private func navigation(_ f:SneakerParallaxShopFrame) -> some View {
        let tint=f.e>0.5 ? Color.white : ink
        return ZStack(alignment:.topLeading) {
            Button { if f.e>0.5 { interact(.init(q:Double(f.detail),e:0,detail:f.detail)) } else if let onBack { onBack() } else { dismiss() } } label: {
                Image(systemName:"arrow.left").font(.system(size:21,weight:.light)).frame(width:44,height:44)
            }.position(x:26,y:76).accessibilityLabel(f.e>0.5 ? "Back to shoes" : "Back")
            Button { if f.e>0.5 { favorite.toggle();touchControl() } else { filter=(filter+1)%5; interact(.init(q:Double(filter%3))) } } label: {
                Image(systemName:f.e>0.5 ? (favorite ? "heart.fill" : "heart") : "magnifyingglass").font(.system(size:20,weight:.light)).frame(width:44,height:44)
            }.position(x:360,y:76).accessibilityLabel(f.e>0.5 ? "Favorite product" : "Browse shoes")
        }.foregroundStyle(tint).buttonStyle(.plain)
    }
    private func catalogChrome(_ f:SneakerParallaxShopFrame) -> some View {
        ZStack(alignment:.topLeading) {
            text("Shoes",27,.semibold).frame(width:360,height:34,alignment:.leading).position(x:194,y:113)
            HStack(spacing:9) {
                ForEach(Array(["All","Court","Run","Trail","Daily"].enumerated()),id:\.offset) { index,name in
                    Button { filter=index; interact(.init(q:Double(index%3))) } label: {
                        text(name,14,.medium,filter==index ? .white : muted)
                            .frame(width:[61.0,82,70,78,74][index],height:34)
                            .background(filter==index ? ink : .clear,in:Capsule())
                            .overlay(Capsule().stroke(SneakerParallaxShopMath.color(0xE5DFDC),lineWidth:filter==index ? 0 : 1))
                    }.buttonStyle(.plain).frame(height:44).contentShape(Rectangle())
                }
            }.fixedSize().offset(x:15,y:140)
            text("243 OPTIONS",12,.regular,muted).tracking(1).position(x:78,y:590)
            image(2).frame(width:131,height:90).position(x:85.5,y:665)
            image(3).frame(width:131,height:90).position(x:85.5,y:780)
            text("Cinder Pace\nLow Runner",17,.medium).lineSpacing(1).frame(width:191,height:44,alignment:.topLeading).position(x:277.5,y:658)
            text("₹12,797",14,.regular,muted).frame(width:191,height:19,alignment:.leading).position(x:277.5,y:692.5)
            text("Lowtide Mesh",17,.medium).frame(width:191,height:24,alignment:.leading).position(x:277.5,y:763)
            text("₹9,995",14,.regular,muted).frame(width:191,height:19,alignment:.leading).position(x:277.5,y:787.5)
        }
    }
    private func hero(_ f:SneakerParallaxShopFrame) -> some View {
        ZStack(alignment:.topLeading) {
            ForEach((0..<3).filter { $0 != f.detail }+[f.detail],id:\.self) { index in
                let expanded=index==f.detail ? f.e : 0
                let alpha=index==f.detail ? 1 : 1-f.e
                let panelX=18+349*(Double(index)-f.q)
                SneakerParallaxShopSurface(x:panelX,expansion:expanded)
                    .fill(SneakerParallaxShopMath.color(Self.colors[index])).frame(width:393,height:852).opacity(alpha)
                ZStack(alignment:.topLeading) {
                    text(Self.names[index],25,.semibold,.white).frame(width:240,height:31,alignment:.leading).offset(x:45+349*(Double(index)-f.q),y:233-191*expanded)
                    text(Self.prices[index],19,.regular,.white.opacity(0.64)).frame(width:200,height:25,alignment:.leading).offset(x:45+349*(Double(index)-f.q),y:269-191*expanded)
                    Rectangle().fill(.white.opacity(0.22)).frame(width:0.7,height:179).offset(x:45+349*(Double(index)-f.q),y:301-191*expanded)
                }.opacity((1-expanded)*alpha)
                let shoeX=72+349*(Double(index)-f.q)*0.84
                let baseCenter=Self.catalogCenters[index]
                let targetCenter=Self.detailCenters[index]
                let centerX=(baseCenter.x+349*(Double(index)-f.q)*0.84)*(1-expanded)+targetCenter.x*expanded
                let centerY=baseCenter.y*(1-expanded)+targetCenter.y*expanded
                let imageSize=Self.catalogSizes[index]+(294.57-Self.catalogSizes[index])*expanded
                Ellipse().fill(SneakerParallaxShopMath.color(0x3C3330).opacity(0.15)).frame(width:188,height:24).blur(radius:12)
                    .position(x:(shoeX+163)*(1-expanded)+183*expanded,y:504*(1-expanded)+246*expanded).opacity(alpha)
                Ellipse().fill(SneakerParallaxShopMath.color(0x3C3330).opacity(0.16)).frame(width:148,height:8).blur(radius:3)
                    .position(x:(shoeX+163)*(1-expanded)+183*expanded,y:504*(1-expanded)+246*expanded).opacity(alpha)
                ZStack {
                    image(index).opacity(index==f.detail && colorway>0 ? 1-expanded : 1)
                    if index==f.detail && colorway>0 { image(colorway+3).opacity(expanded) }
                }.frame(width:imageSize,height:imageSize)
                    .rotationEffect(.degrees((Self.catalogAngles[index]+11*sin(.pi*f.q))*(1-expanded)+Self.detailAngles[index]*expanded))
                    .position(x:centerX,y:centerY).opacity(alpha).allowsHitTesting(false)
                Color.clear.frame(width:282,height:325).contentShape(RoundedRectangle(cornerRadius:19))
                    .position(x:panelX+141,y:367.5).onTapGesture {
                        if abs(f.q-Double(index))<0.05 { open(index) } else { interact(.init(q:Double(index))) }
                    }.allowsHitTesting(f.e<0.05)
                    .accessibilityElement().accessibilityHidden(f.e>=0.05)
                    .accessibilityLabel("\(Self.names[index]), \(Self.prices[index]). Open details")
                    .accessibilityAddTraits(.isButton)
            }
        }.frame(width:393,height:852,alignment:.topLeading)
    }
    private func detailControls(_ f:SneakerParallaxShopFrame) -> some View {
        let item=min(2,max(0,f.detail))
        return ZStack(alignment:.topLeading) {
            text(Self.names[item],24,.bold).frame(width:235,height:31,alignment:.leading).position(x:144.5,y:340.5)
            text(Self.prices[item],18,.semibold).frame(width:79,height:25,alignment:.trailing).position(x:331.5,y:341.5)
            text("Built for the moments that move you most.\nLight on your feet, ready for every turn.\nSoft landings. A little more possibility.",16.5,.regular,muted).lineSpacing(3.5)
                .frame(width:340,height:76,alignment:.topLeading).position(x:197,y:410)
            Button { more.toggle();touchControl() } label: { text(more ? "LESS" : "MORE",11,.semibold) }
                .frame(width:50,height:44).position(x:346,y:431).accessibilityLabel("More product details")
            if more { text("Choose your pace. Make it yours.",13,.regular,muted).position(x:160,y:452) }
            HStack(spacing:11) {
                ForEach(0..<3,id:\.self) { slot in
                    let artwork=slot==0 ? item : slot+3
                    Button { colorway=slot; touchControl() } label: {
                        image(artwork).rotationEffect(.degrees(slot==0 ? Self.detailAngles[item] : 0)).frame(width:88,height:77)
                            .overlay(RoundedRectangle(cornerRadius:18).stroke(ink,lineWidth:colorway==slot ? 1.6 : 0))
                    }.buttonStyle(.plain).accessibilityLabel("\(["Original","Moss","Night"][slot]) colorway")
                        .accessibilityAddTraits(colorway==slot ? .isSelected : [])
                }
            }.offset(x:22,y:469)
            text("Select Size",16,.semibold).frame(width:200,height:24,alignment:.leading).position(x:127,y:602)
            ForEach(0..<8,id:\.self) { index in
                let disabled=index==5 || index==7
                Button { sizeIndex=index;touchControl() } label: {
                    text("UK \(index+6)",14,.regular,ink.opacity(disabled ? 0.3 : 1))
                        .frame(width:73,height:42).background(SneakerParallaxShopMath.color(0xFFFCFA),in:RoundedRectangle(cornerRadius:9))
                        .overlay(RoundedRectangle(cornerRadius:9).stroke(ink,lineWidth:sizeIndex==index ? 1.5 : 0))
                }.disabled(disabled).buttonStyle(.plain).frame(height:44).contentShape(Rectangle()).position(x:63.5+Double(index%4)*90.5,y:649+Double(index/4)*62)
                    .accessibilityLabel("Size UK \(index+6)").accessibilityAddTraits(sizeIndex==index ? .isSelected : [])
            }
            Button { added=true;touchControl(); if !reduced { UIImpactFeedbackGenerator(style:.light).impactOccurred() } } label: {
                text(added ? "Added to Bag" : "Add to Bag",16,.medium,.white).frame(width:344,height:49).background(ink,in:RoundedRectangle(cornerRadius:13))
            }.buttonStyle(.plain).position(x:199,y:804.5)
        }.frame(width:393,height:852,alignment:.topLeading)
    }
}

private struct SneakerParallaxShopSurface: Shape {
    var x: Double
    var expansion: Double
    func path(in rect:CGRect) -> Path {
        let e=expansion, fast=SneakerParallaxShopMath.clamp(2.29*e)
        let px=x*(1-fast), y=205-473.9*e, w=282+111*fast, r=19*(1-fast)
        let right=530*(1-e)+240*e, left=530*(1-e)+125*e
        var p=Path()
        p.move(to:.init(x:px+r,y:y));p.addLine(to:.init(x:px+w-r,y:y))
        p.addQuadCurve(to:.init(x:px+w,y:y+r),control:.init(x:px+w,y:y))
        p.addLine(to:.init(x:px+w,y:right-r));p.addQuadCurve(to:.init(x:px+w-r,y:right),control:.init(x:px+w,y:right))
        p.addCurve(to:.init(x:px+r,y:left),control1:.init(x:(px+w*0.72)*(1-e)+289*e,y:530*(1-e)+300*e),control2:.init(x:(px+w*0.25)*(1-e)+103*e,y:530*(1-e)+287*e))
        p.addQuadCurve(to:.init(x:px,y:left-r),control:.init(x:px,y:left));p.addLine(to:.init(x:px,y:y+r));p.addQuadCurve(to:.init(x:px+r,y:y),control:.init(x:px,y:y));p.closeSubpath()
        return p
    }
}
