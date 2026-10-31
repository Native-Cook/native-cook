import SwiftUI
import UIKit

struct ClayPrompt:View {
  static let loop=ClayPromptMotion.loop
  var autoPlay:Bool
  init(autoPlay:Bool=false) { self.autoPlay=autoPlay }
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var phase
  @Environment(\.displayScale) private var displayScale
  @StateObject private var driver=ClayPromptDriver()
  private var pin:Double? {
    #if DEBUG
    if let v=UserDefaults.standard.object(forKey:"ncPinTime") { return (v as? NSNumber)?.doubleValue ?? Double("\(v)") }
    #endif
    return nil
  }
  var body:some View {
    GeometryReader { safe in
      GeometryReader { geometry in
        let l=ClayPromptTheme.Layout(geometry.size.width,geometry.size.height,safe.safeAreaInsets.top,safe.safeAreaInsets.bottom)
        TimelineView(.animation(paused:pin != nil || phase != .active || idle)) { timeline in
          let sample=driver.sample(timeline.date,autoPlay,reduced,pin)
          let f=sample.0, clock=sample.1
          let value=driver.useUserText || reduced || !autoPlay ? driver.text : String(ClayPromptTheme.prompt.prefix(f.chars))
          scene(l,f,clock,value,timeline.date)
            .onChange(of:clock) { _,_ in driver.tick(f,clock,l,autoPlay,reduced) }
        }
        .onReceive(NotificationCenter.default.publisher(for:UIResponder.keyboardWillChangeFrameNotification)) { note in
          guard let frame=note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
          let lift=max(0,geometry.size.height-frame.minY-safe.safeAreaInsets.bottom)
          withAnimation(.easeOut(duration:0.25)) { driver.keyboard=lift }
        }
        .onReceive(NotificationCenter.default.publisher(for:UIResponder.keyboardWillHideNotification)) { _ in
          withAnimation(.easeOut(duration:0.25)) { driver.keyboard=0 }
        }
      }.ignoresSafeArea()
    }
    .background(ClayPromptTheme.page).preferredColorScheme(.dark)
    .onAppear { driver.manual = !autoPlay || reduced;driver.useUserText = !autoPlay || reduced;driver.epoch=Date() }
    .onChange(of:reduced) { _,r in
      driver.manual = !autoPlay || r;driver.useUserText = !autoPlay || r;driver.epoch=Date();driver.dust.reset()
    }
  }
  private var idle:Bool {
    if autoPlay && !reduced { return false }
    let now=Date()
    if now.timeIntervalSince(driver.genStart)<driver.genDuration*(driver.curve == 2 ? 1.5 : 1) || now.timeIntervalSince(driver.popStart)<2 { return false }
    return reduced || driver.kinds.isEmpty
  }
  private func scene(_ l:ClayPromptTheme.Layout,_ f:ClayPromptMotion.Frame,_ clock:Double,_ value:String,_ now:Date)->some View {
    let look=ClayPromptMotion.look(f.g)
    let xs=driver.manual || reduced || !autoPlay ? driver.sampleXs(now,l) : (0..<4).map { ClayPromptMotion.slotX($0,max(1,f.pops.reduce(0,+)),l.w) }
    let kinds=driver.manual || reduced || !autoPlay ? driver.slotKinds : [0,1,2,3]
    return ZStack(alignment:.topLeading) {
      ClayPromptTheme.page
      RoundedRectangle(cornerRadius:28)
        .stroke(ClayPromptTheme.line,style:StrokeStyle(lineWidth:1.5,dash:[6,6]))
        .frame(width:l.cardW-1,height:l.cardH-1)
        .position(x:l.cx,y:l.cardY+l.cardH/2)
        .opacity((1-look.reveal*(1-f.clear))*0.9)
      if reduced {
        shader(l,0,clock,f.pops,xs,kinds,f.clear).opacity(1-f.g)
        shader(l,1,clock,f.pops,xs,kinds,f.clear).opacity(f.g)
      } else {
        shader(l,f.g,clock,f.pops,xs,kinds,f.clear)
        Canvas { context,_ in
          driver.dust.update(clock,f.g,f.clear,l.cx,l.cy)
          driver.dust.draw(&context)
        }.allowsHitTesting(false)
      }
      bar(l)
      ZStack {
        VStack(spacing:8) {
          Text("Describe a scene").font(ClayPromptTheme.font(20,"SemiBold")).foregroundStyle(ClayPromptTheme.ink)
          Text("Its things turn into clay. Try a cat, a sofa, the moon or a plant.")
            .font(ClayPromptTheme.font(15)).foregroundStyle(ClayPromptTheme.mute)
            .lineSpacing(3).multilineTextAlignment(.center)
        }.padding(.horizontal,32)
          .opacity((1-min(1,f.g*3))*(1-min(1,f.pops.reduce(0) { $0+max(0,$1) })))
        ClayPromptTouchView { state,translation,velocity in
          handleDrag(state,translation,velocity,f,l)
        }
      }
      .frame(width:l.cardW,height:l.cardH).position(x:l.cx,y:l.cardY+l.cardH/2)
      .accessibilityElement(children:.ignore).accessibilityAddTraits(.isImage)
      .accessibilityLabel(driver.generated ? "Picture: \(value.isEmpty ? ClayPromptTheme.prompt : value), sculpted in clay" : "Empty picture. Describe a scene below.")
      .accessibilityHint(driver.generated ? "Drag down to melt it back into the prompt" : "")
      HStack(spacing:12) {
        Text("“\(value.isEmpty ? ClayPromptTheme.prompt : value)”").font(ClayPromptTheme.font(15)).foregroundStyle(ClayPromptTheme.mute).lineLimit(1)
        Spacer(minLength:0)
        if !autoPlay || driver.generated {
          Button { driver.takeOver(f,l);driver.animate(0,reduced ? 0.2 : 0.9,1);driver.generated=false } label: {
            Text("Remix").font(ClayPromptTheme.font(14,"Medium")).foregroundStyle(ClayPromptTheme.ink)
              .padding(.horizontal,12).frame(height:32).background(ClayPromptTheme.surface,in:Capsule())
          }.accessibilityHint("Melts the picture back into clay on the prompt box")
        }
      }.padding(.horizontal,4).frame(width:l.cardW,height:32).position(x:l.cx,y:l.captionTop+16)
        .opacity(ClayPromptMotion.clamp01((f.g-0.8)/0.2)*(1-f.clear))
      composer(l,f,value).position(x:l.cx,y:l.composerTop+60-driver.keyboard)
      if f.finger>0 {
        let x=f.fingerAt == 0 ? l.w-16-12-58 : l.cx+40
        let y=f.fingerAt == 0 ? l.composerTop+120-12-22 : l.cardY+l.cardH*0.4+f.dragY
        Circle().fill(.white.opacity(0.16*f.finger))
          .overlay(Circle().stroke(.white.opacity(0.55*f.finger),lineWidth:1.5))
          .frame(width:44*(1-0.12*f.press),height:44*(1-0.12*f.press)).position(x:x,y:y).allowsHitTesting(false)
      }
    }.frame(width:l.w,height:l.h,alignment:.topLeading).coordinateSpace(name:"ClayPromptScreen").clipped()
  }
  private func shader(_ l:ClayPromptTheme.Layout,_ g:Double,_ clock:Double,_ pops:[Double],_ xs:[Double],_ kinds:[Int],_ wipe:Double)->some View {
    let look=ClayPromptMotion.look(g)
    let sofa=kinds.enumerated().contains { $0.element == 1 && pops[$0.offset]>0.01 }
    var objects=[Shader.Argument](),properties=[Shader.Argument]()
    for i in 0..<4 {
      let k=i<kinds.count ? kinds[i] : i
      let c=ClayPromptMotion.composed(k,sofa,16,l.cardY,l.cardW,l.cardH)
      let pose=ClayPromptMotion.poseAt(g,xs[i],l.tokenY-driver.keyboard,34,c.0,c.1,c.2,l.cx,l.cy)
      objects.append(.float4(Float(pose.x),Float(pose.y),Float(pose.size),Float(max(0,pops[i])*(1-wipe))))
      let turn=reduced ? 0 : 0.28*sin(clock*1.1+Double(i)*1.7)*(1-look.reveal*0.6)
      let squash=pose.squash+0.5*max(-0.2,min(0.2,pops[i]-1))
      properties.append(.float4(Float(k),Float(turn),Float(squash),Float(0.6+0.4*look.night)))
    }
    let args=objects+properties+[
      .float4(Float(l.cx),Float(l.cy),Float(look.k),Float(look.pull)),
      .float4(16,Float(l.cardY),Float(l.cardW),Float(l.cardH)),
      .float2(Float(look.reveal),Float(look.night)),.float(Float(look.blobR)),
      .float(Float(1-wipe)),.float(Float(reduced ? 0 : clock)),.float(Float(displayScale))]
    return Rectangle().fill(.white).colorEffect(Shader(function:ShaderFunction(library:.default,name:"ClayPromptMain"),arguments:args)).allowsHitTesting(false)
  }
  private func bar(_ l:ClayPromptTheme.Layout)->some View {
    HStack {
      Button {} label: {
        Path { p in p.move(to:CGPoint(x:4,y:6.5));p.addLine(to:CGPoint(x:16,y:6.5));p.move(to:CGPoint(x:4,y:13.5));p.addLine(to:CGPoint(x:12,y:13.5)) }
          .stroke(ClayPromptTheme.ink,style:StrokeStyle(lineWidth:1.8,lineCap:.round))
          .frame(width:20,height:20).frame(width:40,height:40).background(ClayPromptTheme.surface,in:Circle())
      }.accessibilityLabel("Menu")
      Spacer()
      Text("Mold").font(ClayPromptTheme.font(17,"SemiBold")).foregroundStyle(ClayPromptTheme.ink)
      Spacer()
      HStack(spacing:6) {
        ClayPromptSpark().fill(ClayPromptTheme.accent).frame(width:14,height:14)
        Text("24").font(ClayPromptTheme.font(15,"SemiBold")).foregroundStyle(ClayPromptTheme.ink).monospacedDigit()
      }.padding(.horizontal,12).frame(height:32).background(ClayPromptTheme.surface,in:Capsule())
        .accessibilityElement(children:.ignore).accessibilityLabel("24 credits")
    }.frame(width:l.w-32,height:44).position(x:l.cx,y:l.barTop+22)
  }
  private func composer(_ l:ClayPromptTheme.Layout,_ f:ClayPromptMotion.Frame,_ value:String)->some View {
    VStack(spacing:0) {
      ZStack(alignment:.topLeading) {
        ClayPromptWordLayout {
          if value.isEmpty {
            if driver.focused { caret }
            Text("Describe a scene…").font(ClayPromptTheme.font(17)).foregroundStyle(ClayPromptTheme.faint).frame(height:24).padding(.horizontal,2).padding(.trailing,5)
          } else {
            let words=value.components(separatedBy:" ")
            ForEach(Array(words.enumerated()),id:\.offset) { i,word in
              let k=ClayPromptTheme.kind(word)
              let complete=k != nil && (driver.useUserText || i<words.count-1 || f.chars>=ClayPromptTheme.prompt.count)
              Text(word).font(ClayPromptTheme.font(17)).foregroundStyle(complete ? ClayPromptTheme.tints[k!] : ClayPromptTheme.ink)
                .frame(height:24).padding(.horizontal,2)
                .background(complete ? ClayPromptTheme.tints[k!].opacity(0.15) : .clear,in:RoundedRectangle(cornerRadius:6))
                .background(GeometryReader { wordGeo in
                  let rect=wordGeo.frame(in:.named("ClayPromptScreen"))
                  Color.clear.onAppear { if let k { driver.wordPoints[k]=CGPoint(x:rect.midX,y:rect.midY) } }
                    .onChange(of:rect) { _,r in if let k { driver.wordPoints[k]=CGPoint(x:r.midX,y:r.midY) } }
                }).padding(.trailing,5)
            }
            if driver.focused || (!driver.useUserText && f.chars>0 && f.chars<ClayPromptTheme.prompt.count) { caret }
          }
        }.padding(2).opacity(1-f.clear).allowsHitTesting(false)
        ClayPromptInput(value:value,onChange:{ driver.edit($0,f,l,reduced) },onFocus:{ focus in
          if focus { driver.takeOver(f,l);driver.useUserText=true }
          driver.focused=focus;driver.lastTouch=Date()
        })
      }.frame(maxWidth:.infinity,maxHeight:.infinity)
      HStack {
        Text("Clay").font(ClayPromptTheme.font(14,"Medium")).foregroundStyle(ClayPromptTheme.mute)
          .padding(.horizontal,12).frame(height:32).overlay(Capsule().stroke(ClayPromptTheme.line,lineWidth:1/displayScale))
          .accessibilityLabel("Style: clay")
        Spacer()
        Button {
          driver.takeOver(f,l)
          guard f.pops.reduce(0,+)>=0.5 || !driver.kinds.isEmpty else { return }
          UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder),to:nil,from:nil,for:nil)
          driver.animate(1,reduced ? 0.3 : 2.4*(1-driver.currentG(Date())))
        } label: {
          HStack(spacing:8) {
            ClayPromptSpark().fill(ClayPromptTheme.accentInk).frame(width:16,height:16)
            Text("Generate").font(ClayPromptTheme.font(16,"SemiBold")).foregroundStyle(ClayPromptTheme.accentInk)
          }.padding(.horizontal,16).frame(height:44).background(ClayPromptTheme.accent,in:Capsule())
        }.buttonStyle(ClayPromptPressStyle(reduced:reduced))
          .disabled(!autoPlay && driver.kinds.isEmpty)
          .opacity(!autoPlay && driver.kinds.isEmpty ? 0.4 : 1)
          .scaleEffect(1-0.04*f.press)
      }
    }.padding(12).frame(width:l.w-32,height:120)
      .background(ClayPromptTheme.surface,in:RoundedRectangle(cornerRadius:24))
      .overlay(RoundedRectangle(cornerRadius:24).stroke(ClayPromptTheme.line,lineWidth:1/displayScale))
  }
  private var caret:some View { RoundedRectangle(cornerRadius:1).fill(ClayPromptTheme.accent).frame(width:2,height:20).padding(.leading,-2) }
  private func handleDrag(_ state:UIGestureRecognizer.State,_ translation:CGSize,_ velocity:CGVector,_ f:ClayPromptMotion.Frame,_ l:ClayPromptTheme.Layout) {
    if state == .began {
      driver.takeOver(f,l);driver.touchDown=true;driver.dragFrom=f.g;driver.dragCaught=false;driver.dragFailed=false
      if f.g>=0.5 { driver.genDuration=0;driver.endG=f.g }
    } else if state == .changed {
      driver.lastTouch=Date()
      if !driver.dragCaught && abs(translation.width)>16 { driver.dragFailed=true }
      if !driver.dragFailed && translation.height>8 { driver.dragCaught=true }
      guard driver.dragFrom>=0.5,driver.dragCaught else { return }
      driver.endG=ClayPromptMotion.clamp01(driver.dragFrom-max(0,translation.height)/340);driver.genDuration=0;driver.revision += 1
    } else if state == .ended || state == .cancelled || state == .failed {
      driver.touchDown=false;driver.lastTouch=Date()
      guard driver.dragFrom>=0.5 else { return }
      let back=translation.height>150 || velocity.dy>900
      driver.animate(back ? 0 : 1,back ? 0.7 : 0.52,2,-velocity.dy/340)
      if back { driver.generated=false }
    }
  }
}

struct ClayPromptSpark:Shape {
  func path(in rect:CGRect)->Path {
    var p=Path();p.move(to:CGPoint(x:7,y:1))
    p.addCurve(to:CGPoint(x:13,y:7),control1:CGPoint(x:7.6,y:4.6),control2:CGPoint(x:9.4,y:6.4))
    p.addCurve(to:CGPoint(x:7,y:13),control1:CGPoint(x:9.4,y:7.6),control2:CGPoint(x:7.6,y:9.4))
    p.addCurve(to:CGPoint(x:1,y:7),control1:CGPoint(x:6.4,y:9.4),control2:CGPoint(x:4.6,y:7.6))
    p.addCurve(to:CGPoint(x:7,y:1),control1:CGPoint(x:4.6,y:6.4),control2:CGPoint(x:6.4,y:4.6));p.closeSubpath()
    return p.applying(CGAffineTransform(scaleX:rect.width/14,y:rect.height/14))
  }
}
struct ClayPromptWordLayout:Layout {
  func sizeThatFits(proposal:ProposedViewSize,subviews:Subviews,cache:inout ())->CGSize {
    arrange(proposal.width ?? 340,subviews).0
  }
  func placeSubviews(in bounds:CGRect,proposal:ProposedViewSize,subviews:Subviews,cache:inout ()) {
    let positions=arrange(bounds.width,subviews).1
    for i in subviews.indices { subviews[i].place(at:CGPoint(x:bounds.minX+positions[i].x,y:bounds.minY+positions[i].y),anchor:.topLeading,proposal:.unspecified) }
  }
  private func arrange(_ width:Double,_ subviews:Subviews)->(CGSize,[CGPoint]) {
    var x=0.0,y=0.0,positions=[CGPoint]()
    for view in subviews {
      let size=view.sizeThatFits(.unspecified)
      if x+size.width>width && x>0 { x=0;y += 26 }
      positions.append(CGPoint(x:x,y:y));x += size.width
    }
    return (CGSize(width:width,height:y+24),positions)
  }
}
struct ClayPromptPressStyle:ButtonStyle {
  var reduced:Bool
  func makeBody(configuration:Configuration)->some View {
    configuration.label.scaleEffect(configuration.isPressed && !reduced ? 0.96 : 1).animation(.easeOut(duration:0.15),value:configuration.isPressed)
  }
}
