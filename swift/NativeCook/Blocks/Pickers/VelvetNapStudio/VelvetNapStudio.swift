import SwiftUI
import UIKit

private final class VelvetNapStudioDisplayed {
    var frame:VelvetNapStudioFrame?
    var tint:SIMD3<Double>?
    var date:Date?
}

private struct VelvetNapStudioPressStyle:ButtonStyle {
    let onPress:(Bool)->Void
    func makeBody(configuration:Configuration)->some View {
        configuration.label.onChange(of:configuration.isPressed) {_,pressed in onPress(pressed)}
    }
}

struct VelvetNapStudio:View {
    static let loop=VelvetNapStudioMath.loop
    var autoPlay=false
    var previewTime:Double?=nil
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var phase
    @Environment(\.dismiss) private var dismiss
    @State private var visible=false
    @State private var epoch=Date()
    @State private var session:VelvetNapStudioSession?
    @State private var event:VelvetNapStudioEvent?
    @State private var touchGeneration=UUID()
    @State private var displayed=VelvetNapStudioDisplayed()
    @State private var selected=3
    @State private var yards=2
    @State private var liked=false
    @State private var drive=VelvetNapStudioDriver()
    @State private var addedAt:Date?
    @State private var lastAngle=atan2(-0.6,0.8)
    @State private var strokes:[VelvetNapStudioStroke]=[]
    private var paused:Bool {
        if !visible || phase != .active || previewTime != nil {return true}
        if drive.palette != nil {return false}
        if drive.isControlHeld {return true}
        if let session {return reduced || session.settled}
        if event != nil || drive.control != nil {return false}
        return reduced || !autoPlay
    }
    var body:some View {
        GeometryReader { geo in
            let scale=min(geo.size.width/393,geo.size.height/852)
            let x=(geo.size.width-393*scale)/2,y=(geo.size.height-852*scale)/2
            ZStack(alignment:.topLeading) {
                VelvetNapStudioResources.color(0xFAFBF7).ignoresSafeArea()
                ZStack(alignment:.topLeading) {
                    TimelineView(.animation(paused:paused)) { timeline in
                        let f=frame(at:timeline.date),color=tint(at:timeline.date)
                        ZStack(alignment:.topLeading) {
                            VelvetNapStudioArt(frame:f,color:color).offset(x:43,y:121)
                            controls(at:timeline.date)
                            Canvas {_,_ in displayed.frame=f;displayed.tint=color;displayed.date=timeline.date}.frame(width:1,height:1).allowsHitTesting(false).accessibilityHidden(true)
                        }.frame(width:393,height:852,alignment:.topLeading)
                    }
                    VelvetNapStudioTouch(enabled:visible && phase == .active && previewTime == nil,generation:touchGeneration,onSample:receive)
                        .frame(width:331,height:298).offset(x:31,y:109).accessibilityHidden(true)
                }.frame(width:393,height:852,alignment:.topLeading).scaleEffect(scale,anchor:.topLeading).offset(x:x,y:y)
            }.frame(width:geo.size.width,height:geo.size.height,alignment:.topLeading).clipped()
        }.ignoresSafeArea().preferredColorScheme(.light)
            .onAppear {_ = VelvetNapStudioResources.shared;visible=true;epoch=Date()}
            .onDisappear {visible=false;clear()}
            .onChange(of:phase) {_,p in if p != .active {clear()} else {epoch=Date()}}
            .onChange(of:reduced) {_,_ in clear()}
            .onChange(of:autoPlay) {_,_ in clear()}
            .onChange(of:previewTime) {_,_ in clear()}
            .task(id:session?.stampID) {await completeStamp()}
            .task(id:event?.id) {await completeEvent()}
            .task(id:drive.palette?.id) {await completePalette()}
            .task(id:drive.control?.id) {await completeControl()}
            .task(id:addedAt) {await completeAdded()}
    }
    private func held(_ s:VelvetNapStudioSession,_ date:Date)->VelvetNapStudioFrame {
        VelvetNapStudioDriver.held(s,at:date,reduced:reduced)
    }
    private func frame(at date:Date)->VelvetNapStudioFrame {
        if let previewTime {return reduced ? .rest:VelvetNapStudioMath.authored(previewTime)}
        if let session {return held(session,date)}
        if let captured=drive.controlFrame(at:date,autoPlay:autoPlay,reduced:reduced) {return captured}
        if let event {
            let age=max(0,date.timeIntervalSince(event.date))
            if age<2 {return held(event.session,date)}
            if age<2.6 && !reduced {return VelvetNapStudioMath.recovering(held(event.session,event.date.addingTimeInterval(2)),elapsed:age-2)}
            return autoPlay && !reduced ? VelvetNapStudioMath.authored(age-2.6):.rest
        }
        return autoPlay && !reduced ? VelvetNapStudioMath.authored(max(0,date.timeIntervalSince(epoch))):.rest
    }
    private func snapshot(_ date:Date)->VelvetNapStudioFrame {displayed.frame ?? frame(at:date)}
    private func receive(_ sample:VelvetNapStudioTouchSample) {
        guard previewTime==nil,visible,phase == .active else{return}
        switch sample.phase {
        case .began:
            guard session==nil else{return}
            let from=VelvetNapStudioMath.coalesced(snapshot(sample.date))
            drive.freezeTint(displayed.tint ?? tint(at:sample.date));drive.control=nil
            let base=VelvetNapStudioMath.manualBaked(from)
            let angle=lastAngle
            let target=VelvetNapStudioMath.stamped(base,from:sample.point,to:sample.point,angle:angle,width:44,baseline:from)
            event=nil;session = .init(id:sample.id,from:from,target:target,stamp:sample.date,lastPoint:sample.point,samplePoint:sample.point,lastDate:sample.date,angle:angle,samples:[sample.point])
        case .moved:move(sample)
        case .ended,.cancelled:
            guard session?.id==sample.id else{return}
            if sample.phase == .ended {move(sample)}
            guard let s=session else{return}
            if s.samples.count>1 {strokes.append(.init(points:s.samples,angle:s.angle,width:52));if strokes.count>12 {strokes.removeFirst(strokes.count-12)}}
            session=nil
            if drive.isControlHeld {
                drive.captureControl(frame:displayed.frame ?? held(s,sample.date),date:sample.date);event=nil
            } else {
                event = .init(date:sample.date,session:s)
                drive.resumeTint(from:drive.frozenTint ?? tint(at:sample.date),toward:paletteTint(selected),date:sample.date)
            }
        }
    }
    private func move(_ sample:VelvetNapStudioTouchSample) {
        guard var s=session,s.id==sample.id,sample.point != s.lastPoint else{return}
        let dx=sample.point.x-s.lastPoint.x,dy=sample.point.y-s.lastPoint.y,dt=max(0.001,sample.date.timeIntervalSince(s.lastDate))
        let speed=hypot(dx,dy)/dt,theta=atan2(dy,dx)
        s.angle=VelvetNapStudioMath.angle(from:s.angle,to:theta,mix:1-exp(-dt/0.045))
        lastAngle=s.angle
        s.lastPoint=sample.point;s.lastDate=sample.date
        if hypot(sample.point.x-s.samplePoint.x,sample.point.y-s.samplePoint.y)>=4 {
            let current=held(s,sample.date),base=VelvetNapStudioMath.manualBaked(current)
            s.from = .init(time:current.time,authorWeight:current.authorWeight*current.strength,fields:[base],weights:[1])
            s.target=VelvetNapStudioMath.stamped(base,from:s.samplePoint,to:sample.point,angle:s.angle,width:52+min(14,speed*0.02),baseline:s.from)
            s.samplePoint=sample.point;s.stamp=sample.date;s.stampID=UUID();s.settled=false
            s.samples.append(sample.point)
            if s.samples.count>64 {s.samples=s.samples.enumerated().filter{$0.offset%2==0 || $0.offset==s.samples.count-1}.map(\.element)}
        }
        session=s
    }
    private func completeStamp() async {
        guard let id=session?.stampID else{return}
        do {try await Task.sleep(for:.milliseconds(95))} catch{return}
        guard !Task.isCancelled,var s=session,s.stampID==id else{return};s.settled=true;session=s
    }
    private func completeEvent() async {
        guard let e=event else{return}
        let duration=reduced ? 2.0:2.6
        do {try await Task.sleep(for:.seconds(max(0,duration-Date().timeIntervalSince(e.date))))} catch{return}
        guard !Task.isCancelled,event?.id==e.id else{return}
        epoch=e.date.addingTimeInterval(duration);event=nil;displayed.frame=nil;strokes.removeAll()
    }
    private func completeControl() async {
        guard !drive.isControlHeld,let c=drive.control else{return}
        let duration=reduced ? 2.0:2.6
        do {try await Task.sleep(for:.seconds(max(0,duration-Date().timeIntervalSince(c.date))))} catch{return}
        guard !Task.isCancelled,drive.control?.id==c.id else{return}
        epoch=c.date.addingTimeInterval(duration);drive.control=nil;displayed.frame=nil;displayed.date=nil;strokes.removeAll()
    }
    private func controlPressed(_ pressed:Bool) {
        guard visible,phase == .active else{return}
        let date=Date(),visibleTint=displayed.tint ?? tint(at:date)
        drive.isControlHeld=pressed
        captureControl(at:date)
        if pressed {drive.freezeTint(visibleTint)}
    }
    private func captureControl(at date:Date) {
        let captured=snapshot(date),visibleTint=displayed.tint ?? tint(at:date)
        event=nil
        if let active=session {
            session=VelvetNapStudioDriver.freeze(active,frame:captured,date:date)
            drive.control=nil;drive.freezeTint(visibleTint)
        } else {
            drive.captureControl(frame:captured,date:date)
            drive.resumeTint(from:visibleTint,toward:paletteTint(selected),date:date)
        }
    }
    private func clear() {
        session=nil;event=nil;drive.reset(tint:paletteTint(selected));addedAt=nil;touchGeneration=UUID();epoch=Date()
        displayed.frame=nil;displayed.tint=nil;displayed.date=nil;strokes.removeAll();lastAngle=atan2(-0.6,0.8)
    }
    private func tint(at date:Date)->SIMD3<Double> {drive.tint(at:date)}
    private func paletteTint(_ index:Int)->SIMD3<Double> {
        if index==3 {return SIMD3(repeating:1)}
        let rgb=VelvetNapStudioResources.colors[index]
        let target=SIMD3(Double((rgb>>16)&255)/255,Double((rgb>>8)&255)/255,Double(rgb&255)/255)
        // Fixed navy atlas to selected fabric palette; every variant keeps the same fine grain.
        return target/SIMD3(0.028,0.235,0.408)
    }
    private func choose(_ index:Int) {
        let now=Date(),visibleTint=displayed.tint ?? tint(at:now)
        captureControl(at:now)
        guard selected != index else{return}
        selected=index
        if session==nil {drive.resumeTint(from:visibleTint,toward:paletteTint(index),date:now)}
        UIImpactFeedbackGenerator(style:.light).impactOccurred()
    }
    private func completePalette() async {
        guard let p=drive.palette else{return}
        do {try await Task.sleep(for:.seconds(max(0,0.18-Date().timeIntervalSince(p.date))))} catch{return}
        if !Task.isCancelled,drive.palette?.id==p.id {drive.palette=nil}
    }
    private func save() {
        let now=Date();captureControl(at:now);addedAt=now
        UIImpactFeedbackGenerator(style:.soft).impactOccurred();UIAccessibility.post(notification:.announcement,argument:"Sample added")
    }
    private func completeAdded() async {
        guard let date=addedAt else{return};do {try await Task.sleep(for:.seconds(max(0,1.2-Date().timeIntervalSince(date))))} catch{return}
        if addedAt==date {addedAt=nil}
    }
    private func accessibleBrush(_ up:Bool) {
        let now=Date(),point=CGPoint(x:153.5,y:137)
        let from=VelvetNapStudioMath.coalesced(snapshot(now))
        let visibleTint=displayed.tint ?? tint(at:now)
        drive.control=nil;session=nil;drive.resumeTint(from:visibleTint,toward:paletteTint(selected),date:now)
        let base=VelvetNapStudioMath.manualBaked(from)
        let target=VelvetNapStudioMath.stamped(base,from:CGPoint(x:point.x-45,y:point.y+30),to:CGPoint(x:point.x+45,y:point.y-30),angle:atan2(-0.6,0.8)+(up ? 0:.pi),width:58,baseline:from)
        let s=VelvetNapStudioSession(id:UUID(),from:from,target:target,stamp:now,lastPoint:point,samplePoint:point,lastDate:now,angle:0,samples:[])
        event = .init(date:now,session:s)
    }
    private func controls(at date:Date)->some View {
        let ink=VelvetNapStudioResources.color(0x171C16),muted=VelvetNapStudioResources.color(0x787E72)
        let added=VelvetNapStudioDriver.confirmationVisible(addedAt,at:date)
        return ZStack(alignment:.topLeading) {
            Button {dismiss()} label:{Image(systemName:"chevron.left").font(.system(size:18)).frame(width:44,height:44)}.position(x:38,y:77).accessibilityLabel("Back")
            Text("Material studio").font(.system(size:13,weight:.medium)).position(x:196.5,y:79)
            Button(action:save) {Image(systemName:"bag").font(.system(size:18)).frame(width:44,height:44)}.position(x:355,y:77).accessibilityLabel("Sample bag")
            Color.clear.frame(width:307,height:274).position(x:196.5,y:258)
                .accessibilityElement().accessibilityLabel("Folded crushed velvet fabric")
                .accessibilityValue("Directional pile, \(VelvetNapStudioResources.names[selected])")
                .accessibilityHint("Brush the fabric in either direction to change the nap.")
                .accessibilityAction(named:"Brush up") {accessibleBrush(true)}
                .accessibilityAction(named:"Brush down") {accessibleBrush(false)}
            Circle().stroke(VelvetNapStudioResources.color(0xBDC8AB),lineWidth:1).frame(width:40,height:40)
                .position(x:45+43*Double(selected),y:422).animation(reduced ? nil:.spring(response:0.22,dampingFraction:0.85),value:selected).allowsHitTesting(false).accessibilityHidden(true)
            ForEach(0..<8,id:\.self) { index in
                Button {choose(index)} label:{thumbnail(index).frame(width:44,height:44).contentShape(Rectangle())}
                    .position(x:45+Double(index)*43,y:422).accessibilityLabel("\(VelvetNapStudioResources.names[index]) fabric")
                    .accessibilityAddTraits(selected==index ? .isSelected:[])
            }
            VStack(alignment:.leading,spacing:0) {
                Text("Crushed velvet").frame(height:22,alignment:.topLeading)
                Text("\(VelvetNapStudioResources.names[selected]) No. \(String(format:"%02d",selected+1))").frame(height:22,alignment:.topLeading)
            }.font(.system(size:18,weight:.semibold)).frame(width:303,height:44,alignment:.topLeading).position(x:175.5,y:485)
            Button {captureControl(at:Date());liked.toggle()} label:{Image(systemName:liked ? "heart.fill":"heart").font(.system(size:18)).frame(width:44,height:44)}.position(x:351,y:479).accessibilityLabel(liked ? "Remove favorite":"Favorite fabric")
            Text("$8.40 / yd").font(.system(size:18,weight:.semibold)).monospacedDigit().frame(width:190,height:26,alignment:.leading).position(x:119,y:529)
            HStack(spacing:3) {Image(systemName:"star.fill").font(.system(size:8));Text("4.8 (126 reviews)")}.font(.system(size:10)).frame(width:117,height:20,alignment:.trailing).position(x:310.5,y:531)
            Button {choose((selected+1)%8)} label:{HStack(spacing:15) {Text("Color").font(.system(size:10));Ellipse().fill(VelvetNapStudioResources.color(VelvetNapStudioResources.colors[selected])).frame(width:32,height:14);Spacer(minLength:0);Image(systemName:"chevron.down").font(.system(size:8))}.padding(.horizontal,17).frame(width:167,height:38).background(VelvetNapStudioResources.color(0xF0F2EB),in:Capsule()).frame(height:44)}.position(x:107.5,y:578).accessibilityLabel("Color, \(VelvetNapStudioResources.names[selected])")
            Button {captureControl(at:Date());yards=yards==8 ? 1:yards+1} label:{HStack {Text("Yards").font(.system(size:11));Spacer();Text("\(yards)").font(.system(size:12)).monospacedDigit();Image(systemName:"chevron.down").font(.system(size:8))}.padding(.horizontal,17).frame(width:167,height:38).background(VelvetNapStudioResources.color(0xF0F2EB),in:Capsule()).frame(height:44)}.position(x:285.5,y:578).accessibilityLabel("Yards, \(yards)")
            Text("Description").font(.system(size:13,weight:.semibold)).frame(width:345,height:20,alignment:.leading).position(x:196.5,y:627)
            VStack(alignment:.leading,spacing:0) {Text("A soft crushed pile with a quiet sheen.");Text("Brush both ways to feel the difference.")}.font(.system(size:12)).foregroundStyle(muted).lineSpacing(2).frame(width:345,height:34,alignment:.topLeading).position(x:196.5,y:664)
            ForEach(0..<4,id:\.self) {i in
                Circle().fill(VelvetNapStudioResources.color([0xD6D8CA,0xBFC4B1,0xE0DECC,0xC9CAB9][i])).frame(width:23,height:23)
                    .overlay(Image(systemName:["leaf","sparkle","circle.grid.2x2","scissors"][i]).font(.system(size:9)).foregroundStyle(VelvetNapStudioResources.color(0x626A58)))
                    .position(x:31+14*Double(i),y:707).accessibilityHidden(true)
            }
            Text("Made for slow projects").font(.system(size:11)).foregroundStyle(muted).frame(width:230,height:20,alignment:.leading).position(x:211,y:707)
            Capsule().fill(VelvetNapStudioResources.color(0xF0F2EB)).frame(width:99,height:52).position(x:73.5,y:781).accessibilityHidden(true)
            Button {captureControl(at:Date());yards=max(1,yards-1)} label:{Image(systemName:"minus").font(.system(size:12)).frame(width:44,height:52)}.position(x:46,y:781).accessibilityLabel("Decrease yards")
            Text("\(yards)").font(.system(size:12,weight:.medium)).monospacedDigit().position(x:73.5,y:781).accessibilityHidden(true)
            Button {captureControl(at:Date());yards=min(8,yards+1)} label:{Image(systemName:"plus").font(.system(size:12)).frame(width:44,height:52)}.position(x:101,y:781).accessibilityLabel("Increase yards")
            Button(action:save) {HStack(spacing:9) {Image(systemName:!added ? "bag":"checkmark").font(.system(size:16));Text(!added ? "Add to samples":"Sample added").font(.system(size:14,weight:.semibold))}.foregroundStyle(VelvetNapStudioResources.color(0x25310F)).frame(width:234,height:52).background(VelvetNapStudioResources.color(0xAFCC4F),in:Capsule())}.position(x:252,y:781).accessibilityLabel(!added ? "Add to samples":"Sample added")
        }.buttonStyle(VelvetNapStudioPressStyle(onPress:controlPressed)).foregroundStyle(ink).frame(width:393,height:852,alignment:.topLeading)
    }
    private func thumbnail(_ i:Int)->some View {
        VelvetNapStudioArt(frame:.rest,color:paletteTint(i))
            .scaleEffect(24/307,anchor:.topLeading)
            .frame(width:24,height:21.42,alignment:.topLeading).clipped().accessibilityHidden(true)
    }
}
