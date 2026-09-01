#include <iostream>
#include <string>
#include <sstream>
#include "json.hpp"

using json = nlohmann::json;

int main(int argc, char* argv[]) {
    std::string inputJsonStr;

    if (argc > 1) {
        inputJsonStr = argv[1];
    } else {
        std::stringstream buffer;
        buffer << std::cin.rdbuf();
        inputJsonStr = buffer.str();
    }

    if (inputJsonStr.empty()) {
        json errorResp = {
            {"type", "https://hotel.local/errors/invalid-query"},
            {"title", "Consulta de Busca Invalida"},
            {"status", 400},
            {"detail", "Nenhum payload JSON fornecido via argumento ou stdin."},
            {"instance", "cli://search_service"}
        };
        std::cout << errorResp.dump(2) << std::endl;
        return 1;
    }

    try {
        json inputJson = json::parse(inputJsonStr);
        std::string query = inputJson.value("query", "");

        json response;
        response["status"] = "OK";
        response["received_query"] = query;
        response["message"] = "Motor CLI inicializado com sucesso.";

        std::cout << response.dump(2) << std::endl;
        return 0;
    } catch (const std::exception& e) {
        json errorResp = {
            {"type", "https://hotel.local/errors/invalid-json"},
            {"title", "JSON Malformado"},
            {"status", 400},
            {"detail", e.what()},
            {"instance", "cli://search_service"}
        };
        std::cout << errorResp.dump(2) << std::endl;
        return 1;
    }
}
