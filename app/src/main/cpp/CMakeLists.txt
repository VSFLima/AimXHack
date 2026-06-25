cmake_minimum_required(VERSION 3.22)
project(aimxhack)

set(CMAKE_CXX_STANDARD 17)

add_library(aimxhack SHARED
    native_bridge.cpp
    memory/MemoryManager.cpp
    predictor/Prediction.cpp
)

target_include_directories(aimxhack PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)
target_link_libraries(aimxhack ${log-lib})
