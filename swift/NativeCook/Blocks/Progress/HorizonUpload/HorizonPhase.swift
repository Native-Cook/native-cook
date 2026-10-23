/// Semantic and displayed phases are distinct until the badge flip, matching the source.
enum HorizonPhase: String, CaseIterable {
  case idle, uploading, paused, done, stopped
  var power: Double {
    switch self { case .idle: 0.2; case .uploading, .done: 1; case .paused: 0.4; case .stopped: 0.1 }
  }
  var action: String {
    switch self { case .idle: "start"; case .uploading: "pause"; case .paused: "resume"; case .done, .stopped: "again" }
  }
  var image: String {
    switch self { case .uploading: "status-spinner"; case .stopped: "status-canceled"; default: "status-\(rawValue)" }
  }
}
