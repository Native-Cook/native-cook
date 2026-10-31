package com.ngocdevv.nativecook.blocks.cards.pixeltrophyunlock

import kotlin.math.*

internal object PixelTrophyUnlockMotion {
    const val LOOP = 8.0
    val recent = listOf("supernova", "superstar", "sorcerer")
    val titles = listOf(listOf("STEP", "SUPERNOVA"), listOf("STEP", "SUPERSTAR"), listOf("STEP", "SORCERER"))
    val frames = listOf("#67e23c", "#3d7bff", "#3d7bff")
    val grid = listOf("stepstar", "tenfold", "fastest", "momentum", "nightrun", "hillclimb", "streak", "superstar")
    val names = listOf("Step Star", "Tenfold Triumph", "Fastest 1km", "Momentum Maker", "Night Owl", "Hill Seeker", "Week on Fire", "City Lights")
    fun clamp(x: Double) = x.coerceIn(0.0,1.0)
    fun mix(a: Double,b: Double,k: Double) = a+(b-a)*k
    fun smooth(k: Double): Double { val x = clamp(k);return x*x*(3-2*x) }
    fun ease(k: Double): Double { val x = clamp(k);return if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    fun back(k: Double): Double { val x = clamp(k);return 1+2.4*(x-1).pow(3)+1.4*(x-1).pow(2) }
    data class Channels(val pulse: Double, val colour: Double, val fly: Double, val shift: Double, val bounce: Double, val gone: Double, val sweep: Double, val earned: Boolean)
    fun channels(a: Double,b: Double,reduced: Boolean = false): Channels {
        val pulse = if(a>=0 && a<0.3) sin(a/0.3*PI) else 0.0
        var colour = smooth((a-0.2)/0.7);var fly = ease((a-0.95)/0.9);var shift = back((a-1.25)/0.62)
        val land = a-1.85
        val bounce = if(land>=0 && land<0.5) sin(land*18)*exp(-land*9)*0.06 else 0.0
        var earned = a>=1.85;var gone = 0.0
        if(b>=0) { gone = smooth(b/0.45);shift *= 1-ease((b-0.2)/0.6);colour *= 1-smooth(b/0.6);if(b>=0.4)earned = false;if(b>0.8)fly = 0.0 }
        return Channels(if(reduced)0.0 else pulse,colour,if(reduced)0.0 else fly,shift,bounce,gone,if(reduced)-1.0 else if(a>=0.2 && a<1)(a-0.2)/0.8 else -1.0,earned)
    }
    fun at(t: Double,reduced: Boolean = false) = channels(if(t>=1)t-1 else -1.0,if(t>=6.6)t-6.6 else -1.0,reduced)
}
