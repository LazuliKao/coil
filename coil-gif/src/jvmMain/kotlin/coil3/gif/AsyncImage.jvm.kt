package coil3.gif

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage as CoilAsyncImage
import coil3.compose.LocalPlatformContext

@Composable
actual fun AsyncImage(
    model: String,
    contentDescription: String?,
    modifier: Modifier,
    onLoading: (() -> Unit)?,
    onSuccess: (() -> Unit)?,
    onError: ((Throwable?) -> Unit)?
) {
    CoilAsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = SingletonImageLoader.get(LocalPlatformContext.current),
        modifier = modifier,
        onLoading = { onLoading?.invoke() },
        onSuccess = { onSuccess?.invoke() },
        onError = { onError?.invoke(it.result.throwable) }
    )
}
