#include <assert.h>
#include <stdbool.h>
#include <stddef.h>

// Modelo formal C do algoritmo de desacentuacao UTF-8 do Normalizer.cpp
// Verifica ausencia de buffer overflow e ponteiros fora dos limites

char toLowerAndStripAccents_C(const char* input, size_t len, char* output, size_t out_cap, size_t* out_len) {
    size_t in_idx = 0;
    size_t out_idx = 0;

    while (in_idx < len) {
        unsigned char c = (unsigned char)input[in_idx];

        if (c == 0xC3 && in_idx + 1 < len) {
            unsigned char next = (unsigned char)input[in_idx + 1];
            char mapped = '\0';

            if ((next >= 0xA0 && next <= 0xA5) || (next >= 0x80 && next <= 0x85)) mapped = 'a';
            else if ((next >= 0xA8 && next <= 0xAB) || (next >= 0x88 && next <= 0x8B)) mapped = 'e';
            else if ((next >= 0xAC && next <= 0xAF) || (next >= 0x8C && next <= 0x8F)) mapped = 'i';
            else if ((next >= 0xB2 && next <= 0xB6) || (next >= 0x92 && next <= 0x96)) mapped = 'o';
            else if ((next >= 0xB9 && next <= 0xBC) || (next >= 0x99 && next <= 0x9C)) mapped = 'u';
            else if (next == 0xA7 || next == 0x87) mapped = 'c';
            else if (next == 0xB1 || next == 0x91) mapped = 'n';

            if (mapped != '\0') {
                assert(out_idx < out_cap);
                output[out_idx++] = mapped;
                in_idx += 2;
                continue;
            }
        }

        if (c >= 'A' && c <= 'Z') {
            assert(out_idx < out_cap);
            output[out_idx++] = (char)(c + 32);
        } else {
            assert(out_idx < out_cap);
            output[out_idx++] = (char)c;
        }
        in_idx++;
    }

    *out_len = out_idx;
    return 0;
}

int main() {
    #define MAX_LEN 5
    char input[MAX_LEN];
    char output[MAX_LEN];
    size_t out_len = 0;

    // Entrada simbólica com caracteres arbitrários
    toLowerAndStripAccents_C(input, MAX_LEN, output, MAX_LEN, &out_len);

    // Invariante formal: o tamanho da saída nunca pode exceder o tamanho da entrada
    assert(out_len <= MAX_LEN);

    return 0;
}
