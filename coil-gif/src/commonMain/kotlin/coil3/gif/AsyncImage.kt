package coil3.gif

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 跨平台的 AsyncImage 组件封装。
 *
 * Android: 使用 Coil 原生 AsyncImage
 * OHOS: 使用原生图像解码与 Compose 动画桥接
 *
 * @param model 图片 URL（支持网络、本地等来源）
 * @param contentDescription 内容描述
 * @param modifier Compose 修饰符
 * @param onLoading 加载中的回调
 * @param onSuccess 加载成功的回调
 * @param onError 加载失败的回调
 */
@Composable
expect fun AsyncImage(
    model: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onLoading: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
    onError: ((Throwable?) -> Unit)? = null
)
