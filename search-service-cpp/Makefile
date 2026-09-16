CXX = g++
CXXFLAGS = -std=c++17 -Wall -Wextra -O2 -Iinclude -pthread

JSON_HPP = include/json.hpp
HTTPLIB_H = include/httplib.h

SRCS = $(wildcard src/*.cpp)
TARGET = search_service

TEST_SRCS = $(wildcard tests/*.cpp)
TEST_TARGET = test_search

.PHONY: all build clean run test deps

all: build

deps: $(JSON_HPP) $(HTTPLIB_H)

$(JSON_HPP):
	@mkdir -p include
	@if [ ! -f $(JSON_HPP) ]; then \
		echo "Downloading nlohmann/json.hpp..."; \
		curl -fsSLo $(JSON_HPP) https://github.com/nlohmann/json/releases/download/v3.11.3/json.hpp \
		|| wget -qO $(JSON_HPP) https://github.com/nlohmann/json/releases/download/v3.11.3/json.hpp \
		|| { echo "erro ao baixar json.hpp"; rm -f $(JSON_HPP); exit 1; }; \
	fi

$(HTTPLIB_H):
	@mkdir -p include
	@if [ ! -f $(HTTPLIB_H) ]; then \
		echo "Downloading cpp-httplib/httplib.h..."; \
		curl -fsSLo $(HTTPLIB_H) https://raw.githubusercontent.com/yhirose/cpp-httplib/v0.15.3/httplib.h \
		|| wget -qO $(HTTPLIB_H) https://raw.githubusercontent.com/yhirose/cpp-httplib/v0.15.3/httplib.h \
		|| { echo "erro ao baixar httplib.h"; rm -f $(HTTPLIB_H); exit 1; }; \
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
