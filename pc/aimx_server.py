#!/usr/bin/env python3
"""
AimXHack - PC Server + Physics
Captura tela via ADB, detecta bolas, calcula física, envia overlay para o celular
"""

import cv2
import numpy as np
import subprocess
import os
import sys
import time
import math
import json
import socket
import threading

ADB = os.path.expanduser('~/android-sdk/platform-tools/adb')

# Constantes do jogo
GAME_TABLE_WIDTH = 254.0
GAME_TABLE_HEIGHT = 127.0
BALL_RADIUS = 3.800475
POCKET_RADIUS = 8.0

class PhysicsEngine:
    """Simula física das bolas"""
    
    def predict_trajectory(self, cue_ball, balls, angle, power):
        """Prevê trajetória da bola branca e colisões"""
        if cue_ball is None:
            return []
        
        cx, cy = cue_ball['game_x'], cue_ball['game_y']
        vx = power * math.cos(angle)
        vy = power * math.sin(angle)
        
        trajectory = [{'x': cx, 'y': cy, 'type': 'cue'}]
        collisions = []
        
        for tick in range(2000):
            # Mover
            cx += vx * 0.005
            cy += vy * 0.005
            
            # Colisão com paredes
            if cx <= -GAME_TABLE_WIDTH/2 + BALL_RADIUS:
                cx = -GAME_TABLE_WIDTH/2 + BALL_RADIUS
                vx = -vx * 0.82
                collisions.append({'x': cx, 'y': cy, 'type': 'wall'})
            elif cx >= GAME_TABLE_WIDTH/2 - BALL_RADIUS:
                cx = GAME_TABLE_WIDTH/2 - BALL_RADIUS
                vx = -vx * 0.82
                collisions.append({'x': cx, 'y': cy, 'type': 'wall'})
            
            if cy <= -GAME_TABLE_HEIGHT/2 + BALL_RADIUS:
                cy = -GAME_TABLE_HEIGHT/2 + BALL_RADIUS
                vy = -vy * 0.82
                collisions.append({'x': cx, 'y': cy, 'type': 'wall'})
            elif cy >= GAME_TABLE_HEIGHT/2 - BALL_RADIUS:
                cy = GAME_TABLE_HEIGHT/2 - BALL_RADIUS
                vy = -vy * 0.82
                collisions.append({'x': cx, 'y': cy, 'type': 'wall'})
            
            # Colisão com outras bolas
            for ball in balls:
                if ball == cue_ball:
                    continue
                bx, by = ball['game_x'], ball['game_y']
                dist = math.sqrt((cx - bx)**2 + (cy - by)**2)
                if dist < BALL_RADIUS * 2:
                    # Calcular reflexão
                    nx = (cx - bx) / dist
                    ny = (cy - by) / dist
                    dot = vx * nx + vy * ny
                    vx = vx - 2 * dot * nx
                    vy = vy - 2 * dot * ny
                    vx *= 0.95
                    vy *= 0.95
                    collisions.append({'x': cx, 'y': cy, 'type': 'ball', 'target': ball.get('type', 'unknown')})
                    break
            
            # Verificar buracos
            pockets = [
                (-GAME_TABLE_WIDTH/2, -GAME_TABLE_HEIGHT/2),
                (0, -GAME_TABLE_HEIGHT/2 - 5),
                (GAME_TABLE_WIDTH/2, -GAME_TABLE_HEIGHT/2),
                (-GAME_TABLE_WIDTH/2, GAME_TABLE_HEIGHT/2),
                (0, GAME_TABLE_HEIGHT/2 + 5),
                (GAME_TABLE_WIDTH/2, GAME_TABLE_HEIGHT/2),
            ]
            for px, py in pockets:
                dist = math.sqrt((cx - px)**2 + (cy - py)**2)
                if dist < POCKET_RADIUS:
                    trajectory.append({'x': cx, 'y': cy, 'type': 'pocket'})
                    return {'trajectory': trajectory, 'collisions': collisions}
            
            # Fricção
            speed = math.sqrt(vx**2 + vy**2)
            if speed < 0.5:
                break
            vx *= 0.998
            vy *= 0.998
            
            # Adicionar ponto
            if len(trajectory) < 2 or math.sqrt((cx - trajectory[-1]['x'])**2 + (cy - trajectory[-1]['y'])**2) > 2:
                trajectory.append({'x': cx, 'y': cy, 'type': 'path'})
        
        return {'trajectory': trajectory, 'collisions': collisions}


def capture_screen():
    result = subprocess.run([ADB, 'exec-out', 'screencap', '-p'], capture_output=True)
    if result.returncode != 0:
        return None
    img_array = np.frombuffer(result.stdout, dtype=np.uint8)
    return cv2.imdecode(img_array, cv2.IMREAD_COLOR)


def detect_table(frame):
    hsv = cv2.cvtColor(frame, cv2.COLOR_BGR2HSV)
    mask = cv2.inRange(hsv, np.array([90, 40, 40]), np.array([115, 255, 255]))
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    
    if not contours:
        return None
    
    best = None
    best_area = 0
    for c in contours:
        x, y, w, h = cv2.boundingRect(c)
        ratio = w / h if h > 0 else 0
        area = w * h
        if 1.7 < ratio < 2.3 and area > best_area and area > 200000:
            best = (x, y, w, h)
            best_area = area
    
    if best is None:
        return None
    
    x, y, w, h = best
    scale = w / GAME_TABLE_WIDTH
    return {
        'left': x, 'top': y, 'right': x + w, 'bottom': y + h,
        'width': w, 'height': h, 'scale': scale
    }


def detect_balls(frame, table):
    if table is None:
        return []
    
    x1, y1 = table['left'], table['top']
    x2, y2 = table['right'], table['bottom']
    roi = frame[y1:y2, x1:x2]
    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    gray_blur = cv2.GaussianBlur(gray, (9, 9), 2)
    
    expected_r = int(BALL_RADIUS * table['scale'])
    circles = cv2.HoughCircles(
        gray_blur, cv2.HOUGH_GRADIENT, dp=1, minDist=expected_r * 2,
        param1=80, param2=15,
        minRadius=max(3, expected_r - 8), maxRadius=expected_r + 10
    )
    
    balls = []
    if circles is not None:
        circles = np.uint16(np.around(circles))
        for (cx, cy, r) in circles[0, :]:
            sx = x1 + int(cx)
            sy = y1 + int(cy)
            margin = int(r * 2)
            if sx < x1 + margin or sx > x2 - margin: continue
            if sy < y1 + margin or sy > y2 - margin: continue
            too_close = any(math.sqrt((sx - b['screen_x'])**2 + (sy - b['screen_y'])**2) < r * 1.5 for b in balls)
            if too_close: continue
            
            ball_roi = frame[max(0, sy-r):sy+r, max(0, sx-r):sx+r]
            if ball_roi.size == 0: continue
            
            avg_brightness = np.mean(ball_roi)
            if avg_brightness < 30: continue
            
            white_mask = cv2.inRange(ball_roi, np.array([180, 180, 180]), np.array([255, 255, 255]))
            white_ratio = cv2.countNonZero(white_mask) / (ball_roi.shape[0] * ball_roi.shape[1])
            
            avg_bgr = np.mean(ball_roi, axis=(0, 1))
            b_c, g_c, r_c = avg_bgr
            brightness = (r_c + g_c + b_c) / 3
            
            if brightness > 170 and white_ratio > 0.3:
                tipo = "cue"
            elif brightness < 40:
                tipo = "8ball"
            elif white_ratio > 0.15:
                tipo = "striped"
            else:
                tipo = "solid"
            
            game_x = (int(cx) - table['width'] / 2) / table['scale']
            game_y = (int(cy) - table['height'] / 2) / table['scale']
            
            balls.append({
                'screen_x': sx, 'screen_y': sy,
                'radius': int(r), 'type': tipo,
                'game_x': game_x, 'game_y': game_y
            })
    
    return balls


def main():
    print("=" * 50)
    print("  AimXHack - PC Server")
    print("=" * 50)
    
    result = subprocess.run([ADB, 'devices'], capture_output=True, text=True)
    if 'device' not in result.stdout:
        print("ERRO: Celular não conectado")
        sys.exit(1)
    
    physics = PhysicsEngine()
    
    # Porta TCP para comunicação com o app Android
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('0.0.0.0', 9999))
    server.listen(1)
    server.settimeout(1.0)
    
    print("Servidor TCP na porta 9999")
    print("Aguardando conexão do app Android...")
    
    client = None
    
    try:
        while True:
            # Aceitar conexão do app Android
            try:
                if client is None:
                    client, addr = server.accept()
                    print(f"App conectado: {addr}")
            except socket.timeout:
                pass
            
            # Capturar tela
            frame = capture_screen()
            if frame is None:
                time.sleep(0.5)
                continue
            
            # Detectar mesa
            table = detect_table(frame)
            
            # Detectar bolas
            balls = detect_balls(frame, table)
            
            # Encontrar bola branca
            cue_ball = next((b for b in balls if b['type'] == 'cue'), None)
            
            # Calcular trajetória (se bola branca encontrada)
            trajectory_data = None
            if cue_ball:
                # Testar diferentes ângulos para encontrar melhor shot
                best_score = -1
                best_trajectory = None
                
                for angle_deg in range(0, 360, 5):
                    angle_rad = math.radians(angle_deg)
                    result = physics.predict_trajectory(cue_ball, balls, angle_rad, 500)
                    
                    # Pontuação: quantas bolas encaçariam
                    score = sum(1 for c in result['collisions'] if c['type'] == 'ball')
                    pockets = sum(1 for p in result['trajectory'] if p['type'] == 'pocket')
                    score += pockets * 10
                    
                    if score > best_score:
                        best_score = score
                        best_trajectory = result
                        best_trajectory['angle'] = angle_deg
                
                trajectory_data = best_trajectory
            
            # Preparar dados para enviar ao celular
            data = {
                'table': table,
                'balls': balls,
                'trajectory': trajectory_data,
                'timestamp': time.time()
            }
            
            # Enviar via TCP
            if client:
                try:
                    msg = json.dumps(data) + '\n'
                    client.sendall(msg.encode())
                except:
                    client = None
                    print("App desconectado")
            
            # Mostrar no PC
            overlay = frame.copy()
            if table:
                cv2.rectangle(overlay, (table['left'], table['top']), (table['right'], table['bottom']), (0, 255, 0), 3)
            
            for ball in balls:
                color = (255, 255, 255) if ball['type'] == 'cue' else (0, 0, 255) if ball['type'] == 'solid' else (255, 200, 0)
                cv2.circle(overlay, (ball['screen_x'], ball['screen_y']), ball['radius'], color, 2)
            
            if trajectory_data and 'trajectory' in trajectory_data:
                for i in range(len(trajectory_data['trajectory']) - 1):
                    p1 = trajectory_data['trajectory'][i]
                    p2 = trajectory_data['trajectory'][i + 1]
                    if table:
                        x1 = int(p1['x'] * table['scale'] + table['left'] + table['width'] / 2)
                        y1 = int(p1['y'] * table['scale'] + table['top'] + table['height'] / 2)
                        x2 = int(p2['x'] * table['scale'] + table['left'] + table['width'] / 2)
                        y2 = int(p2['y'] * table['scale'] + table['top'] + table['height'] / 2)
                        cv2.line(overlay, (x1, y1), (x2, y2), (0, 0, 255), 2)
            
            cv2.imshow('AimXHack Server', overlay)
            
            key = cv2.waitKey(1) & 0xFF
            if key == ord('q') or key == 27:
                break
            
            time.sleep(0.1)
    
    except KeyboardInterrupt:
        print("\nSaindo...")
    
    finally:
        if client:
            client.close()
        server.close()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
