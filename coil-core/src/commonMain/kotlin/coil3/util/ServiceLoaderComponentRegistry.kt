package coil3.util

import coil3.ComponentRegistry
import coil3.RealImageLoader
import coil3.annotation.InternalCoilApi
import coil3.decode.Decoder
import coil3.fetch.Fetcher
import coil3.serviceLoaderEnabled
import kotlin.reflect.KClass

@InternalCoilApi
expect object ServiceLoaderComponentRegistry {
    val fetchers: List<FetcherServiceLoaderTarget<*>>
    val decoders: List<DecoderServiceLoaderTarget>

    // Only available on non-JVM. Added these declarations to work-around a compiler bug.
    fun register(fetcher: FetcherServiceLoaderTarget<*>)
    fun register(decoder: DecoderServiceLoaderTarget)
}

@InternalCoilApi
interface FetcherServiceLoaderTarget<T : Any> {
    fun factory(): Fetcher.Factory<T>?
    fun type(): KClass<T>?
    fun priority(): Int = 0
}

@InternalCoilApi
interface DecoderServiceLoaderTarget {
    fun factory(): Decoder.Factory?
    fun priority(): Int = 0
}

@Suppress("UNCHECKED_CAST")
internal fun ComponentRegistry.Builder.addServiceLoaderComponents(
    options: RealImageLoader.Options,
): ComponentRegistry.Builder {
    if (options.serviceLoaderEnabled) {
        // Delay reading the fetchers and decoders until the fetching/decoding stage.
        addFetcherFactories {
            ServiceLoaderComponentRegistry.fetchers
                .sortedByDescending { it.priority() }
                .mapNotNullIndices { target ->
                    target as FetcherServiceLoaderTarget<Any>
                    val factory = target.factory() ?: return@mapNotNullIndices null
                    val type = target.type() ?: return@mapNotNullIndices null
                    factory to type
                }
        }
        addDecoderFactories {
            ServiceLoaderComponentRegistry.decoders
                .sortedByDescending { it.priority() }
                .mapNotNullIndices { target ->
                    target.factory()
                }
        }
    }
    return this
}
