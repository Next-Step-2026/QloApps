#include "Normalizer.hpp"

#include <algorithm>
#include <cctype>
#include <sstream>

const std::unordered_set<std::string> Normalizer::STOPWORDS = {
    "de",  "do",  "da",   "dos", "das",  "em",   "no",    "na",    "nos",
    "nas", "com", "para", "por", "pelo", "pela", "pelos", "pelas", "um",
    "uma", "uns", "umas", "o",   "a",    "os",   "as",    "e",     "ou",
    "que", "se",  "ao",   "aos", "nao",  "não",  "the",   "in",    "on",
    "at",  "to",  "for",  "of",  "and",  "or",   "with",  "an"};

std::string Normalizer::toLowerAndStripAccents(const std::string& input) {
  std::string result;
  result.reserve(input.size());

  for (size_t i = 0; i < input.size(); ++i) {
    unsigned char c = static_cast<unsigned char>(input[i]);

    // Verifica sequencia UTF-8 de 2 bytes (0xC3 ...)
    if (c == 0xC3 && i + 1 < input.size()) {
      unsigned char next = static_cast<unsigned char>(input[i + 1]);
      ++i;  // consome o segundo byte
      switch (next) {
        // A / a
        case 0x80:
        case 0x81:
        case 0x82:
        case 0x83:
        case 0x84:
        case 0x85:  // À Á Â Ã Ä Å
        case 0xA0:
        case 0xA1:
        case 0xA2:
        case 0xA3:
        case 0xA4:
        case 0xA5:  // à á â ã ä å
          result += 'a';
          break;
        // E / e
        case 0x88:
        case 0x89:
        case 0x8A:
        case 0x8B:  // È É Ê Ë
        case 0xA8:
        case 0xA9:
        case 0xAA:
        case 0xAB:  // è é ê ë
          result += 'e';
          break;
        // I / i
        case 0x8C:
        case 0x8D:
        case 0x8E:
        case 0x8F:  // Ì Í Î Ï
        case 0xAC:
        case 0xAD:
        case 0xAE:
        case 0xAF:  // ì í î ï
          result += 'i';
          break;
        // O / o
        case 0x92:
        case 0x93:
        case 0x94:
        case 0x95:
        case 0x96:  // Ò Ó Ô Õ Ö
        case 0xB2:
        case 0xB3:
        case 0xB4:
        case 0xB5:
        case 0xB6:  // ò ó ô õ ö
          result += 'o';
          break;
        // U / u
        case 0x99:
        case 0x9A:
        case 0x9B:
        case 0x9C:  // Ù Ú Û Ü
        case 0xB9:
        case 0xBA:
        case 0xBB:
        case 0xBC:  // ù ú û ü
          result += 'u';
          break;
        // C / c
        case 0x87:  // Ç
        case 0xA7:  // ç
          result += 'c';
          break;
        // N / n
        case 0x91:  // Ñ
        case 0xB1:  // ñ
          result += 'n';
          break;
        default:
          // Caso nao mapeado, mantem original aproximado
          result += ' ';
          break;
      }
    } else if (c < 128) {
      // Caractere ASCII simples: converte para minusculo
      result += static_cast<char>(std::tolower(c));
    } else {
      // Outros caracteres multibyte nao mapeados -> espaco
      result += ' ';
    }
  }

  return result;
}

std::vector<std::string> Normalizer::tokenize(const std::string& input,
                                              bool removeStopwords) {
  std::string normalized = toLowerAndStripAccents(input);

  // Substitui pontuacoes e caracteres especiais por espacos
  for (char& ch : normalized) {
    if (!std::isalnum(static_cast<unsigned char>(ch))) {
      ch = ' ';
    }
  }

  std::vector<std::string> tokens;
  std::istringstream iss(normalized);
  std::string token;

  while (iss >> token) {
    if (removeStopwords && isStopword(token)) {
      continue;
    }
    tokens.push_back(token);
  }

  return tokens;
}

bool Normalizer::isStopword(const std::string& token) {
  return STOPWORDS.find(token) != STOPWORDS.end();
}
