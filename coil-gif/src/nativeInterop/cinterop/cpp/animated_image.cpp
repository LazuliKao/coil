#include "animated_image.h"
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <new>
#include <hilog/log.h>
#include <multimedia/image_framework/image/image_common.h>
#include <multimedia/image_framework/image/image_source_native.h>
#include <multimedia/image_framework/image/pixelmap_native.h>
#include <arkui/native_interface.h>
#include <arkui/native_node.h>
#include <arkui/native_type.h>
#undef LOG_DOMAIN
#undef LOG_TAG
#define LOG_DOMAIN 0x3200
#define LOG_TAG "HeifImage"

int load_image_pixels(int64_t handle, uint8_t** out_data,
        uint32_t* out_width, uint32_t* out_height) {
    OH_LOG_INFO(LOG_APP, "=== 读取图片像素数据 ===");
    OH_LOG_INFO(LOG_APP, "句柄: %{public}ld", static_cast<long>(handle));

    if (handle == 0 || !out_data || !out_width || !out_height) {
        OH_LOG_ERROR(LOG_APP, "无效参数");
        return -1;
    }

    // 步骤1：从句柄转换为 PixelMap
    OH_PixelmapNative* pixelmap = reinterpret_cast<OH_PixelmapNative*>(handle);

    // 步骤2：获取 PixelMap 信息
    OH_Pixelmap_ImageInfo* pixelmapInfo = nullptr;
    OH_PixelmapImageInfo_Create(&pixelmapInfo);
    Image_ErrorCode errCode = OH_PixelmapNative_GetImageInfo(pixelmap, pixelmapInfo);
    if (errCode != IMAGE_SUCCESS) {
        OH_LOG_ERROR(LOG_APP, "获取PixelMap信息失败");
        OH_PixelmapImageInfo_Release(pixelmapInfo);
        return -1;
    }

    uint32_t width = 0, height = 0;
    OH_PixelmapImageInfo_GetWidth(pixelmapInfo, &width);
    OH_PixelmapImageInfo_GetHeight(pixelmapInfo, &height);
    OH_PixelmapImageInfo_Release(pixelmapInfo);

    OH_LOG_INFO(LOG_APP, "PixelMap 尺寸: %{public}u x %{public}u", width, height);

    // 步骤3：读取像素数据
    size_t bufferSize = width * height * 4;  // RGBA格式
    uint8_t* buffer = new uint8_t[bufferSize];

    errCode = OH_PixelmapNative_ReadPixels(pixelmap, buffer, &bufferSize);
    if (errCode != IMAGE_SUCCESS) {
        OH_LOG_ERROR(LOG_APP, "读取像素数据失败，错误码: %{public}d", errCode);
        delete[] buffer;
        return -1;
    }

    OH_LOG_INFO(LOG_APP, "✓ 读取像素数据成功，大小: %{public}zu 字节", bufferSize);

    // 步骤4：返回数据
    *out_data = buffer;
    *out_width = width;
    *out_height = height;

    OH_LOG_INFO(LOG_APP, "=== 读取图片像素数据成功 ===");
    return 0;
}

void free_image_data(uint8_t* data) {
    if (data) {
        delete[] data;
        OH_LOG_INFO(LOG_APP, "✓ 像素数据内存已释放");
    }
}

struct AnimationFrame {
    std::vector<uint8_t> pixels;
    uint32_t width = 0;
    uint32_t height = 0;
    int32_t delayMs = 0;
};

struct HeifAnimation {
    std::vector<AnimationFrame> frames;
};

static bool CopyPixelmapToFrame(OH_PixelmapNative* pixelmap, AnimationFrame& outFrame) {
    if (!pixelmap) {
        return false;
    }

    uint8_t* data = nullptr;
    uint32_t width = 0;
    uint32_t height = 0;
    if (load_image_pixels(reinterpret_cast<int64_t>(pixelmap), &data, &width, &height) != 0) {
        return false;
    }

    const size_t size = static_cast<size_t>(width) * static_cast<size_t>(height) * 4;
    outFrame.width = width;
    outFrame.height = height;
    outFrame.pixels.assign(data, data + size);
    free_image_data(data);
    return true;
}

int64_t create_heif_image(uint8_t *buffer, size_t length, float width, float height) {
    OH_LOG_INFO(LOG_APP, "=== HEIF 解码开始===");

    if (buffer == nullptr || length == 0) {
        OH_LOG_ERROR(LOG_APP, "无效的数据缓冲区");
        return 0;
    }

    // ===== 步骤1：创建 ImageSource =====
    OH_LOG_INFO(LOG_APP, "步骤1: 创建 ImageSource...");
    OH_ImageSourceNative* source = nullptr;
    OH_LOG_INFO(LOG_APP, "OH_ImageSourceNative_CreateFromUri: %{public}p", (void*)OH_ImageSourceNative_CreateFromUri);
    Image_ErrorCode errCode =  OH_ImageSourceNative_CreateFromData(buffer, length, &source);

    if (errCode != IMAGE_SUCCESS || !source) {
        OH_LOG_ERROR(LOG_APP, "创建 ImageSource 失败，错误码: %{public}d", errCode);
        return 0;
    }
    OH_LOG_INFO(LOG_APP, "✓ ImageSource 创建成功");

    // ===== 步骤2：获取图片信息 =====
    OH_LOG_INFO(LOG_APP, "步骤2: 获取图片信息...");
    OH_ImageSource_Info* imageInfo = nullptr;
    OH_ImageSourceInfo_Create(&imageInfo);
    if (imageInfo) {
        errCode = OH_ImageSourceNative_GetImageInfo(source, 0, imageInfo);
        if (errCode == IMAGE_SUCCESS) {
            uint32_t imgWidth = 0, imgHeight = 0;
            OH_ImageSourceInfo_GetWidth(imageInfo, &imgWidth);
            OH_ImageSourceInfo_GetHeight(imageInfo, &imgHeight);
            OH_LOG_INFO(LOG_APP, "✓ 原始图片尺寸: %{public}u x %{public}u", imgWidth, imgHeight);
        } else {
            OH_LOG_WARN(LOG_APP, "获取图片信息失败，但继续处理...");
        }
        OH_ImageSourceInfo_Release(imageInfo);
    }

    // ===== 步骤3：获取帧数，判断动图 =====
    uint32_t frameCnt = 1;
    errCode = OH_ImageSourceNative_GetFrameCount(source, &frameCnt);
    if (errCode != IMAGE_SUCCESS) {
        OH_LOG_ERROR(LOG_APP, "获取帧数失败，错误码: %{public}d", errCode);
        OH_ImageSourceNative_Release(source);
        return 0;
    }
    OH_LOG_INFO(LOG_APP, "帧数: %{public}u", frameCnt);

    // ===== 步骤4：设置解码选项 =====
    OH_LOG_INFO(LOG_APP, "步骤3: 设置解码选项...");
    OH_DecodingOptions* decodingOpts = nullptr;
    OH_DecodingOptions_Create(&decodingOpts);
    if (decodingOpts) {
        // 设置动态范围（支持HDR）
        OH_DecodingOptions_SetDesiredDynamicRange(decodingOpts, IMAGE_DYNAMIC_RANGE_AUTO);
    }

    // ===== 步骤4：创建单帧 PixelMap（无论动图或静态图，仅极速解码第 0 帧） =====
    OH_LOG_INFO(LOG_APP, "步骤4: 创建首帧 PixelMap...");
    OH_PixelmapNative* pixelmap = nullptr;
    errCode = OH_ImageSourceNative_CreatePixelmap(source, decodingOpts, &pixelmap);
    OH_LOG_INFO(LOG_APP, "OH_ImageSourceNative_CreatePixelmap: %{public}p", (void*)OH_ImageSourceNative_CreatePixelmap);

    if (decodingOpts) {
        OH_DecodingOptions_Release(decodingOpts);
        decodingOpts = nullptr;
    }

    if (errCode != IMAGE_SUCCESS || !pixelmap) {
        OH_LOG_ERROR(LOG_APP, "创建 PixelMap 失败，错误码: %{public}d", errCode);
        OH_ImageSourceNative_Release(source);
        return 0;
    }

    OH_ImageSourceNative_Release(source);

    OH_LOG_INFO(LOG_APP, "✓ PixelMap 创建成功");

    // ===== 步骤5：获取并验证 PixelMap 信息 =====
    OH_LOG_INFO(LOG_APP, "步骤5: 验证 PixelMap...");
    OH_Pixelmap_ImageInfo* pixelmapInfo = nullptr;
    OH_PixelmapImageInfo_Create(&pixelmapInfo);

    if (pixelmapInfo) {
        errCode = OH_PixelmapNative_GetImageInfo(pixelmap, pixelmapInfo);
        if (errCode == IMAGE_SUCCESS) {
            uint32_t pmWidth = 0, pmHeight = 0;
            OH_PixelmapImageInfo_GetWidth(pixelmapInfo, &pmWidth);
            OH_PixelmapImageInfo_GetHeight(pixelmapInfo, &pmHeight);
            int32_t pixelFormat = 0;
            OH_PixelmapImageInfo_GetPixelFormat(pixelmapInfo, &pixelFormat);

            bool isHdr = false;
            OH_PixelmapImageInfo_GetDynamicRange(pixelmapInfo, &isHdr);

            OH_LOG_INFO(LOG_APP, "✓ PixelMap 尺寸: %{public}u x %{public}u", pmWidth, pmHeight);
            OH_LOG_INFO(LOG_APP, "✓ 像素格式: %{public}d", pixelFormat);
            OH_LOG_INFO(LOG_APP, "✓ 是否HDR: %{public}s", isHdr ? "是" : "否");
        }
        OH_PixelmapImageInfo_Release(pixelmapInfo);
    }

    OH_LOG_INFO(LOG_APP, "=== HEIF 解码成功 ===");
    OH_LOG_INFO(LOG_APP, "返回 PixelMap 句柄: %{public}ld",
            static_cast<long>(reinterpret_cast<int64_t>(pixelmap)));
    //返回 PixelMap 作为句柄
    return reinterpret_cast<int64_t>(pixelmap);
}

void destroy_heif_image(int64_t handle) {
    OH_LOG_INFO(LOG_APP, "=== 销毁 HEIF 图像 ===");
    OH_LOG_INFO(LOG_APP, "句柄: %{public}ld", static_cast<long>(handle));

    if (handle == 0) {
        OH_LOG_WARN(LOG_APP, "无效句柄 (0)");
        return;
    }

    // 将句柄转换回 PixelMap 并释放
    OH_PixelmapNative* pixelmap = reinterpret_cast<OH_PixelmapNative*>(handle);

    // 释放 PixelMap
    OH_PixelmapNative_Release(pixelmap);

    OH_LOG_INFO(LOG_APP, "✓ PixelMap 已释放");
}

// === 动图接口实现 ===
int64_t create_heif_animation(uint8_t *buffer, size_t length) {
    OH_LOG_INFO(LOG_APP, "[Animation] create_heif_animation start, length=%{public}zu", length);
    if (buffer == nullptr || length == 0) {
        OH_LOG_ERROR(LOG_APP, "[Animation] invalid buffer");
        return 0;
    }

    OH_ImageSourceNative* source = nullptr;
    Image_ErrorCode errCode = OH_ImageSourceNative_CreateFromData(buffer, length, &source);
    if (errCode != IMAGE_SUCCESS || !source) {
        OH_LOG_ERROR(LOG_APP, "[Animation] CreateFromData failed, errCode=%{public}d", errCode);
        return 0;
    }

    uint32_t frameCnt = 1;
    errCode = OH_ImageSourceNative_GetFrameCount(source, &frameCnt);
    if (errCode != IMAGE_SUCCESS) {
        OH_LOG_WARN(LOG_APP, "[Animation] GetFrameCount failed, errCode=%{public}d, fallback to single frame.", errCode);
        frameCnt = 1;
    }
    if (frameCnt == 0) {
        frameCnt = 1;
    }
    OH_LOG_INFO(LOG_APP, "[Animation] frame count = %{public}u", frameCnt);

    OH_DecodingOptions* decodingOpts = nullptr;
    OH_DecodingOptions_Create(&decodingOpts);
    if (decodingOpts) {
        OH_DecodingOptions_SetDesiredDynamicRange(decodingOpts, IMAGE_DYNAMIC_RANGE_AUTO);
    }

    std::vector<OH_PixelmapNative*> pixelmaps(frameCnt, nullptr);
    if (frameCnt > 1) {
        errCode = OH_ImageSourceNative_CreatePixelmapList(source, decodingOpts, pixelmaps.data(), frameCnt);
        if (errCode != IMAGE_SUCCESS) {
            OH_LOG_ERROR(LOG_APP, "[Animation] CreatePixelmapList failed, errCode=%{public}d", errCode);
            if (decodingOpts) {
                OH_DecodingOptions_Release(decodingOpts);
            }
            OH_ImageSourceNative_Release(source);
            return 0;
        }
    } else {
        OH_PixelmapNative* pixelmap = nullptr;
        errCode = OH_ImageSourceNative_CreatePixelmap(source, decodingOpts, &pixelmap);
        if (errCode != IMAGE_SUCCESS || !pixelmap) {
            OH_LOG_ERROR(LOG_APP, "[Animation] CreatePixelmap failed, errCode=%{public}d", errCode);
            if (decodingOpts) {
                OH_DecodingOptions_Release(decodingOpts);
            }
            OH_ImageSourceNative_Release(source);
            return 0;
        }
        pixelmaps[0] = pixelmap;
    }

    int32_t* delayList = nullptr;
    std::vector<int32_t> delays(frameCnt, 0);
    if (frameCnt > 1) {
        delayList = new int32_t[frameCnt];
        errCode = OH_ImageSourceNative_GetDelayTimeList(source, delayList, frameCnt);
        if (errCode != IMAGE_SUCCESS) {
            OH_LOG_WARN(LOG_APP, "[Animation] GetDelayTimeList failed, errCode=%{public}d", errCode);
            std::fill(delays.begin(), delays.end(), 100);
        } else {
            for (uint32_t i = 0; i < frameCnt; ++i) {
                delays[i] = delayList[i];
            }
        }
    }

    if (decodingOpts) {
        OH_DecodingOptions_Release(decodingOpts);
    }
    OH_ImageSourceNative_Release(source);
    delete[] delayList;

    auto* animation = new (std::nothrow) HeifAnimation();
    if (!animation) {
        OH_LOG_ERROR(LOG_APP, "[Animation] failed to allocate HeifAnimation");
        for (auto* pixelmap : pixelmaps) {
            if (pixelmap) {
                OH_PixelmapNative_Release(pixelmap);
            }
        }
        return 0;
    }

    animation->frames.reserve(frameCnt);
    for (uint32_t i = 0; i < frameCnt; ++i) {
        OH_PixelmapNative* pixelmap = pixelmaps[i];
        if (!pixelmap) {
            OH_LOG_WARN(LOG_APP, "[Animation] pixelmap[%{public}u] is null", i);
            continue;
        }

        AnimationFrame frame;
        if (!CopyPixelmapToFrame(pixelmap, frame)) {
            OH_LOG_ERROR(LOG_APP, "[Animation] copy pixels failed for frame %{public}u", i);
            OH_PixelmapNative_Release(pixelmap);
            for (uint32_t j = i + 1; j < pixelmaps.size(); ++j) {
                if (pixelmaps[j]) {
                    OH_PixelmapNative_Release(pixelmaps[j]);
                }
            }
            destroy_heif_animation(reinterpret_cast<int64_t>(animation));
            return 0;
        }
        if (!delays.empty()) {
            frame.delayMs = delays[i];
        }
        animation->frames.push_back(std::move(frame));
        OH_PixelmapNative_Release(pixelmap);
    }

    if (animation->frames.empty()) {
        OH_LOG_ERROR(LOG_APP, "[Animation] no frames decoded");
        destroy_heif_animation(reinterpret_cast<int64_t>(animation));
        return 0;
    }

    OH_LOG_INFO(LOG_APP, "[Animation] create_heif_animation success, frames=%{public}zu",
            animation->frames.size());
    return reinterpret_cast<int64_t>(animation);
}

int animation_get_frame_count(int64_t handle, uint32_t* out_count) {
    if (handle == 0 || out_count == nullptr) {
        return -1;
    }
    auto* animation = reinterpret_cast<HeifAnimation*>(handle);
    *out_count = static_cast<uint32_t>(animation->frames.size());
    return 0;
}

int animation_get_frame_info(int64_t handle, uint32_t index,
        uint32_t* out_width, uint32_t* out_height, int32_t* out_delay_ms) {
    auto* animation = reinterpret_cast<HeifAnimation*>(handle);
    if (!animation || index >= animation->frames.size()) {
        return -1;
    }
    const AnimationFrame& frame = animation->frames[index];
    if (out_width) {
        *out_width = frame.width;
    }
    if (out_height) {
        *out_height = frame.height;
    }
    if (out_delay_ms) {
        *out_delay_ms = frame.delayMs;
    }
    return 0;
}

int animation_copy_frame_pixels(int64_t handle, uint32_t index, uint8_t* dest, size_t dest_size) {
    auto* animation = reinterpret_cast<HeifAnimation*>(handle);
    if (!animation || index >= animation->frames.size() || dest == nullptr) {
        return -1;
    }
    const AnimationFrame& frame = animation->frames[index];
    const size_t expectedSize = static_cast<size_t>(frame.width) * static_cast<size_t>(frame.height) * 4;
    if (dest_size < expectedSize) {
        return -2;
    }
    std::memcpy(dest, frame.pixels.data(), expectedSize);
    return 0;
}

void destroy_heif_animation(int64_t handle) {
    auto* animation = reinterpret_cast<HeifAnimation*>(handle);
    if (!animation) {
        return;
    }
    delete animation;
}
