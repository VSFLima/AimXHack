#pragma once

#include "pattern_scanner.h"
#include <android/log.h>

// Estruturas encontradas automaticamente
struct GameAddresses {
    ADDRESS gameModuleBase = 0;
    ADDRESS sharedGameManager = 0;
    ADDRESS sharedMenuManager = 0;
    ADDRESS sharedUserSettings = 0;
    
    // Offsets relativos encontrados por pattern scanning
    OFFSET gameManagerOffset = 0;
    OFFSET menuManagerOffset = 0;
    OFFSET userSettingsOffset = 0;
    
    // Ball offsets (encontrados dinamicamente)
    OFFSET ballPositionOffset = 0x28;
    OFFSET ballClassificationOffset = 0x78;
    OFFSET ballStateOffset = 0x7C;
    
    // VisualCue offsets
    OFFSET visualCueOffset = 0x2D8;
    OFFSET aimAngleOffset = 0x18;
    OFFSET powerOffset = 0x280;
    OFFSET spinOffset = 0x298;
    
    // CueProperties offsets
    OFFSET cuePowerOffset = 0x35685D0;
    OFFSET cueSpinOffset = 0x35685D8;
    
    bool isValid() const {
        return gameModuleBase > 0 && sharedGameManager > 0;
    }
};

class GameDetector {
public:
    // Encontra a base do módulo do jogo
    static ADDRESS findGameModule() {
        FILE* maps = fopen("/proc/self/maps", "rt");
        if (!maps) return 0;
        
        char line[512];
        ADDRESS base = 0;
        
        while (fgets(line, sizeof(line), maps)) {
            // Procura por libgame ou libil2cpp
            if (strstr(line, "libgame-BPM") || strstr(line, "libil2cpp.so")) {
                base = (ADDRESS)strtoul(line, nullptr, 16);
                break;
            }
        }
        fclose(maps);
        return base;
    }

    // Encontra o tamanho do módulo
    static size_t getModuleSize(ADDRESS base) {
        FILE* maps = fopen("/proc/self/maps", "rt");
        if (!maps) return 0;
        
        char line[512];
        size_t size = 0;
        
        while (fgets(line, sizeof(line), maps)) {
            ADDRESS start = strtoul(line, nullptr, 16);
            if (start == base) {
                // Encontrou o início, procurar o fim
                char* dash = strchr(line, '-');
                if (dash) {
                    ADDRESS end = strtoul(dash + 1, nullptr, 16);
                    size = end - base;
                }
                break;
            }
        }
        fclose(maps);
        return size;
    }

    // Detecta estruturas do jogo por pattern scanning
    static bool detect(GameAddresses& addresses) {
        // 1. Encontrar módulo do jogo
        addresses.gameModuleBase = findGameModule();
        if (!addresses.gameModuleBase) {
            LOGD("Game module not found");
            return false;
        }
        
        size_t moduleSize = getModuleSize(addresses.gameModuleBase);
        if (moduleSize == 0) {
            LOGD("Module size unknown, using default");
            moduleSize = 0x10000000; // 256MB fallback
        }
        
        LOGD("Game module at %p, size: %zu", (void*)addresses.gameModuleBase, moduleSize);
        
        // 2. Procurar SharedGameManager
        // Padrão: ponteiro para GameManager geralmente está logo após o início do módulo
        // Tentar offsets conhecidos primeiro
        OFFSET knownOffsets[] = {0x34E2238, 0x350AA84, 0x35AFD50, 0x35AA79C};
        for (OFFSET offset : knownOffsets) {
            ADDRESS ptr = MemoryManager::read<ADDRESS>(addresses.gameModuleBase + offset);
            if (ptr > 0x10000 && ptr < 0xFFFFFFFF) {
                // Verificar se é um ponteiro válido lendo um valor
                ADDRESS test = MemoryManager::read<ADDRESS>(ptr);
                if (test > 0x10000 && test < 0xFFFFFFFF) {
                    addresses.sharedGameManager = ptr;
                    addresses.gameManagerOffset = offset;
                    LOGD("Found SharedGameManager at offset 0x%X: %p", offset, (void*)ptr);
                    break;
                }
            }
        }
        
        // 3. Se não encontrou, usar pattern scanning
        if (!addresses.sharedGameManager) {
            LOGD("Known offsets failed, trying pattern scan...");
            addresses.sharedGameManager = scanForGameManager(addresses.gameModuleBase, moduleSize);
        }
        
        // 4. Encontrar MenuManager (geralmente próximo ao GameManager)
        if (addresses.sharedGameManager) {
            // Tentar offsets conhecidos
            OFFSET menuOffsets[] = {0x350AA84, 0x34E2238 + 0x1000};
            for (OFFSET offset : menuOffsets) {
                ADDRESS ptr = MemoryManager::read<ADDRESS>(addresses.gameModuleBase + offset);
                if (ptr > 0x10000 && ptr < 0xFFFFFFFF) {
                    addresses.sharedMenuManager = ptr;
                    addresses.menuManagerOffset = offset;
                    LOGD("Found MenuManager at offset 0x%X: %p", offset, (void*)ptr);
                    break;
                }
            }
        }
        
        // 5. Encontrar UserSettings
        if (addresses.sharedGameManager) {
            OFFSET settingsOffsets[] = {0x35AFD50, 0x34E2238 + 0x2000};
            for (OFFSET offset : settingsOffsets) {
                ADDRESS ptr = MemoryManager::read<ADDRESS>(addresses.gameModuleBase + offset);
                if (ptr > 0x10000 && ptr < 0xFFFFFFFF) {
                    addresses.sharedUserSettings = ptr;
                    addresses.userSettingsOffset = offset;
                    LOGD("Found UserSettings at offset 0x%X: %p", offset, (void*)ptr);
                    break;
                }
            }
        }
        
        // 6. Verificar se encontrou o suficiente
        if (!addresses.sharedGameManager) {
            LOGD("Failed to find SharedGameManager");
            return false;
        }
        
        LOGD("Detection complete: GM=%p MM=%p US=%p", 
             (void*)addresses.sharedGameManager,
             (void*)addresses.sharedMenuManager,
             (void*)addresses.sharedUserSettings);
        
        return true;
    }

private:
    // Pattern scanning para encontrar GameManager
    static ADDRESS scanForGameManager(ADDRESS base, size_t size) {
        // Padrão comum: sequência de ponteiros válidos
        // GameManager geralmente contém muitos ponteiros para outras estruturas
        
        const uint8_t* memory = reinterpret_cast<const uint8_t*>(base);
        
        for (size_t i = 0; i < size - 100; i += 4) {
            // Verificar se parece um ponteiro válido
            ADDRESS candidate = *reinterpret_cast<const ADDRESS*>(memory + i);
            
            if (candidate > 0x10000 && candidate < 0xFFFFFFFF) {
                // Verificar se o ponteiro aponta para memória válida
                ADDRESS deref = MemoryManager::read<ADDRESS>(candidate);
                if (deref > 0x10000 && deref < 0xFFFFFFFF) {
                    // Verificar se há mais ponteiros válidos nas proximidades
                    int validPointers = 0;
                    for (int j = 0; j < 20; j++) {
                        ADDRESS ptr = MemoryManager::read<ADDRESS>(candidate + j * 4);
                        if (ptr > 0x10000 && ptr < 0xFFFFFFFF) {
                            validPointers++;
                        }
                    }
                    
                    // Se encontrou muitos ponteiros válidos, pode ser GameManager
                    if (validPointers >= 10) {
                        LOGD("Potential GameManager at offset 0x%zX: %p", i, (void*)candidate);
                        return candidate;
                    }
                }
            }
        }
        
        return 0;
    }
};
