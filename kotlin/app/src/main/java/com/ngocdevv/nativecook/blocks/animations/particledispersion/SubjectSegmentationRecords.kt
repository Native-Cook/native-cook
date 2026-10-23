// Adapted from the original renderer/segmentation core without Expo.
package com.ngocdevv.nativecook.blocks.animations.particledispersion


class PrepareEffectFrameOptions {
  var sourceUri: String = ""

  var requestId: String = ""

  var targetWidth: Int = 0

  var targetHeight: Int = 0
}

class SegmentEffectFrameOptions {
  var frameUri: String = ""

  var requestId: String = ""

  var selectionX: Double? = null

  var selectionY: Double? = null
}
