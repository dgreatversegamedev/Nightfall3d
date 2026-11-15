package com.d200.nightfall3d.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NfColors {
    val Bg = Color(0xFF0B0F24)
    val Panel = Color(0xFF151A3A)
    val PanelLight = Color(0xFF1F2652)
    val Accent = Color(0xFFFF8A00) // orange team colour
    val Gold = Color(0xFFFFC83D)
    val Diamond = Color(0xFF3DB8FF)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFA7B0D6)
}

@Composable
fun NfButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) NfColors.Accent else NfColors.Panel)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            style = TextStyle(
                color = if (primary) Color.Black else NfColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}