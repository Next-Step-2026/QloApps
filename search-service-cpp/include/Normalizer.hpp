#pragma once

#include <string>
#include <vector>
#include <unordered_set>

class Normalizer {
public:
    // Converte para minusculas e remove acentos/diacriticos UTF-8
    static std::string toLowerAndStripAccents(const std::string& input);

    // Normaliza e divide em tokens, removendo pontuacao
    static std::vector<std::string> tokenize(const std::string& input, bool removeStopwords = true);

    // Verifica se o termo e uma stopword descartavel
    static bool isStopword(const std::string& token);

private:
    static const std::unordered_set<std::string> STOPWORDS;
};
