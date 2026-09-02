# Makefile for QLO-FEAT-007: Overlap Detector Service (overlap-service-cpp)
# Stack: C++17, cpp-httplib, nlohmann/json, POSIX threads

CXX ?= g++
CXXFLAGS ?= -std=c++17 -Wall -Wextra -O2
CPPFLAGS ?= -I$(INC_DIR) -MMD -MP
LDFLAGS ?=
LDLIBS ?= -lpthread

SRC_DIR ?= src
INC_DIR ?= include
TEST_DIR ?= tests
BUILD_DIR ?= build

TARGET ?= overlap_service
TEST_TARGET ?= test_overlaps

SRCS := $(wildcard $(SRC_DIR)/*.cpp)
OBJS := $(patsubst $(SRC_DIR)/%.cpp,$(BUILD_DIR)/%.o,$(SRCS))
DEPS := $(OBJS:.o=.d)

TEST_SRCS := $(wildcard $(TEST_DIR)/*.cpp)
TEST_OBJS := $(patsubst $(TEST_DIR)/%.cpp,$(BUILD_DIR)/%.o,$(TEST_SRCS))
NON_MAIN_OBJS := $(filter-out $(BUILD_DIR)/main.o,$(OBJS))

.PHONY: all clean run test help

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CXX) $(CXXFLAGS) $(OBJS) $(LDFLAGS) $(LDLIBS) -o $@

$(BUILD_DIR)/%.o: $(SRC_DIR)/%.cpp | $(BUILD_DIR)
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -c $< -o $@

$(BUILD_DIR)/%.o: $(TEST_DIR)/%.cpp | $(BUILD_DIR)
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -c $< -o $@

$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

-include $(DEPS)

run: $(TARGET)
	./$(TARGET)

test: $(TARGET)
	@if [ -n "$(TEST_SRCS)" ]; then \
		echo "Building unit test binary $(TEST_TARGET)..."; \
		$(MAKE) $(TEST_TARGET); \
		echo "Running unit tests..."; \
		./$(TEST_TARGET); \
	else \
		echo "No test sources found in $(TEST_DIR)/. When tests are added, 'make test' will build and run them."; \
	fi

$(TEST_TARGET): $(NON_MAIN_OBJS) $(TEST_OBJS)
	$(CXX) $(CXXFLAGS) $(NON_MAIN_OBJS) $(TEST_OBJS) $(LDFLAGS) $(LDLIBS) -o $@

clean:
	rm -rf $(BUILD_DIR) $(TARGET) $(TEST_TARGET)

help:
	@echo "Available targets:"
	@echo "  all     : Compile and build the $(TARGET) binary (default)"
	@echo "  run     : Build and launch the $(TARGET) service"
	@echo "  test    : Build and run test suite if tests exist"
	@echo "  clean   : Remove build artifacts and compiled binaries"
	@echo "  help    : Display this help message"
