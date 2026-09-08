package coil3.gif

import coil3.Canvas
import coil3.Image
import coil3.ImageLoader
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

internal data class JvmGifAnimation(
    val bytes: ByteArray,
    val delays: IntArray,
    val repetitionCount: Int,
    val width: Int,
    val height: Int,
)

internal class JvmGifImage(
    private val bitmap: Bitmap,
    val animation: JvmGifAnimation?,
) : Image {
    override val size: Long = bitmap.imageInfo.computeMinByteSize().toLong() +
        (animation?.bytes?.size ?: 0)
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override val shareable: Boolean = true

    override fun draw(canvas: Canvas) {
        canvas.writePixels(bitmap, 0, 0)
    }
}

internal class JvmGifDecoder(
    private val source: ImageSource,
) : Decoder {

    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        try {
            val frameCount = codec.frameCount
            val frameInfo = if (frameCount > 1) codec.framesInfo else emptyArray()
            val bitmap = codec.readPixels()
            val animation = if (frameCount > 1) {
                JvmGifAnimation(
                    bytes = bytes,
                    delays = IntArray(frameCount) { index ->
                        val duration = frameInfo.getOrNull(index)?.duration
                        if (duration != null && duration > 0) duration else DEFAULT_FRAME_DELAY_MILLIS
                    },
                    repetitionCount = codec.repetitionCount,
                    width = bitmap.width,
                    height = bitmap.height,
                )
            } else {
                null
            }

            return DecodeResult(
                image = JvmGifImage(bitmap, animation),
                isSampled = false,
            )
        } finally {
            codec.close()
        }
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? {
            val source = result.source.source()
            return if (DecodeUtils.isGif(source) || DecodeUtils.isAnimatedWebP(source)) {
                JvmGifDecoder(result.source)
            } else {
                null
            }
        }
    }

    private companion object {
        const val DEFAULT_FRAME_DELAY_MILLIS = 100
    }
}
