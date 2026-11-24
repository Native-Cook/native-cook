// The demo's screen: Almanac's settings, drawn for one theme (AlmanacSettings.tsx). Everything here is
// plain composables, so the louver has real content to snapshot; the switches are Material 3's and
// the theme picker the same pill the React Native app draws on Android.
package com.ngocdevv.nativecook.blocks.navigation.louverthemeswitch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rgba

data class AlmanacPrefs(val weekNumbers: Boolean = true, val declined: Boolean = false, val travel: Boolean = true)

enum class AlmanacPref { WeekNumbers, Declined, Travel }

private class Palette(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val mute: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val teal: Color,
    /** the card's two outer shadows and its inset top highlight */
    val shadowFar: Color,
    val shadowNear: Color,
    val highlight: Color,
    /** the picker */
    val track: Color,
    val selected: Color,
    val label: Color,
    /** the switch's off track */
    val off: Color,
)

private val LIGHT = Palette(
    bg = Color(0xFFF3F0E8),
    surface = Color(0xFFFFFDF8),
    ink = Color(0xFF1F1D19),
    mute = Color(0xFF7F796D),
    line = rgba(40, 36, 28, 0.11f),
    accent = Color(0xFFC4513A),
    onAccent = Color(0xFFFFF8F2),
    teal = Color(0xFF2F7F78),
    shadowFar = rgba(66, 54, 36, 0.10f),
    shadowNear = rgba(66, 54, 36, 0.12f),
    highlight = rgba(255, 255, 255, 0.9f),
    track = rgba(40, 36, 28, 0.09f),
    selected = Color(0xFFFFFDF8),
    label = Color(0xFF1E1C18),
    off = rgba(120, 112, 98, 0.22f),
)

private val DARK = Palette(
    bg = Color(0xFF14151B),
    surface = Color(0xFF202129),
    ink = Color(0xFFECE9E2),
    mute = Color(0xFF918F99),
    line = rgba(236, 233, 226, 0.10f),
    accent = Color(0xFFEC7E63),
    onAccent = Color(0xFF2A120C),
    teal = Color(0xFF62BDB3),
    shadowFar = rgba(0, 0, 0, 0.38f),
    shadowNear = rgba(0, 0, 0, 0.4f),
    highlight = rgba(255, 255, 255, 0.07f),
    track = rgba(235, 232, 225, 0.12f),
    selected = Color(0xFF3A3B45),
    label = Color(0xFFECE9E2),
    off = rgba(235, 232, 225, 0.16f),
)

private val WEEK = listOf("M" to 23, "T" to 24, "W" to 25, "T" to 26, "F" to 27, "S" to 28, "S" to 29)
private const val TODAY = 24
private class Event(val title: String, val time: String, val accent: Boolean)
private val AGENDA = listOf(Event("Design review", "10:30", true), Event("Lunch with Noor", "12:45", false))

/** Almanac's settings screen for `screen.theme` */
@Composable
fun AlmanacSettings(screen: LouverScreenScope, prefs: AlmanacPrefs, setPref: (AlmanacPref, Boolean) -> Unit) {
    val c = if (screen.theme == LouverTheme.Dark) DARK else LIGHT
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val compact = maxHeight < 740.dp
        val rowH = if (compact) 40.dp else 50.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = insets.calculateTopPadding() + if (compact) 6.dp else 14.dp,
                    bottom = maxOf(insets.calculateBottomPadding(), 12.dp) + 4.dp,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Label("Almanac", 15f, FontWeight.SemiBold, c.mute)
                Label(
                    "Settings", if (compact) 28f else 34f, FontWeight.Bold, c.ink, Modifier.padding(top = 2.dp),
                    spacing = -0.6f, line = if (compact) 33f else 40f,
                )
            }

            Column(Modifier.fillMaxWidth().card(c).padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label("November", 17f, FontWeight.Bold, c.ink, Modifier.alignByBaseline())
                    Label("Week 48", 13f, FontWeight.Medium, c.mute, Modifier.alignByBaseline())
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    WEEK.forEachIndexed { i, (letter, day) ->
                        val on = day == TODAY
                        Column(Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Label(letter, 11f, FontWeight.SemiBold, c.mute, Modifier.padding(bottom = 4.dp))
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(if (on) c.accent else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Label("$day", 15f, FontWeight.SemiBold, if (on) c.onAccent else if (i > 4) c.mute else c.ink)
                            }
                        }
                    }
                }
                for (e in AGENDA.take(if (compact) 1 else 2)) {
                    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .padding(end = 10.dp)
                                .size(3.dp, 16.dp)
                                .background(if (e.accent) c.accent else c.teal, RoundedCornerShape(1.5.dp)),
                        )
                        Label(e.title, 14f, FontWeight.Medium, c.ink, Modifier.weight(1f))
                        Label(e.time, 13f, FontWeight.Medium, c.mute)
                    }
                }
            }

            Column {
                Section("Appearance", c)
                Box(screen.controlModifier) {
                    ThemePicker(screen.theme, screen.select, c)
                }
            }

            Column {
                Section("Calendar", c)
                Column(Modifier.fillMaxWidth().card(c).padding(horizontal = 16.dp)) {
                    ValueRow("Start week on", "Monday", c, rowH)
                    ValueRow("Default length", "45 min", c, rowH)
                    ToggleRow("Week numbers", prefs.weekNumbers, { setPref(AlmanacPref.WeekNumbers, it) }, c, rowH)
                    ToggleRow("Show declined", prefs.declined, { setPref(AlmanacPref.Declined, it) }, c, rowH, last = true)
                }
            }

            Column {
                Section("Alerts", c)
                Column(Modifier.fillMaxWidth().card(c).padding(horizontal = 16.dp)) {
                    ValueRow("Morning brief", "07:30", c, rowH)
                    ToggleRow("Travel time", prefs.travel, { setPref(AlmanacPref.Travel, it) }, c, rowH, last = true)
                }
            }
        }
    }
}

/** the card: two outer shadows, the surface, and a 1 dp inset highlight along its top */
private fun Modifier.card(c: Palette): Modifier = this
    .cssBoxShadow(c.shadowFar, offsetY = 10.dp, blur = 24.dp, radius = 18.dp)
    .cssBoxShadow(c.shadowNear, offsetY = 1.dp, blur = 3.dp, radius = 18.dp)
    .background(c.surface, RoundedCornerShape(18.dp))
    .drawWithCache {
        val r = CornerRadius(18.dp.toPx())
        val dy = 1.dp.toPx()
        val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
        val inner = Path().apply { addRoundRect(RoundRect(0f, dy, size.width, size.height + dy, r)) }
        val crescent = Path().apply { op(outer, inner, PathOperation.Difference) }
        onDrawBehind { drawPath(crescent, c.highlight) }
    }

@Composable
private fun Section(text: String, c: Palette) {
    Label(text, 13f, FontWeight.SemiBold, c.mute, Modifier.padding(start = 4.dp, bottom = 6.dp))
}

/** a hairline under every row but the last */
private fun Modifier.rowLine(c: Palette, last: Boolean): Modifier = if (last) this else drawBehind {
    val w = Dp.Hairline.toPx().coerceAtLeast(1f)
    drawRect(c.line, topLeft = Offset(0f, size.height - w), size = androidx.compose.ui.geometry.Size(size.width, w))
}

@Composable
private fun ValueRow(label: String, value: String, c: Palette, height: Dp, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(height).rowLine(c, last), verticalAlignment = Alignment.CenterVertically) {
        Label(label, 16f, FontWeight.Normal, c.ink, Modifier.weight(1f))
        Label(value, 16f, FontWeight.Normal, c.mute, Modifier.padding(end = 8.dp))
        // a 7 dp box with its top and right borders (1.6 dp), turned 45°
        Canvas(Modifier.padding(end = 2.dp).size(7.dp)) {
            val w = 1.6.dp.toPx()
            val s = size.width
            rotate(45f) {
                val path = Path().apply {
                    moveTo(0f, w / 2f)
                    lineTo(s - w / 2f, w / 2f)
                    lineTo(s - w / 2f, s)
                }
                drawPath(path, c.mute, style = Stroke(w, cap = StrokeCap.Butt))
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit, c: Palette, height: Dp, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(height).rowLine(c, last), verticalAlignment = Alignment.CenterVertically) {
        Label(label, 16f, FontWeight.Normal, c.ink, Modifier.weight(1f))
        Switch(
            checked = value,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = label },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = c.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = c.off,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/** Light / Dark: a pill with the chosen half raised (AlmanacControls.tsx) */
@Composable
private fun ThemePicker(theme: LouverTheme, onSelect: (LouverTheme) -> Unit, c: Palette) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(c.track, RoundedCornerShape(18.dp))
            .padding(2.dp),
    ) {
        for (option in LouverTheme.entries) {
            val on = option == theme
            var segment = Modifier
                .weight(1f)
                .fillMaxHeight()
            if (on && theme == LouverTheme.Light) {
                segment = segment.cssBoxShadow(rgba(40, 36, 28, 0.2f), offsetY = 1.dp, blur = 3.dp, radius = 16.dp)
            }
            if (on) segment = segment.background(c.selected, RoundedCornerShape(16.dp))
            Box(
                segment.selectable(
                    selected = on,
                    onClick = { onSelect(option) },
                    role = Role.Tab,
                    interactionSource = null,
                    indication = null,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Label(if (option == LouverTheme.Light) "Light" else "Dark", 14f, FontWeight.SemiBold, c.label)
            }
        }
    }
}

/** text in dp, so the system's font scale does not move the page */
@Composable
internal fun Label(
    text: String,
    size: Float,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    spacing: Float = 0f,
    line: Float = 0f,
) {
    val density = LocalDensity.current
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            color = color,
            fontSize = with(density) { size.dp.toSp() },
            fontWeight = weight,
            letterSpacing = with(density) { spacing.dp.toSp() },
            lineHeight = if (line > 0f) with(density) { line.dp.toSp() } else TextStyle.Default.lineHeight,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            fontFeatureSettings = "tnum",
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}
