// Closed state set; stopped is the source's canceled transfer, not a transport error.
package com.ngocdevv.nativecook.blocks.progress.horizonupload

internal enum class HorizonPhase { Idle, Uploading, Paused, Done, Stopped }
