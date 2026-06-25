#pragma once

#include "../data/Point2D.h"
#include "../data/GameConstants.h"
#include <cmath>

namespace NumberUtils {

    inline double calcAngle(const Point2D& delta) {
        if (delta.x == 0.0) {
            return (delta.y >= 0.0) ? PI * 0.5 : PI * 1.5;
        }
        double angle = atan(delta.y / delta.x);
        if (delta.x < 0.0) angle += PI;
        return angle;
    }

    inline double calcAngle(const Point2D& src, const Point2D& dst) {
        return calcAngle(src - dst);
    }

    inline void normalizeAngle(double& angle) {
        if (angle >= MAX_ANGLE_RADIANS) {
            angle = fmod(angle, MAX_ANGLE_RADIANS);
        } else if (angle < 0.0) {
            angle = MAX_ANGLE_RADIANS - fmod(-angle, MAX_ANGLE_RADIANS);
        }
    }
}
