package coil3.test.utils

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect

actual fun decodeBitmapResource(
    path: String,
): Bitmap {
    // Retry multiple times as the emulator can be flaky.
    var failures = 0
    while (true) {
        try {
            val source = FileSystem.RESOURCES.source(path.toPath())
            val image = Image.makeFromEncoded(source.buffer().readByteArray())
            val bitmap = Bitmap()
            bitmap.allocN32Pixels(image.width, image.height)
            val canvas = Canvas(bitmap)
            val rect = Rect.makeWH(image.width.toFloat(), image.height.toFloat())
            canvas.drawImageRect(image, rect)
            return bitmap
        } catch (e: Exception) {
            if (failures++ > 5) throw e
        }
    }
}
