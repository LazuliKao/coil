package coil3.gif

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage as CoilAsyncImage
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

@Composable
actual fun AsyncImage(
    model: String,
    contentDescription: String?,
    modifier: Modifier,
    onLoading: (() -> Unit)?,
    onSuccess: (() -> Unit)?,
    onError: ((Throwable?) -> Unit)?
) {
    val imageLoader = SingletonImageLoader.get(LocalPlatformContext.current)
    var animation by remember(model) { mutableStateOf<JvmGifAnimation?>(null) }
    val fillParentBounds = modifier != Modifier

    Box(modifier = modifier) {
        CoilAsyncImage(
            model = model,
            contentDescription = contentDescription,
            imageLoader = imageLoader,
            modifier = if (fillParentBounds) Modifier.fillMaxSize() else Modifier,
            onLoading = {
                animation = null
                onLoading?.invoke()
            },
            onSuccess = { success ->
                animation = (success.result.image as? JvmGifImage)
                    ?.animation
                    ?.takeIf { it.delays.size > 1 }
                onSuccess?.invoke()
            },
            onError = { error ->
                animation = null
                onError?.invoke(error.result.throwable)
            },
        )

        AnimatedOverlay(animation, contentDescription, fillParentBounds)
    }
}

@Composable
private fun BoxScope.AnimatedOverlay(
    animation: JvmGifAnimation?,
    contentDescription: String?,
    fillParentBounds: Boolean,
) {
    animation ?: return

    var currentFrame by remember(animation) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(animation) {
        val codec = Codec.makeFromData(Data.makeFromBytes(animation.bytes))
        try {
            var completedLoops = 0
            while (isActive &&
                (animation.repetitionCount < 0 || completedLoops <= animation.repetitionCount)
            ) {
                for (frame in animation.delays.indices) {
                    if (!isActive) break
                    val bitmap = Bitmap()
                    check(bitmap.allocPixels(codec.imageInfo))
                    codec.readPixels(bitmap, frame)
                    currentFrame = bitmap.asComposeImageBitmap()
                    delay(animation.delays[frame].coerceAtLeast(MIN_FRAME_DELAY_MILLIS).toLong())
                }
                completedLoops++
            }
        } finally {
            codec.close()
        }
    }

    currentFrame?.let { frame ->
        Image(
            bitmap = frame,
            contentDescription = contentDescription,
            modifier = if (fillParentBounds) Modifier.fillMaxSize() else Modifier,
            contentScale = if (fillParentBounds) ContentScale.FillBounds else ContentScale.Fit,
        )
    }
}

private const val MIN_FRAME_DELAY_MILLIS = 16
