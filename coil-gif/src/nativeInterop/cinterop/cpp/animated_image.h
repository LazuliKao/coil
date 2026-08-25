
#ifndef COIL3_ANIMATED_IMAGE_H
#define COIL3_ANIMATED_IMAGE_H
#ifdef __cplusplus
extern "C" {
#endif
#include <stdint.h>
#include <stddef.h>

// 从PixelMap句柄读取像素数据
// 参数：
//   handle - PixelMap句柄（从 create_heif_image 获取）
//   out_data - 输出像素数据（RGBA格式，调用者需要用 free_image_data 释放）
//   out_width - 图片宽度
//   out_height - 图片高度
// 返回：0成功，非0失败
int load_image_pixels(int64_t handle, uint8_t** out_data,
        uint32_t* out_width, uint32_t* out_height);

// 释放图像数据内存
void free_image_data(uint8_t* data);

// === 静态图片接口（保持向后兼容） ===
// 创建HEIF图片组件，返回PixelMap句柄
int64_t create_heif_image(uint8_t *buffer, size_t length, float width, float height);

// 销毁PixelMap
void destroy_heif_image(int64_t handle);

// === 动图接口 ===
// 创建 HEIF 动图解码句柄
int64_t create_heif_animation(uint8_t *buffer, size_t length);

// 获取帧数
int animation_get_frame_count(int64_t handle, uint32_t* out_count);

// 获取指定帧的宽、高、延迟（毫秒）
int animation_get_frame_info(int64_t handle, uint32_t index,
        uint32_t* out_width, uint32_t* out_height, int32_t* out_delay_ms);

// 将指定帧像素拷贝到目标缓冲区（RGBA8888）
int animation_copy_frame_pixels(int64_t handle, uint32_t index, uint8_t* dest, size_t dest_size);

// 销毁 HEIF 动图解码句柄
void destroy_heif_animation(int64_t handle);

#ifdef __cplusplus
}
#endif
#endif
