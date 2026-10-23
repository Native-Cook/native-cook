package com.ngocdevv.nativecook.blocks.text.bakeryflapboard

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.floor
import kotlin.math.sin

/** A menu item: what the board spells, and what its card shows. */
class BakeItem(
    /** The name as the board spells it: capitals, up to eleven characters. */
    val board: String,
    /** The price as the board shows it, four characters. */
    val price: String,
    /** The name on the item card. */
    val name: String,
    /** One line about it on the card. */
    val note: String,
    /** When it came out of the oven, shown on the card. */
    val baked: String,
    /** Unsplash photo id. */
    val photo: String,
    /** The photo's own width ÷ height, so the card can frame it without fetching it first. */
    val aspect: Float = 1.5f,
    /** Where the subject sits in the photo, as shares of its width and height: the card centres the frame on it as far as the photo allows. */
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    /** How much closer than a plain cover the card frames the photo (1 = cover). */
    val zoom: Float = 1f,
)

internal fun unsplash(id: String, width: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$width&q=80"

/** The address of an item's card photo: wide enough for the card on a large phone, at its zoom. */
internal fun cardPhoto(item: BakeItem) = unsplash(item.photo, Math.round(minOf(1800f, 1000f * maxOf(1f, item.zoom))))

/** Where an item's photo sits behind the card's photo frame (w × h, dp): left, top, width, height. */
internal class PhotoFrame(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * The photo scaled to cover the frame (times its zoom), and moved so its subject is as near the
 * frame's middle as the photo's edges allow.
 */
internal fun framePhoto(item: BakeItem, w: Float, h: Float): PhotoFrame {
    val ratio = w / maxOf(1f, h)
    val aspect = if (item.aspect > 0f) item.aspect else ratio
    val zoom = maxOf(1f, item.zoom)
    val coverW = if (aspect > ratio) h * aspect else w
    val coverH = if (aspect > ratio) h else w / aspect
    val width = coverW * zoom
    val height = coverH * zoom
    val left = minOf(0f, maxOf(w - width, w / 2f - item.focusX * width))
    val top = minOf(0f, maxOf(h - height, h / 2f - item.focusY * height))
    return PhotoFrame(left, top, width, height)
}

internal val MENU = listOf(
    listOf(
        BakeItem("SOURDOUGH", "6.40", "Sourdough", "Three-day ferment, a dark blistered crust and an open, glossy crumb.", "06:40", "photo-1549413468-cd78edb7e75c", aspect = 0.667f, focusX = 0.5f, focusY = 0.42f),
        BakeItem("SEEDED RYE", "5.80", "Seeded Rye", "Dense dark rye with sunflower, flax and a spoon of treacle.", "07:05", "photo-1509440159596-0249088772ff", aspect = 1.5f, focusX = 0.55f, focusY = 0.5f),
        BakeItem("SPELT BOULE", "7.20", "Spelt Boule", "Wholegrain spelt, shaped by hand and baked on the stone.", "06:55", "photo-1567042661848-7161ce446f85", aspect = 0.8f, focusX = 0.5f, focusY = 0.48f),
    ),
    listOf(
        BakeItem("BAGUETTE", "3.20", "Baguette", "Long-proved, with a crackling crust. Best eaten before noon.", "07:10", "photo-1559811814-e2c57b5e69df", aspect = 0.667f, focusX = 0.5f, focusY = 0.4f),
        BakeItem("MILK BREAD", "4.60", "Milk Bread", "A soft, pillowy loaf in a paper bag, sliced to order.", "06:30", "photo-1622808516114-02a5749cd965", aspect = 0.667f, focusX = 0.5f, focusY = 0.38f),
    ),
    listOf(
        BakeItem("CROISSANT", "3.40", "Croissant", "Twenty-seven layers of cultured butter, laminated overnight.", "06:15", "photo-1546237769-6f84ec1a512a", aspect = 1f, focusX = 0.5f, focusY = 0.42f),
        BakeItem("OAT COOKIE", "2.10", "Oat Cookie", "Brown butter, flaked salt and a chewy middle.", "08:00", "photo-1523294587484-bae6cc870010", aspect = 1.72f, focusX = 0.8f, focusY = 0.42f, zoom = 1.7f),
    ),
    listOf(
        BakeItem("HOUSE TEA", "3.00", "House Tea", "A pot of black tea with bergamot, poured at the counter.", "ALL DAY", "photo-1638303322580-5e6aacd59c0d", aspect = 0.75f, focusX = 0.6f, focusY = 0.58f),
        BakeItem("PEAR TART", "4.90", "Pear Tart", "Poached pear on almond cream, glazed with honey.", "09:20", "photo-1543773407-ebc74e2bc7ff", aspect = 1.5f, focusX = 0.68f, focusY = 0.6f, zoom = 1.35f),
    ),
)

internal val STRIP = listOf(
    "photo-1608198093002-ad4e005484ec",
    "photo-1587241321921-91a834d6d191",
    "photo-1579697096985-41fe1430e5df",
    "photo-1711672284661-bd70e38f31b2",
    "photo-1568254183919-78a4f43a2877",
)

/**
 * The characters on each flap drum, in the order the flaps fall. A cell only moves forward through
 * this list, so a change from Y to B passes through Z, the digits and the marks first.
 */
internal const val DRUM = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.'&-"
internal const val COLUMNS = 16

private val HOURS = listOf("TWELVE", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN")
private val MINUTES = mapOf(
    5 to "FIVE PAST", 10 to "TEN PAST", 15 to "QUARTER PAST", 20 to "TWENTY PAST", 25 to "TWENTY FIVE PAST",
    30 to "HALF PAST", 35 to "TWENTY FIVE TO", 40 to "TWENTY TO", 45 to "QUARTER TO", 50 to "TEN TO", 55 to "FIVE TO",
)

/** The time in words to the nearest five minutes, on two lines of the board. */
internal fun timeInWords(hours: Int, minutes: Int): List<String> {
    // JavaScript's Math.round: halves round up.
    var m = floor(minutes / 5.0 + 0.5).toInt() * 5
    var h = hours
    if (m == 60) {
        m = 0
        h += 1
    }
    if (m == 0) return listOf("IT IS", "${HOURS[h % 12]} O'CLOCK")
    return listOf(MINUTES.getValue(m), HOURS[(if (m > 30) h + 1 else h) % 12])
}

/** `text` centred on a row of flaps. */
internal fun centred(text: String): String {
    val pad = maxOf(0, (COLUMNS - text.length) / 2)
    return (" ".repeat(pad) + text).padEnd(COLUMNS, ' ').take(COLUMNS)
}

/** A menu line: the name on the left, the price on the right. */
internal fun menuLine(name: String, price: String): String {
    var line = name
    while (line.length < COLUMNS - price.length) line += " "
    return (line + price).take(COLUMNS)
}

// Dark walnut with long, slightly wavy grain and a fine fibre on top, for the board's case (AGSL,
// Android 13+).
internal const val WOOD = """
uniform float2 size;

float hash(float2 p) {
  return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

float noise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
             mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

half4 main(float2 xy) {
  float2 p = xy / size.x;
  float wave = noise(float2(p.x * 2.2, p.y * 9.0)) * 0.9 + noise(float2(p.x * 5.0, p.y * 30.0)) * 0.35;
  float rings = 0.5 + 0.5 * sin(p.y * 70.0 + wave * 7.0);
  float fibre = noise(float2(p.x * 40.0, p.y * 700.0));
  float3 dark = float3(0.105, 0.062, 0.038);
  float3 light = float3(0.235, 0.137, 0.078);
  float3 col = mix(dark, light, rings * 0.55 + wave * 0.3);
  col *= 0.86 + fibre * 0.22;
  // A soft light from the top left, falling off towards the bottom.
  col *= 1.08 - 0.32 * clamp(xy.y / size.y, 0.0, 1.0);
  return half4(half3(col), 1.0);
}
"""

/**
 * The same walnut worked out on the CPU, one pixel per `step` dp, for Android 12 and older, which
 * have no runtime shaders.
 */
internal fun woodBitmap(width: Float, height: Float, step: Float): Bitmap {
    val w = maxOf(1, (width / step).toInt())
    val h = maxOf(1, (height / step).toInt())
    fun fract(v: Double) = v - floor(v)
    fun hash(x: Double, y: Double) = fract(sin(x * 127.1 + y * 311.7) * 43758.5453)
    fun noise(x: Double, y: Double): Double {
        val ix = floor(x)
        val iy = floor(y)
        val fx = x - ix
        val fy = y - iy
        val ux = fx * fx * (3 - 2 * fx)
        val uy = fy * fy * (3 - 2 * fy)
        val a = hash(ix, iy) + (hash(ix + 1, iy) - hash(ix, iy)) * ux
        val b = hash(ix, iy + 1) + (hash(ix + 1, iy + 1) - hash(ix, iy + 1)) * ux
        return a + (b - a) * uy
    }
    val pixels = IntArray(w * h)
    for (j in 0 until h) {
        for (i in 0 until w) {
            val x = (i + 0.5) * step
            val y = (j + 0.5) * step
            val px = x / width
            val py = y / width
            val wave = noise(px * 2.2, py * 9.0) * 0.9 + noise(px * 5.0, py * 30.0) * 0.35
            val rings = 0.5 + 0.5 * sin(py * 70.0 + wave * 7.0)
            val fibre = noise(px * 40.0, py * 700.0)
            val t = rings * 0.55 + wave * 0.3
            val light = (1.08 - 0.32 * (y / height).coerceIn(0.0, 1.0)) * (0.86 + fibre * 0.22)
            fun channel(dark: Double, bright: Double) = ((dark + (bright - dark) * t) * light * 255).toInt().coerceIn(0, 255)
            pixels[j * w + i] = Color.rgb(channel(0.105, 0.235), channel(0.062, 0.137), channel(0.038, 0.078))
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
