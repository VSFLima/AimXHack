#pragma once

#include "../data/GameConstants.h"
#include "../data/Point2D.h"
#include "../data/Vector3D.h"
#include <vector>

class Prediction {
public:
    static bool pocketStatus[TABLE_POCKETS_COUNT];
    static float shotResult[MAX_SHOT_RESULT_SIZE];

    Prediction() = default;

    float* getShotResult();
    int getShotResultSize() const { return shotResultSize; }
    bool determineShotResult();

    class Ball {
    public:
        int index;
        BallClassification classification;
        BallState state;
        bool originalOnTable;
        bool onTable;

        Point2D velocity;
        Vector3D spin;
        Point2D initialPosition;
        Point2D predictedPosition;
        std::vector<Point2D> positions;

        void findNextCollision(void* pData, double* time);
        void calcVelocity();
        void calcVelocityPostCollision(const double& angle);
        void move(const double& time);
        bool isMovingOrSpinning() const;

        Ball() : index(0), classification(ERR_CLASSIFICATION),
                 state(ERR_STATE), originalOnTable(false), onTable(false) {}

    private:
        bool isBallBallCollision(double* smallestTime, Ball& otherBall) const;
        bool willCollideWithTable(const double* smallestTime) const;
        void determineBallTableCollision(void* pData, double* smallestTime);
        bool isBallLineCollision(double* pTime, const Point2D& a, const Point2D& b) const;
        bool isBallPointCollision(double* smallestTime, const Point2D& point) const;
    };

    class Collision {
    public:
        enum Type : int { BALL, LINE, POINT };

        bool valid;
        Type type;
        double angle;
        Point2D point;
        Ball* ballA;
        Ball* ballB;
        Ball* firstHitBall;

        Collision() : valid(false), type(POINT), angle(0), ballA(nullptr),
                      ballB(nullptr), firstHitBall(nullptr) {}
    };

    class SceneData {
    public:
        int ballsCount;
        Ball balls[MAX_BALLS_COUNT];
        Collision collision;
        bool shotState;

        SceneData() : ballsCount(0), shotState(false) {}
    } guiData;

private:
    int shotResultSize = 0;

    void calculateShotResultSize();
    void initBalls();
    void initCueBall(double angle, double power, double spinX, double spinY);
    void determineBallsPositions();
    void handleCollision();
    void handleBallBallCollision() const;
    void determineShotState();
};

extern Prediction* gPrediction;
