import Foundation
import CoreGraphics

struct VelvetNapStudioSession {
    let id:UUID
    var from:VelvetNapStudioFrame
    var target:VelvetNapStudioField?
    var stamp:Date
    var stampID=UUID()
    var settled=false
    var lastPoint:CGPoint
    var samplePoint:CGPoint
    var lastDate:Date
    var angle:Double
    var samples:[CGPoint]
}
struct VelvetNapStudioEvent {let id=UUID();let date:Date;let session:VelvetNapStudioSession}
struct VelvetNapStudioPalette {let id=UUID();let date:Date;let from:SIMD3<Double>;let to:SIMD3<Double>}
struct VelvetNapStudioControl {let id=UUID();let date:Date;let frame:VelvetNapStudioFrame}

/// Event-stamped control holds and palette handoffs; no image/stroke is invented for a button.
struct VelvetNapStudioDriver {
    var isControlHeld=false
    var control:VelvetNapStudioControl?
    var palette:VelvetNapStudioPalette?
    var frozenTint:SIMD3<Double>?
    var baseTint=SIMD3<Double>(repeating:1)

    mutating func captureControl(frame:VelvetNapStudioFrame,date:Date) {
        control = .init(date:date,frame:frame)
    }
    func controlFrame(at date:Date,autoPlay:Bool,reduced:Bool)->VelvetNapStudioFrame? {
        guard let control else{return nil}
        let age=max(0,date.timeIntervalSince(control.date))
        if isControlHeld || age<2 {return control.frame}
        if !reduced && age<2.6 {return VelvetNapStudioMath.recovering(control.frame,elapsed:age-2)}
        let duration=reduced ? 2.0:2.6
        return autoPlay && !reduced ? VelvetNapStudioMath.authored(age-duration):.rest
    }
    static func held(_ session:VelvetNapStudioSession,at date:Date,reduced:Bool)->VelvetNapStudioFrame {
        guard let target=session.target else{return session.from}
        let amount=(reduced || session.settled) ? 1:VelvetNapStudioMath.smooth(max(0,date.timeIntervalSince(session.stamp))/0.09)
        return VelvetNapStudioMath.mixed(from:session.from,to:target,amount:amount)
    }
    static func freeze(_ session:VelvetNapStudioSession,frame:VelvetNapStudioFrame,date:Date)->VelvetNapStudioSession {
        var session=session
        session.from=frame;session.target=nil;session.stamp=date;session.stampID=UUID();session.settled=true
        return session
    }
    mutating func freezeTint(_ visible:SIMD3<Double>) {
        frozenTint=visible;baseTint=visible;palette=nil
    }
    mutating func resumeTint(from visible:SIMD3<Double>,toward selected:SIMD3<Double>,date:Date) {
        frozenTint=nil;baseTint=selected
        palette=visible==selected ? nil:.init(date:date,from:visible,to:selected)
    }
    func tint(at date:Date)->SIMD3<Double> {
        if let frozenTint {return frozenTint}
        if let palette {
            let u=VelvetNapStudioMath.smooth(max(0,date.timeIntervalSince(palette.date))/0.18)
            return palette.from+(palette.to-palette.from)*u
        }
        return baseTint
    }
    mutating func reset(tint:SIMD3<Double>) {isControlHeld=false;control=nil;palette=nil;frozenTint=nil;baseTint=tint}
    static func confirmationVisible(_ stamp:Date?,at date:Date)->Bool {
        guard let stamp else{return false};return max(0,date.timeIntervalSince(stamp))<1.2
    }
}
