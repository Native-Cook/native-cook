import SwiftUI

struct ZodiacJellyDish: View {
  static let loop = ZodiacJellyDishMotion.loop
  var autoPlay = false
  @Environment(\.accessibilityReduceMotion) private var reduced
  @Environment(\.scenePhase) private var scene
  @Environment(\.displayScale) private var scale
  @StateObject private var clock = ZodiacJellyDishClock()
  private let resources = ZodiacJellyDishResources.shared
  private var pinned: Double? {
    #if DEBUG
    if let value = UserDefaults.standard.object(forKey:"zodiacJellyDishTime") {
      return (value as? NSNumber)?.doubleValue ?? Double("\(value)") ?? 0
    }
    #endif
    return nil
  }
  var body: some View {
    GeometryReader { safe in
      let top = safe.safeAreaInsets.top, bottom = safe.safeAreaInsets.bottom
      GeometryReader { full in
        let art = resources.scene(full.size,top)
        TimelineView(.animation(paused:pinned != nil || scene != .active || ((!autoPlay || reduced) && !clock.awake))) { timeline in
          let frame = clock.sample(timeline.date,autoPlay && !reduced,reduced,pinned)
          let size = full.size
          let center = CGPoint(x:size.width/2-2,y:top+293)
          let buyY = size.height-bottom-121
          ZStack(alignment:.topLeading) {
            Canvas(opaque:true,colorMode:.nonLinear) { c, size in
              c.fill(art.background,with:.color(Self.canvasColor(0x07080d)))
              for i in 0..<70 {
                let tw = 0.35+0.65*max(0,sin(2 * .pi * Double(1+i%3)*frame.t/8+Double(i)))
                let a = 0.08+0.3*tw*(Double(i%5)/5)
                c.fill(art.stars[i],with:.color(.white.opacity(a)))
              }
              c.fill(art.sheet,with:.color(Self.canvasColor(0x15161c)))
              if let shadow = resources.shadowImage {
                c.draw(shadow,in:CGRect(x:center.x-233.5,y:center.y-299.5,width:467,height:599))
              }
            }
            ZodiacJellyDishMetal(frame:frame,center:center,scale:scale)
            Circle().fill(Self.color(0x24252c))
              .overlay(Circle().strokeBorder(.white.opacity(0.18),lineWidth:1/scale))
              .overlay(Text("×").font(.system(size:24,weight:.light)).foregroundStyle(.white).offset(y:-7/3))
              .frame(width:36,height:36).position(x:size.width-36,y:top+44)
              .accessibilityElement(children:.ignore).accessibilityLabel("Close").accessibilityAddTraits(.isButton)
            Text("Virgos, don't let anyone crush your raspberries — keep them safe inside.")
              .font(.system(size:13.5)).foregroundStyle(.white.opacity(0.78))
              .lineSpacing(ZodiacJellyDishResources.bodyLeading)
              .multilineTextAlignment(.center)
              .frame(width:size.width-80,height:38,alignment:.top)
              .position(x:size.width/2,y:size.height-bottom-174+19+4/3)
            buy(frame)
              .frame(width:285,height:46).scaleEffect(frame.buyScale)
              .position(x:size.width/2,y:buyY+23)
            Text("Save for later").font(.system(size:16,weight:.medium)).foregroundStyle(.white)
              .frame(width:285,height:46).background(Self.color(0x2a2b31),in:Capsule())
              .overlay(Capsule().strokeBorder(.white.opacity(0.12),lineWidth:1/scale))
              .scaleEffect(1-frame.savePress*0.04)
              .position(x:size.width/2,y:buyY+84)
              .accessibilityAddTraits(.isButton).accessibilityAction { if pinned == nil { clock.save(reduced) } }
            if pinned == nil {
              ZodiacJellyDishTouch { phase, p in clock.touch(phase,p,size,top,bottom,reduced) }
                .accessibilityHidden(true)
            }
          }
          .frame(width:size.width,height:size.height,alignment:.topLeading)
        }
      }.ignoresSafeArea()
    }
    .background(Self.color(0x07080d)).preferredColorScheme(.dark)
    .persistentSystemOverlays(.hidden)
    .dynamicTypeSize(.large)
    .onAppear { resources.preload(!reduced); clock.reset() }
    .onDisappear { resources.preload(false); clock.reset() }
    .onChange(of:scene) { _, phase in resources.preload(phase == .active && !reduced); clock.reset() }
    .onChange(of:reduced) { _, value in resources.preload(!value); clock.reset() }
  }
  private func buy(_ frame: ZodiacJellyDishMotion.Frame) -> some View {
    ZStack {
      HStack(spacing:0) {
        Text("+").font(.system(size:22,weight:.light)).offset(y:-1).padding(.trailing,8)
        Text("$3.20").font(.system(size:16,weight:.semibold))
        Text("$4.00").font(.system(size:15,weight:.medium)).strikethrough().foregroundStyle(.white.opacity(0.7)).padding(.leading,8)
      }.opacity(1-frame.added).offset(y:-frame.added*10)
      Text("Added to your order").font(.system(size:16,weight:.semibold))
        .opacity(frame.added).offset(y:(1-frame.added)*10)
    }
    .foregroundStyle(.white).frame(width:285,height:46).background(Self.color(0xf26a1b),in:Capsule())
    .accessibilityElement(children:.ignore).accessibilityAddTraits(.isButton)
    .accessibilityLabel("+ $3.20 $4.00 Added to your order")
    .accessibilityAction { if pinned == nil { clock.add(reduced) } }
  }
  static func color(_ hex: Int) -> Color {
    Color(.sRGB,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,opacity:1)
  }
  private static func canvasColor(_ hex: Int) -> Color {
    Color(.displayP3,red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,opacity:1)
  }
}
