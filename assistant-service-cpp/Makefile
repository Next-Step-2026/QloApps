CXX ?= g++
CXXFLAGS ?= -std=c++17 -Wall -Wextra -O2 -D_FORTIFY_SOURCE=2 -fstack-protector-strong -pthread
INCLUDES = -Iinclude
TARGET = assistant_service
SRCS = src/main.cpp src/app.cpp src/http_server.cpp

.PHONY: all clean run test test_unit

all: $(TARGET)

$(TARGET): $(SRCS)
	$(CXX) $(CXXFLAGS) $(INCLUDES) $(SRCS) -o $@

test_classifier: tests/test_classifier.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDES) $< -o $@

test_unit: test_classifier
	./test_classifier

run: $(TARGET)
	./$(TARGET)

test: $(TARGET) test_classifier
	./test_classifier
	@bash tests/test_api.sh

clean:
	rm -f $(TARGET) test_classifier test_runner *.o
