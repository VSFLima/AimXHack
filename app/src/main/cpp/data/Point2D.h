#pragma once

#include <cmath>

struct Point2D {
    double x, y;

    constexpr Point2D() : x(0), y(0) {}
    constexpr Point2D(double x, double y) : x(x), y(y) {}

    Point2D operator-(const Point2D& o) const { return {x - o.x, y - o.y}; }
    Point2D operator+(const Point2D& o) const { return {x + o.x, y + o.y}; }
    Point2D operator*(double s) const { return {x * s, y * s}; }

    bool operator!=(const Point2D& o) const { return x != o.x || y != o.y; }

    double square() const { return x * x + y * y; }
    double length() const { return sqrt(square()); }
    double dot(const Point2D& o) const { return x * o.x + y * o.y; }

    void nullify() { x = y = 0; }
    bool isZero() const { return x == 0.0 && y == 0.0; }
    bool isNotZero() const { return !isZero(); }
};
