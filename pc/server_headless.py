#!/usr/bin/env python3
"""AimXHack - Servidor headless (sem GUI)"""
import cv2, numpy as np, subprocess, os, sys, time, math, json, socket

ADB = os.path.expanduser('~/android-sdk/platform-tools/adb')
TABLE_W, TABLE_H, BALL_R = 254.0, 127.0, 3.800475

def capture():
    r = subprocess.run([ADB, 'exec-out', 'screencap', '-p'], capture_output=True, timeout=5)
    if r.returncode != 0: return None
    a = np.frombuffer(r.stdout, dtype=np.uint8)
    return cv2.imdecode(a, cv2.IMREAD_COLOR)

def detect_table(f):
    hsv = cv2.cvtColor(f, cv2.COLOR_BGR2HSV)
    mask = cv2.inRange(hsv, np.array([90,40,40]), np.array([115,255,255]))
    cnts, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not cnts: return None
    best, ba = None, 0
    for c in cnts:
        x,y,w,h = cv2.boundingRect(c)
        r = w/h if h>0 else 0
        if 1.7<r<2.3 and w*h>ba and w*h>200000:
            best, ba = (x,y,w,h), w*h
    if not best: return None
    x,y,w,h = best
    return {'l':x,'t':y,'r':x+w,'b':y+h,'w':w,'h':h,'s':w/TABLE_W}

def detect_balls(f, tb):
    if not tb: return []
    x1,y1,x2,y2 = tb['l'],tb['t'],tb['r'],tb['b']
    roi = f[y1:y2, x1:x2]
    g = cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)
    g = cv2.GaussianBlur(g, (9,9), 2)
    er = int(BALL_R*tb['s'])
    cs = cv2.HoughCircles(g, cv2.HOUGH_GRADIENT, 1, er*2, param1=80, param2=15, minRadius=max(3,er-8), maxRadius=er+10)
    balls = []
    if cs is not None:
        for cx,cy,r in np.uint16(np.around(cs))[0]:
            sx,sy = x1+int(cx), y1+int(cy)
            m = int(r*2)
            if sx<x1+m or sx>x2-m or sy<y1+m or sy>y2-m: continue
            if any(math.sqrt((sx-b[0])**2+(sy-b[1])**2)<r*1.5 for b in balls): continue
            br = f[max(0,sy-r):sy+r, max(0,sx-r):sx+r]
            if br.size==0 or np.mean(br)<30: continue
            wm = cv2.inRange(br, np.array([180,180,180]), np.array([255,255,255]))
            wr = cv2.countNonZero(wm)/(br.shape[0]*br.shape[1])
            avg = np.mean(br, axis=(0,1))
            b_c,g_c,r_c = avg
            brt = (r_c+g_c+b_c)/3
            if brt>170 and wr>0.3: t="cue"
            elif brt<40: t="8ball"
            elif wr>0.15: t="striped"
            else: t="solid"
            gx = (int(cx)-tb['w']/2)/tb['s']
            gy = (int(cy)-tb['h']/2)/tb['s']
            balls.append((sx,sy,int(r),t,gx,gy))
    return balls

def predict(cue, balls, angle, power):
    cx,cy = cue[4],cue[5]
    vx,vy = power*math.cos(angle), power*math.sin(angle)
    traj = [{'x':cx,'y':cy}]
    for _ in range(2000):
        cx+=vx*0.005; cy+=vy*0.005
        if cx<=-TABLE_W/2+BALL_R: cx=-TABLE_W/2+BALL_R; vx=-vx*0.82
        elif cx>=TABLE_W/2-BALL_R: cx=TABLE_W/2-BALL_R; vx=-vx*0.82
        if cy<=-TABLE_H/2+BALL_R: cy=-TABLE_H/2+BALL_R; vy=-vy*0.82
        elif cy>=TABLE_H/2-BALL_R: cy=TABLE_H/2-BALL_R; vy=-vy*0.82
        for b in balls:
            if b==cue: continue
            d=math.sqrt((cx-b[4])**2+(cy-b[5])**2)
            if d<BALL_R*2:
                nx,ny=(cx-b[4])/d,(cy-b[5])/d
                dot=vx*nx+vy*ny
                vx-=2*dot*nx; vy-=2*dot*ny
                vx*=0.95; vy*=0.95
                break
        for px,py in [(-TABLE_W/2,-TABLE_H/2),(0,-TABLE_H/2-5),(TABLE_W/2,-TABLE_H/2),(-TABLE_W/2,TABLE_H/2),(0,TABLE_H/2+5),(TABLE_W/2,TABLE_H/2)]:
            if math.sqrt((cx-px)**2+(cy-py)**2)<8.0:
                traj.append({'x':cx,'y':cy})
                return traj
        sp=math.sqrt(vx**2+vy**2)
        if sp<0.5: break
        vx*=0.998; vy*=0.998
        if len(traj)<2 or math.sqrt((cx-traj[-1]['x'])**2+(cy-traj[-1]['y'])**2)>2:
            traj.append({'x':cx,'y':cy})
    return traj

def main():
    print("AimXHack Headless Server", flush=True)
    r = subprocess.run([ADB,'devices'], capture_output=True, text=True)
    if 'device' not in r.stdout: print("ERRO: sem ADB"); sys.exit(1)
    
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(('0.0.0.0', 9999))
    srv.listen(1)
    srv.settimeout(1.0)
    print("TCP 9999 pronto", flush=True)
    
    client = None
    while True:
        try:
            if client is None:
                client, addr = srv.accept()
                print(f"App: {addr}", flush=True)
        except socket.timeout: pass
        
        frame = capture()
        if frame is None: time.sleep(0.5); continue
        
        tb = detect_table(frame)
        balls = detect_balls(frame, tb)
        cue = next((b for b in balls if b[3]=="cue"), None)
        
        traj = None
        if cue:
            best_s, best_t = -1, None
            for a in range(0, 360, 5):
                r = predict(cue, balls, math.radians(a), 500)
                s = len(r)
                if s > best_s: best_s, best_t = s, r
            if best_t: traj = best_t
        
        data = {'table': tb, 'balls': [{'screen_x':b[0],'screen_y':b[1],'radius':b[2],'type':b[3],'game_x':b[4],'game_y':b[5]} for b in balls], 'trajectory': traj}
        
        if client:
            try: client.sendall((json.dumps(data)+'\n').encode())
            except: client = None; print("Desconectado", flush=True)
        
        solids = sum(1 for b in balls if b[3]=='solid')
        striped = sum(1 for b in balls if b[3]=='striped')
        print(f"\rS:{solids} L:{striped} T:{len(balls)} Traj:{len(traj) if traj else 0}", end='', flush=True)
        time.sleep(0.1)

if __name__=="__main__": main()
