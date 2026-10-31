import SwiftUI
import UIKit

struct ClayReactionMorphBubbleKey: PreferenceKey {
  static var defaultValue = CGRect.zero
  // siblings that do not report anything pass the empty default: keep the bubble's frame
  static func reduce(value: inout CGRect, nextValue: () -> CGRect) { let n = nextValue(); if !n.isEmpty { value = n } }
}

struct ClayReactionMorph: View {
  typealias H = ClayReactionMorphTheme
  typealias M = ClayReactionMorphMotion
  static let loop = M.loop
  let autoPlay: Bool
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var driver = ClayReactionMorphDriver()
  @State private var bubble = CGRect(x:16,y:600,width:240,height:68)
  init(autoPlay: Bool = false) { self.autoPlay = autoPlay }
  var body: some View {
    GeometryReader { outer in
      let top = outer.safeAreaInsets.top
      let bottom = max(outer.safeAreaInsets.bottom,12)
      GeometryReader { geo in
        TimelineView(.animation(paused: scenePhase != .active || !driver.moving)) { timeline in
          let clock = timeline.date.timeIntervalSinceReferenceDate-driver.epoch
          let frame = driver.frame(clock)
          content(size:geo.size,top:top,bottom:bottom,clock:clock,frame:frame)
        }
      }
      .coordinateSpace(name:"ClayReactionMorphRoot")
      .ignoresSafeArea()
    }
    .background(H.page)
    .onAppear { driver.configure(auto:autoPlay,reduced:reduced) }
    .onChange(of:reduced) { _, value in driver.configure(auto:autoPlay,reduced:value) }
    .onChange(of:autoPlay) { _, value in driver.configure(auto:value,reduced:reduced) }
    .onPreferenceChange(ClayReactionMorphBubbleKey.self) { if !$0.isEmpty { bubble = $0 } }
    .preferredColorScheme(.dark)
  }
  private func content(size: CGSize, top: Double, bottom: Double, clock: Double,
                       frame f: ClayReactionMorphFrame) -> some View {
    let heroSize = min(132,size.width*0.33)
    let trayY = bubble.minY-16-44
    let heroY = max(top+56+24+heroSize,trayY-28-heroSize)
    return ZStack(alignment:.topLeading) {
      H.page
      header(top:top,width:size.width)
      thread(width:size.width,frame:f)
        .padding(.bottom,12)
        .frame(width:size.width,height:max(0,size.height-(top+56)-(bottom+60)),alignment:.bottom)
        .offset(y:top+56)
      composer(width:size.width).offset(x:16,y:size.height-bottom-44)
      shader(size:size,heroSize:heroSize,heroY:heroY,clock:clock,frame:f)
        .allowsHitTesting(false).accessibilityHidden(true)
      tray(frame:f)
        .offset(x:(size.width-288)/2-4,y:trayY+8*(1-f.hold))
        .opacity(M.smooth(0.2,1,f.hold)).accessibilityHidden(true).allowsHitTesting(false)
      Circle().fill(.white.opacity(0.4))
        .overlay(Circle().strokeBorder(.white.opacity(0.8),lineWidth:1.5))
        .frame(width:44,height:44).shadow(color:.black.opacity(0.2),radius:6,x:0,y:1)
        .scaleEffect(1-0.12*f.press)
        .offset(x:bubble.minX+bubble.width*0.42+f.fingerDx*72-22,y:bubble.midY-22)
        .opacity(0.85*f.finger).allowsHitTesting(false).accessibilityHidden(true)
      ClayReactionMorphTouchView { state, point, travel, speed in
        switch state {
        case .began: driver.begin(point,bubble:bubble)
        case .changed: driver.change(point,translation:travel,velocity:speed)
        case .ended,.cancelled,.failed: driver.end()
        default: break
        }
      }
      .frame(width:size.width,height:size.height)
      .accessibilityHidden(true)
    }
    .frame(width:size.width,height:size.height,alignment:.topLeading)
  }
  private func label(_ text: String, _ size: Double, _ color: Color, _ weight: String = "Regular") -> some View {
    Text(text).font(H.font(size,weight)).foregroundStyle(color)
  }
  private func header(top: Double, width: Double) -> some View {
    HStack(spacing:12) {
      Path { p in p.move(to:CGPoint(x:10,y:2)); p.addLine(to:CGPoint(x:2,y:10)); p.addLine(to:CGPoint(x:10,y:18)) }
        .stroke(H.head,style:StrokeStyle(lineWidth:2.4,lineCap:.round,lineJoin:.round))
        .frame(width:12,height:20).accessibilityLabel("Back").accessibilityAddTraits(.isButton)
      Circle().fill(H.color(0xE8A23A)).frame(width:36,height:36)
        .overlay(label("M",16,H.color(0x2A1A05),"SemiBold"))
      VStack(alignment:.leading,spacing:1) {
        label("Mai Tran",16,H.head,"SemiBold").frame(height:21).accessibilityAddTraits(.isHeader)
        label("Active now",13,H.mute).frame(height:17)
      }
      Spacer(minLength:0)
    }
    .padding(.horizontal,16).frame(width:width,height:56)
    .overlay(alignment:.bottom) { H.fieldLine.frame(height:1/UIScreen.main.scale) }
    .offset(y:top)
  }
  private func thread(width: Double, frame f: ClayReactionMorphFrame) -> some View {
    VStack(spacing:8) {
      label("Today 9:14 AM",12,H.mute,"Medium").frame(height:16).padding(.bottom,8)
      ForEach(0..<6,id:\.self) { index in
        message(index,width:width,frame:f)
      }
    }
    .padding(.horizontal,16)
  }
  private func message(_ index: Int, width: Double, frame f: ClayReactionMorphFrame) -> some View {
    let incoming = index%2 == 0
    let text = H.messages[index]
    let dimensions = bubbleSize(text,maxWidth:(width-32)*0.74)
    return HStack(spacing:0) {
      if !incoming { Spacer(minLength:0) }
      ClayReactionMorphLabel(text:text)
        .frame(width:dimensions.width-32,height:dimensions.height-22,alignment:.leading)
        .padding(.horizontal,16).padding(.vertical,11)
        .background(incoming ? H.incoming : H.outgoing,in:RoundedRectangle(cornerRadius:20,style:.continuous))
        .scaleEffect(index == 4 ? 1-0.03*f.press : 1)
        .background {
          if index == 4 {
            GeometryReader { proxy in
              Color.clear.preference(key:ClayReactionMorphBubbleKey.self,value:proxy.frame(in:.named("ClayReactionMorphRoot")))
            }
          }
        }
        .accessibilityElement(children:.ignore)
        .accessibilityLabel("\(incoming ? "Mai" : "You"): \(text)")
        .accessibilityValue(index == 4 ? "Your reaction: \(H.reactions[driver.badge])" : "")
        .accessibilityHint(index == 4 ? "Swipe up or down to change your reaction" : "")
        .modifier(ClayReactionMorphAdjustable(enabled:index == 4,action:driver.choose))
      if incoming { Spacer(minLength:0) }
    }
    .padding(.top,index == 4 ? 14 : 0)
  }
  private func bubbleSize(_ text: String, maxWidth: Double) -> CGSize {
    _ = H.register
    let font = UIFont(name:"Inter24pt-Regular",size:17) ?? .systemFont(ofSize:17)
    let unwrapped = (text as NSString).size(withAttributes:[.font:font]).width
    let textWidth = min(maxWidth-32,ceil(unwrapped))
    let rect = (text as NSString).boundingRect(with:CGSize(width:textWidth,height:1000),options:[.usesLineFragmentOrigin,.usesFontLeading],attributes:[.font:font],context:nil)
    let lines = max(1,ceil(rect.height/font.lineHeight))
    return CGSize(width:textWidth+32,height:lines*23+22)
  }
  private func composer(width: Double) -> some View {
    HStack(spacing:12) {
      Circle().fill(H.field).frame(width:36,height:36).overlay {
        Path { p in p.move(to:CGPoint(x:8,y:2)); p.addLine(to:CGPoint(x:8,y:14)); p.move(to:CGPoint(x:2,y:8)); p.addLine(to:CGPoint(x:14,y:8)) }
          .stroke(H.head,style:StrokeStyle(lineWidth:2,lineCap:.round)).frame(width:16,height:16)
      }.accessibilityLabel("Attach").accessibilityAddTraits(.isButton)
      label("Message",16,H.mute).padding(.horizontal,16)
        .frame(maxWidth:.infinity,minHeight:40,alignment:.leading)
        .background(H.field,in:Capsule()).overlay(Capsule().strokeBorder(H.fieldLine,lineWidth:1))
    }.frame(width:width-32,height:44)
  }
  private func tray(frame f: ClayReactionMorphFrame) -> some View {
    let k = min(3,max(0,f.r))
    return ZStack(alignment:.topLeading) {
      Capsule().fill(H.color(0x202028).opacity(0.92))
        .overlay(Capsule().strokeBorder(.white.opacity(0.08),lineWidth:1))
      Capsule().fill(H.blendedTint(k).opacity(0.24))
        .overlay(Capsule().strokeBorder(H.blendedTint(k).opacity(0.6),lineWidth:1))
        .frame(width:72,height:34).offset(x:4+k*72,y:4)
      HStack(spacing:0) {
        ForEach(0..<4,id:\.self) { i in
          label(H.reactions[i],14,H.labelColor(max(0,1-abs(f.r-Double(i)))),"SemiBold")
            .frame(width:72,height:44)
        }
      }.offset(x:4)
    }.frame(width:296,height:44)
  }
  private func shader(size: CGSize, heroSize: Double, heroY: Double, clock: Double,
                      frame f: ClayReactionMorphFrame) -> some View {
    let l = f.lift, arc = sin(.pi*M.clamp(l))
    let seatX = bubble.maxX-4, seatY = bubble.minY+2
    let x = seatX+(size.width/2-seatX)*l+36*arc
    let y = seatY+(heroY-seatY)*l-24*arc
    let radius = 11+(heroSize-11)*l
    let live = reduced ? 0 : M.smooth(0.7,1,l)
    let frac = f.r-floor(f.r)
    let yaw = (reduced ? 0 : 0.4*sin(.pi*frac))+0.12*live*sin(clock*1.3)
    return Rectangle().fill(.white)
      .colorEffect(ShaderLibrary.ClayReactionMorphMain(
        .float4(Float(x),Float(y),Float(radius),1),
        .float4(Float(f.r),Float(yaw),Float(f.lean),Float(f.squash)),
        .float4(Float(clock),Float(live),Float(M.smooth(0.35,1,l)),Float(UIScreen.main.scale)),
        .float4(Float(bubble.minX),Float(bubble.minY),Float(bubble.width),Float(bubble.height)),
        .float4(Float(f.hold),20,Float(1-0.03*f.press),0.043),
        .float4(Float(seatX),Float(seatY),17,Float(1-M.smooth(0,0.22,l)))))
      .frame(width:size.width,height:size.height)
  }
}

struct ClayReactionMorphLabel: UIViewRepresentable {
  let text: String
  func makeUIView(context: Context) -> UILabel {
    let label = UILabel(); label.numberOfLines = 0
    label.setContentCompressionResistancePriority(.defaultLow,for:.horizontal)
    return label
  }
  func updateUIView(_ label: UILabel, context: Context) {
    _ = ClayReactionMorphTheme.register
    let paragraph = NSMutableParagraphStyle(); paragraph.minimumLineHeight = 23; paragraph.maximumLineHeight = 23
    let index = ClayReactionMorphTheme.messages.firstIndex(of:text) ?? 0
    let ink = index%2 == 1 ? UIColor.white : UIColor(ClayReactionMorphTheme.head)
    label.attributedText = NSAttributedString(string:text,attributes:[
      .font:UIFont(name:"Inter24pt-Regular",size:17) ?? UIFont.systemFont(ofSize:17),
      .foregroundColor:ink,.paragraphStyle:paragraph])
  }
}
struct ClayReactionMorphAdjustable: ViewModifier {
  var enabled: Bool
  var action: (Int) -> Void
  @ViewBuilder func body(content: Content) -> some View {
    if enabled {
      content.accessibilityAdjustableAction { direction in
        switch direction { case .increment:action(1); case .decrement:action(-1); @unknown default:break }
      }
    } else { content }
  }
}
