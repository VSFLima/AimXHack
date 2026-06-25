#pragma once

#include <cstdint>

using ADDRESS = uintptr_t;

class MemoryManager {
public:
    static ADDRESS gameModuleBase;
    static ADDRESS sharedGameManager;
    static ADDRESS sharedMenuManager;
    static ADDRESS sharedUserSettings;

    static ADDRESS findModuleBase(const char* moduleName);
    static bool initialize();

    template<typename T>
    static T read(ADDRESS address) {
        return *reinterpret_cast<T*>(address);
    }

    template<typename T>
    static void read(ADDRESS address, T* buffer, size_t size) {
        memcpy(buffer, reinterpret_cast<void*>(address), size);
    }
};

namespace GameManager {
    void initialize(ADDRESS base);
    int getState();
    int getPlayerClassification(int index);
    bool isPlayerTurn();
    int getGameMode();
    ADDRESS getTable();
}

namespace VisualCue {
    void initialize(ADDRESS base);
    double getShotAngle();
    double getShotPower();
    void getShotSpin(double* spinX, double* spinY);
}

namespace MenuManager {
    void initialize(ADDRESS base);
    bool isInGame();
}

namespace Balls {
    void initializeBallsList();
    int getBallsCount();
    ADDRESS getBall(int index);
    double getBallPositionX(int index);
    double getBallPositionY(int index);
    int getBallClassification(int index);
    int getBallState(int index);
    bool isBallOnTable(int state);
}
