package coil3.gif

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * iOS 平台的 AsyncImage 实现（当前占位）。
 *
 * iOS 端暂时使用空实现，因为主要目标是 Android 与 OHOS。
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
    // iOS 端暂时使用空实现
    // 如果需要 iOS 支持，可以在这里添加相应的实现
}
