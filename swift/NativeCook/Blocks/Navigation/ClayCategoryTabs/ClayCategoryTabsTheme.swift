import SwiftUI
import CoreText
import UIKit

enum ClayCategoryTabsTheme {
  static let searchH = 56.0, stageH = 244.0, iconY = 108.0, iconS = 76.0
  static let ghostS = 24.0, labelY = 204.0, hop = 30.0
  static let page = color(0x111015), card = color(0x1B1A21), line = color(0x2A2932)
  static let ink = color(0xF4F3F7), mute = color(0x8E8C99), accent = color(0xFF5A4F), star = color(0xFFC24A)
  static func color(_ hex: Int) -> Color {
    Color(.sRGB, red: Double((hex >> 16) & 255)/255, green: Double((hex >> 8) & 255)/255, blue: Double(hex & 255)/255, opacity: 1)
  }
  static let fontNames: [String] = {
    ["Regular", "Medium", "SemiBold"].map { weight in
      guard let url = Bundle.main.url(forResource: "ClayCategoryTabs-Inter-" + weight, withExtension: "ttf") else {
        return weight == "Regular" ? "Inter24pt-Regular" : "Inter-" + weight
      }
      CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
      let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor]
      return descriptors.flatMap { $0.first }.map { CTFontDescriptorCopyAttribute($0, kCTFontNameAttribute) as? String ?? "Inter-" + weight } ?? "Inter-" + weight
    }
  }()
  struct Listing {
    let title: String, meta: String, when: String, price: String, unit: String, rating: String
    var label: String { "\(title), \(meta), \(when), \(price) per \(unit), rated \(rating)" }
  }
  struct Category { let name: String, heading: String; let listings: [Listing] }
  static let categories: [Category] = [
    Category(name: "Homes", heading: "Stays near Lisbon", listings: [
      Listing(title: "Cabin above the tide", meta: "Ericeira · 2 beds", when: "Nov 12 – 17", price: "€142", unit: "night", rating: "4.92"),
      Listing(title: "Whitewashed loft in Alfama", meta: "Lisbon · 1 bed", when: "Nov 3 – 6", price: "€118", unit: "night", rating: "4.88"),
      Listing(title: "Cork-oak farmhouse", meta: "Alentejo · 4 beds", when: "Dec 1 – 5", price: "€205", unit: "night", rating: "4.97"),
    ]),
    Category(name: "Experiences", heading: "Things to do nearby", listings: [
      Listing(title: "Sunrise balloon over the plains", meta: "Évora · 3 hours", when: "Sat, 6:10 AM", price: "€190", unit: "guest", rating: "4.99"),
      Listing(title: "Paint your own azulejo", meta: "Lisbon · 2 hours", when: "Every day", price: "€45", unit: "guest", rating: "4.91"),
      Listing(title: "Surf the north swell", meta: "Ericeira · 4 hours", when: "Sun, 9:00 AM", price: "€60", unit: "guest", rating: "4.86"),
    ]),
    Category(name: "Services", heading: "Brought to your stay", listings: [
      Listing(title: "Private seafood dinner", meta: "Chef at home · up to 6", when: "Any evening", price: "€85", unit: "guest", rating: "4.95"),
      Listing(title: "Morning yoga on your terrace", meta: "60 minutes", when: "7 – 10 AM", price: "€40", unit: "guest", rating: "4.90"),
      Listing(title: "Luggage drop and city transfer", meta: "Door to door", when: "Any day", price: "€28", unit: "trip", rating: "4.84"),
    ]),
  ]
  struct Layout {
    let w: Double, top: Double
    var searchTop: Double { top + 8 }
    var stageTop: Double { searchTop + searchH + 8 }
    var pagerTop: Double { stageTop + stageH }
    var slot: Double { min(114, (w - 64)/3) }
    var xs: [Double] { [w/2 - slot, w/2, w/2 + slot] }
  }
}
