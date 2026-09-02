CXX ?= g++
CXXFLAGS ?= -std=c++17 -Wall -Wextra -O2 -D_FORTIFY_SOURCE=2 -fstack-protector-strong -pthread
INCLUDES = -Iinclude
TARGET = assistant_service

.PHONY: all clean run

all: $(TARGET)

$(TARGET): src/main.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDES) $< -o $@

run: $(TARGET)
	./$(TARGET)

clean:
	rm -f $(TARGET) test_runner *.o
