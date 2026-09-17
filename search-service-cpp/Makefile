CXX = g++
CXXFLAGS = -std=c++17 -Wall -Wextra -O2 -Iinclude -pthread

JSON_HPP = include/json.hpp
HTTPLIB_H = include/httplib.h

SRCS = $(wildcard src/*.cpp)
TARGET = search_service

TEST_SRCS = $(wildcard tests/*.cpp)
TEST_TARGET = test_search

GTEST_DIR = vendor/googletest
GTEST_INCLUDE = $(GTEST_DIR)/include
GTEST_ALL_CC = $(GTEST_DIR)/src/gtest-all.cc
GTEST_MAIN_CC = $(GTEST_DIR)/src/gtest_main.cc
GTEST_OBJ = $(GTEST_DIR)/gtest-all.o
GTEST_MAIN_OBJ = $(GTEST_DIR)/gtest_main.o

.PHONY: all build clean run test test-integration test-smoke test-coverage test-fuzz test-all deps gtest-deps

all: build

deps: $(JSON_HPP) $(HTTPLIB_H)

gtest-deps: $(GTEST_ALL_CC)

$(GTEST_ALL_CC):
	@mkdir -p $(GTEST_DIR)
	@if [ ! -f $(GTEST_ALL_CC) ]; then \
		echo "Downloading GoogleTest v1.14.0..."; \
		curl -fsSL https://github.com/google/googletest/archive/refs/tags/v1.14.0.tar.gz | \
		tar -xz -C $(GTEST_DIR) --strip-components=2 googletest-1.14.0/googletest \
		|| { echo "Erro ao baixar GoogleTest"; rm -rf $(GTEST_DIR); exit 1; }; \
	fi

$(GTEST_OBJ): $(GTEST_ALL_CC)
	$(CXX) $(CXXFLAGS) -isystem $(GTEST_INCLUDE) -I$(GTEST_DIR) -c $(GTEST_ALL_CC) -o $(GTEST_OBJ)

$(GTEST_MAIN_OBJ): $(GTEST_ALL_CC)
	$(CXX) $(CXXFLAGS) -isystem $(GTEST_INCLUDE) -I$(GTEST_DIR) -c $(GTEST_MAIN_CC) -o $(GTEST_MAIN_OBJ)

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

test: deps $(GTEST_OBJ) $(GTEST_MAIN_OBJ)
	@if [ -n "$(TEST_SRCS)" ]; then \
		$(CXX) $(CXXFLAGS) -isystem $(GTEST_INCLUDE) $(TEST_SRCS) $(filter-out src/main.cpp, $(wildcard src/*.cpp)) $(GTEST_OBJ) $(GTEST_MAIN_OBJ) -o $(TEST_TARGET) && ./$(TEST_TARGET); \
	else \
		echo "Nenhum teste encontrado em tests/. Criando scaffold de testes..."; \
	fi

test-integration: build
	python3 -m pytest tests_integration/ -v

test-smoke: build
	./tests_integration/smoke_test.sh

test-coverage: deps $(GTEST_OBJ) $(GTEST_MAIN_OBJ)
	@rm -f *.gcda *.gcno src/*.gcda src/*.gcno tests/*.gcda tests/*.gcno
	$(CXX) $(CXXFLAGS) --coverage -isystem $(GTEST_INCLUDE) $(TEST_SRCS) $(filter-out src/main.cpp, $(wildcard src/*.cpp)) $(GTEST_OBJ) $(GTEST_MAIN_OBJ) -o $(TEST_TARGET)
	./$(TEST_TARGET)
	@echo "\n=== Relatório de Cobertura C++ (gcov / gcovr) ==="
	gcovr --root . --filter src/ --print-summary

test-fuzz:
	@which st >/dev/null 2>&1 || { echo "Schemathesis não encontrado no PATH. Execute: python3 -m pip install --user schemathesis"; exit 1; }
	st run openapi.yaml --url http://127.0.0.1:8108 --mode all --checks not_a_server_error

test-all: test test-integration test-fuzz

run: build
	./$(TARGET)

clean:
	rm -f $(TARGET) $(TEST_TARGET) *.o src/*.o $(GTEST_DIR)/*.o
