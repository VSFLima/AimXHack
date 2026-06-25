#include "MemoryManager.h"
#include "Offsets.h"
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <unistd.h>

ADDRESS MemoryManager::gameModuleBase = 0;
ADDRESS MemoryManager::sharedGameManager = 0;
ADDRESS MemoryManager::sharedMenuManager = 0;
ADDRESS MemoryManager::sharedUserSettings = 0;

static ADDRESS localGameManager = 0;
static ADDRESS localMenuManager = 0;
static ADDRESS localUserSettings = 0;

static ADDRESS ballsListBase = 0;
static int ballsCount = 0;

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
    gameModuleBase = findModuleBase("libgame-BPM-GooglePlay-Gold-Release-Module");
    if (gameModuleBase <= 0) {
        gameModuleBase = findModuleBase("libil2cpp.so");
    }
    if (gameModuleBase <= 0) return false;

    sharedGameManager = read<ADDRESS>(gameModuleBase + Offsets::SharedManager::SharedGameManager);
    sharedMenuManager = read<ADDRESS>(gameModuleBase + Offsets::SharedManager::SharedMenuManager);
    sharedUserSettings = read<ADDRESS>(gameModuleBase + Offsets::SharedManager::SharedUserSettings);

    if (!sharedGameManager || !sharedMenuManager) return false;

    GameManager::initialize(sharedGameManager);
    VisualCue::initialize(sharedGameManager);
    MenuManager::initialize(sharedMenuManager);
    return true;
}

// ======== GameManager ========

void GameManager::initialize(ADDRESS base) {
    localGameManager = base;
}

int GameManager::getState() {
    ADDRESS stateMgr = MemoryManager::read<ADDRESS>(localGameManager + Offsets::GameManager::StateManager);
    if (!stateMgr) return -1;
    ADDRESS buffer = MemoryManager::read<ADDRESS>(stateMgr + 0x04);
    if (!buffer) return -1;
    int count = MemoryManager::read<int>(buffer + 0x04);
    if (count <= 0) return -1;
    ADDRESS entry = MemoryManager::read<ADDRESS>(buffer + 0x0C);
    ADDRESS lastObj = MemoryManager::read<ADDRESS>(entry + (count - 1) * 4);
    return MemoryManager::read<int>(lastObj + 0x0C);
}

int GameManager::getPlayerClassification(int index) {
    return MemoryManager::read<int>(localGameManager + Offsets::GameManager::PlayerClassification + index * 4);
}

bool GameManager::isPlayerTurn() {
    return getState() == 4;
}

int GameManager::getGameMode() {
    return MemoryManager::read<int>(localGameManager + Offsets::GameManager::GameMode);
}

ADDRESS GameManager::getTable() {
    return MemoryManager::read<ADDRESS>(localGameManager + Offsets::GameManager::Table);
}

// ======== VisualCue ========

static ADDRESS localVisualCue = 0;

void VisualCue::initialize(ADDRESS base) {
    localVisualCue = MemoryManager::read<ADDRESS>(base + Offsets::VisualCue::VisualCue);
}

double VisualCue::getShotAngle() {
    if (!localVisualCue) return 0;
    ADDRESS visualGuide = MemoryManager::read<ADDRESS>(localVisualCue + Offsets::VisualCue::VisualGuide);
    if (!visualGuide) return 0;
    return MemoryManager::read<double>(visualGuide + Offsets::VisualCue::AimAngle);
}

double VisualCue::getShotPower() {
    if (!localVisualCue) return 0;
    return MemoryManager::read<double>(localVisualCue + Offsets::VisualCue::Power);
}

void VisualCue::getShotSpin(double* spinX, double* spinY) {
    if (!localVisualCue) { *spinX = *spinY = 0; return; }
    ADDRESS spinObj = MemoryManager::read<ADDRESS>(localVisualCue + Offsets::VisualCue::SpinObject);
    if (!spinObj) { *spinX = *spinY = 0; return; }
    *spinX = MemoryManager::read<double>(spinObj + Offsets::VisualCue::Spin);
    *spinY = MemoryManager::read<double>(spinObj + Offsets::VisualCue::Spin + 8);
}

// ======== MenuManager ========

void MenuManager::initialize(ADDRESS base) {
    localMenuManager = base;
}

bool MenuManager::isInGame() {
    ADDRESS stateMgr = MemoryManager::read<ADDRESS>(localMenuManager + Offsets::MenuManager::MenuStateManager);
    if (!stateMgr) return false;
    ADDRESS buffer = MemoryManager::read<ADDRESS>(stateMgr + 0x04);
    if (!buffer) return false;
    int count = MemoryManager::read<int>(buffer + 0x04);
    if (count <= 0) return false;
    ADDRESS entry = MemoryManager::read<ADDRESS>(buffer + 0x0C);
    ADDRESS lastObj = MemoryManager::read<ADDRESS>(entry + (count - 1) * 4);
    int state = MemoryManager::read<int>(lastObj + 0x0C);
    return state == 4 || state == 7;
}

// ======== Balls ========

void Balls::initializeBallsList() {
    ADDRESS table = GameManager::getTable();
    if (!table) { ballsCount = 0; return; }
    ADDRESS balls = MemoryManager::read<ADDRESS>(table + Offsets::Balls::Balls);
    if (!balls) { ballsCount = 0; return; }
    ballsCount = MemoryManager::read<int>(balls + Offsets::Balls::Count);
    ballsListBase = MemoryManager::read<ADDRESS>(balls + Offsets::Balls::Entry);
}

int Balls::getBallsCount() {
    return ballsCount;
}

ADDRESS Balls::getBall(int index) {
    if (index < 0 || index >= ballsCount || !ballsListBase) return 0;
    return MemoryManager::read<ADDRESS>(ballsListBase + index * 4);
}

double Balls::getBallPositionX(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return 0;
    return MemoryManager::read<double>(ball + Offsets::Ball::Position);
}

double Balls::getBallPositionY(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return 0;
    return MemoryManager::read<double>(ball + Offsets::Ball::Position + 8);
}

int Balls::getBallClassification(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return -8;
    return MemoryManager::read<int>(ball + Offsets::Ball::Classification);
}

int Balls::getBallState(int index) {
    ADDRESS ball = getBall(index);
    if (!ball) return -8;
    return MemoryManager::read<int>(ball + Offsets::Ball::State);
}

bool Balls::isBallOnTable(int state) {
    return state == 1 || state == 2;
}
