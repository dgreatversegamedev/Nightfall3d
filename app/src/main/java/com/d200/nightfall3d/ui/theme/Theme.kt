package com.d200.nightfall3d.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateDraw
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- colors

object NfColors {
    val Bg = Color(0xFF0B0F24)
    val Panel = Color(0xFF141A3C)
    val PanelLight = Color(0xFF1F2756)
    val Border = Color(0xFF3A4BA8)
    val Accent = Color(0xFFFF8A00)
    val Gold = Color(0xFFFFC83D)
    val Cyan = Color(0xFF35D3FF)
    val Purple = Color(0xFF8B5CF6)
    val Green = Color(0xFF3DDC84)
    val Red = Color(0xFFFF4D5E)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFA7B0D6)
}

object NfBrush {
    val Screen = Brush.linearGradient(
        listOf(Color(0xFF070A1E), Color(0xFF1A0F4A), Color(0xFF080B22))
    )
    val Gold = Brush.verticalGradient(
        listOf(Color(0xFFFFE45C), Color(0xFFFFC21A), Color(0xFFFF8F00))
    )
    // Glass: translucent so the screen glow shows through
    val Panel = Brush.verticalGradient(listOf(Color(0xCC222B6E), Color(0xB30F1438)))
    val Violet = Brush.verticalGradient(listOf(Color(0xFFA06BFF), Color(0xFF5B2FD0)))
    val Blue = Brush.verticalGradient(listOf(Color(0xFF45B4FF), Color(0xFF1666D6)))
    val Glow = Brush.linearGradient(listOf(Color(0x4435D3FF), Color(0x228B5CF6)))
    val Row = Brush.horizontalGradient(listOf(Color(0xCC0B0F24), Color(0x330B0F24)))
}

/** Slanted (parallelogram) shape used by the gold buttons, like the reference PLAY button. */
class SlantShape(private val skew: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val k = with(density) { skew.toPx() }.coerceAtMost(size.width / 3f)
        val p = Path().apply {
            moveTo(k, 0f); lineTo(size.width, 0f); lineTo(size.width - k, size.height); lineTo(0f, size.height); close()
        }
        return Outline.Generic(p)
    }
}

fun formatNum(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

// ---------------------------------------------------------------- text

@Composable
fun NfText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    color: Color = NfColors.Text,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    italic: Boolean = false
) {
    BasicText(
        text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = align
        ),
        maxLines = maxLines
    )
}

// ---------------------------------------------------------------- icons

enum class NfIconKind {
    STORE, GIFT, USER, WEAPONS, VAULT, PET, MISSIONS, EVENTS,
    SETTINGS, MAIL, SHIELD, BACK, GROUP,
    GLOBE, OFFLINE, TARGET, LOCK, CHECK, JUMP, DUCK, BULLET, CLOSE,
    TURRET, SWAP, FIST, SHIELD_LINE, SCOPE
}

@Composable
fun NfIcon(
    kind: NfIconKind,
    modifier: Modifier = Modifier.size(20.dp),
    color: Color = Color.White
) {
    Canvas(modifier) {
        val s = size.minDimension
        val sw = s * 0.10f
        val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        fun z(w: Float, h: Float) = Size(w * s, h * s)
        when (kind) {
            NfIconKind.STORE -> {
                val p = Path().apply {
                    moveTo(0.05f * s, 0.15f * s); lineTo(0.20f * s, 0.15f * s)
                    lineTo(0.32f * s, 0.62f * s); lineTo(0.82f * s, 0.62f * s)
                    lineTo(0.92f * s, 0.28f * s); lineTo(0.24f * s, 0.28f * s)
                }
                drawPath(p, color, style = st)
                drawCircle(color, s * 0.07f, o(0.38f, 0.82f))
                drawCircle(color, s * 0.07f, o(0.74f, 0.82f))
            }
            NfIconKind.GIFT -> {
                drawRoundRect(color, o(0.12f, 0.44f), z(0.76f, 0.44f), CornerRadius(s * 0.05f), style = st)
                drawRoundRect(color, o(0.06f, 0.28f), z(0.88f, 0.18f), CornerRadius(s * 0.04f))
                drawRect(color, o(0.46f, 0.28f), z(0.08f, 0.60f))
                drawCircle(color, s * 0.09f, o(0.36f, 0.18f), style = st)
                drawCircle(color, s * 0.09f, o(0.64f, 0.18f), style = st)
            }
            NfIconKind.USER -> {
                drawCircle(color, s * 0.20f, o(0.5f, 0.30f))
                drawArc(color, 180f, 180f, true, o(0.16f, 0.62f), z(0.68f, 0.60f))
            }
            NfIconKind.WEAPONS -> {
                drawRoundRect(color, o(0.04f, 0.36f), z(0.22f, 0.20f), CornerRadius(4f))
                drawRoundRect(color, o(0.24f, 0.32f), z(0.42f, 0.24f), CornerRadius(4f))
                drawRect(color, o(0.66f, 0.38f), z(0.30f, 0.09f))
                drawRoundRect(color, o(0.38f, 0.54f), z(0.10f, 0.32f), CornerRadius(3f))
                drawRoundRect(color, o(0.54f, 0.54f), z(0.07f, 0.20f), CornerRadius(3f))
            }
            NfIconKind.VAULT -> {
                drawRoundRect(color, o(0.10f, 0.12f), z(0.80f, 0.76f), CornerRadius(s * 0.10f), style = st)
                drawCircle(color, s * 0.20f, o(0.5f, 0.5f), style = st)
                drawLine(color, o(0.5f, 0.5f), o(0.62f, 0.38f), sw)
            }
            NfIconKind.PET -> {
                drawCircle(color, s * 0.10f, o(0.20f, 0.42f))
                drawCircle(color, s * 0.10f, o(0.38f, 0.24f))
                drawCircle(color, s * 0.10f, o(0.62f, 0.24f))
                drawCircle(color, s * 0.10f, o(0.80f, 0.42f))
                drawOval(color, o(0.28f, 0.50f), z(0.44f, 0.36f))
            }
            NfIconKind.MISSIONS -> {
                drawRoundRect(color, o(0.18f, 0.14f), z(0.64f, 0.78f), CornerRadius(s * 0.08f), style = st)
                drawRect(color, o(0.36f, 0.06f), z(0.28f, 0.14f))
                val p = Path().apply {
                    moveTo(0.32f * s, 0.56f * s); lineTo(0.45f * s, 0.69f * s); lineTo(0.68f * s, 0.43f * s)
                }
                drawPath(p, color, style = st)
            }
            NfIconKind.EVENTS -> {
                drawRoundRect(color, o(0.10f, 0.20f), z(0.80f, 0.70f), CornerRadius(s * 0.08f), style = st)
                drawRect(color, o(0.10f, 0.20f), z(0.80f, 0.20f))
                drawLine(color, o(0.30f, 0.08f), o(0.30f, 0.28f), sw, StrokeCap.Round)
                drawLine(color, o(0.70f, 0.08f), o(0.70f, 0.28f), sw, StrokeCap.Round)
                drawCircle(color, s * 0.045f, o(0.34f, 0.64f))
                drawCircle(color, s * 0.045f, o(0.50f, 0.64f))
                drawCircle(color, s * 0.045f, o(0.66f, 0.64f))
            }
            NfIconKind.SETTINGS -> {
                val c = o(0.5f, 0.5f)
                for (i in 0 until 8) {
                    rotateDraw(i * 45f, c) {
                        drawRect(color, o(0.45f, 0.06f), z(0.10f, 0.16f))
                    }
                }
                drawCircle(color, s * 0.28f, c, style = Stroke(sw * 1.3f))
                drawCircle(color, s * 0.10f, c)
            }
            NfIconKind.MAIL -> {
                drawRoundRect(color, o(0.08f, 0.22f), z(0.84f, 0.56f), CornerRadius(s * 0.06f), style = st)
                val p = Path().apply {
                    moveTo(0.10f * s, 0.28f * s); lineTo(0.50f * s, 0.58f * s); lineTo(0.90f * s, 0.28f * s)
                }
                drawPath(p, color, style = st)
            }
            NfIconKind.SHIELD -> {
                val p = Path().apply {
                    moveTo(0.50f * s, 0.06f * s); lineTo(0.88f * s, 0.20f * s); lineTo(0.88f * s, 0.50f * s)
                    quadraticBezierTo(0.88f * s, 0.80f * s, 0.50f * s, 0.94f * s)
                    quadraticBezierTo(0.12f * s, 0.80f * s, 0.12f * s, 0.50f * s)
                    lineTo(0.12f * s, 0.20f * s); close()
                }
                drawPath(p, color)
                drawLine(Color(0x66000000), o(0.34f, 0.42f), o(0.50f, 0.66f), sw, StrokeCap.Round)
                drawLine(Color(0x66000000), o(0.50f, 0.66f), o(0.68f, 0.30f), sw, StrokeCap.Round)
            }
            NfIconKind.BACK -> {
                val p = Path().apply {
                    moveTo(0.62f * s, 0.18f * s); lineTo(0.30f * s, 0.50f * s); lineTo(0.62f * s, 0.82f * s)
                }
                drawPath(p, color, style = Stroke(sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            NfIconKind.GLOBE -> {
                drawCircle(color, s * 0.38f, o(0.5f, 0.5f), style = st)
                drawOval(color, o(0.31f, 0.12f), z(0.38f, 0.76f), style = Stroke(sw * 0.8f))
                drawLine(color, o(0.12f, 0.5f), o(0.88f, 0.5f), sw * 0.8f)
            }
            NfIconKind.OFFLINE -> {
                drawArc(color, 225f, 90f, false, o(0.12f, 0.22f), z(0.76f, 0.76f), style = st)
                drawArc(color, 225f, 90f, false, o(0.28f, 0.38f), z(0.44f, 0.44f), style = st)
                drawCircle(color, s * 0.06f, o(0.5f, 0.82f))
                drawLine(color, o(0.15f, 0.12f), o(0.85f, 0.88f), sw, StrokeCap.Round)
            }
            NfIconKind.TARGET -> {
                drawCircle(color, s * 0.38f, o(0.5f, 0.5f), style = st)
                drawCircle(color, s * 0.20f, o(0.5f, 0.5f), style = st)
                drawCircle(color, s * 0.06f, o(0.5f, 0.5f))
                drawLine(color, o(0.5f, 0.02f), o(0.5f, 0.2f), sw * 0.8f)
                drawLine(color, o(0.5f, 0.8f), o(0.5f, 0.98f), sw * 0.8f)
                drawLine(color, o(0.02f, 0.5f), o(0.2f, 0.5f), sw * 0.8f)
                drawLine(color, o(0.8f, 0.5f), o(0.98f, 0.5f), sw * 0.8f)
            }
            NfIconKind.LOCK -> {
                drawRoundRect(color, o(0.20f, 0.45f), z(0.60f, 0.45f), CornerRadius(s * 0.06f))
                drawArc(color, 180f, 180f, false, o(0.30f, 0.10f), z(0.40f, 0.70f), style = st)
            }
            NfIconKind.CHECK -> {
                val p = Path().apply {
                    moveTo(0.20f * s, 0.52f * s); lineTo(0.42f * s, 0.74f * s); lineTo(0.82f * s, 0.28f * s)
                }
                drawPath(p, color, style = Stroke(sw * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            NfIconKind.JUMP -> {
                drawLine(color, o(0.5f, 0.85f), o(0.5f, 0.2f), sw * 1.2f, StrokeCap.Round)
                val p = Path().apply {
                    moveTo(0.25f * s, 0.45f * s); lineTo(0.5f * s, 0.18f * s); lineTo(0.75f * s, 0.45f * s)
                }
                drawPath(p, color, style = Stroke(sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            NfIconKind.DUCK -> {
                val a = Path().apply {
                    moveTo(0.25f * s, 0.28f * s); lineTo(0.5f * s, 0.54f * s); lineTo(0.75f * s, 0.28f * s)
                }
                val b = Path().apply {
                    moveTo(0.25f * s, 0.52f * s); lineTo(0.5f * s, 0.78f * s); lineTo(0.75f * s, 0.52f * s)
                }
                val k = Stroke(sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(a, color, style = k)
                drawPath(b, color, style = k)
            }
            NfIconKind.BULLET -> {
                drawRoundRect(color, o(0.10f, 0.38f), z(0.52f, 0.24f), CornerRadius(s * 0.04f))
                val p = Path().apply {
                    moveTo(0.64f * s, 0.38f * s); lineTo(0.92f * s, 0.50f * s); lineTo(0.64f * s, 0.62f * s); close()
                }
                drawPath(p, color)
            }
            NfIconKind.CLOSE -> {
                drawLine(color, o(0.22f, 0.22f), o(0.78f, 0.78f), sw * 1.2f, StrokeCap.Round)
                drawLine(color, o(0.78f, 0.22f), o(0.22f, 0.78f), sw * 1.2f, StrokeCap.Round)
            }
            NfIconKind.TURRET -> {
                drawLine(color, o(0.50f, 0.54f), o(0.28f, 0.88f), sw, StrokeCap.Round)
                drawLine(color, o(0.50f, 0.54f), o(0.72f, 0.88f), sw, StrokeCap.Round)
                drawLine(color, o(0.50f, 0.54f), o(0.50f, 0.88f), sw, StrokeCap.Round)
                drawCircle(color, s * 0.10f, o(0.46f, 0.42f), style = st)
                drawLine(color, o(0.54f, 0.38f), o(0.90f, 0.26f), sw, StrokeCap.Round)
            }
            NfIconKind.SHIELD_LINE -> {
                val p = Path().apply {
                    moveTo(0.50f * s, 0.08f * s); lineTo(0.86f * s, 0.22f * s); lineTo(0.86f * s, 0.50f * s)
                    quadraticBezierTo(0.86f * s, 0.78f * s, 0.50f * s, 0.92f * s)
                    quadraticBezierTo(0.14f * s, 0.78f * s, 0.14f * s, 0.50f * s)
                    lineTo(0.14f * s, 0.22f * s); close()
                }
                drawPath(p, color, style = st)
                drawLine(color, o(0.50f, 0.26f), o(0.50f, 0.72f), sw * 0.8f, StrokeCap.Round)
            }
            NfIconKind.SCOPE -> {
                drawCircle(color, s * 0.34f, o(0.5f, 0.5f), style = st)
                drawLine(color, o(0.5f, 0.08f), o(0.5f, 0.40f), sw * 0.8f, StrokeCap.Round)
                drawLine(color, o(0.5f, 0.60f), o(0.5f, 0.92f), sw * 0.8f, StrokeCap.Round)
                drawLine(color, o(0.08f, 0.5f), o(0.40f, 0.5f), sw * 0.8f, StrokeCap.Round)
                drawLine(color, o(0.60f, 0.5f), o(0.92f, 0.5f), sw * 0.8f, StrokeCap.Round)
                drawCircle(color, s * 0.04f, o(0.5f, 0.5f))
            }
            NfIconKind.SWAP -> {
                val k = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawLine(color, o(0.15f, 0.32f), o(0.85f, 0.32f), sw, StrokeCap.Round)
                drawLine(color, o(0.85f, 0.68f), o(0.15f, 0.68f), sw, StrokeCap.Round)
                val a = Path().apply {
                    moveTo(0.65f * s, 0.14f * s); lineTo(0.85f * s, 0.32f * s); lineTo(0.65f * s, 0.50f * s)
                }
                val b = Path().apply {
                    moveTo(0.35f * s, 0.50f * s); lineTo(0.15f * s, 0.68f * s); lineTo(0.35f * s, 0.86f * s)
                }
                drawPath(a, color, style = k)
                drawPath(b, color, style = k)
            }
            NfIconKind.FIST -> {
                drawRoundRect(color, o(0.25f, 0.44f), z(0.46f, 0.42f), CornerRadius(s * 0.10f))
                drawCircle(color, s * 0.075f, o(0.32f, 0.40f))
                drawCircle(color, s * 0.075f, o(0.43f, 0.37f))
                drawCircle(color, s * 0.075f, o(0.54f, 0.37f))
                drawCircle(color, s * 0.075f, o(0.65f, 0.40f))
                drawRoundRect(color, o(0.68f, 0.52f), z(0.16f, 0.20f), CornerRadius(s * 0.06f))
            }
            NfIconKind.GROUP -> {
                drawCircle(color, s * 0.14f, o(0.36f, 0.32f))
                drawArc(color, 180f, 180f, true, o(0.10f, 0.54f), z(0.52f, 0.46f))
                drawCircle(color, s * 0.11f, o(0.70f, 0.38f))
                drawArc(color, 180f, 180f, true, o(0.50f, 0.58f), z(0.42f, 0.38f))
            }
        }
    }
}

@Composable
fun NfIconButton(
    kind: NfIconKind,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Color(0xE60A0E22))
            .border(1.dp, Color(0x22FFFFFF), shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        NfIcon(kind, Modifier.size(size * 0.55f), NfColors.Text)
    }
}

// ---------------------------------------------------------------- panels

@Composable
fun NfPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    var m = modifier
        .drawBehind {
            if (selected) {
                drawRoundRect(
                    NfColors.Gold.copy(alpha = 0.18f),
                    Offset(-3f, -3f),
                    Size(size.width + 6f, size.height + 6f),
                    CornerRadius(30f)
                )
            }
        }
        .clip(shape)
        .background(NfBrush.Panel)
    m = if (selected) m.border(1.dp, NfColors.Gold.copy(alpha = 0.8f), shape) else m.border(1.dp, NfBrush.Glow, shape)
    if (onClick != null) m = m.clickable { onClick() }
    Box(m, content = content)
}

/** Solid dark menu button that fits its content, like the reference main menu. */
@Composable
fun NfMenuRow(
    label: String,
    icon: NfIconKind,
    accent: Color,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    badge: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .clip(shape)
            .background(Color(0xE60A0E22))
            .border(1.dp, if (highlight) NfColors.Gold.copy(alpha = 0.9f) else Color(0x22FFFFFF), shape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NfIcon(icon, Modifier.size(20.dp), accent)
        NfText(label.uppercase(), size = 13, bold = true, color = if (highlight) NfColors.Gold else NfColors.Text)
        if (badge) Box(Modifier.size(7.dp).clip(CircleShape).background(NfColors.Red))
    }
}

// ---------------------------------------------------------------- buttons

enum class BtnStyle { GOLD, VIOLET, BLUE, DARK }

@Composable
fun NfButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    style: BtnStyle = if (primary) BtnStyle.GOLD else BtnStyle.DARK,
    size: Int = 15,
    onClick: () -> Unit
) {
    val brush = when (style) {
        BtnStyle.GOLD -> NfBrush.Gold
        BtnStyle.VIOLET -> NfBrush.Violet
        BtnStyle.BLUE -> NfBrush.Blue
        BtnStyle.DARK -> NfBrush.Panel
    }
    val glowColor = when (style) {
        BtnStyle.GOLD -> NfColors.Gold
        BtnStyle.VIOLET -> NfColors.Purple
        BtnStyle.BLUE -> NfColors.Cyan
        BtnStyle.DARK -> Color.Transparent
    }
    val textColor = if (style == BtnStyle.GOLD) Color(0xFF1A1200) else NfColors.Text
    val shape = if (style == BtnStyle.DARK) {
        RoundedCornerShape(10.dp)
    } else {
        SlantShape(10.dp)
    }
    Box(
        modifier
            .drawBehind {
                if (style != BtnStyle.DARK) {
                    drawRoundRect(
                        glowColor.copy(alpha = 0.12f),
                        Offset(-4f, -4f),
                        Size(this.size.width + 8f, this.size.height + 8f),
                        CornerRadius(24f)
                    )
                }
            }
            .clip(shape)
            .background(brush)
            .drawWithContent {
                drawContent()
                if (style != BtnStyle.DARK) {
                    // gloss highlight on the top half
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0x33FFFFFF), Color.Transparent),
                            endY = this.size.height * 0.5f
                        ),
                        size = Size(this.size.width, this.size.height * 0.5f)
                    )
                }
            }
            .border(
                1.dp,
                if (style == BtnStyle.DARK) NfBrush.Glow else Brush.verticalGradient(
                    listOf(Color(0x55FFFFFF), Color(0x11FFFFFF))
                ),
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        NfText(
            text,
            size = size,
            color = textColor,
            bold = true,
            align = TextAlign.Center,
            italic = style == BtnStyle.GOLD
        )
    }
}

// ---------------------------------------------------------------- currency

@Composable
fun CoinDot(color: Color, diamond: Boolean = false) {
    if (diamond) {
        Box(
            Modifier
                .size(12.dp)
                .rotate(45f)
                .background(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), color, color)),
                    RoundedCornerShape(2.dp)
                )
        )
    } else {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFFE680), color, Color(0xFFE59A00))))
                .border(1.dp, Color(0x55FFFFFF), CircleShape)
        )
    }
}

@Composable
fun CurrencyChip(value: Int, color: Color, diamond: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Color(0xE60A0E22))
            .border(1.dp, Color(0x22FFFFFF), shape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CoinDot(color, diamond)
        NfText(formatNum(value), bold = true)
        NfText("+", color = NfColors.Accent, size = 16, bold = true)
    }
}

@Composable
fun NfPrice(value: Int, color: Color, diamond: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CoinDot(color, diamond)
        NfText(formatNum(value), size = 12, bold = true)
    }
}

@Composable
fun NfTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        NfText(text, size = 11, bold = true)
    }
}

@Composable
fun NfSectionTitle(text: String) {
    NfText(text, size = 12, color = NfColors.Cyan, bold = true)
}

// ---------------------------------------------------------------- screen frame

@Composable
fun NfScreen(
    title: String,
    onBack: () -> Unit,
    coins: Int? = null,
    diamonds: Int? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(NfBrush.Screen)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x448B5CF6), Color.Transparent),
                        Offset(size.width * 0.85f, size.height * 0.1f),
                        size.height
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x2235D3FF), Color.Transparent),
                        Offset(size.width * 0.1f, size.height * 0.95f),
                        size.height * 0.9f
                    )
                )
            }
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NfIconButton(NfIconKind.BACK) { onBack() }
            NfText(title, size = 20, bold = true)
            Spacer(Modifier.weight(1f))
            if (coins != null) CurrencyChip(coins, NfColors.Gold)
            if (diamonds != null) CurrencyChip(diamonds, NfColors.Cyan, true)
            actions()
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f), content = content)
    }
}

// ---------------------------------------------------------------- tabs, rows, toggles

@Composable
private fun TabChip(
    text: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (on) NfBrush.Gold else NfBrush.Panel)
            .border(
                1.dp,
                if (on) Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x11FFFFFF))) else NfBrush.Glow,
                shape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        NfText(
            text,
            size = 13,
            bold = true,
            color = if (on) Color(0xFF1A1200) else NfColors.Text
        )
    }
}

@Composable
fun NfTabs(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEachIndexed { i, t -> TabChip(t, i == selected) { onSelect(i) } }
    }
}

@Composable
fun NfSideTabs(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { i, t ->
            TabChip(t, i == selected, Modifier.fillMaxWidth()) { onSelect(i) }
        }
    }
}

@Composable
fun NfChoice(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> TabChip(o, o == selected) { onSelect(o) } }
    }
}

@Composable
fun NfRow(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NfBrush.Row)
            .border(1.dp, Color(0x14FFFFFF), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NfText(label, size = 13, modifier = Modifier.weight(1f))
        content()
    }
}

@Composable
fun NfToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .width(46.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (on) Brush.horizontalGradient(listOf(Color(0xFF2BBF6E), NfColors.Green))
                else Brush.horizontalGradient(listOf(Color(0xFF2A2F5C), Color(0xFF3A3F66)))
            )
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
            .clickable { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun StatBar(label: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NfText(label, size = 12, color = NfColors.TextDim, modifier = Modifier.width(70.dp))
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x33FFFFFF))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color)))
            )
        }
    }
}

// ---------------------------------------------------------------- art placeholders

@Composable
fun ArtBlock(modifier: Modifier, top: Color, bottom: Color, label: String = "") {
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x33FFFFFF), Color.Transparent),
                        Offset(size.width * 0.5f, size.height * 0.35f),
                        size.maxDimension * 0.6f
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (label.isNotEmpty()) {
            NfText(label, size = 30, bold = true, color = Color(0x55FFFFFF))
        }
    }
}

@Composable
fun GunSilhouette(modifier: Modifier = Modifier, color: Color = Color(0xFFB8C0E8)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, Offset(w * 0.05f, h * 0.38f), Size(w * 0.22f, h * 0.20f), CornerRadius(6f))
        drawRoundRect(color, Offset(w * 0.25f, h * 0.34f), Size(w * 0.40f, h * 0.24f), CornerRadius(6f))
        drawRect(color, Offset(w * 0.65f, h * 0.40f), Size(w * 0.30f, h * 0.09f))
        drawRoundRect(color, Offset(w * 0.40f, h * 0.56f), Size(w * 0.09f, h * 0.30f), CornerRadius(4f))
        drawRoundRect(color, Offset(w * 0.55f, h * 0.56f), Size(w * 0.07f, h * 0.20f), CornerRadius(4f))
    }
}

@Composable
fun CharacterSilhouette(modifier: Modifier = Modifier, accent: Color = NfColors.Accent) {
    Canvas(modifier) {
        val u = size.height
        val cx = size.width / 2f
        val glow = Offset(cx, u * 0.55f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.40f), Color.Transparent),
                center = glow,
                radius = u * 0.6f
            ),
            radius = u * 0.6f,
            center = glow
        )
        val dark = Color(0xFF1B1E3D)
        val skin = Color(0xFFE8C7A8)
        val hair = Color(0xFFC62833)
        drawRoundRect(dark, Offset(cx - u * 0.115f, u * 0.58f), Size(u * 0.095f, u * 0.34f), CornerRadius(u * 0.02f))
        drawRoundRect(dark, Offset(cx + u * 0.020f, u * 0.58f), Size(u * 0.095f, u * 0.34f), CornerRadius(u * 0.02f))
        drawRoundRect(accent, Offset(cx - u * 0.125f, u * 0.89f), Size(u * 0.115f, u * 0.06f), CornerRadius(u * 0.02f))
        drawRoundRect(accent, Offset(cx + u * 0.010f, u * 0.89f), Size(u * 0.115f, u * 0.06f), CornerRadius(u * 0.02f))
        drawRoundRect(dark, Offset(cx - u * 0.200f, u * 0.27f), Size(u * 0.06f, u * 0.30f), CornerRadius(u * 0.03f))
        drawRoundRect(dark, Offset(cx + u * 0.140f, u * 0.27f), Size(u * 0.06f, u * 0.30f), CornerRadius(u * 0.03f))
        drawRoundRect(dark, Offset(cx - u * 0.140f, u * 0.25f), Size(u * 0.28f, u * 0.36f), CornerRadius(u * 0.04f))
        drawRoundRect(accent, Offset(cx - u * 0.020f, u * 0.25f), Size(u * 0.04f, u * 0.36f), CornerRadius(u * 0.01f))
        drawCircle(hair, u * 0.075f, Offset(cx, u * 0.15f))
        drawCircle(skin, u * 0.060f, Offset(cx, u * 0.17f))
    }
}
