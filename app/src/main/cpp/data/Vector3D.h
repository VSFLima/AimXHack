#pragma once

struct Vector3D {
    double x, y, z;

    constexpr Vector3D() : x(0), y(0), z(0) {}

    void nullify() { x = y = z = 0; }
    bool isZero() const { return x == 0.0 && y == 0.0 && z == 0.0; }
    bool isNotZero() const { return !isZero(); }
};
