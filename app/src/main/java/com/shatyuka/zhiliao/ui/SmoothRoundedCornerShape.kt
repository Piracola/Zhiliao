package com.shatyuka.zhiliao.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * G2 连续曲率圆角矩形（超椭圆逼近），对标 MIUI/HyperOS 的图标底座。
 * Compose 自带的 RoundedCornerShape 是 G1 圆弧，角点处曲率不连续，观感偏"硬"；
 * 这里用三次贝塞尔逼近 squircle，曲率平滑过渡。
 *
 * 每个圆角是一段三次贝塞尔：两个控制点分别从角点两侧沿边线向内收 K·r，
 * K 取 0.5432（相对四分之一圆弧的 0.5523 略小，形状更"方"一点，接近 squircle）。
 */
class SmoothRoundedCornerShape(
    private val cornerRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ) = androidx.compose.ui.graphics.Outline.Generic(createPath(size, density))

    internal fun createPath(size: Size, density: Density): Path {
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val k = r * K
        val w = size.width
        val h = size.height
        val path = Path()

        // 顺时针：从上边中段开始
        path.moveTo(r, 0f)
        path.lineTo(w - r, 0f)
        // 右上角：(w-r,0) -> (w,r)，控制点分别沿两边向内收 k
        path.cubicTo(w - r + k, 0f, w, r - k, w, r)
        path.lineTo(w, h - r)
        // 右下角：(w,h-r) -> (w-r,h)
        path.cubicTo(w, h - r + k, w - r + k, h, w - r, h)
        path.lineTo(r, h)
        // 左下角：(r,h) -> (0,h-r)
        path.cubicTo(r - k, h, 0f, h - r + k, 0f, h - r)
        path.lineTo(0f, r)
        // 左上角：(0,r) -> (r,0)
        path.cubicTo(0f, r - k, r - k, 0f, r, 0f)

        path.close()
        return path
    }

    companion object {
        private const val K = 0.5432f
    }
}
