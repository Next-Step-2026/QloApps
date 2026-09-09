cmake_minimum_required(VERSION 3.14)
project(assistant_service VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_compile_options(-Wall -Wextra -O2 -D_FORTIFY_SOURCE=2 -fstack-protector-strong)

include_directories(include)

add_executable(assistant_service src/main.cpp)
find_package(Threads REQUIRED)
target_link_libraries(assistant_service PRIVATE Threads::Threads)
