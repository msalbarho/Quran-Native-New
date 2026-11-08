cmake_minimum_required(VERSION 3.22.1)
project(quranwhisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_BUILD_TYPE Release)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper)

set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
# Baseline ISA only (armv8-a / x86-64) so the library runs on every arm64 phone (e.g. Cortex-A53).
set(GGML_CPU_ARM_ARCH "" CACHE STRING "" FORCE)
add_subdirectory(${WHISPER_DIR}/ggml ggml)

add_library(quranwhisper SHARED
    ${WHISPER_DIR}/src/whisper.cpp
    whisper_jni.cpp)
target_include_directories(quranwhisper PRIVATE ${WHISPER_DIR}/include ${WHISPER_DIR}/src ${WHISPER_DIR}/ggml/include)
target_compile_definitions(quranwhisper PRIVATE GGML_USE_CPU)
target_compile_options(quranwhisper PRIVATE -O3 -ffunction-sections -fdata-sections)
target_link_options(quranwhisper PRIVATE -Wl,--gc-sections)
# NDK r27 defaults to 4 KiB. Keep every LOAD segment usable on 16 KiB devices.
target_link_options(quranwhisper PRIVATE -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
find_library(LOG_LIB log)
target_link_libraries(quranwhisper ggml ${LOG_LIB} android)
