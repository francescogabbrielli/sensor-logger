cmake_minimum_required(VERSION 3.4.1)
set (CMAKE_VERBOSE_MAKEFILE "ON")

#set (OpenCV_DIR "sdk/native/jni/")
#find_package( OpenCV REQUIRED core imgproc)

#message (STATUS "core: ${OPENCV_core_FOUND}")
#message (STATUS "highgui: ${OPENCV_highgui_FOUND}")
#message (STATUS "imgproc: ${OPENCV_imgproc_FOUND}")
#message (STATUS "Libraries loaded: ${OpenCV_LIBS}")

add_library(opencv SHARED "src/main/cpp/static.cpp")

target_link_libraries(opencv)