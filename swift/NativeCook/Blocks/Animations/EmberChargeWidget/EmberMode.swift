/// Manual choreography; autoplay retains its own clock until a finger takes over.
enum EmberMode {
  case idle
  case charging
  case ignited
  case cooling
  case releasing
}
