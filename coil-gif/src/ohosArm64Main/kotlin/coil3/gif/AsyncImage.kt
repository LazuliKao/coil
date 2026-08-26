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
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.Canvas
import coil3.Image
import coil3.SingletonImageLoader
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

/**
 * 鸿蒙端动图元数据对象（不可变纯 Kotlin 数据结构，避免在 Coil 内存缓存中存放 C++ 原生指针导致 Use-After-Free）
 */
data class OhosGifAnimation(
    val bytes: ByteArray,
)

private const val MAX_ANIMATION_PIXELS = 4_000_000  // ~e.g. 2000x2000
private const val MAX_ANIMATION_FRAMES = 300

internal class SkiaImageWrapper(
    val image: SkiaImage,
    val animation: OhosGifAnimation?
) : Image {
    override val size: Long = (image.width * image.height * 4).toLong() + (animation?.bytes?.size ?: 0)
    override val width: Int get() = image.width
    override val height: Int get() = image.height
    override val shareable: Boolean = true

    override fun draw(canvas: Canvas) {
        canvas.drawImage(image, 0f, 0f)
    }
}

/**
 * 鸿蒙平台专用图片解码器，支持 GIF/HEIF 动图解析与 Skia 极速降级
 */
class OhosImageDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult = withContext(Dispatchers.IO) {
        val bytes = source.source().use { it.readByteArray() }
        // 1. 在后台 IO 线程极速异步解码首帧底图（毫秒级完成上屏）
        val staticResult = decodeWithStatic(bytes)

        // 2. 检测是否为动图格式（GIF, WebP, HEIF 等），若是则附带动图原始字节供视图层后台异步播放
        val buffer = okio.Buffer().write(bytes)
        val isAnimated = DecodeUtils.isGif(buffer) ||
                DecodeUtils.isAnimatedWebP(okio.Buffer().write(bytes)) ||
                DecodeUtils.isAnimatedHeif(okio.Buffer().write(bytes))

        if (isAnimated) {
            val wrapper = staticResult.image as? SkiaImageWrapper
            if (wrapper != null) {
                DecodeResult(
                    image = SkiaImageWrapper(wrapper.image, OhosGifAnimation(bytes)),
                    isSampled = staticResult.isSampled
                )
            } else {
                staticResult
            }
        } else {
            staticResult
        }
    }

    private fun decodeWithStatic(bytes: ByteArray): DecodeResult {
        // 优先尝试 Skia 极速解码首帧（针对 PNG 等基础格式）
        val skiaImage = try {
            SkiaImage.makeFromEncoded(bytes)
        } catch (_: Throwable) {
            null
        }
        if (skiaImage != null) {
            return DecodeResult(SkiaImageWrapper(skiaImage, null), isSampled = false)
        }

        // 调用鸿蒙系统 NDK 极速解码第 0 帧（针对 GIF, JPEG, HEIF, WebP 等）
        val handle = bytes.usePinned { pinned ->
            create_heif_image(
                pinned.addressOf(0).reinterpret(),
                bytes.size.toULong(),
                0f,
                0f
            )
        }
        if (handle == 0L) error("create_heif_image and skia decode failed")

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
        animation: OhosGifAnimation?
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

/**
 * 鸿蒙平台支持动图逐帧渲染的 AsyncImage 组件
 */
@Composable
actual fun AsyncImage(
    model: String,
    contentDescription: String?,
    modifier: Modifier,
    onLoading: (() -> Unit)?,
    onSuccess: (() -> Unit)?,
    onError: ((Throwable?) -> Unit)?
) {
    // 统一使用全局 SingletonImageLoader，共享宿主工程配置的 Fetcher 链与缓存体系
    val imageLoader = SingletonImageLoader.get(LocalPlatformContext.current)
    // 保存当前可播放的动图数据，null 时仅渲染静态首帧。
    var animationData by remember(model) { mutableStateOf<OhosGifAnimation?>(null) }
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
                onError?.invoke(error.result.throwable)
            },
            onSuccess = { success ->
                // 请求成功：提取动图元数据，准备异步动图播放。
                val wrapper = success.result.image as? SkiaImageWrapper
                animationData = wrapper?.animation
                onSuccess?.invoke()
            }
        )

        AnimatedOverlay(animationData, contentDescription, fillParentBounds)
    }
}

/**
 * 动图覆层，通过与组件生命周期绑定的协程在后台异步驱动逐帧解码与播放
 */
@Composable
private fun BoxScope.AnimatedOverlay(
    animation: OhosGifAnimation?,
    contentDescription: String?,
    fillParentBounds: Boolean,
) {
    animation ?: return

    var currentFrame by remember(animation) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(animation) {
        // 在后台计算线程异步初始化 NDK 动图解码句柄并提取帧元数据，完全不阻塞 UI 渲染
        withContext(Dispatchers.Default) {
            val handle = animation.bytes.usePinned { pinned ->
                create_heif_animation(pinned.addressOf(0).reinterpret(), animation.bytes.size.toULong())
            }
            if (handle == 0L) return@withContext

            try {
                // 异步读取帧数与各帧延迟
                val meta = memScoped {
                    val countVar = alloc<UIntVar>()
                    if (animation_get_frame_count(handle, countVar.ptr) != 0 || countVar.value <= 1u) {
                        return@memScoped null
                    }
                    val frameCount = countVar.value.toInt()
                    if (frameCount > MAX_ANIMATION_FRAMES) return@memScoped null

                    val delays = MutableList(frameCount) { 100 }
                    var width = 0
                    var height = 0

                    for (index in 0 until frameCount) {
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

                    if (width <= 0 || height <= 0 || width * height > MAX_ANIMATION_PIXELS) {
                        return@memScoped null
                    }
                    if ((width * height).toLong() * frameCount > 300_000_000L) {
                        return@memScoped null
                    }

                    Triple(frameCount, width to height, delays)
                } ?: return@withContext

                val frameCount = meta.first
                val (width, height) = meta.second
                val delays = meta.third

                // 逐帧拉取像素并刷新 Compose 层 Bitmap，复用单块缓冲区避免 GC 抖动
                val pixelBuffer = ByteArray(width * height * 4)
                val info = ImageInfo(
                    width,
                    height,
                    org.jetbrains.skia.ColorType.RGBA_8888,
                    org.jetbrains.skia.ColorAlphaType.PREMUL
                )
                var frameIndex = 0
                while (isActive) {
                    val copyResult = pixelBuffer.usePinned { pinned ->
                        animation_copy_frame_pixels(handle, frameIndex.toUInt(), pinned.addressOf(0).reinterpret(), pixelBuffer.size.toULong())
                    }
                    if (copyResult == 0) {
                        // 将 RGBA 像素复制成 Skia Image，再转 Compose ImageBitmap
                        currentFrame = SkiaImage.makeRaster(
                            imageInfo = info,
                            bytes = pixelBuffer.copyOf(),
                            rowBytes = width * 4
                        ).toComposeImageBitmap()
                    }

                    val delayMs = delays.getOrElse(frameIndex) { 100 }
                        .coerceAtLeast(16) // 下限 16ms 保持约 60 FPS
                    // 按帧延迟节奏驱动下一帧
                    delay(delayMs.toLong())
                    frameIndex = (frameIndex + 1) % frameCount
                }
            } finally {
                // 当 Composable 卸载或重新触发时，安全销毁当前生命周期的解码句柄
                destroy_heif_animation(handle)
            }
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
