#pragma once

namespace Offsets {
    using OFFSET = unsigned int;

    namespace Ball {
        constexpr OFFSET Position = 0x28;
        constexpr OFFSET Spin = 0x10;
        constexpr OFFSET Velocity = 0x38;
        constexpr OFFSET Radius = 0x50;
        constexpr OFFSET Classification = 0x78;
        constexpr OFFSET State = 0x7C;
    }

    namespace Balls {
        constexpr OFFSET Count = 0x04;
        constexpr OFFSET Entry = 0x0C;
        constexpr OFFSET Balls = 0x2F0;
    }

    namespace VisualCue {
        constexpr OFFSET VisualCue = 0x2D8;
        constexpr OFFSET VisualGuide = 0x27C;
        constexpr OFFSET AimAngle = 0x18;
        constexpr OFFSET Power = 0x280;
        constexpr OFFSET SpinObject = 0x2E0;
        constexpr OFFSET Spin = 0x298;
    }

    namespace CueProperties {
        constexpr OFFSET CuePower = 0x35685D0;
        constexpr OFFSET CueSpin = 0x35685D8;
        constexpr OFFSET CueAccuracy = 0x35685E0;
    }

    namespace SharedManager {
        constexpr OFFSET SharedGameManager = 0x34E2238;
        constexpr OFFSET SharedMenuManager = 0x350AA84;
        constexpr OFFSET SharedUserSettings = 0x35AFD50;
    }

    namespace GameManager {
        constexpr OFFSET StateManager = 0x300;
        constexpr OFFSET Rules = 0x2A8;
        constexpr OFFSET GameMode = 0x370;
        constexpr OFFSET Table = 0x2AC;
        constexpr OFFSET PlayerClassification = 0x5C;
    }

    namespace MenuManager {
        constexpr OFFSET MenuStateManager = 0x284;
    }
}
