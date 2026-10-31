import Foundation

enum PixelTrophyUnlockTheme {
    static let recent = ["supernova", "superstar", "sorcerer"]
    static let titles = [["STEP", "SUPERNOVA"], ["STEP", "SUPERSTAR"], ["STEP", "SORCERER"]]
    static let frames = ["#67e23c", "#3d7bff", "#3d7bff"]
    static let grid = ["stepstar", "tenfold", "fastest", "momentum", "nightrun", "hillclimb", "streak", "superstar"]
    static let names = ["Step Star", "Tenfold Triumph", "Fastest 1km", "Momentum Maker", "Night Owl", "Hill Seeker", "Week on Fire", "City Lights"]
}

enum PixelTrophyUnlockMotion {
    static let LOOP = 8.0
    static func clamp(_ x: Double) -> Double { min(1,max(0,x)) }
    static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a+(b-a)*k }
    static func smooth(_ k: Double) -> Double { let x = clamp(k); return x*x*(3-2*x) }
    static func ease(_ k: Double) -> Double { let x = clamp(k); return x<0.5 ? 4*x*x*x : 1-pow(-2*x+2,3)/2 }
    static func back(_ k: Double) -> Double { let x = clamp(k); return 1+2.4*pow(x-1,3)+1.4*pow(x-1,2) }
    struct Channels {
        var pulse, colour, fly, shift, bounce, gone, sweep: Double
        var earned: Bool
    }
    static func channels(_ a: Double, _ b: Double, _ reduced: Bool = false) -> Channels {
        let pulse = a>=0 && a<0.3 ? sin(a/0.3 * .pi) : 0
        var colour = smooth((a-0.2)/0.7), fly = ease((a-0.95)/0.9), shift = back((a-1.25)/0.62)
        let land = a-1.85
        let bounce = land>=0 && land<0.5 ? sin(land*18)*exp(-land*9)*0.06 : 0
        var earned = a>=1.85, gone = 0.0
        if b>=0 {
            gone = smooth(b/0.45); shift *= 1-ease((b-0.2)/0.6);colour *= 1-smooth(b/0.6)
            if b>=0.4 { earned = false };if b>0.8 { fly = 0 }
        }
        return Channels(pulse:reduced ? 0 : pulse,colour:colour,fly:reduced ? 0 : fly,shift:shift,bounce:bounce,gone:gone,sweep:reduced ? -1 : (a>=0.2 && a<1 ? (a-0.2)/0.8 : -1),earned:earned)
    }
    static func at(_ t: Double, _ reduced: Bool = false) -> Channels {
        channels(t>=1 ? t-1 : -1,t>=6.6 ? t-6.6 : -1,reduced)
    }
}
