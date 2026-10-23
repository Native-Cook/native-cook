package com.ngocdevv.nativecook.blocks.gestures.cassettedeck

/** Who drives the deck (RN `st.mode`: 0, 1, 2). */
internal enum class CassetteDeckMode {
    /** The scripted finger plays the loop. */
    AUTOPLAY,

    /** A real finger touched the deck. */
    FINGERS,

    /** Back to rest before the script resumes: the play key is released and the first cassette comes home. */
    RETURNING,
}
