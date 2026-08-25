@file:OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)

package coil3.gif

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.ImageLoader
import coil3.compose.AsyncImage as CoilAsyncImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.Canvas
import coil3.Image
import coil3.annotation.InternalCoilApi
import coil3.compose.LocalPlatformContext
import coil3.request.ErrorResult
import coil3.gif.native.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okio.use
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import kotlin.experimental.ExperimentalNativeApi

internal data class HeifAnimationData(
    val handle: Long,
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val delays: List<Int>
)

private const val MAX_ANIMATION_PIXELS = 1_500_000  // ~e.g. 1224x1224
private const val MAX_ANIMATION_FRAMES = 120

internal class SkiaImageWrapper(
    private val image: SkiaImage,
    val animation: HeifAnimationData?
) : Image {
    override val size: Long = (image.width * image.height * 4).toLong()
    override val width: Int get() = image.width
    override val height: Int get() = image.height
    override val shareable: Boolean = true

    override fun draw(canvas: Canvas) {
        canvas.drawImage(image, 0f, 0f)
    }
}

internal class OhosImageDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult = withContext(Dispatchers.IO) {
        val bytes = source.source().use { it.readByteArray() }
        decodeWithAnimation(bytes) ?: decodeWithStatic(bytes)
    }

    private fun decodeWithAnimation(bytes: ByteArray): DecodeResult? {
        val handle = bytes.usePinned { pinned ->
            create_heif_animation(pinned.addressOf(0).reinterpret(), bytes.size.toULong())
        }
        if (handle == 0L) return null

        var success = false
        return try {
            val result = memScoped {
                val countVar = alloc<UIntVar>()
                if (animation_get_frame_count(handle, countVar.ptr) != 0 || countVar.value == 0u) {
                    return@memScoped null
                }
                val frameCount = countVar.value.toInt()

                if (frameCount <= 0 || frameCount > MAX_ANIMATION_FRAMES) {
                    return@memScoped null
                }

                val delays = MutableList(frameCount) { 100 }
                var width = 0
                var height = 0

                for (index in 0 until frameCount) {
                    // 逐帧读取宽高与延迟，确保数据合法。
                    val widthVar = alloc<UIntVar>()
                    val heightVar = alloc<UIntVar>()
                    val delayVar = alloc<IntVar>()
                    if (animation_get_frame_info(handle, index.toUInt(), widthVar.ptr, heightVar.ptr, delayVar.ptr) != 0) {
                        return@memScoped null
                    }

                    width = widthVar.value.toInt()
                    height = heightVar.value.toInt()
                    delays[index] = delayVar.value.takeIf { it > 0 } ?: 100
                }

                if (width <= 0 || height <= 0) return@memScoped null
                if (width * height > MAX_ANIMATION_PIXELS) {
                    return@memScoped null
                }
                if ((width * height).toLong() * frameCount > MAX_ANIMATION_PIXELS.toLong() * MAX_ANIMATION_FRAMES) {
                    return@memScoped null
                }

                if (width == 0 || height == 0) return@memScoped null

                val firstFrame = ByteArray(width * height * 4)
                val copyResult = firstFrame.usePinned { pinned ->
                    animation_copy_frame_pixels(handle, 0u, pinned.addressOf(0).reinterpret(), firstFrame.size.toULong())
                }
                if (copyResult != 0) return@memScoped null

                val animationData = HeifAnimationData(
                    handle = handle,
                    width = width,
                    height = height,
                    frameCount = frameCount,
                    delays = delays
                )

                buildDecodeResult(width, height, firstFrame, animationData)
            }
            if (result != null) {
                success = true
            }
            result
        } finally {
            if (!success) {
                destroy_heif_animation(handle)
            }
        }
    }

    private fun decodeWithStatic(bytes: ByteArray): DecodeResult {
        val handle = bytes.usePinned { pinned ->
            create_heif_image(
                pinned.addressOf(0).reinterpret(),
                bytes.size.toULong(),
                0f,
                0f
            )
        }
        if (handle == 0L) error("create_heif_image failed")

        try {
            return memScoped {
                val dataPtr = alloc<CPointerVar<UByteVar>>()
                val widthVar = alloc<UIntVar>()
                val heightVar = alloc<UIntVar>()
                if (load_image_pixels(handle, dataPtr.ptr, widthVar.ptr, heightVar.ptr) != 0) {
                    error("load_image_pixels failed")
                }

                val width = widthVar.value.toInt()
                val height = heightVar.value.toInt()
                val size = width * height * 4
                val buffer = ByteArray(size)
                // 将 native 指针数据逐字节拷贝到 JVM ByteArray。
                for (i in 0 until size) {
                    buffer[i] = dataPtr.value!![i].toByte()
                }
                free_image_data(dataPtr.value)

                buildDecodeResult(width, height, buffer, null)
            }
        } finally {
            destroy_heif_image(handle)
        }
    }

    private fun buildDecodeResult(
        width: Int,
        height: Int,
        pixels: ByteArray,
        animation: HeifAnimationData?
    ): DecodeResult {
        // 将原生像素缓冲封装成 Skia Image，并连同动图元数据回传给 Coil。
        val imageInfo = ImageInfo(
            width,
            height,
            org.jetbrains.skia.ColorType.RGBA_8888,
            org.jetbrains.skia.ColorAlphaType.PREMUL
        )
        val skiaImage = SkiaImage.makeRaster(
            imageInfo = imageInfo,
            bytes = pixels,
            rowBytes = width * 4
        )
        return DecodeResult(SkiaImageWrapper(skiaImage, animation), isSampled = false)
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder = OhosImageDecoder(result.source, options)
    }
}

@Composable
actual fun AsyncImage(
    model: String,
    contentDescription: String?,
    modifier: Modifier,
    onLoading: (() -> Unit)?,
    onSuccess: (() -> Unit)?,
    onError: ((Throwable?) -> Unit)?
) {
    val context = LocalPlatformContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(OhosImageDecoder.Factory())
            }
            .build()
    }

    var animationData by remember(model) { mutableStateOf<HeifAnimationData?>(null) }
    // 保存当前可播放的动图数据，null 时仅渲染静态首帧。
    val fillParentBounds = modifier != Modifier

    Box(modifier = modifier) {
        CoilAsyncImage(
            model = model,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            modifier = if (fillParentBounds) Modifier.fillMaxSize() else Modifier,
            onLoading = {
                // 开始请求图片：重置状态并透传 onLoading。
                animationData = null
                onLoading?.invoke()
            },
            onError = { error ->
                // 请求异常：清空动画数据，并把 Throwable 返回给调用方。
                animationData = null
                onError?.invoke((error as? ErrorResult)?.throwable)
            },
            onSuccess = { success ->
                // 请求成功：提取动图元数据，判断是否满足播放条件。
                val wrapper = success.result.image as? SkiaImageWrapper
                val animation = wrapper?.animation
                if (animation != null) {
                    val framePixels = animation.width * animation.height
                    val canPlay = animation.frameCount > 1 &&
                            framePixels <= MAX_ANIMATION_PIXELS &&
                            framePixels.toLong() * animation.frameCount <= MAX_ANIMATION_PIXELS.toLong() * MAX_ANIMATION_FRAMES

                    if (canPlay) {
                        animationData = animation
                    } else {
                        destroy_heif_animation(animation.handle)
                        animationData = null
                    }
                } else {
                    animationData = null
                }
                onSuccess?.invoke()
            }
        )

        AnimatedOverlay(animationData, contentDescription, fillParentBounds)
    }
}

@Composable
private fun BoxScope.AnimatedOverlay(
    animation: HeifAnimationData?,
    contentDescription: String?,
    fillParentBounds: Boolean,
) {
    animation ?: return
    if (animation.frameCount <= 1) return

    DisposableEffect(animation) {
        // 与 Compose 生命周期绑定，释放 native 句柄。
        onDispose {
            destroy_heif_animation(animation.handle)
        }
    }

    var currentFrame by remember(animation) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(animation) {
        // 逐帧拉取像素并刷新 Compose 层 Bitmap。
        val pixelBuffer = ByteArray(animation.width * animation.height * 4)
        var frameIndex = 0
        while (isActive) {
            val copyResult = pixelBuffer.usePinned { pinned ->
                animation_copy_frame_pixels(animation.handle, frameIndex.toUInt(), pinned.addressOf(0).reinterpret(), pixelBuffer.size.toULong())
            }
            if (copyResult == 0) {
                // 将 RGBA 像素复制成 Skia Image，再转 Compose ImageBitmap。
                val bitmap = withContext(Dispatchers.Default) {
                    val info = ImageInfo(
                        animation.width,
                        animation.height,
                        org.jetbrains.skia.ColorType.RGBA_8888,
                        org.jetbrains.skia.ColorAlphaType.PREMUL
                    )
                    SkiaImage.makeRaster(
                        imageInfo = info,
                        bytes = pixelBuffer.copyOf(),
                        rowBytes = animation.width * 4
                    ).toComposeImageBitmap()
                }
                currentFrame = bitmap
            }

            val delayMs = animation.delays.getOrElse(frameIndex) { 100 }
                .coerceAtLeast(16) // 下限 16ms 保持约 60 FPS
            // 按帧延迟节奏驱动下一帧。
            delay(delayMs.toLong())
            frameIndex = (frameIndex + 1) % animation.frameCount
        }
    }

    currentFrame?.let { frameBitmap ->
        Image(
            bitmap = frameBitmap,
            contentDescription = contentDescription,
            modifier = if (fillParentBounds) Modifier.fillMaxSize() else Modifier,
            contentScale = if (fillParentBounds) ContentScale.FillBounds else ContentScale.Fit
        )
    }
}
