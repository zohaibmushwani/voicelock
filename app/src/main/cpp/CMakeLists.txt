cmake_minimum_required(VERSION 3.22.1)

project("voicelock-native")

include(FetchContent)

# kaldi-native-fbank integration
FetchContent_Declare(
  kaldi_native_fbank
  GIT_REPOSITORY https://github.com/csukuangfj/kaldi-native-fbank.git
  GIT_TAG master
)

set(KALDI_NATIVE_FBANK_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(KALDI_NATIVE_FBANK_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(KALDI_NATIVE_FBANK_BUILD_PYTHON OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(kaldi_native_fbank)

add_library(${CMAKE_PROJECT_NAME} SHARED
        fbank-jni.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${kaldi_native_fbank_SOURCE_DIR}
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        kaldi-native-fbank-core
        log
)
