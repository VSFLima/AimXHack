#include "Prediction.h"
#include "../memory/MemoryManager.h"
#include "../utils/NumberUtils.h"
#include <cmath>
#include <cstring>

static Prediction prediction;
Prediction* gPrediction = &prediction;

bool Prediction::pocketStatus[] = {};
float Prediction::shotResult[MAX_SHOT_RESULT_SIZE];

static double prevAngle = 0, prevPower = 0, prevSpinX = 0, prevSpinY = 0;

constexpr Point2D pockets[TABLE_POCKETS_COUNT] = {
    {-130.8, -67.3}, {0.0, -72.0}, {130.8, -67.3},
    {130.8, 67.3}, {0.0, 72.0}, {-130.8, 67.3}
};

constexpr Point2D tableShape[TABLE_SHAPE_SIZE] = {
    {-127,53.5},{-136.9,64.1},{-138.2,69.2},{-136.7,73.2},{-132.7,74.7},{-127.6,73.4},
    {-117,63.5},{-7.8,63.5},{-6.1,68.6},{-5.7,72.7},{-3.7,75.4},{0,76.7},
    {3.7,75.4},{5.7,72.7},{6.1,68.6},{7.8,63.5},{117,63.5},{127.6,73.4},
    {132.7,74.7},{136.7,73.2},{138.2,69.2},{136.9,64.1},{127,53.5},{127,-53.5},
    {136.9,-64.1},{138.2,-69.2},{136.7,-73.2},{132.7,-74.7},{127.6,-73.4},{117,-63.5},
    {7.8,-63.5},{6.1,-68.6},{5.7,-72.7},{3.7,-75.4},{0,-76.7},{-3.7,-75.4},
    {-5.7,-72.7},{-6.1,-68.6},{-7.8,-63.5},{-117,-63.5},{-127.6,-73.4},{-132.7,-74.7},
    {-136.7,-73.2},{-138.2,-69.2},{-136.9,-64.1},{-127,-53.5}
};

// ======== PUBLIC ========

float* Prediction::getShotResult() {
    this->calculateShotResultSize();
    int idx = 0;
    constexpr int nBallsIdx = 1;
    shotResult[idx++] = 1.0f; // isTrajectoryEnabled
    shotResult[idx++] = 0.0f; // nOfBalls placeholder
    for (int i = 0; i < guiData.ballsCount; i++) {
        Ball& ball = guiData.balls[i];
        if (ball.initialPosition != ball.predictedPosition) {
            shotResult[nBallsIdx] += 1.0f;
            shotResult[idx++] = (float)ball.index;
            shotResult[idx++] = (float)ball.positions.size();
            for (auto& pos : ball.positions) {
                shotResult[idx++] = (float)pos.x;
                shotResult[idx++] = (float)pos.y;
            }
        }
    }
    shotResult[idx++] = 1.0f; // isShotStateEnabled
    for (int i = 0; i < TABLE_POCKETS_COUNT; i++) {
        shotResult[idx++] = (float)(pocketStatus[i] && guiData.shotState);
        shotResult[idx++] = (float)pockets[i].x;
        shotResult[idx++] = (float)pockets[i].y;
    }
    return shotResult;
}

void Prediction::calculateShotResultSize() {
    shotResultSize = 2;
    for (int i = 0; i < guiData.ballsCount; i++) {
        Ball& ball = guiData.balls[i];
        if (ball.initialPosition != ball.predictedPosition) {
            shotResultSize += (int)ball.positions.size() * 2 + 2;
        }
    }
    shotResultSize++;
    shotResultSize += TABLE_POCKETS_COUNT * 3;
}

bool Prediction::determineShotResult() {
    if (!MenuManager::isInGame() || !GameManager::isPlayerTurn()) return false;

    double angle = VisualCue::getShotAngle();
    double power = VisualCue::getShotPower();
    double spinX, spinY;
    VisualCue::getShotSpin(&spinX, &spinY);

    if (angle == prevAngle && power == prevPower && spinX == prevSpinX && spinY == prevSpinY)
        return false;

    prevAngle = angle; prevPower = power; prevSpinX = spinX; prevSpinY = spinY;

    this->initBalls();
    this->initCueBall(angle, power, spinX, spinY);
    guiData.collision.firstHitBall = nullptr;
    for (bool& ps : pocketStatus) ps = false;
    this->determineBallsPositions();
    this->determineShotState();

    for (int i = 0; i < guiData.ballsCount; i++) {
        Ball& ball = guiData.balls[i];
        if (ball.positions.back() != ball.predictedPosition)
            ball.positions.push_back(ball.predictedPosition);
    }
    return true;
}

// ======== PRIVATE ========

void Prediction::initBalls() {
    Balls::initializeBallsList();
    guiData.ballsCount = Balls::getBallsCount();
    for (int i = 0; i < guiData.ballsCount; i++) {
        Ball& ball = guiData.balls[i];
        ball.index = i;
        ball.state = (BallState)Balls::getBallState(i);
        ball.originalOnTable = Balls::isBallOnTable(ball.state);
        ball.onTable = ball.originalOnTable;
        ball.classification = (BallClassification)Balls::getBallClassification(i);
        ball.initialPosition = {Balls::getBallPositionX(i), Balls::getBallPositionY(i)};
        ball.predictedPosition = ball.initialPosition;
        ball.velocity.nullify();
        ball.spin.nullify();
        ball.positions.clear();
        ball.positions.reserve(20);
        ball.positions.push_back(ball.initialPosition);
    }
}

void Prediction::initCueBall(double angle, double power, double spinX, double spinY) {
    double s = sin(angle), c = cos(angle);
    Ball& cue = guiData.balls[0];
    cue.velocity.x = power * c;
    cue.velocity.y = power * s;
    double sf = power / BALL_RADIUS;
    double v31 = -spinY * sf;
    cue.spin.x = -(s * v31);
    cue.spin.y = c * v31;
    cue.spin.z = spinX * sf;
}

void Prediction::determineBallsPositions() {
    bool anyMoving;
    double time, time2;
    do {
        time = TIME_PER_TICK;
        do {
            time2 = time;
            guiData.collision.valid = false;
            for (int i = 0; i < guiData.ballsCount; i++) {
                Ball& ball = guiData.balls[i];
                if (ball.onTable) ball.findNextCollision(&guiData, &time2);
            }
            for (int i = 0; i < guiData.ballsCount; i++) {
                Ball& ball = guiData.balls[i];
                if (ball.onTable && ball.isMovingOrSpinning()) ball.move(time2);
            }
            if (guiData.collision.valid) handleCollision();
            time -= time2;
        } while (time > MIN_TIME);

        anyMoving = false;
        for (int i = 0; i < guiData.ballsCount; i++) {
            Ball& ball = guiData.balls[i];
            if (ball.onTable) {
                ball.calcVelocity();
                if (ball.isMovingOrSpinning()) anyMoving = true;
            }
        }
    } while (anyMoving);
}

void Prediction::handleCollision() {
    Ball& a = *guiData.collision.ballA;
    Ball& b = *guiData.collision.ballB;
    a.positions.push_back(a.predictedPosition);
    switch (guiData.collision.type) {
        case Collision::BALL:
            handleBallBallCollision();
            b.positions.push_back(b.predictedPosition);
            if (!guiData.collision.firstHitBall) guiData.collision.firstHitBall = &b;
            break;
        case Collision::LINE:
            a.calcVelocityPostCollision(guiData.collision.angle);
            break;
        default: {
            Point2D delta = {guiData.collision.point.y - a.predictedPosition.y,
                            -(guiData.collision.point.x - a.predictedPosition.x)};
            guiData.collision.angle = -NumberUtils::calcAngle(delta);
            a.calcVelocityPostCollision(guiData.collision.angle);
            break;
        }
    }
}

void Prediction::handleBallBallCollision() const {
    Ball& a = *guiData.collision.ballA;
    Ball& b = *guiData.collision.ballB;
    Point2D rel = a.predictedPosition - b.predictedPosition;
    double inv = 1.0 / sqrt(rel.square());
    Point2D n = {rel.x * inv, rel.y * inv};
    double vA = a.velocity.x * n.x + a.velocity.y * n.y;
    double vB = b.velocity.x * n.x + b.velocity.y * n.y;
    Point2D velA = {n.x * vA, n.y * vA};
    Point2D velB = {n.x * vB, n.y * vB};
    a.velocity.x = velB.x - (velA.x - a.velocity.x);
    a.velocity.y = velB.y - (velA.y - a.velocity.y);
    b.velocity.x = velA.x - (velB.x - b.velocity.x);
    b.velocity.y = velA.y - (velB.y - b.velocity.y);
}

void Prediction::determineShotState() {
    guiData.shotState = false;
    if (!guiData.collision.firstHitBall) return;
    if (!guiData.balls[0].onTable) return;

    BallClassification pc = (BallClassification)GameManager::getPlayerClassification(0);

    if (pc == ANY) {
        if (guiData.collision.firstHitBall->classification == EIGHT_BALL) return;
        for (int i = 0; i < guiData.ballsCount; i++) {
            Ball& ball = guiData.balls[i];
            if (ball.originalOnTable != ball.onTable) {
                guiData.shotState = guiData.balls[8].onTable;
                return;
            }
        }
    } else {
        if (guiData.collision.firstHitBall->classification != pc) return;
        if (pc == NINE_BALL_RULE) {
            for (int i = 1; i < guiData.ballsCount; i++) {
                if (guiData.balls[i].originalOnTable != guiData.balls[i].onTable) {
                    guiData.shotState = true;
                    return;
                }
            }
            return;
        }
    }

    if (pc == EIGHT_BALL) {
        guiData.shotState = !guiData.balls[8].onTable;
        return;
    }

    int start = (pc == SOLID) ? 1 : 9;
    for (int i = start; i < start + 7; i++) {
        if (guiData.balls[i].originalOnTable != guiData.balls[i].onTable) {
            guiData.shotState = guiData.balls[8].onTable;
            return;
        }
    }
}

// ======== BALL METHODS ========

void Prediction::Ball::findNextCollision(void* pData, double* time) {
    auto* data = reinterpret_cast<SceneData*>(pData);
    if (state == DEFAULT) {
        for (int i = index + 1; i < data->ballsCount; i++) {
            Ball& other = data->balls[i];
            if (other.state == DEFAULT && isBallBallCollision(time, other)) {
                data->collision.valid = true;
                data->collision.ballA = this;
                data->collision.type = Collision::BALL;
                data->collision.ballB = &other;
            }
        }
    }
    if (willCollideWithTable(time)) {
        if (state == DEFAULT) {
            for (int i = 0; i < TABLE_POCKETS_COUNT; i++) {
                Point2D delta = {pockets[i].x - predictedPosition.x, pockets[i].y - predictedPosition.y};
                double dsq = delta.x * delta.x + delta.y * delta.y;
                if (dsq < POCKET_RADIUS_SQUARE) {
                    double t = *time * 120.0;
                    velocity.x += delta.x * t;
                    velocity.y += delta.y * t;
                    if (dsq < BALL_RADIUS_SQUARE) {
                        state = IN_POCKET;
                        pocketStatus[i] = true;
                    }
                }
            }
        }
        determineBallTableCollision(pData, time);
    }
    if (state == IN_POCKET) {
        state = UNKNOWN;
        onTable = false;
        velocity.nullify();
        spin.nullify();
    }
}

void Prediction::Ball::calcVelocity() {
    if (!isMovingOrSpinning()) return;
    double v15 = BALL_RADIUS * spin.x - velocity.y;
    double v16 = -velocity.x - spin.y * BALL_RADIUS;
    double v17 = sqrt(v16 * v16 + v15 * v15);
    double v18 = v17 * 0.00145772594752187;
    if (v18 > MIN_TIME) {
        double v20 = (v18 < TIME_PER_TICK) ? (v17 * 0.00145772594752187) : TIME_PER_TICK;
        double v21 = 196.0 * v20 / v17;
        velocity.x += v16 * v21;
        velocity.y += v15 * v21;
        spin.x -= v15 * v21 * 0.6578125102783204;
        spin.y += v16 * v21 * 0.6578125102783204;
    }
    if (v18 < TIME_PER_TICK) {
        double v24 = velocity.x, v25 = velocity.y;
        double v27 = (TIME_PER_TICK - v18) * 10.878;
        double v28 = 1.0 - v27 / sqrt(v25 * v25 + v24 * v24);
        if (v28 < 0.0) v28 = 0.0;
        velocity.x = v24 * v28;
        velocity.y = v25 * v28;
        spin.x = v25 * v28 / BALL_RADIUS;
        spin.y = -(v24 * v28) / BALL_RADIUS;
    }
    constexpr double v29 = 9.8 * TIME_PER_TICK;
    spin.z = (spin.z > 0.0) ? fmax(spin.z - v29, 0.0) : fmin(spin.z + v29, 0.0);
}

void Prediction::Ball::calcVelocityPostCollision(const double& angle) {
    double c = cos(angle), s = sin(angle);
    double vx = c * velocity.x - s * velocity.y;
    double vy = s * velocity.x + c * velocity.y;
    double sf = vx - BALL_RADIUS * spin.z;
    double absSF = (sf > 0) ? sf : -sf;
    double vf = absSF / 2.5;
    double absVY = (vy > 0) ? vy : -vy;
    double dir = (sf > 0) ? 1.0 : -1.0;
    double minSF = 0.4 * absVY;
    if (vf < minSF) minSF = vf;
    double sc = dir * minSF;
    double nvx = vx - sc / 2.5;
    double nvy = -0.804 * vy;
    velocity.x = s * nvy + c * nvx;
    velocity.y = c * nvy - nvx * s;
    double nsx = s * spin.x + c * spin.y;
    double nsy = c * spin.x - s * spin.y - vy * 0.1420875022201172;
    double nsz = spin.z + sc * 0.6578125102783204;
    spin.x = s * nsx + c * nsy;
    spin.y = c * nsx - nsy * s;
    spin.z = nsz;
}

void Prediction::Ball::move(const double& time) {
    if (!velocity.isZero()) {
        predictedPosition.x += velocity.x * time;
        predictedPosition.y += velocity.y * time;
    }
}

bool Prediction::Ball::isMovingOrSpinning() const {
    return velocity.isNotZero() || spin.isNotZero();
}

bool Prediction::Ball::isBallBallCollision(double* smallestTime, Ball& other) const {
    Point2D rp = other.predictedPosition - predictedPosition;
    Point2D dv = other.velocity - velocity;
    double v24 = (rp.x * dv.x + rp.y * dv.y) * 2.0;
    if (v24 >= 0) return false;
    double dvsq = dv.square();
    double v27 = (rp.square() - BALL_RADIUS_SQUARE * 4) * (dvsq * 4.0);
    double sq = v24 * v24;
    if (sq < v27) return false;
    double v28 = (-v24 - sqrt(sq - v27)) / (dvsq * 2.0);
    if (v28 < 0 || v28 - MIN_TIME > *smallestTime) return false;
    *smallestTime = v28;
    return true;
}

bool Prediction::Ball::willCollideWithTable(const double* t) const {
    double px = predictedPosition.x + velocity.x * *t;
    double py = predictedPosition.y + velocity.y * *t;
    double lx = (velocity.x > 0) ? predictedPosition.x : px;
    double rx = (velocity.x > 0) ? px : predictedPosition.x;
    double ty = (velocity.y > 0) ? predictedPosition.y : py;
    double by = (velocity.y > 0) ? py : predictedPosition.y;
    return lx < TABLE_BOUND_LEFT || rx > TABLE_BOUND_RIGHT || ty < TABLE_BOUND_TOP || by > TABLE_BOUND_BOTTOM;
}

void Prediction::Ball::determineBallTableCollision(void* pData, double* smallestTime) {
    auto* data = reinterpret_cast<SceneData*>(pData);
    for (int i = 0; i < TABLE_SHAPE_SIZE; i++) {
        const Point2D& a = tableShape[i];
        const Point2D& b = tableShape[(i + 1) % TABLE_SHAPE_SIZE];
        if (isBallLineCollision(smallestTime, a, b)) {
            data->collision.valid = true;
            data->collision.ballA = this;
            data->collision.type = Collision::LINE;
            data->collision.angle = -NumberUtils::calcAngle(b, a);
        } else if (isBallPointCollision(smallestTime, a)) {
            data->collision.valid = true;
            data->collision.ballA = this;
            data->collision.point = a;
            data->collision.type = Collision::POINT;
        }
    }
}

bool Prediction::Ball::isBallLineCollision(double* pTime, const Point2D& a, const Point2D& b) const {
    if (velocity.isZero()) return false;
    Point2D d = b - a;
    double v17 = d.y * velocity.x - d.x * velocity.y;
    if (v17 == 0) return false;
    double inv = 1.0 / sqrt(d.square());
    double v21 = inv * BALL_RADIUS;
    double v22 = predictedPosition.x - a.x - d.y * v21;
    double v23 = predictedPosition.y - a.y + d.x * v21;
    double v24 = (v22 * -velocity.y - v23 * -velocity.x) / v17;
    if (v24 <= 0 || v24 >= 1) return false;
    double t = (d.x * v23 - d.y * v22) / v17;
    if (t <= 0 || t - MIN_TIME > *pTime) return false;
    if (velocity.x * (d.y * inv) + velocity.y * -(d.x * inv) > 0) return false;
    *pTime = t;
    return true;
}

bool Prediction::Ball::isBallPointCollision(double* smallestTime, const Point2D& pt) const {
    Point2D d = pt - predictedPosition;
    double v16 = -(velocity.x * d.x * 2.0) - (velocity.y * d.y * 2.0);
    if (v16 >= 0) return false;
    double vsq = velocity.square();
    double dsq = d.square();
    double usq = v16 * v16;
    if (dsq - usq / (vsq * 4.0) >= BALL_RADIUS_SQUARE) return false;
    double v22 = (-v16 - sqrt(usq - vsq * 4.0 * (dsq - BALL_RADIUS_SQUARE))) / (vsq * 2.0);
    if (v22 < 0 || v22 - MIN_TIME > *smallestTime) return false;
    *smallestTime = v22;
    return true;
}
