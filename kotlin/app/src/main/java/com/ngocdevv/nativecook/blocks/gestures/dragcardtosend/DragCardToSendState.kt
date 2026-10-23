package com.ngocdevv.nativecook.blocks.gestures.dragcardtosend

internal enum class DragCardToSendPhase {
    Rest,
    Pulling,
    Holding,
    Docked,
    Sending,
    Closing,
    Sent,
}

internal enum class DragCardToSendHaptic {
    Light,
    Medium,
    Select,
    Success,
}
