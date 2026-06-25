#include "MemoryManager.h"
#include "Offsets.h"
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <unistd.h>
#include <dlfcn.h>
#include <android/log.h>

#define LOG_TAG "AimXHack"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

ADDRESS MemoryManager::gameModuleBase = 0;
ADDRESS MemoryManager::sharedGameManager = 0;
ADDRESS MemoryManager::sharedMenuManager = 0;
ADDRESS MemoryManager::sharedUserSettings = 0;

static ADDRESS localGameManager = 0;
static ADDRESS localMenuManager = 0;

static ADDRESS ballsListBase = 0;
static int ballsCount = 0;

// IL2CPP function pointers
typedef void* (*il2cpp_domain_get_t)();
typedef void* (*il2cpp_assembly_get_image_t)(void* assembly);
typedef void* (*il2cpp_class_from_name_t)(void* image, const char* ns, const char* name);
typedef void* (*il2cpp_class_get_field_from_name_t)(void* klass, const char* name);
typedef void (*il2cpp_field_static_get_value_t)(void* field, void* value);
typedef void* (*il2cpp_class_get_method_from_name_t)(void* klass, const char* name, int param_count);

static il2cpp_domain_get_t p_il2cpp_domain_get = nullptr;
static il2cpp_assembly_get_image_t p_il2cpp_assembly_get_image = nullptr;
static il2cpp_class_from_name_t p_il2cpp_class_from_name = nullptr;
static il2cpp_class_get_field_from_name_t p_il2cpp_class_get_field_from_name = nullptr;
static il2cpp_field_static_get_value_t p_il2cpp_field_static_get_value = nullptr;

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

static bool initIL2CPP() {
    void* handle = dlopen("libil2cpp.so", RTLD_NOLOAD);
    if (!handle) {
        handle = dlopen("libil2cpp.so", RTLD_LAZY);
    }
    if (!handle) {
        LOGE("Failed to load libil2cpp.so: %s", dlerror());
        return false;
    }

    p_il2cpp_domain_get = (il2cpp_domain_get_t)dlsym(handle, "il2cpp_domain_get");
    p_il2cpp_assembly_get_image = (il2cpp_assembly_get_image_t)dlsym(handle, "il2cpp_assembly_get_image");
    p_il2cpp_class_from_name = (il2cpp_class_from_name_t)dlsym(handle, "il2cpp_class_from_name");
    p_il2cpp_class_get_field_from_name = (il2cpp_class_get_field_from_name_t)dlsym(handle, "il2cpp_class_get_field_from_name");
    p_il2cpp_field_static_get_value = (il2cpp_field_static_get_value_t)dlsym(handle, "il2cpp_field_static_get_value");

    if (!p_il2cpp_domain_get || !p_il2cpp_class_from_name || !p_il2cpp_field_static_get_value) {
        LOGE("Failed to find IL2CPP functions");
        return false;
    }

    LOGD("IL2CPP functions loaded successfully");
    return true;
}

static void* getClass(const char* ns, const char* name) {
    if (!p_il2cpp_domain_get || !p_il2cpp_class_from_name) return nullptr;
    void* domain = p_il2cpp_domain_get();
    if (!domain) return nullptr;
    // Try to get class from all assemblies
    // For now, use nullptr image which searches all
    return p_il2cpp_class_from_name(nullptr, ns, name);
}

static ADDRESS getStaticFieldValue(void* klass, const char* fieldName) {
    if (!klass || !p_il2cpp_class_get_field_from_name || !p_il2cpp_field_static_get_value) return 0;
    void* field = p_il2cpp_class_get_field_from_name(klass, fieldName);
    if (!field) return 0;
    ADDRESS value = 0;
    p_il2cpp_field_static_get_value(field, &value);
    return value;
}

bool MemoryManager::initialize() {
    // Try to find the game module base (for fallback offset scanning)
    gameModuleBase = findModuleBase("libgame-BPM");
    if (gameModuleBase <= 0) {
        gameModuleBase = findModuleBase("libil2cpp.so");
    }

    // Try IL2CPP approach first
    if (initIL2CPP()) {
        LOGD("Using IL2CPP API approach");

        // Try to find GameManager singleton
        void* gameManagerClass = getClass("EightBallPool", "GameManager");
        if (gameManagerClass) {
            LOGD("Found GameManager class");
            sharedGameManager = getStaticFieldValue(gameManagerClass, "Instance");
            if (sharedGameManager) {
                LOGD("GameManager instance: %p", (void*)sharedGameManager);
            }
        }

        if (!sharedGameManager) {
            // Try alternative class names
            const char* classNames[] = {
                "GameManager", "GameController", "PoolGameManager",
                "EightBallPool.GameManager", nullptr
            };
            for (int i = 0; classNames[i]; i++) {
                gameManagerClass = getClass("", classNames[i]);
                if (gameManagerClass) {
                    sharedGameManager = getStaticFieldValue(gameManagerClass, "Instance");
                    if (sharedGameManager) {
                        LOGD("Found via class: %s, instance: %p", classNames[i], (void*)sharedGameManager);
                        break;
                    }
                }
            }
        }
    }

    // Fallback: direct memory scanning
    if (!sharedGameManager && gameModuleBase > 0) {
        LOGD("IL2CPP failed, trying direct offset scan at base: %p", (void*)gameModuleBase);
        // Try known offsets for different versions
        ADDRESS offsets[] = {
            0x34E2238,  // v5.8.0
            0x35685D0,  // alternative
            0
        };
        for (int i = 0; offsets[i]; i++) {
            ADDRESS candidate = read<ADDRESS>(gameModuleBase + offsets[i]);
            if (candidate > 0x1000 && candidate < 0xFFFFFFFF) {
                sharedGameManager = candidate;
                LOGD("Found SharedGameManager at offset 0x%X: %p", offsets[i], (void*)candidate);
                break;
            }
        }
    }

    if (!sharedGameManager) {
        LOGE("Failed to find SharedGameManager");
        return false;
    }

    localGameManager = sharedGameManager;
    LOGD("MemoryManager initialized successfully");
    return true;
}

// ======== GameManager ========

void GameManager::initialize(ADDRESS base) {
    localGameManager = base;
}

int GameManager::getState() {
    if (!localGameManager) return -1;
    // Try common offsets for state
    ADDRESS stateMgr = read<ADDRESS>(localGameManager + 0x300);
    if (!stateMgr) stateMgr = read<ADDRESS>(localGameManager + 0x2A0);
    if (!stateMgr) return -1;

    ADDRESS buffer = read<ADDRESS>(stateMgr + 0x04);
    if (!buffer) return -1;
    int count = read<int>(buffer + 0x04);
    if (count <= 0 || count > 100) return -1;
    ADDRESS entry = read<ADDRESS>(buffer + 0x0C);
    if (!entry) return -1;
    ADDRESS lastObj = read<ADDRESS>(entry + (count - 1) * 4);
    if (!lastObj) return -1;
    return read<int>(lastObj + 0x0C);
}

int GameManager::getPlayerClassification(int index) {
    if (!localGameManager) return -8;
    return read<int>(localGameManager + 0x5C + index * 4);
}

bool GameManager::isPlayerTurn() {
    return getState() == 4;
}

int GameManager::getGameMode() {
    if (!localGameManager) return -1;
    return read<int>(localGameManager + 0x370);
}

ADDRESS GameManager::getTable() {
    if (!localGameManager) return 0;
    return read<ADDRESS>(localGameManager + 0x2AC);
}

// ======== VisualCue ========

static ADDRESS localVisualCue = 0;

void VisualCue::initialize(ADDRESS base) {
    localVisualCue = read<ADDRESS>(base + 0x2D8);
}

double VisualCue::getShotAngle() {
    if (!localVisualCue) return 0;
    ADDRESS visualGuide = read<ADDRESS>(localVisualCue + 0x27C);
    if (!visualGuide) return 0;
    return read<double>(visualGuide + 0x18);
}

double VisualCue::getShotPower() {
    if (!localVisualCue) return 0;
    return read<double>(localVisualCue + 0x280);
}

void VisualCue::getShotSpin(double* spinX, double* spinY) {
    if (!localVisualCue) { *spinX = *spinY = 0; return; }
    ADDRESS spinObj = read<ADDRESS>(localVisualCue + 0x2E0);
    if (!spinObj) { *spinX = *spinY = 0; return; }
    *spinX = read<double>(spinObj + 0x298);
    *spinY = read<double>(spinObj + 0x2A0);
}

// ======== MenuManager ========

void MenuManager::initialize(ADDRESS base) {
    localMenuManager = base;
}

bool MenuManager::isInGame() {
    if (!localMenuManager) return false;
    ADDRESS stateMgr = read<ADDRESS>(localMenuManager + 0x284);
    if (!stateMgr) return false;
    ADDRESS buffer = read<ADDRESS>(stateMgr + 0x04);
    if (!buffer) return false;
    int count = read<int>(buffer + 0x04);
    if (count <= 0 || count > 100) return false;
    ADDRESS entry = read<ADDRESS>(buffer + 0x0C);
    if (!entry) return false;
    ADDRESS lastObj = read<ADDRESS>(entry + (count - 1) * 4);
    if (!lastObj) return false;
    int state = read<int>(lastObj + 0x0C);
    return state == 4 || state == 7;
}

// ======== Balls ========

void Balls::initializeBallsList() {
    ADDRESS table = GameManager::getTable();
    if (!table) { ballsCount = 0; return; }
    ADDRESS balls = read<ADDRESS>(table + 0x2F0);
    if (!balls) { ballsCount = 0; return; }
    ballsCount = read<int>(balls + 0x04);
    if (ballsCount < 0 || ballsCount > 20) { ballsCount = 0; return; }
    ballsListBase = read<ADDRESS>(balls + 0x0C);
}

int Balls::getBallsCount() { return ballsCount; }

ADDRESS Balls::getBall(int index) {
    if (index < 0 || index >= ballsCount || !ballsListBase) return 0;
    return read<ADDRESS>(ballsListBase + index * 4);
}

double Balls::getBallPositionX(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return 0;
    return read<double>(ball + 0x28);
}

double Balls::getBallPositionY(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return 0;
    return read<double>(ball + 0x30);  // Y is at +8 from X (double = 8 bytes)
}

int Balls::getBallClassification(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return -8;
    return read<int>(ball + 0x78);
}

int Balls::getBallState(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return -8;
    return read<int>(ball + 0x7C);
}

bool Balls::isBallOnTable(int state) {
    return state == 1 || state == 2;
}
