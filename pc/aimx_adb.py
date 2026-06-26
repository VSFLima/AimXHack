#!/usr/bin/env python3
"""
AimXHack - PC + ADB approach
Detecta bolas do 8 Ball Pool via captura de tela ADB
"""

import cv2
import numpy as np
import subprocess
import os
import sys
import time
import math

ADB = os.path.expanduser('~/android-sdk/platform-tools/adb')

# Constantes do jogo
GAME_TABLE_WIDTH = 254.0
GAME_TABLE_HEIGHT = 127.0
BALL_RADIUS = 3.800475

def capture_screen():
    """Captura tela do celular via ADB"""
    result = subprocess.run([ADB, 'exec-out', 'screencap', '-p'], capture_output=True)
    if result.returncode != 0:
        return None
    img_array = np.frombuffer(result.stdout, dtype=np.uint8)
    return cv2.imdecode(img_array, cv2.IMREAD_COLOR)

def detect_table(frame):
    """Detecta mesa de sinuca"""
    hsv = cv2.cvtColor(frame, cv2.COLOR_BGR2HSV)
    
    # Cor da mesa: ciano/azul-esverdeado (H=90-115)
    mask = cv2.inRange(hsv, np.array([90, 40, 40]), np.array([115, 255, 255]))
    
    # Encontrar contornos
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    
    if not contours:
        return None
    
    # Pegar maior contorno com proporção ~2:1
    best = None
    best_area = 0
    
    for c in contours:
        x, y, w, h = cv2.boundingRect(c)
        ratio = w / h if h > 0 else 0
        area = w * h
        
        if 1.5 < ratio < 2.5 and area > best_area:
            best = (x, y, w, h)
            best_area = area
    
    if best is None:
        return None
    
    x, y, w, h = best
    scale = w / GAME_TABLE_WIDTH
    
    return {
        'left': x, 'top': y,
        'right': x + w, 'bottom': y + h,
        'width': w, 'height': h,
        'scale': scale
    }

def detect_balls(frame, table):
    """Detecta bolas na mesa"""
    if table is None:
        return []
    
    # Recortar região da mesa
    x1, y1 = table['left'], table['top']
    x2, y2 = table['right'], table['bottom']
    roi = frame[y1:y2, x1:x2]
    
    # Converter para cinza e suavizar
    gray = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (9, 9), 2)
    
    # Raio esperado em pixels
    expected_r = int(BALL_RADIUS * table['scale'])
    min_r = max(3, expected_r - 5)
    max_r = expected_r + 8
    
    # Detectar círculos
    circles = cv2.HoughCircles(
        gray, cv2.HOUGH_GRADIENT,
        dp=1, minDist=expected_r * 2,
        param1=80, param2=20,
        minRadius=min_r, maxRadius=max_r
    )
    
    balls = []
    
    if circles is not None:
        circles = np.uint16(np.around(circles))
        
        for (cx, cy, r) in circles[0, :]:
            # Converter para coordenadas da tela
            screen_x = x1 + cx
            screen_y = y1 + cy
            
            # Filtrar: não pode estar muito perto da borda (provavelmente UI)
            margin = int(r * 2)
            if screen_x < x1 + margin or screen_x > x2 - margin:
                continue
            if screen_y < y1 + margin or screen_y > y2 - margin:
                continue
            
            # Filtrar: não pode estar muito perto de outro já detectado
            too_close = False
            for b in balls:
                dist = math.sqrt((screen_x - b['x'])**2 + (screen_y - b['y'])**2)
                if dist < r * 1.5:
                    too_close = True
                    break
            if too_close:
                continue
            
            # Classificar bola por cor
            ball_type = classify_ball(frame, screen_x, screen_y, r)
            
            # Coordenadas do jogo
            game_x = (cx - table['width']//2) / table['scale']
            game_y = (cy - table['height']//2) / table['scale']
            
            balls.append({
                'x': screen_x,
                'y': screen_y,
                'radius': int(r),
                'type': ball_type,
                'game_x': game_x,
                'game_y': game_y
            })
    
    return balls

def classify_ball(frame, cx, cy, radius):
    """Classifica bola por cor"""
    # Região da bola
    x1 = max(0, cx - radius)
    y1 = max(0, cy - radius)
    x2 = min(frame.shape[1], cx + radius)
    y2 = min(frame.shape[0], cy + radius)
    
    roi = frame[y1:y2, x1:x2]
    if roi.size == 0:
        return 'unknown'
    
    # Calcular cor média
    avg_color = np.mean(roi, axis=(0, 1))
    b, g, r = avg_color
    brightness = (r + g + b) / 3
    
    # Classificar
    if brightness > 180:
        return 'white'  # Bola branca (cue)
    elif brightness < 50:
        return 'black'  # Bola 8
    elif r > 140 and g < 100 and b < 100:
        return 'red'    # Bola sólida vermelha
    elif r > 140 and g > 100 and b < 80:
        return 'orange' # Bola sólida laranja
    elif r > 140 and g > 140 and b < 80:
        return 'yellow' # Bola sólida amarela
    elif g > 140 and r < 80 and b < 80:
        return 'green'  # Bola sólida verde
    elif b > 140 and r < 80 and g < 80:
        return 'blue'   # Bola sólida azul
    elif r > 100 and b > 100 and g < 70:
        return 'purple' # Bola sólida roxa
    elif r > 100 and g > 60 and b > 60:
        return 'brown'  # Bola sólida marrom
    else:
        return 'other'

def draw_overlay(frame, table, balls):
    """Desenha overlay na imagem"""
    overlay = frame.copy()
    
    # Desenhar borda da mesa
    if table:
        cv2.rectangle(overlay,
            (table['left'], table['top']),
            (table['right'], table['bottom']),
            (0, 255, 0), 3)
        
        # Mostrar dimensões
        cv2.putText(overlay,
            f"Mesa: {table['width']}x{table['height']} escala={table['scale']:.2f}",
            (table['left'], table['top'] - 10),
            cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
    
    # Desenhar bolas
    ball_colors = {
        'white': (255, 255, 255),
        'black': (0, 0, 0),
        'red': (0, 0, 255),
        'orange': (0, 128, 255),
        'yellow': (0, 255, 255),
        'green': (0, 200, 0),
        'blue': (255, 100, 0),
        'purple': (200, 0, 200),
        'brown': (0, 100, 150),
    }
    
    for ball in balls:
        color = ball_colors.get(ball['type'], (128, 128, 128))
        
        # Círculo da bola
        cv2.circle(overlay, (ball['x'], ball['y']), ball['radius'], color, 2)
        
        # Centro
        cv2.circle(overlay, (ball['x'], ball['y']), 3, (255, 255, 255), -1)
        
        # Label
        label = ball['type'][:1].upper()
        cv2.putText(overlay, label,
            (ball['x'] - 5, ball['y'] + 5),
            cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 255, 255), 1)
    
    # Painel de status
    cv2.rectangle(overlay, (0, 0), (400, 60), (0, 0, 0), -1)
    cv2.putText(overlay, f"AimXHack | Bolas: {len(balls)}",
        (10, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
    
    if table:
        cv2.putText(overlay, f"Mesa: {table['width']}x{table['height']}",
            (10, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1)
    
    return overlay

def main():
    print("=" * 50)
    print("  AimXHack - PC + ADB")
    print("=" * 50)
    print("Celular conectado. Capturando tela...")
    print("Pressione 'q' ou ESC para sair")
    print()
    
    # Verificar ADB
    result = subprocess.run([ADB, 'devices'], capture_output=True, text=True)
    if 'device' not in result.stdout:
        print("ERRO: Celular não conectado")
        sys.exit(1)
    
    print("ADB OK! Iniciando detecção...")
    
    frame_count = 0
    fps_time = time.time()
    
    try:
        while True:
            # Capturar tela
            frame = capture_screen()
            if frame is None:
                time.sleep(0.5)
                continue
            
            # Detectar mesa
            table = detect_table(frame)
            
            # Detectar bolas
            balls = detect_balls(frame, table)
            
            # Desenhar overlay
            overlay = draw_overlay(frame, table, balls)
            
            # Mostrar no PC
            cv2.imshow('AimXHack', overlay)
            
            # FPS
            frame_count += 1
            if time.time() - fps_time >= 1:
                fps = frame_count / (time.time() - fps_time)
                print(f"\rFPS: {fps:.1f} | Bolas: {len(balls)} | Mesa: {'OK' if table else 'N/A'}", end='', flush=True)
                frame_count = 0
                fps_time = time.time()
            
            # Tecla
            key = cv2.waitKey(1) & 0xFF
            if key == ord('q') or key == 27:
                break
            
            time.sleep(0.1)  # ~10 FPS
    
    except KeyboardInterrupt:
        print("\nSaindo...")
    
    finally:
        cv2.destroyAllWindows()

if __name__ == "__main__":
    main()
