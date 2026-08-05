package com.elevator.qr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 100% 保持与网页版 (QRious / 标准 ZXing) 一致的标准黑白二维码
 * 无圆角点阵、无中央 Logo 阻挡，具备充足的 Quiet Zone 白边，确保电梯硬件扫码器秒级识别
 */
@Composable
fun StyledQrCanvas(
    content: String,
    modifier: Modifier = Modifier
) {
    val bitMatrix = remember(content) {
        if (content.isEmpty()) null
        else try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: Exception) {
            null
        }
    }

    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                bitMatrix?.let { matrix ->
                    val matrixWidth = matrix.width
                    val matrixHeight = matrix.height
                    val cellSize = size.width / matrixWidth

                    // Draw standard solid black background pixels
                    for (x in 0 until matrixWidth) {
                        for (y in 0 until matrixHeight) {
                            if (matrix.get(x, y)) {
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(x * cellSize, y * cellSize),
                                    size = Size(cellSize + 0.5f, cellSize + 0.5f) // Slight overlap to prevent subpixel anti-aliasing gaps
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
