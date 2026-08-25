# CMake 最低版本
cmake_minimum_required(VERSION 3.28.0)
project(resources_utils)

# 工程路径
set(NATIVERENDER_ROOT_PATH .)

# 如果外部传了 PACKAGE_FIND_FILE，则 include
if(DEFINED PACKAGE_FIND_FILE)
    include(${PACKAGE_FIND_FILE})
endif()

# 头文件路径
include_directories(${NATIVERENDER_ROOT_PATH}
        ${NATIVERENDER_ROOT_PATH}/include)

# 生成静态库 resource_utils
add_library(resource_utils STATIC animated_image.cpp)

# 链接鸿蒙提供的 N-API/资源库
target_link_libraries(resource_utils PUBLIC
        libace_napi.z.so
        libimage_source.so
        libohresmgr.so
        librawfile.z.so
        libhilog_ndk.z.so
        arkui_ndk.z.so
        libace_ndk.z.so
        libimage_common.so
        libpixelmap.so
)
