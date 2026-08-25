package coil3.network.ktor3.internal

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import okio.BufferedSink
import okio.FileSystem
import okio.Path

internal actual suspend fun ByteReadChannel.writeTo(sink: BufferedSink) {
    val buffer = ByteArray(8192)
    while (!isClosedForRead) {
        val read = readAvailable(buffer, 0, buffer.size)
        if (read <= 0) break
        sink.write(buffer, 0, read)
    }
}

internal actual suspend fun ByteReadChannel.writeTo(fileSystem: FileSystem, path: Path) {
    fileSystem.write(path) {
        writeTo(this)
    }
}
