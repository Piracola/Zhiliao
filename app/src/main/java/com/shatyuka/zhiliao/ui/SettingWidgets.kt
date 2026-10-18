package com.shatyuka.zhiliao.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分组强调色：对标 MIUI 控制中心/桌面图标的彩色底座。
 * 每组一个颜色，让四十多个设置项在长列表里还能一眼认出来。
 */
object Accent {
    val Blue = Color(0xFF3482FF)
    val Orange = Color(0xFFFF7A45)
    val Green = Color(0xFF00B578)
    val Purple = Color(0xFF9B5BF7)
    val Indigo = Color(0xFF5856D6)
    val Pink = Color(0xFFFF5B7A)
    val Cyan = Color(0xFF00B4C8)
    val Amber = Color(0xFFFF9F0A)
    val Grey = Color(0xFF8E8E93)
}

/** 页面与卡片的底色：浅色是 MIUI 的灰底白卡，深色是纯黑底 + 一档灰卡。 */
object AppSurface {
    @Composable
    fun page(): Color = if (isSystemInDarkTheme()) Color(0xFF000000) else Color(0xFFF1F2F4)

    @Composable
    fun card(): Color = if (isSystemInDarkTheme()) Color(0xFF1D1D1F) else Color(0xFFFFFFFF)

    /** 输入框/次级容器的底色。 */
    @Composable
    fun field(): Color = if (isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFF2F3F5)
}

/** 设置分组的小标题（MIUI 的灰色小节标题）。 */
@Composable
fun SectionHeader(title: String) {
    SmallTitle(
        text = title,
        insideMargin = PaddingValues(start = 26.dp, end = 26.dp, top = 16.dp, bottom = 7.dp),
    )
}

/** 设置分组的卡片容器：白底、G2 圆角、行内容左右各留 16dp 由行自己控制。 */
@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = AppSurface.card()),
        content = content,
    )
}

/** 卡片内的行分隔线，左侧让开图标底座。 */
@Composable
fun RowDivider(startInset: Dp = 62.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.75.dp)
            .padding(start = startInset)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

/**
 * 设置项图标底座：G2 圆角矩形 + 白色 Lucide 图标。
 * 图标沿用知乎内设置页那套资源（tools/update-icons.mjs 生成的 ic_*.xml）。
 */
@Composable
fun IconBadge(
    iconRes: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 30.dp,
    cornerRadius: Dp = 9.dp,
) {
    val base = if (enabled) accent else accent.copy(alpha = 0.34f)
    Box(
        modifier = modifier
            .size(size)
            .clip(SmoothRoundedCornerShape(cornerRadius))
            .background(base),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White.copy(alpha = if (enabled) 1f else 0.9f)),
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** 右侧的 ">" 箭头：设置项的进入暗示。 */
@Composable
fun ChevronRight(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.7.dp.toPx()
        drawLine(color, Offset(w * 0.36f, h * 0.25f), Offset(w * 0.64f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.64f, h * 0.5f), Offset(w * 0.36f, h * 0.75f), stroke, StrokeCap.Round)
    }
}

/** 设置项右侧的取值文字 + 箭头。 */
@Composable
fun ValueWithArrow(
    text: String,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontSize = 13.sp,
                color = if (enabled) {
                    MiuixTheme.colorScheme.onSurfaceVariantActions
                } else {
                    MiuixTheme.colorScheme.disabledOnSurface
                },
            ),
        )
        ChevronRight(
            color = if (enabled) {
                MiuixTheme.colorScheme.onSurfaceVariantActions
            } else {
                MiuixTheme.colorScheme.disabledOnSurface
            },
            size = 18.dp,
        )
    }
}

/** 行尾的动作文字（MIUI 里那种蓝色可点文字，不是按钮）。 */
@Composable
fun ActionText(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    BasicText(
        text = text,
        modifier = Modifier
            .clip(SmoothRoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = TextStyle(
            fontSize = 15.sp,
            color = if (enabled) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.disabledOnSurface
            },
        ),
    )
}

/** 卡片里的说明段落（免责声明那种）。 */
@Composable
fun CardParagraph(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = color),
    )
}
