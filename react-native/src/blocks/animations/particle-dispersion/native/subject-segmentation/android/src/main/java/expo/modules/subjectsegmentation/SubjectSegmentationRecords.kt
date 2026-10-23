package expo.modules.subjectsegmentation

import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import expo.modules.kotlin.types.OptimizedRecord

@OptimizedRecord
class PrepareEffectFrameOptions : Record {
  @Field
  var sourceUri: String = ""

  @Field
  var requestId: String = ""

  @Field
  var targetWidth: Int = 0

  @Field
  var targetHeight: Int = 0
}

@OptimizedRecord
class SegmentEffectFrameOptions : Record {
  @Field
  var frameUri: String = ""

  @Field
  var requestId: String = ""

  @Field
  var selectionX: Double? = null

  @Field
  var selectionY: Double? = null
}
