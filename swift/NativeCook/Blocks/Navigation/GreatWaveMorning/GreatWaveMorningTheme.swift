import SwiftUI

enum GreatWaveMorningTheme {
  static let REF_W = 402.0
  static let REF_H = 874.0
  static let S = 402.0 / 736.0
  static let DY = 40.0
  static let PIN_W = 736.0
  static let COLLAPSE = 340.0
  static let MAX_SCROLL = 430.0
  enum C {

    static let sky = 0x133157
    static let skyLow = 0x1B3D69
    static let pill = 0x3D6BAE
    static let cloud = 0xE4D1B0
    static let cloudShade = 0xCDB48C
    static let bands: [Int] = [
      0x2A4F82, 0x3B67A2, 0x24497C, 0x4F7FBA, 0x2D578F, 0x5A8BC6, 0x264C85, 0x4373B0,
    ]
    static let river = 0x7AA6D6
    static let riverLow = 0x5A8CC2
    static let riverEdge = 0x3A6BA0
    static let crest = 0xEFE9DF
    static let crestShade = 0xC9C2B6
    static let page = 0xF2ECDF
    static let card = 0xEEE5CF
    static let cardEdge = 0xC9B99B
    static let navy = 0x0F2347
    static let blue = 0x2F5E9E
    static let tan = 0xC39E67
    static let green = 0x21704A
    static let grey = 0x6B6457
    static let time = 0xA08F70
    static let line = 0xC2AE8E
    static let row = 0xFAF6EF
    static let tabBg = 0xF7F0E4
    static let tabIcon = 0xB49E7C
    static let avatar = 0xFAF6EE
    static let avatarRing = 0xC9A56A
    static let boat = 0xF3EEE6
    static let sail = 0xFBF8F2
    static let sailShade = 0xD9D2C6

  }
  struct Stat {
    let label: String
    let value: String
    let sub: [String]
    let color: Int
    let wave: Int
  }
  struct Row {
    let title: String
    let time: String
    let status: String
    let bar: Int
    let pill: Int
    let ink: Int
  }
  enum COPY {
    static let hello = "Good morning, Yuna"
    static let short = "Today"
    static let initials = "YS"
    static let sprint = ["Sprint 8", "·", "14 tasks due this week"]
    static let focus = "Today's Focus"
    static let tabs = ["Home", "Tasks", "Projects", "Employees", "Reports"]
    static let stats: [Stat] = [
      Stat(
        label: "Open", value: "38", sub: ["", "across 6 projects"], color: C.blue, wave: 0x4E7FBE),
      Stat(
        label: "Due today", value: "6", sub: ["3", " high priority"], color: C.tan, wave: 0xC9A86E),
      Stat(
        label: "Done this sprint", value: "94", sub: ["", "of 147 total"], color: C.green,
        wave: 0x5E9A55),
    ]
    static let rows: [Row] = [
      Row(
        title: "Review new component specs with dev", time: "09:30", status: "Due now",
        bar: 0x133F7A, pill: 0xD8B477, ink: 0x0F2347),
      Row(
        title: "Update sprint board before standup", time: "11:00", status: "In progress",
        bar: 0x5689C0, pill: 0x4C82BF, ink: 0xFFFFFF),
      Row(
        title: "Approve Q2 contractor invoices", time: "13:30", status: "Pending", bar: 0xD9B579,
        pill: 0xE6CFA6, ink: 0x0F2347),
      Row(
        title: "QA walkthrough for Tide app v2.3", time: "15:00", status: "Not started",
        bar: 0x133F7A, pill: 0xD9D7D1, ink: 0x3B4256),
      Row(
        title: "Resolve blocked API auth issue", time: "17:00", status: "Blocked", bar: 0x133F7A,
        pill: 0x102A4F, ink: 0xFFFFFF),
    ]
    static let done = (status: "Done", bar: 0x21704A, pill: 0xCFE3D3, ink: 0x1B5A3B)
  }
  enum UI {
    static let hello = (x: 42.0, y: 78.0)
    static let avatar = (x: 663.0, y: 77.0, r: 40.0)
    static let pill = (x: 40.0, y: 113.0, h: 42.0)
    static let header = (bottom: 508.0, collapsed: 168.0)
    static let cards = (y: 538.0, h: 192.0, x: [33.0, 261.0, 488.0], w: 212.0, r: 10.0)
    static let dots = (y: 762.0, x: [331.0, 361.0, 391.0])
    static let focus = (y: 814.0, lineY: 821.0)
    static let rows = (y0: 845.0, h: 84.0, pitch: 100.0, x: 33.0, w: 678.0)
    static let tab = (y: 1366.0, h: 106.0, x: [80.0, 222.0, 366.0, 512.0, 660.0], tile: 84.0)
    static let boat = (x: 368.0, y: 400.0)
  }
}
