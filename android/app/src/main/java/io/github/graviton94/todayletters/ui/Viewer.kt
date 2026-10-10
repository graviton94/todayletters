package io.github.graviton94.todayletters.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.graviton94.todayletters.R
import io.github.graviton94.todayletters.design.Tokens
import io.github.graviton94.todayletters.design.Type

/** assets 안 그림을 원래 크기 그대로 (크게 보기용). */
@Composable
fun rememberAsset(path: String): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(path) {
        if (path.isBlank()) null else runCatching { ctx.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
    }
}

/**
 * 사진 · 작품 크게 보기 (메시지 사진, 갤러리 작품이 함께 쓴다).
 * 처음에는 그림 전체가 다 보이게 맞추고, 두 손가락으로 키우면 위아래 양옆 어디로나 끌어 볼 수 있다. 두 번 누르면 그 자리를 확대 / 원래대로.
 */
@Composable
fun ZoomViewer(bmp: ImageBitmap?, caption: String?, credit: String?, onClose: () -> Unit) {
    val ink = Color(0xFFEADFC8); val soft = Color(0xFFA8977C)
    BackHandler { onClose() }
    var scale by remember(bmp) { mutableFloatStateOf(1f) }
    var off by remember(bmp) { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxSize().background(Color(0xF7120D09)).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).pressable { onClose() }, contentAlignment = Alignment.Center) {
                Text("✕", style = Type.body, color = ink)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
            val aspect = bmp?.let { it.width.toFloat() / it.height } ?: 1f
            // 화면 안에 맞춘 그림 크기
            val fw = if (aspect > bw / bh) bw else bh * aspect
            val fh = fw / aspect
            fun clamp(o: Offset, z: Float) = Offset(
                o.x.coerceIn(-((fw * z - bw) / 2).coerceAtLeast(0f), ((fw * z - bw) / 2).coerceAtLeast(0f)),
                o.y.coerceIn(-((fh * z - bh) / 2).coerceAtLeast(0f), ((fh * z - bh) / 2).coerceAtLeast(0f)),
            )
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(bmp) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val z = (scale * zoom).coerceIn(1f, 5f)
                            scale = z; off = clamp(off + pan, z)
                        }
                    }
                    .pointerInput(bmp) {
                        detectTapGestures(onDoubleTap = { pos ->
                            if (scale > 1.01f) { scale = 1f; off = Offset.Zero }
                            else {
                                val z = 2.5f
                                val c = Offset(bw / 2, bh / 2)
                                scale = z; off = clamp((pos - c) * (1 - z), z)
                            }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) Image(bmp, caption, Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y
                }, contentScale = ContentScale.Fit)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!caption.isNullOrBlank()) Text(caption, style = Type.small.ui(), color = ink, textAlign = TextAlign.Center)
            if (!credit.isNullOrBlank()) Text(credit, style = Type.small.copy(fontSize = Tokens.Text.caps), color = soft, textAlign = TextAlign.Center)
            Text(stringResource(R.string.viewer_hint), style = Type.small.ui().copy(fontSize = Tokens.Text.caps), color = soft.copy(alpha = 0.8f),
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
