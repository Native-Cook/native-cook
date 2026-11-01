import SwiftUI
import UIKit

extension PopUpQuestCardResources {
  func grid(_ l: PopUpQuestCardMotion.Layout, hidden: Int, pressed: Int = -1) -> UIImage {
    cached("grid-\(l.size)-\(l.top)-\(l.bottom)-\(hidden)-\(pressed)", l.size) { cv in
      let h0 = line(14) + 2 + line(26, true)
      text("Hi, Mia & Theo", 20, l.top + 8, 14, false, "#8C8A93")
      text("Where to next?", 20, l.top + 8 + line(14) + 2, 26, true)
      let avatarY = ((l.top + 8 + (h0 - 44) / 2) * UIScreen.main.scale).rounded() / UIScreen.main.scale
      rounded(cv, CGRect(x: l.w - 64, y: avatarY, width: 44, height: 44), 22, "#F6E3C8")
      icon("happy-outline", l.w - 53, avatarY + 11, 22, "#5A4A3A")
      func panel(_ r: CGRect, _ radius: Double, _ alpha: Double, _ blur: Double, _ offset: Double) {
        cv.saveGState(); cv.setShadow(offset: CGSize(width: 0, height: offset), blur: blur * 2, color: Self.color("#3A2E1F", alpha).cgColor)
        rounded(cv, r, radius, "#FFFFFF"); cv.restoreGState()
      }
      panel(CGRect(x: 16, y: l.top + 74, width: l.w - 32, height: 50), 25, 0.06, 12, 4)
      icon("search", 34, l.top + 90, 18, "#8C8A93")
      text("Search next adventure…", 62, l.top + 74 + (50 - line(15)) / 2, 15, false, "#A9A6B2")
      text("Recommended for you!", 20, l.top + 140, 20, true)
      for i in quests.indices {
        let q = quests[i], c = l.card(i), b = l.illustration(i)
        cv.saveGState()
        if pressed == i { cv.translateBy(x: c.midX, y: c.midY); cv.scaleBy(x: 0.96, y: 0.96); cv.translateBy(x: -c.midX, y: -c.midY) }
        panel(c, 26, 0.07, 14, 6)
        if hidden != i {
          cv.saveGState(); cv.addPath(CGPath(roundedRect: b, cornerWidth: 18, cornerHeight: 18, transform: nil)); cv.clip()
          cv.setFillColor(Self.color(q.bg).cgColor); cv.fill(b)
          let sc = max(b.width / 1000, b.height / 700)
          cv.translateBy(x: b.midX - 500 * sc, y: b.maxY - 700 * sc); cv.scaleBy(x: sc, y: sc)
          for art in q.art { art.draw(in: CGRect(x: -100, y: -100, width: 1200, height: 900)) }
          cv.restoreGState()
          rounded(cv, CGRect(x: b.maxX - 36, y: b.minY + 8, width: 28, height: 28), 14, "#FFFFFF", 0.85)
          icon("heart-outline", b.maxX - 29.5, b.minY + 14.5, 15, "#3A3442")
        }
        text(q.title, c.minX + 12, b.maxY + 10, 15, true, width: c.width - 20)
        text(q.price, c.minX + 12, b.maxY + 10 + line(15, true) + 2, 12, false, "#8C8A93")
        cv.restoreGState()
      }
      let y = l.h - l.bottom - 68, width = l.w - 48
      panel(CGRect(x: 24, y: y, width: width, height: 60), 30, 0.08, 16, 6)
      let gap = (width - 240) / 5
      var x = 24 + gap / 2
      for (i, name) in ["compass-outline", "heart-outline", "sparkles", "document-text-outline", "notifications-outline"].enumerated() {
        let w = i == 2 ? 64.0 : 44.0
        if i == 2 { rounded(cv, CGRect(x: x, y: y + 8, width: w, height: 44), 22, "#2A2633") }
        icon(name, x + (w - 21) / 2, y + 19.5, 21, i == 2 ? "#FFFFFF" : "#6B6876")
        x += w + gap
      }
    }
  }
  func sheetPositions() -> (cats: Double, sub: Double, acts: Double) {
    let cats = 29 + line(28, true) + 16
    let sub = cats + 52 + 6 + line(12) + 20
    return (cats, sub, sub + line(18, true) + 6)
  }
  func sheetBase(_ l: PopUpQuestCardMotion.Layout, _ active: Int, pressed: Int = -1) -> UIImage {
    let size = CGSize(width: l.w, height: l.h - l.sheetTop), q = quests[active]
    return cached("sheet-\(l.size)-\(l.bottom)-\(active)-\(pressed)", size) { cv in
      let r = CGRect(origin: .zero, size: size)
      let path = UIBezierPath(roundedRect: r, byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: 32, height: 32))
      cv.addPath(path.cgPath); cv.setFillColor(UIColor.white.cgColor); cv.fillPath()
      rounded(cv, CGRect(x: (l.w - 40) / 2, y: 10, width: 40, height: 5), 3, "#E6E1EA")
      text(q.title + "!", 20, 29, 28, true)
      text("Activities:", 20, sheetPositions().sub, 18, true)
      let y = size.height - l.bottom - 68
      cv.saveGState()
      if pressed == 5 { cv.translateBy(x: 20 + (l.w - 108) / 2, y: y + 28); cv.scaleBy(x: 0.96, y: 0.96); cv.translateBy(x: -20 - (l.w - 108) / 2, y: -y - 28) }
      rounded(cv, CGRect(x: 20, y: y, width: l.w - 108, height: 56), 28, "#2A2633")
      text("Book now · " + q.price.replacingOccurrences(of: "From ", with: ""), 20, y + (56 - line(17, true)) / 2, 17, true, "#FFFFFF", width: l.w - 108, center: true)
      cv.restoreGState(); cv.saveGState()
      if pressed == 6 { cv.translateBy(x: l.w - 48, y: y + 28); cv.scaleBy(x: 0.96, y: 0.96); cv.translateBy(x: -l.w + 48, y: -y - 28) }
      rounded(cv, CGRect(x: l.w - 76, y: y, width: 56, height: 56), 28, "#FDECEA")
      icon("calendar-outline", l.w - 59, y + 17, 22, "#E2574C")
      cv.restoreGState()
    }
  }
  func sheetItem(_ l: PopUpQuestCardMotion.Layout, _ active: Int, _ k: Int) -> UIImage {
    let q = quests[active], size = CGSize(width: l.w, height: l.h - l.sheetTop)
    return cached("item-\(l.size)-\(active)-\(k)", size) { cv in
      let positions = sheetPositions()
      if k < 4 {
        let x = 20 + Double(k) * ((l.w - 40 - 72) / 3), y = positions.cats
        rounded(cv, CGRect(x: x + 10, y: y, width: 52, height: 52), 18, q.bg)
        icon(q.cats[k][0], x + 25, y + 15, 22, q.tint)
        text(q.cats[k][1], x, y + 58, 12, false, "#6B6876", width: 72, center: true)
      } else {
        let a = q.acts[k - 4], y = positions.acts + Double(k - 4) * 72 + 8
        rounded(cv, CGRect(x: 20, y: y, width: l.w - 40, height: 64), 20, "#FBF8F3")
        rounded(cv, CGRect(x: 32, y: y + 11, width: 42, height: 42), 14, q.bg)
        icon(a["icon"]!, 43, y + 22, 20, q.tint)
        let textY = y + (64 - line(15, true) - 2 - line(12)) / 2
        text(a["title"]!, 86, textY, 15, true, width: l.w - 148)
        text(a["sub"]!, 86, textY + line(15, true) + 2, 12, false, "#8C8A93", width: l.w - 148)
        icon("chevron-forward", l.w - 50, y + 23, 18, "#B4B0BE")
      }
    }
  }
  func back() -> UIImage {
    cached("back", CGSize(width: 44, height: 44)) { cv in
      rounded(cv, CGRect(x: 0, y: 0, width: 44, height: 44), 22, "#FFFFFF", 0.92)
      icon("chevron-back", 11, 11, 22, "#2A2633")
    }
  }
}
