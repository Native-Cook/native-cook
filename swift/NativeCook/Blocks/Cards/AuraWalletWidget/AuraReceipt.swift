// Money remains integer cents until formatting at the view boundary.
struct AuraReceipt: Equatable {
  let friend: Int
  let cents: Int
}
