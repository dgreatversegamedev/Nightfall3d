package com.d200.nightfall3d.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        listOf(Color(0xFF0A0E26), Color(0xFF1B1048), Color(0xFF0A0E26))
    )
    val Gold = Brush.verticalGradient(listOf(Color(0xFFFFD84A), Color(0xFFFF9A00)))
    val Panel = Brush.verticalGradient(listOf(Color(0xFF1C2358), Color(0xFF121739)))
    val Violet = Brush.verticalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF5B2FD0)))
    val Blue = Brush.verticalGradient(listOf(Color(0xFF3DA9FF), Color(0xFF1666D6)))
}

fun formatNum(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

@Composable
fun NfText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    color: Color = NfColors.Text,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align
        ),
        maxLines = maxLines
    )
}

@Composable
fun NfPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    var m = modifier
        .clip(shape)
        .background(NfBrush.Panel)
        .border(
            if (selected) 2.dp else 1.dp,
            if (selected) NfColors.Gold else NfColors.Border,
            shape
        )
    if (onClick != null) m = m.clickable { onClick() }
    Box(m, content = content)
}

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
    val textColor = if (style == BtnStyle.GOLD) Color(0xFF1A1200) else NfColors.Text
    val borderColor = if (style == BtnStyle.DARK) NfColors.Border else Color(0x55FFFFFF)
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(brush)
            .border(1.dp, borderColor, shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        NfText(text, size = size, color = textColor, bold = true, align = TextAlign.Center)
    }
}

@Composable
fun CoinDot(color: Color, diamond: Boolean = false) {
    if (diamond) {
        Box(Modifier.size(12.dp).rotate(45f).background(color, RoundedCornerShape(2.dp)))
    } else {
        Box(Modifier.size(16.dp).clip(CircleShape).background(color))
    }
}

@Composable
fun CurrencyChip(value: Int, color: Color, diamond: Boolean = false) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .clip(shape)
            .background(NfColors.Panel)
            .border(1.dp, NfColors.Border, shape)
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
            .clip(RoundedCornerShape(6.dp))
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
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .size(36.dp)
                    .clip(shape)
                    .background(NfBrush.Panel)
                    .border(1.dp, NfColors.Border, shape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                NfText("<", size = 18, bold = true)
            }
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

@Composable
private fun TabChip(
    text: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (on) NfBrush.Gold else NfBrush.Panel)
            .border(1.dp, if (on) Color.Transparent else NfColors.Border, shape)
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
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x33000000))
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
            .background(if (on) NfColors.Green else Color(0xFF3A3F66))
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
                    .background(color)
            )
        }
    }
}

@Composable
fun ArtBlock(modifier: Modifier, top: Color, bottom: Color, label: String = "") {
    Box(
        modifier.background(Brush.verticalGradient(listOf(top, bottom))),
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
