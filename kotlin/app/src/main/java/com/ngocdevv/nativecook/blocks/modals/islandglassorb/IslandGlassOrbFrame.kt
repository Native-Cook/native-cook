package com.ngocdevv.nativecook.blocks.modals.islandglassorb

/** Values drawn by a single reproducible reference frame. */
internal data class IslandGlassOrbFrame(
    var m: Double = 0.0, var pill: Double = 0.0, var ban: Double = 0.0,
    var antic: Double = 0.0, var dx: Double = 0.0, var dy: Double = 0.0, var e: Double = 0.0,
    var amp: Double = 0.0, var ampLag: Double = 0.0,
    var lineLen: Double = 0.0, var lineX: Double = 200.0, var lineY: Double = 30.0,
    var mic: Double = 0.0, var spinA: Double = 0.0, var spinT: Double = -4.7, var spinBlur: Double = 0.0,
    var pillWob: Double = 0.0, var clock: Double = 1.0,
    var textT: Double = -6.0, var textOut: Double = 0.0,
    var arcA: Double = 0.0, var arcW: Double = 140.0, var arcHue: Double = -3.1,
    var spillY: Double = 103.0, var spillA: Double = 0.0,
    var rimL: Double = 0.0, var rimR: Double = 0.0, var row: Double = 0.0,
    var pluckX: Double = 0.0, var pluckD: Double = 0.0,
    var causticAmp: Double = 0.0, var bandY: Double = 0.0, var bandA: Double = 0.0, var floorY: Double = 0.0, var floorA: Double = 0.0, var bandHue: Double = 0.0, var floorHue: Double = 0.0, var free: Double = 0.0, var cap: Double = .46, var neck: Double = 28.0, var kJoin: Double = 28.0, var iw: Double = 126.0, var ih: Double = 37.0,
) {
    fun values() = doubleArrayOf(m,pill,ban,antic,dx,dy,e,amp,ampLag,lineLen,lineX,lineY,mic,spinA,spinT,spinBlur,pillWob,clock,textT,textOut,arcA,arcW,arcHue,spillY,spillA,rimL,rimR,row,pluckX,pluckD,causticAmp,bandY,bandA,floorY,floorA,bandHue,floorHue,free,cap,neck,kJoin,iw,ih)

    companion object {
        fun from(v: DoubleArray) = IslandGlassOrbFrame(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],v[11],v[12],v[13],v[14],v[15],v[16],v[17],v[18],v[19],v[20],v[21],v[22],v[23],v[24],v[25],v[26],v[27],v[28],v[29],v[30],v[31],v[32],v[33],v[34],v[35],v[36],v[37],v[38],v[39],v[40],v[41],v[42])
    }
}
