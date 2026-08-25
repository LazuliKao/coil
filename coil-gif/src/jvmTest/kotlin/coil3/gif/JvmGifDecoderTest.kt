package coil3.gif

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

class JvmGifDecoderTest {

    @Test
    fun decodesAnimatedGifMetadataAndFrames() = runTest {
        val bytes = ANIMATED_GIF
        val result = decode(bytes)
        val image = assertIs<JvmGifImage>(result.image)
        val animation = assertIs<JvmGifAnimation>(image.animation)

        assertEquals(2, animation.delays.size)
        assertEquals(10, animation.delays[0])
        assertEquals(20, animation.delays[1])
        assertContentEquals(bytes, animation.bytes)

        val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
        val firstFrame = codec.readFrame(0)
        val secondFrame = codec.readFrame(1)
        assertFalse(firstFrame.readPixels()!!.contentEquals(secondFrame.readPixels()!!))
        codec.close()
    }

    @Test
    fun doesNotHandleNonGifSource() {
        val source = imageSource(PNG_HEADER)
        val result = SourceFetchResult(source, "image/png", DataSource.MEMORY)

        assertNull(JvmGifDecoder.Factory().create(result, options, imageLoader))
    }

    private suspend fun decode(bytes: ByteArray) = JvmGifDecoder.Factory()
        .create(
            SourceFetchResult(imageSource(bytes), "image/gif", DataSource.MEMORY),
            options,
            imageLoader,
        )!!
        .decode()!!

    private fun imageSource(bytes: ByteArray) = ImageSource(
        source = Buffer().write(bytes),
        fileSystem = FileSystem.SYSTEM,
    )

    private companion object {
        val imageLoader = ImageLoader(PlatformContext.INSTANCE)
        val options = Options(PlatformContext.INSTANCE)

        // A 1x1, two-frame GIF with 10 ms and 20 ms frame delays.
        val ANIMATED_GIF = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
            0x01, 0x00, 0x01, 0x00, 0x80.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x21, 0xF9.toByte(), 0x04, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00,
            0x21, 0xF9.toByte(), 0x04, 0x00, 0x02, 0x00, 0x00, 0x00,
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x4C, 0x01, 0x00,
            0x3B,
        )

        val PNG_HEADER = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}

private fun Codec.readFrame(index: Int): Bitmap {
    val bitmap = Bitmap()
    check(bitmap.allocPixels(imageInfo))
    readPixels(bitmap, index)
    return bitmap
}
