#if DEBUG
import Foundation

/// Observe actual Canvas/Metal drawing separately from the model clock (including async lag).
final class RaffleDrumSpinRenderTrace {
    static let shared = RaffleDrumSpinRenderTrace()
    private let enabled = UserDefaults.standard.bool(forKey:"rafflePerf")
    private let lock = NSLock()
    private var storage: [Double]
    private var count = 0

    private init() { storage = enabled ? [Double](repeating:0,count:16384*3) : [] }

    func record(_ channel: Int, _ story: Double) {
        guard enabled else { return }
        let epoch=Date().timeIntervalSince1970
        lock.lock()
        if count<16384 {
            storage[count*3]=epoch;storage[count*3+1]=story;storage[count*3+2]=Double(channel);count+=1
        }
        lock.unlock()
    }

    func rows() -> [[String:Any]] {
        lock.lock();let values=storage,n=count;lock.unlock()
        return (0..<n).map { i in ["epoch":values[i*3],"story":values[i*3+1],"channel":Int(values[i*3+2])] }
    }
}
#endif
