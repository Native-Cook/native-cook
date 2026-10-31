import Foundation

struct FriendRadarMapFriend {
  let name, street, sooner: String
  let x, y, skew: Double
}
enum FriendRadarMapMotion {
  static let loop = 9.0
  static let friends = [
    FriendRadarMapFriend(name: "ANASTASIA", street: "IS ON BERSON STREET NOW", sooner: "MEET HER 3 MIN SOONER", x: 430, y: 560, skew: 0.16),
    FriendRadarMapFriend(name: "IGOR", street: "IS AT KILN AVENUE NOW", sooner: "MEET HIM 5 MIN SOONER", x: 760, y: 880, skew: -0.12),
    FriendRadarMapFriend(name: "DASHA", street: "IS BY PIER ROAD NOW", sooner: "MEET HER 2 MIN SOONER", x: 330, y: 1080, skew: 0.08)
  ]
  static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
  static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b-a)*k }
  static func ease(_ p: Double) -> Double {
    let x = clamp(p)
    return x < 0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2
  }
  static func back(_ p: Double) -> Double {
    let x = clamp(p)
    return 1 + 2.5*pow(x-1,3) + 1.5*pow(x-1,2)
  }
  static func hash(_ i: Double) -> Double {
    let s = sin(i*12.9898)*43758.5453
    return s-floor(s)
  }
  static func random(_ i: Double) -> Double {
    let s = sin(i*91.3458)*47453.5453
    return s-floor(s)
  }
  struct Frame {
    var time = 0.0, since = 10.0
    var cur = 0, prev = 0
    var fill = 0.0, burst = -10.0
    var labelIndex: Int { since < 0.3 ? prev : cur }
    static func at(_ t: Double) -> Self {
      let time = t.truncatingRemainder(dividingBy: loop)
      let i = Int(floor(time/3))%3
      return Self(time: time, since: time-Double(i)*3, cur: i, prev: (i+2)%3)
    }
    func decoded(_ reduced: Bool) -> (String, String) {
      let out = !reduced && prev != cur && since < 0.3
      let name = friends[out ? prev : cur].name
      let chars = Array(name), glyphs = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&*+=?")
      var text = ""
      for i in chars.indices {
        var ch: Character = " "
        if out {
          let start = 0.03 + Double(chars.count-1-i)*0.025
          if since < start { ch = chars[i] }
          else if since < start+0.1 { ch = glyphs[Int(floor(hash(Double(i)*7+floor(since*30))*Double(glyphs.count)))] }
        } else {
          let start = 0.3+Double(i)*0.07
          if reduced || since >= start+0.32 { ch = chars[i] }
          else if since >= start { ch = glyphs[Int(floor(hash(Double(i)*13+floor(since*22))*Double(glyphs.count)))] }
        }
        text.append(ch)
      }
      return (name,text)
    }
  }
}
