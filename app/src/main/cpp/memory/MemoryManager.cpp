#include "MemoryManager.h"
#include "Offsets.h"
#include "game_detector.h"
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <dlfcn.h>
#include <android/log.h>

#define TAG "AimXHack"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define RD(type, addr) MemoryManager::read<type>(addr)

ADDRESS MemoryManager::gameModuleBase = 0;
ADDRESS MemoryManager::sharedGameManager = 0;
ADDRESS MemoryManager::sharedMenuManager = 0;
ADDRESS MemoryManager::sharedUserSettings = 0;

static ADDRESS localGameManager = 0;
static ADDRESS localMenuManager = 0;

static ADDRESS ballsListBase = 0;
static int ballsCount = 0;

// Endereços detectados automaticamente
static GameAddresses gameAddresses;

ADDRESS MemoryManager::findModuleBase(const char* moduleName) {
    FILE* maps = fopen("/proc/self/maps", "rt");
    if (maps == nullptr) return 0;
    char line[512] = {0};
    ADDRESS address = 0;
    while (fgets(line, sizeof(line), maps)) {
        if (strstr(line, moduleName)) {
            address = (ADDRESS) strtoul(line, nullptr, 16);
            break;
        }
    }
    fclose(maps);
    return address;
}

bool MemoryManager::initialize() {
    LOGD("Initializing MemoryManager with pattern scanning...");
    
    // Usar GameDetector para encontrar estruturas automaticamente
    if (GameDetector::detect(gameAddresses)) {
        gameModuleBase = gameAddresses.gameModuleBase;
        sharedGameManager = gameAddresses.sharedGameManager;
        sharedMenuManager = gameAddresses.sharedMenuManager;
        sharedUserSettings = gameAddresses.sharedUserSettings;
        
        LOGD("Auto-detection successful!");
        LOGD("GameModule: %p", (void*)gameModuleBase);
        LOGD("GameManager: %p (offset: 0x%X)", (void*)sharedGameManager, gameAddresses.gameManagerOffset);
        LOGD("MenuManager: %p (offset: 0x%X)", (void*)sharedMenuManager, gameAddresses.menuManagerOffset);
        LOGD("UserSettings: %p (offset: 0x%X)", (void*)sharedUserSettings, gameAddresses.userSettingsOffset);
    } else {
        LOGE("Auto-detection failed, trying fallback...");
        
        // Fallback: tentar offsets conhecidos
        gameModuleBase = findModuleBase("libgame-BPM");
        if (gameModuleBase <= 0) gameModuleBase = findModuleBase("libil2cpp.so");
        
        if (gameModuleBase > 0) {
            ADDRESS offsets[] = {0x34E2238, 0x350AA84, 0x35AFD50, 0};
            for (int i = 0; offsets[i]; i++) {
                ADDRESS candidate = RD(ADDRESS, gameModuleBase + offsets[i]);
                if (candidate > 0x10000 && candidate < 0xFFFFFFFF) {
                    sharedGameManager = candidate;
                    break;
                }
            }
        }
    }

    if (!sharedGameManager) {
        LOGE("Failed to find SharedGameManager");
        return false;
    }

    localGameManager = sharedGameManager;
    
    // Inicializar subsistemas
    GameManager::initialize(sharedGameManager);
    if (sharedMenuManager) {
        MenuManager::initialize(sharedMenuManager);
    }
    if (gameModuleBase > 0) {
        VisualCue::initialize(sharedGameManager);
    }

    LOGD("MemoryManager initialized successfully");
    return true;
}

// ======== GameManager ========

void GameManager::initialize(ADDRESS base) { localGameManager = base; }

int GameManager::getState() {
    if (!localGameManager) return -1;
    ADDRESS stateMgr = RD(ADDRESS, localGameManager + 0x300);
    if (!stateMgr) stateMgr = RD(ADDRESS, localGameManager + 0x2A0);
    if (!stateMgr) return -1;
    ADDRESS buffer = RD(ADDRESS, stateMgr + 0x04);
    if (!buffer) return -1;
    int count = RD(int, buffer + 0x04);
    if (count <= 0 || count > 100) return -1;
    ADDRESS entry = RD(ADDRESS, buffer + 0x0C);
    if (!entry) return -1;
    ADDRESS lastObj = RD(ADDRESS, entry + (count - 1) * 4);
    if (!lastObj) return -1;
    return RD(int, lastObj + 0x0C);
}

int GameManager::getPlayerClassification(int index) {
    if (!localGameManager) return -8;
    return RD(int, localGameManager + 0x5C + index * 4);
}

bool GameManager::isPlayerTurn() { return getState() == 4; }

int GameManager::getGameMode() {
    if (!localGameManager) return -1;
    return RD(int, localGameManager + 0x370);
}

ADDRESS GameManager::getTable() {
    if (!localGameManager) return 0;
    return RD(ADDRESS, localGameManager + 0x2AC);
}

// ======== VisualCue ========

static ADDRESS localVisualCue = 0;

void VisualCue::initialize(ADDRESS base) {
    localVisualCue = RD(ADDRESS, base + 0x2D8);
    LOGD("VisualCue: %p", (void*)localVisualCue);
}

double VisualCue::getShotAngle() {
    if (!localVisualCue) return 0;
    ADDRESS vg = RD(ADDRESS, localVisualCue + 0x27C);
    if (!vg) return 0;
    return RD(double, vg + 0x18);
}

double VisualCue::getShotPower() {
    if (!localVisualCue) return 0;
    return RD(double, localVisualCue + 0x280);
}

void VisualCue::getShotSpin(double* spinX, double* spinY) {
    if (!localVisualCue) { *spinX = *spinY = 0; return; }
    ADDRESS spinObj = RD(ADDRESS, localVisualCue + 0x2E0);
    if (!spinObj) { *spinX = *spinY = 0; return; }
    *spinX = RD(double, spinObj + 0x298);
    *spinY = RD(double, spinObj + 0x2A0);
}

// ======== MenuManager ========

void MenuManager::initialize(ADDRESS base) { localMenuManager = base; }

bool MenuManager::isInGame() {
    if (!localMenuManager) return false;
    ADDRESS stateMgr = RD(ADDRESS, localMenuManager + 0x284);
    if (!stateMgr) return false;
    ADDRESS buffer = RD(ADDRESS, stateMgr + 0x04);
    if (!buffer) return false;
    int count = RD(int, buffer + 0x04);
    if (count <= 0 || count > 100) return false;
    ADDRESS entry = RD(ADDRESS, buffer + 0x0C);
    if (!entry) return false;
    ADDRESS lastObj = RD(ADDRESS, entry + (count - 1) * 4);
    if (!lastObj) return false;
    int state = RD(int, lastObj + 0x0C);
    return state == 4 || state == 7;
}

// ======== Balls ========

void Balls::initializeBallsList() {
    ADDRESS table = GameManager::getTable();
    if (!table) { ballsCount = 0; return; }
    ADDRESS balls = RD(ADDRESS, table + 0x2F0);
    if (!balls) { ballsCount = 0; return; }
    ballsCount = RD(int, balls + 0x04);
    if (ballsCount < 0 || ballsCount > 20) { ballsCount = 0; return; }
    ballsListBase = RD(ADDRESS, balls + 0x0C);
}

int Balls::getBallsCount() { return ballsCount; }

ADDRESS Balls::getBall(int index) {
    if (index < 0 || index >= ballsCount || !ballsListBase) return 0;
    return RD(ADDRESS, ballsListBase + index * 4);
}

double Balls::getBallPositionX(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return 0;
    return RD(double, ball + 0x28);
}

double Balls::getBallPositionY(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return 0;
    return RD(double, ball + 0x30);
}

int Balls::getBallClassification(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return -8;
    return RD(int, ball + 0x78);
}

int Balls::getBallState(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return -8;
    return RD(int, ball + 0x7C);
}

bool Balls::isBallOnTable(int state) { return state == 1 || state == 2; }
