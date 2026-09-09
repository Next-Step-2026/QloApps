#pragma once

#include <string>
#include <algorithm>
#include <cctype>

namespace assistant {

inline std::string normalizeText(const std::string& input) {
    std::string result;
    result.reserve(input.size());

    for (size_t i = 0; i < input.size(); ++i) {
        unsigned char c = static_cast<unsigned char>(input[i]);

        // UTF-8 multi-byte character handling (common Portuguese diacritics)
        if (c == 0xC3 && i + 1 < input.size()) {
            unsigned char next = static_cast<unsigned char>(input[i + 1]);
            switch (next) {
                // A: á, à, ã, â, Á, À, Ã, Â
                case 0xA1: case 0xA0: case 0xA3: case 0xA2:
                case 0x81: case 0x80: case 0x83: case 0x82:
                    result += 'a';
                    i++;
                    break;
                // E: é, ê, É, Ê
                case 0xA9: case 0xAA:
                case 0x89: case 0x8A:
                    result += 'e';
                    i++;
                    break;
                // I: í, Í
                case 0xAD: case 0x8D:
                    result += 'i';
                    i++;
                    break;
                // O: ó, õ, ô, Ó, Õ, Ô
                case 0xB3: case 0xB5: case 0xB4:
                case 0x93: case 0x95: case 0x94:
                    result += 'o';
                    i++;
                    break;
                // U: ú, ü, Ú, Ü
                case 0xBA: case 0xBC:
                case 0x9A: case 0x9C:
                    result += 'u';
                    i++;
                    break;
                // C: ç, Ç
                case 0xA7: case 0x87:
                    result += 'c';
                    i++;
                    break;
                default:
                    // Unknown C3 byte sequence, retain or skip
                    result += static_cast<char>(std::tolower(next));
                    i++;
                    break;
            }
        } else {
            result += static_cast<char>(std::tolower(c));
        }
    }

    return result;
}

} // namespace assistant
