CXX = g++
CXXFLAGS = -std=c++17 -Wall -Wextra -O2 -Iinclude

JSON_HPP = include/json.hpp

SRCS = $(wildcard src/*.cpp)
TARGET = search_service

TEST_SRCS = $(wildcard tests/*.cpp)
TEST_TARGET = test_search

.PHONY: all build clean run test deps

all: build

deps: $(JSON_HPP)

$(JSON_HPP):
	@mkdir -p include
	@if [ ! -f $(JSON_HPP) ]; then \
		echo "Downloading nlohmann/json.hpp..."; \
		curl -sLo $(JSON_HPP) https://github.com/nlohmann/json/releases/download/v3.11.3/json.hpp || wget -qO $(JSON_HPP) https://github.com/nlohmann/json/releases/download/v3.11.3/json.hpp; \
	fi

build: deps $(TARGET)

$(TARGET): $(SRCS) $(JSON_HPP)
	$(CXX) $(CXXFLAGS) $(SRCS) -o $(TARGET)

test: deps
	@if [ -n "$(TEST_SRCS)" ]; then \
		$(CXX) $(CXXFLAGS) $(TEST_SRCS) $(filter-out src/main.cpp, $(wildcard src/*.cpp)) -o $(TEST_TARGET) && ./$(TEST_TARGET); \
	else \
		echo "Nenhum teste encontrado em tests/. Criando scaffold de testes..."; \
	fi

run: build
	./$(TARGET)

clean:
	rm -f $(TARGET) $(TEST_TARGET) *.o src/*.o
