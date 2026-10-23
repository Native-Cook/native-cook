package com.ngocdevv.nativecook.blocks.animations.particledispersion

internal class NativeProcessingException(
  val code: String,
  message: String,
  cause: Throwable? = null
) : RuntimeException(message, cause)
