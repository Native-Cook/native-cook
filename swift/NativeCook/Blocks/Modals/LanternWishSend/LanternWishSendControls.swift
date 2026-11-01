import SwiftUI
import UIKit

struct LanternWishSendControls: UIViewRepresentable {
  var frame: LanternWishSendMotion.Frame
  var pinned: Bool
  var clock: LanternWishSendClock
  func makeUIView(context: Context) -> LanternWishSendControlsView {
    let v = LanternWishSendControlsView()
    v.onSend = { clock.send() }; v.onFlick = { clock.flickUp($0) }
    return v
  }
  func updateUIView(_ view: LanternWishSendControlsView, context: Context) {
    view.sample = frame
    view.isUserInteractionEnabled = !pinned
  }
}

final class LanternWishSendControlsView: UIView, UITextViewDelegate {
  var onSend: (() -> Void)?, onFlick: ((Double) -> Void)?
  var sample = LanternWishSendMotion.Frame() { didSet { applyFrame() } }
  private let title = UILabel(), count = UILabel(), to = UILabel(), ctaText = UILabel()
  private let card = UIView(), flickHit = UIView(), button = UIButton(type: .custom)
  private let note = UITextView(), flame = UILabel(), placeholder = UILabel()
  private var oldCount = 0
  private func font(_ name: String, _ size: Double) -> UIFont {
    UIFont(name: name, size: size) ?? UIFont.systemFont(ofSize: size)
  }
  override init(frame: CGRect) {
    super.init(frame: frame)
    _ = LanternWishSendResources.shared
    backgroundColor = .clear
    func label(_ label: UILabel, _ text: String, _ face: String, _ size: Double, _ color: UInt32) {
      label.text = text; label.font = font(face, size); label.textColor = LanternWishSendResources.color(color)
      label.isAccessibilityElement = true
    }
    label(title, "Send a wish", "InstrumentSerif-Italic", 40, 0xFFF1DE)
    label(count, "1,248 lanterns lit tonight", "Inter24pt-Regular", 14, 0xD9C6F2)
    title.textAlignment = .center; count.textAlignment = .center
    addSubview(title); addSubview(count)
    addSubview(flickHit)
    flickHit.addGestureRecognizer(UIPanGestureRecognizer(target: self, action: #selector(pan(_:))))
    addSubview(card)
    card.backgroundColor = LanternWishSendResources.color(0xFFF6E9)
    card.layer.cornerRadius = 20
    card.layer.shadowColor = LanternWishSendResources.color(0xFFB45A).cgColor
    card.layer.shadowOpacity = 0.35; card.layer.shadowRadius = 30; card.layer.shadowOffset = .zero
    label(to, "To Grandma Lin", "Inter-SemiBold", 13, 0xB0705A)
    to.attributedText = NSAttributedString(string: "To Grandma Lin", attributes: [.font: to.font!, .foregroundColor: to.textColor!, .kern: 0.4])
    card.addSubview(to)
    note.backgroundColor = .clear
    note.textContainerInset = .zero; note.textContainer.lineFragmentPadding = 0
    note.isScrollEnabled = false
    note.accessibilityLabel = "Your wish"
    note.delegate = self
    let paragraph = NSMutableParagraphStyle(); paragraph.minimumLineHeight = 32; paragraph.maximumLineHeight = 32
    note.typingAttributes = [.font: font("InstrumentSerif-Italic", 26), .foregroundColor: LanternWishSendResources.color(0x3A1F4F), .paragraphStyle: paragraph]
    note.attributedText = NSAttributedString(string: "Happy 80th, Grandma Lin. Thank you for every bedtime story.", attributes: note.typingAttributes)
    card.addSubview(note)
    label(placeholder, "Write a wish…", "InstrumentSerif-Italic", 26, 0xB69A8A)
    placeholder.isAccessibilityElement = false
    placeholder.isUserInteractionEnabled = false
    placeholder.isHidden = true
    note.addSubview(placeholder)
    addSubview(button)
    button.backgroundColor = LanternWishSendResources.color(0xFFC46B); button.layer.cornerRadius = 29
    button.accessibilityLabel = "Light and send the lantern"
    button.addTarget(self, action: #selector(send), for: .touchUpInside)
    button.addTarget(self, action: #selector(press), for: .touchDown)
    button.addTarget(self, action: #selector(releasePress), for: [.touchUpInside, .touchUpOutside, .touchCancel])
    label(ctaText, "Light & send", "Inter-SemiBold", 17, 0x3A1F4F)
    ctaText.isAccessibilityElement = false
    button.addSubview(ctaText)
    flame.text = "\u{F313}"; flame.font = font("Ionicons", 20)
    flame.textColor = LanternWishSendResources.color(0x3A1F4F)
    flame.isAccessibilityElement = false
    button.addSubview(flame)
  }
  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
  override func layoutSubviews() {
    super.layoutSubviews()
    let w = bounds.width, h = bounds.height, cy = (h * 0.6).rounded()
    let safe = window?.safeAreaInsets ?? safeAreaInsets
    let titleH = ceil(title.font.lineHeight), countH = ceil(count.font.lineHeight)
    title.frame = CGRect(x: 24, y: safe.top + 16, width: w - 48, height: titleH)
    count.frame = CGRect(x: 24, y: title.frame.maxY + 4, width: w - 48, height: countH)
    flickHit.frame = CGRect(x: 0, y: safe.top + 100, width: w, height: max(0, cy - 100 - safe.top - 100))
    card.transform = .identity
    let noteHeight = ceil(note.sizeThatFits(CGSize(width: w - 120, height: .greatestFiniteMagnitude)).height)
    let cardHeight = max(180, 40 + ceil(to.font.lineHeight) + 8 + noteHeight)
    card.frame = CGRect(x: 40, y: cy - 90, width: w - 80, height: cardHeight)
    to.frame = CGRect(x: 20, y: 20, width: w - 120, height: ceil(to.font.lineHeight))
    note.frame = CGRect(x: 20, y: to.frame.maxY + 8, width: w - 120, height: noteHeight)
    placeholder.frame = CGRect(x: 0, y: 0, width: note.bounds.width, height: 32)
    button.bounds = CGRect(x: 0, y: 0, width: w - 40, height: 58)
    button.center = CGPoint(x: w / 2, y: h - safe.bottom - 18 - 29)
    ctaText.sizeToFit()
    let groupW = 20 + 8 + ctaText.bounds.width
    let textPixel = 1 / max(1, traitCollection.displayScale)
    flame.frame = CGRect(x: (button.bounds.width - groupW) / 2, y: 19 + textPixel, width: 20, height: 20)
    ctaText.frame.origin = CGPoint(x: flame.frame.maxX + 8, y: (58 - ctaText.bounds.height) / 2 + textPixel)
    applyFrame()
  }
  private func applyFrame() {
    if oldCount != sample.count {
      count.text = "\(sample.count == 1249 ? "1,249" : "1,248") lanterns lit tonight"
      oldCount = sample.count
    }
    let f = sample.sending ? sample.fold : 0
    let dy = sample.sending ? f * 40 : (1 - sample.back) * 20
    card.alpha = sample.sending ? 1 - f : sample.back
    card.transform = CGAffineTransform(translationX: 0, y: dy).scaledBy(x: 1 - 0.45 * f, y: 1 - 0.7 * f)
  }
  func textViewDidChange(_ textView: UITextView) {
    placeholder.isHidden = !textView.text.isEmpty
    setNeedsLayout()
  }
  func textView(_ textView: UITextView, shouldChangeTextIn range: NSRange, replacementText text: String) -> Bool {
    let available = max(0, 120 - ((textView.text as NSString).length - range.length))
    if text.utf16.count <= available { return true }
    var clipped = "", length = 0
    for character in text {
      let n = String(character).utf16.count
      if length + n > available { break }
      clipped.append(character); length += n
    }
    if !clipped.isEmpty {
      textView.textStorage.replaceCharacters(in: range, with: NSAttributedString(string: clipped, attributes: textView.typingAttributes))
      textView.selectedRange = NSRange(location: range.location + length, length: 0)
      textViewDidChange(textView)
    }
    return false
  }
  @objc private func send() { onSend?() }
  @objc private func press() { button.transform = CGAffineTransform(scaleX: 0.96, y: 0.96) }
  @objc private func releasePress() { button.transform = .identity }
  @objc private func pan(_ gesture: UIPanGestureRecognizer) {
    if gesture.state == .ended { onFlick?(gesture.velocity(in: self).y) }
  }
  override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
    let hit = super.hitTest(point, with: event)
    return hit === self ? nil : hit
  }
}
