import QuartzCore
import SwiftUI
import UIKit

/// A chat composer whose message leaves as a real sheet of paper: the text types itself, send is pressed, the
/// message rises out of the field as flat paper, a 3D sheet takes over the same rectangle and folds itself into a
/// dart along its real creases (sixteen rigid hinged panels), then flies a banked arc over the thread and lands as
/// the new bubble, which pops in on a spring; "Delivered" follows.
///
/// Ported from the React Native block (react-native-filament + Skia): the same plane.glb, converted to USDZ and
/// drawn with SceneKit as a see-through layer over the SwiftUI chat (PaperPlaneSendScene3D), the same film
/// (PaperPlaneSendMotion). Touch: tap send, or drag it up and let go to throw (a harder flick flies a bigger arc).
/// Reduce Motion: the still with the message delivered.
struct PaperPlaneSend: View {
  typealias M = PaperPlaneSendMotion
  var autoPlay: Bool = true

  static let loop: Double = M.loop

  @Environment(\.accessibilityReduceMotion) private var reduceMotion
  @State private var controller: PaperPlaneSendController?
  @State private var pull: CGFloat = 0
  @State private var pressed = false

  private static let gutter: CGFloat = 16
  /// The composer: the field's height and radius, the send button's size, the gap between them, the bottom margin.
  private static let fieldH: CGFloat = 40
  private static let fieldR: CGFloat = 20
  private static let send: CGFloat = 36
  private static let sendGap: CGFloat = 8
  private static let rowBottom: CGFloat = 10
  private static let textX: CGFloat = 14
  private static let textLine: CGFloat = 21
  /// The thread rests this far above the composer; the sheet hangs this far above it.
  private static let threadGap: CGFloat = 16
  private static let sheetGap: CGFloat = 44
  private static let sheetPtMax: CGFloat = 200
  private static let paperR: CGFloat = 1.5
  private static let dot: CGFloat = 44
  /// A one-line bubble's height, the gap between bubbles and the receipt line's height.
  private static let bubbleH: CGFloat = 38
  private static let bubbleGap: CGFloat = 6
  private static let receiptH: CGFloat = 20
  private static let shift: CGFloat = bubbleH + bubbleGap + receiptH
  /// A drag up on the send button: how far it follows (pt), and what counts as a throw.
  private static let pullMax: CGFloat = 84
  private static let throwPt: CGFloat = 28
  private static let throwV: CGFloat = 500
  private static let throwVFull: CGFloat = 3000

  /// Where things are on this screen.
  private struct Layout {
    var W: CGFloat, H: CGFloat, top: CGFloat
    var fieldY: CGFloat, fieldW: CGFloat, sendX: CGFloat, sendY: CGFloat
    var sheetPt: CGFloat, sheetH: CGFloat, sheetCx: CGFloat, sheetCy: CGFloat
    var threadBottom: CGFloat, slotW: CGFloat
    var geo: M.Geo
  }

  private static func layout(_ geo: GeometryProxy) -> Layout {
    let insets = geo.safeAreaInsets
    let W = geo.size.width
    let H = geo.size.height + insets.top + insets.bottom
    let fieldY = H - insets.bottom - rowBottom - fieldH
    let fieldW = W - gutter * 2 - send - sendGap
    let sheetPt = min(sheetPtMax, W * 0.5)
    let sheetH = sheetPt * M.sheetW / M.sheetL
    let sheetCy = fieldY - sheetGap - sheetH / 2
    let threadBottom = fieldY - threadGap
    // the sent bubble's width: its one line of text and 14 pt each side, at most 82 % of the column
    let text = (M.message as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: 16)]).width
    let slotW = min((W - gutter * 2) * 0.82, ceil(text) + 28)
    let g = M.Geo(w: W, h: H, sheetX: W / 2, sheetY: sheetCy, slotX: W - gutter - slotW / 2,
                  slotY: threadBottom - receiptH - bubbleH / 2, sheetPt: sheetPt, amp: 1)
    return Layout(W: W, H: H, top: insets.top, fieldY: fieldY, fieldW: fieldW, sendX: W - gutter - send, sendY: fieldY + (fieldH - send) / 2,
                  sheetPt: sheetPt, sheetH: sheetH, sheetCx: W / 2, sheetCy: sheetCy, threadBottom: threadBottom, slotW: slotW, geo: g)
  }

  var body: some View {
    GeometryReader { geo in
      let l = Self.layout(geo)
      ZStack(alignment: .topLeading) {
        LinearGradient(colors: [Self.color(M.bgTop), Self.color(M.bg)], startPoint: .top, endPoint: .bottom)
        header(l)
        if let controller {
          TimelineView(.animation(paused: controller.reduced)) { _ in
            let f = controller.frame(at: CACurrentMediaTime())
            film(controller, l, t: f.t, geo: f.geo ?? l.geo)
          }
        }
      }
      .frame(width: l.W, height: l.H, alignment: .topLeading)
      .ignoresSafeArea()
    }
    .background(Self.color(M.bg).ignoresSafeArea())
    .preferredColorScheme(.dark)
    .statusBarHidden(false)
    .onAppear {
      if controller == nil { controller = PaperPlaneSendController(autoPlay: autoPlay, reduced: reduceMotion) }
    }
  }

  // MARK: layers

  private func header(_ l: Layout) -> some View {
    ZStack(alignment: .topLeading) {
      HStack(spacing: 0) {
        Circle()
          .fill(LinearGradient(colors: [Self.color(0xF2B36B), Self.color(0xE0764A), Self.color(0xB9483C)], startPoint: .topLeading, endPoint: .bottomTrailing))
          .frame(width: 36, height: 36)
          .overlay(Text(M.contactInitials).font(.system(size: 14, weight: .bold)).foregroundStyle(Self.color(0xFFF7EE)))
        VStack(alignment: .leading, spacing: 0) {
          Text(M.contact).font(.system(size: 16, weight: .semibold)).foregroundStyle(Self.color(M.ink)).frame(height: 20)
          Text("Active now").font(.system(size: 13)).foregroundStyle(Self.color(M.mute)).frame(height: 17)
        }
        .padding(.leading, 10)
        Spacer(minLength: 0)
        Text(M.app).font(.system(size: 13, weight: .semibold)).foregroundStyle(Self.color(M.ink, 0.8))
      }
      .frame(width: l.W - Self.gutter * 2, height: 44)
      .offset(x: Self.gutter, y: l.top + 6)
      Color.white.opacity(0.08).frame(width: l.W, height: 1 / 3).offset(y: l.top + 56)
    }
    .frame(width: l.W, height: l.H, alignment: .topLeading)
    .allowsHitTesting(false)
  }

  /// Everything that moves with the film's time: the thread, the composer, the shadow, the 2D paper, the 3D layer
  /// and the film's finger.
  private func film(_ c: PaperPlaneSendController, _ l: Layout, t: Double, geo g: M.Geo) -> some View {
    let ghost = M.ghostAt(t)
    return ZStack(alignment: .topLeading) {
      thread(l, t: t)
      composer(c, l, t: t, ghost: ghost.pressed)
      // the folding paper's shadow on the chat: under the paper, 2D and 3D
      shadow(l, t: t, geo: g)
      // the message as flat paper, until the 3D sheet takes over
      paper(l, t: t)
      PaperPlaneSendStage3D(controller: c, geo: l.geo)
        .frame(width: l.W, height: l.H)
        // (never quite 0, so the layer keeps drawing while the plane is parked at no size)
        .opacity(max(0.004, M.stageAlpha(t)))
        .allowsHitTesting(false)
      Circle()
        .fill(Color.white.opacity(0.3))
        .overlay(Circle().strokeBorder(Color.white.opacity(0.66), lineWidth: 1.5))
        .frame(width: Self.dot, height: Self.dot)
        .scaleEffect(1 - 0.16 * ghost.pressed)
        .opacity(ghost.opacity * 0.9)
        .offset(x: l.sendX + Self.send / 2 - Self.dot / 2, y: l.sendY + Self.send / 2 - Self.dot / 2)
        .allowsHitTesting(false)
    }
    .frame(width: l.W, height: l.H, alignment: .topLeading)
  }

  private func bubble(_ text: String, out: Bool, maxWidth: CGFloat) -> some View {
    let shape = UnevenRoundedRectangle(topLeadingRadius: 19, bottomLeadingRadius: out ? 19 : 6, bottomTrailingRadius: out ? 6 : 19,
                                       topTrailingRadius: 19, style: .circular)
    return Text(text)
      .font(.system(size: 16))
      .lineLimit(1)
      .foregroundStyle(Self.color(M.ink))
      .padding(.horizontal, 14)
      .frame(height: Self.bubbleH)
      .frame(maxWidth: maxWidth, alignment: .leading)
      .fixedSize(horizontal: true, vertical: false)
      .background(shape.fill(Self.color(out ? M.bubbleOut : M.bubbleIn)))
      .overlay(shape.strokeBorder(Color.white.opacity(out ? 0 : 0.06), lineWidth: 1 / 3))
  }

  /// The conversation: three bubbles that move up as one, and the new message's bubble with its receipt, which pops
  /// in where the plane lands.
  private func thread(_ l: Layout, t: Double) -> some View {
    let col = l.W - Self.gutter * 2
    let b = M.bubbleAt(t)
    let olderH = Self.bubbleH * 3 + Self.bubbleGap * 2
    return ZStack(alignment: .topLeading) {
      VStack(spacing: Self.bubbleGap) {
        ForEach(0..<M.thread.count, id: \.self) { i in
          bubble(M.thread[i].text, out: M.thread[i].out, maxWidth: col * 0.82)
            .frame(width: col, alignment: M.thread[i].out ? .trailing : .leading)
        }
      }
      .frame(width: col, height: olderH)
      .offset(x: Self.gutter, y: l.threadBottom - olderH - Self.shift * M.shiftAt(t))
      bubble(M.message, out: true, maxWidth: col * 0.82)
        .scaleEffect(b.scale, anchor: .bottomTrailing)
        .opacity(b.opacity)
        .accessibilityHidden(b.opacity < 0.5)
        .frame(width: col, alignment: .trailing)
        .offset(x: Self.gutter, y: l.threadBottom - Self.receiptH - Self.bubbleH)
      Text(M.delivered)
        .font(.system(size: 12, weight: .medium))
        .foregroundStyle(Self.color(M.mute))
        .frame(width: col - 4, height: Self.receiptH, alignment: .trailing)
        .opacity(M.deliveredAt(t))
        .accessibilityHidden(M.deliveredAt(t) < 0.5)
        .offset(x: Self.gutter, y: l.threadBottom - Self.receiptH)
    }
    .frame(width: l.W, height: l.H, alignment: .topLeading)
    .allowsHitTesting(false)
  }

  private func composer(_ c: PaperPlaneSendController, _ l: Layout, t: Double, ghost: Double) -> some View {
    let n = M.typedCount(t)
    return ZStack(alignment: .topLeading) {
      // the field: the message types itself into it (never focused, so no keyboard)
      ZStack(alignment: .leading) {
        RoundedRectangle(cornerRadius: Self.fieldR, style: .circular).fill(Self.color(M.field))
        RoundedRectangle(cornerRadius: Self.fieldR, style: .circular).strokeBorder(Color.white.opacity(0.12), lineWidth: 1 / 3)
        Text(n == 0 ? "Message" : String(M.message.prefix(n)))
          .font(.system(size: 16))
          .lineLimit(1)
          .foregroundStyle(Self.color(n == 0 ? M.mute : M.ink))
          .padding(.leading, Self.textX)
      }
      .frame(width: l.fieldW, height: Self.fieldH)
      .offset(x: Self.gutter, y: l.fieldY)
      .allowsHitTesting(false)
      .accessibilityLabel("Message")

      ZStack {
        Circle().fill(Self.color(0x363B46))
          // the button's contact shadow
          .shadow(color: .black.opacity(0.35), radius: 6, x: 0, y: 3)
        Circle().fill(Self.color(M.bubbleOut))
          .overlay(Circle().strokeBorder(Color.white.opacity(0.28), lineWidth: 1 / 3))
          .opacity(M.sendLitAt(t))
        Image(systemName: "arrow.up").font(.system(size: 17, weight: .bold)).foregroundStyle(.white)
      }
      .frame(width: Self.send, height: Self.send)
      .scaleEffect(1 - 0.1 * max(ghost, pressed ? 1 : 0))
      .animation(.easeOut(duration: pressed ? 0.09 : 0.16), value: pressed)
      .offset(y: pull)
      .padding(10)
      .contentShape(Rectangle())
      .gesture(sendGesture(c))
      .offset(x: l.sendX - 10, y: l.sendY - 10)
      .accessibilityElement()
      .accessibilityLabel("Send")
      .accessibilityHint("Sends the message as a paper plane; drag up to throw it")
      .accessibilityAddTraits(.isButton)
    }
    .frame(width: l.W, height: l.H, alignment: .topLeading)
  }

  /// A tap sends; a drag up makes the button follow (rubber-banded) and a release 28 pt up or faster than 500 pt/s
  /// throws: the harder the flick, the bigger (and so the faster) the flight.
  private func sendGesture(_ c: PaperPlaneSendController) -> some Gesture {
    DragGesture(minimumDistance: 0)
      .onChanged { v in
        if !pressed { pressed = true }
        let dy = v.translation.height
        guard abs(dy) >= 8 || pull != 0 else { return }
        let up = dy < 0 ? -dy : 0
        pull = -Self.pullMax * (1 - 1 / (up / Self.pullMax + 1))
      }
      .onEnded { v in
        let dy = v.translation.height
        let vy = (v.predictedEndTranslation.height - dy) / 0.25
        let dragged = pull != 0 || abs(dy) >= 8 || abs(v.translation.width) >= 8
        pressed = false
        if !dragged {
          c.send(CACurrentMediaTime(), amp: 1)
        } else if dy < -Self.throwPt || vy < -Self.throwV {
          let k = min(1, max(0, (-vy - Self.throwV) / (Self.throwVFull - Self.throwV)))
          c.send(CACurrentMediaTime(), amp: 1 + (M.throwMax - 1) * Double(k))
        }
        withAnimation(.interpolatingSpring(mass: 1, stiffness: 260, damping: 15, initialVelocity: pull == 0 ? 0 : Double(vy / -pull))) { pull = 0 }
      }
  }

  /// Under the paper: its soft shadow on the chat. One radial gradient, squashed and moved; no blur.
  private func shadow(_ l: Layout, t: Double, geo g: M.Geo) -> some View {
    let s = M.shadowAt(t, g)
    return Canvas { ctx, _ in
      guard s.alpha > 0.002 else { return }
      var c = ctx
      c.opacity = s.alpha
      c.translateBy(x: s.x, y: s.y)
      c.scaleBy(x: s.rx, y: s.ry)
      let k = { (a: Double) in Color.black.opacity(a) }
      c.fill(Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2)),
             with: .radialGradient(Gradient(stops: [.init(color: k(0.95), location: 0), .init(color: k(0.6), location: 0.45),
                                                    .init(color: k(0.18), location: 0.78), .init(color: k(0), location: 1)]),
                                   center: .zero, startRadius: 0, endRadius: 1))
    }
    .frame(width: l.W, height: l.H)
    .allowsHitTesting(false)
  }

  /// The 2D paper: from the field's rectangle to the sheet's (size and corners change for 0.4 s).
  private func paper(_ l: Layout, t: Double) -> some View {
    let k = M.liftAt(t)
    let w = l.fieldW + (l.sheetPt - l.fieldW) * k
    let h = Self.fieldH + (l.sheetH - Self.fieldH) * k
    let cx = Self.gutter + l.fieldW / 2 + (l.sheetCx - Self.gutter - l.fieldW / 2) * k
    let cy = l.fieldY + Self.fieldH / 2 + (l.sheetCy - l.fieldY - Self.fieldH / 2) * k
    let alpha = M.paperAlpha(t)
    return ZStack(alignment: .topLeading) {
      RoundedRectangle(cornerRadius: Self.fieldR + (Self.paperR - Self.fieldR) * k, style: .circular)
        .fill(Self.color(M.paper))
        .shadow(color: .black.opacity(0.1 + 0.3 * k), radius: 4 + 14 * k, x: 4, y: 12)
      Text(M.message)
        .font(.system(size: 16))
        .lineLimit(1)
        .truncationMode(.tail)
        .foregroundStyle(Self.color(M.paperInk))
        .frame(width: max(0, w - Self.textX), height: Self.textLine, alignment: .leading)
        .opacity(M.wordsAlpha(t))
        .offset(x: Self.textX, y: (Self.fieldH - Self.textLine) / 2)
    }
    .frame(width: w, height: h, alignment: .topLeading)
    .opacity(alpha)
    .offset(x: cx - w / 2, y: cy - h / 2)
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }

  static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
    Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: a)
  }
}
