import SwiftUI
import CoreText

struct FriendRadarMapControls: View {
  typealias M = FriendRadarMapMotion
  typealias R = FriendRadarMapResources
  let frame: M.Frame
  let reduced: Bool
  let bottom: Double
  private func font(_ key:String,_ size:Double)->Font {
    if let face = R.shared.fonts[key] { return .custom(CTFontCopyPostScriptName(face) as String,fixedSize:size) }
    return .system(size:size)
  }
  var body: some View {
    let f = M.friends[frame.labelIndex]
    let opacity = reduced ? 1:(frame.since<0.3 && frame.prev != frame.cur ? 1-M.clamp(frame.since/0.25):M.clamp((frame.since-0.5)/0.4))
    let ordered = frame.fill>=1
    ZStack(alignment:.bottom) {
      VStack(spacing:4) {
        Text(f.street).font(font("street",14)).tracking(0.6).foregroundStyle(.white).offset(x:-0.3)
        Text(f.sooner).font(font("mono",11)).tracking(0.6).foregroundStyle(.white.opacity(0.6))
      }
      .fixedSize().offset(y:(1-opacity)*6).opacity(opacity)
      .padding(.bottom,bottom+120)
      .accessibilityHidden(true)
      HStack(spacing:0) {
        Text(ordered ? "✓":"›")
          .font(font("knob",28)).foregroundStyle(R.color(0x0b0b0d))
          // RN centers its explicit 32 pt line box, rather than the font's 37 pt box.
          .offset(x:-0.3,y:-2.5)
          .frame(width:52,height:52)
          .background(R.color(0xd63ce0),in:Circle())
          .overlay(Circle().strokeBorder(R.color(0x0b0b0d),lineWidth:2))
          .padding(.leading,6)
        Spacer(minLength:0)
        VStack(alignment:.trailing,spacing:0) {
          Text(ordered ? "RIDE ON":"HOLD TO ORDER")
          Text(ordered ? "THE WAY":"A RIDE FOR $5")
        }.font(font("order",13)).tracking(0.5).foregroundStyle(.white).padding(.trailing,24)
      }
      .padding(1).frame(height:64)
      .background {
        ZStack(alignment:.leading) {
          R.color(0x1b1b1f)
          GeometryReader { g in
            R.color(0xc932d4).frame(width:g.size.width*frame.fill)
          }.padding(1)
        }
      }
      .clipShape(Capsule())
      .overlay(Capsule().strokeBorder(.white.opacity(0.08),lineWidth:1))
      .padding(.horizontal,20).padding(.bottom,bottom+22)
      .accessibilityElement(children:.ignore).accessibilityAddTraits(.isButton)
      .accessibilityLabel(ordered ? "Ride on the way":"Order a ride to \(f.name.lowercased()) for 5 dollars, hold to confirm")
    }
    .frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.bottom)
    .allowsHitTesting(false)
  }
}
