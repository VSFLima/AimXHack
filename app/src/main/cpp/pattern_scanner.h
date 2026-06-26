#pragma once

#include <cstdint>
#include <vector>
#include <string>
#include <android/log.h>

#define TAG "AimXHack"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

using ADDRESS = uintptr_t;

struct PatternByte {
    uint8_t value;
    bool isWildcard;
};

class PatternScanner {
public:
    // Busca por padrão de bytes na memória do módulo
    static ADDRESS findPattern(ADDRESS base, size_t size, const std::vector<PatternByte>& pattern) {
        const uint8_t* memory = reinterpret_cast<const uint8_t*>(base);
        
        for (size_t i = 0; i < size - pattern.size(); i++) {
            bool found = true;
            for (size_t j = 0; j < pattern.size(); j++) {
                if (!pattern[j].isWildcard && memory[i + j] != pattern[j].value) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return base + i;
            }
        }
        return 0;
    }

    // Converte string de padrão para vetor de bytes
    // Formato: "48 8B 05 ? ? ? ? 48 89" onde ? = wildcard
    static std::vector<PatternByte> parsePattern(const std::string& patternStr) {
        std::vector<PatternByte> pattern;
        std::string token;
        
        for (char c : patternStr) {
            if (c == ' ') {
                if (!token.empty()) {
                    if (token == "?") {
                        pattern.push_back({0, true});
                    } else {
                        pattern.push_back({(uint8_t)std::stoi(token, nullptr, 16), false});
                    }
                    token.clear();
                }
            } else {
                token += c;
            }
        }
        if (!token.empty()) {
            if (token == "?") {
                pattern.push_back({0, true});
            } else {
                pattern.push_back({(uint8_t)std::stoi(token, nullptr, 16), false});
            }
        }
        return pattern;
    }

    // Busca por string na memória
    static ADDRESS findString(ADDRESS base, size_t size, const std::string& str) {
        const char* memory = reinterpret_cast<const char*>(base);
        for (size_t i = 0; i < size - str.size(); i++) {
            if (memcmp(memory + i, str.c_str(), str.size()) == 0) {
                return base + i;
            }
        }
        return 0;
    }

    // Busca por referência a endereço (para encontrar ponteiros)
    static ADDRESS findReference(ADDRESS base, size_t size, ADDRESS target) {
        const uint8_t* memory = reinterpret_cast<const uint8_t*>(base);
        for (size_t i = 0; i < size - 4; i++) {
            uint32_t ptr = *reinterpret_cast<const uint32_t*>(memory + i);
            if (ptr == (uint32_t)target) {
                return base + i;
            }
        }
        return 0;
    }
};
