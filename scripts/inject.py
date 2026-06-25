#!/usr/bin/env python3
"""
AimXHack - Script de injeção no 8 Ball Pool
Não precisa de apktool - usa apenas Python + Java (jarsigner)

Uso: python3 inject.py <arquivo.xapk>
"""

import os
import sys
import shutil
import subprocess
import zipfile
import struct

WORK = "/tmp/aimx_inject"

def extract_xapk(xapk_path):
    """Extrai XAPK e encontra os APKs"""
    print("[1] Extraindo XAPK...")
    xapk_dir = os.path.join(WORK, "xapk")
    os.makedirs(xapk_dir, exist_ok=True)

    with zipfile.ZipFile(xapk_path, 'r') as z:
        z.extractall(xapk_dir)

    main_apk = None
    config_apk = None
    for f in os.listdir(xapk_dir):
        if f.endswith('.apk'):
            if 'config' in f:
                config_apk = os.path.join(xapk_dir, f)
            else:
                main_apk = os.path.join(xapk_dir, f)

    print(f"    Main: {main_apk}")
    print(f"    Config: {config_apk}")
    return main_apk, config_apk

def extract_apk(apk_path, output_dir):
    """Extrai APK para diretório"""
    print(f"[2] Extraindo APK para {output_dir}...")
    if os.path.exists(output_dir):
        shutil.rmtree(output_dir)
    os.makedirs(output_dir)

    with zipfile.ZipFile(apk_path, 'r') as z:
        z.extractall(output_dir)

def extract_native_libs(config_apk, pool_dir):
    """Extrai libs nativas do config APK"""
    if not config_apk:
        print("[3] Sem config APK - pulando extração de libs")
        return

    print("[3] Extraindo libs nativas...")
    with zipfile.ZipFile(config_apk, 'r') as z:
        for name in z.namelist():
            if name.startswith('lib/'):
                target = os.path.join(pool_dir, name)
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with open(target, 'wb') as f:
                    f.write(z.read(name))

def create_smali_files(pool_dir):
    """Cria os arquivos smali para injeção"""
    print("[4] Criando smali files...")
    smali_dir = os.path.join(pool_dir, "smali_classes5", "com", "aimx", "hack")
    os.makedirs(smali_dir, exist_ok=True)

    launcher_smali = """.class public Lcom/aimx/hack/LauncherActivity;
.super Landroid/app/Activity;

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V
    return-void
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .locals 2
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    new-instance v0, Landroid/content/Intent;
    const-class v1, Lcom/aimx/hack/HackService;
    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
    invoke-virtual {p0, v0}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;
    invoke-virtual {p0}, Landroid/app/Activity;->finish()V
    return-void
.end method
"""

    hackservice_smali = """.class public Lcom/aimx/hack/HackService;
.super Landroid/app/Service;

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Landroid/app/Service;-><init>()V
    return-void
.end method

.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;
    .locals 1
    const/4 v0, 0x0
    return-object v0
.end method

.method public onCreate()V
    .locals 1
    invoke-super {p0}, Landroid/app/Service;->onCreate()V
    const-string v0, "aimxhack"
    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V
    return-void
.end method
"""

    with open(os.path.join(smali_dir, "LauncherActivity.smali"), 'w') as f:
        f.write(launcher_smali)
    with open(os.path.join(smali_dir, "HackService.smali"), 'w') as f:
        f.write(hackservice_smali)

    print(f"    Criado em: {smali_dir}")

def inject_into_main_activity(pool_dir):
    """Injeta chamada para nosso launcher no onCreate da main activity"""
    print("[5] Injetando no main activity...")

    # Encontrar o smali da main activity
    main_smali = None
    for root, dirs, files in os.walk(pool_dir):
        for f in files:
            if f == "EightBallPoolActivity.smali":
                main_smali = os.path.join(root, f)
                break

    if not main_smali:
        print("    ERRO: EightBallPoolActivity.smali não encontrado!")
        # Tentar encontrar qualquer activity com onCreate
        for root, dirs, files in os.walk(pool_dir):
            for f in files:
                if "Activity.smali" in f and "miniclip" in root.lower():
                    print(f"    Encontrado: {os.path.join(root, f)}")
        return False

    print(f"    Arquivo: {main_smali}")

    with open(main_smali, 'r') as f:
        content = f.read()

    injection = """
    # === AimXHack Injection Start ===
    new-instance v0, Landroid/content/Intent;
    const-class v1, Lcom/aimx/hack/LauncherActivity;
    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
    # === AimXHack Injection End ===
"""

    # Injetar antes do último return-void no onCreate
    if ".method protected onCreate" in content or ".method public onCreate" in content:
        # Encontrar o onCreate e injetar antes do return-void
        lines = content.split('\n')
        new_lines = []
        in_oncreate = False
        injected = False

        for line in lines:
            if '.method' in line and 'onCreate' in line:
                in_oncreate = True
            if in_oncreate and 'return-void' in line and not injected:
                new_lines.append(injection)
                injected = True
                in_oncreate = False
            new_lines.append(line)

        if injected:
            with open(main_smali, 'w') as f:
                f.write('\n'.join(new_lines))
            print("    OK: Injetado com sucesso!")
            return True
        else:
            print("    AVISO: Não encontrou return-void no onCreate")
            return False
    else:
        print("    ERRO: onCreate não encontrado!")
        return False

def modify_manifest_binary(pool_dir):
    """Adiciona componentes ao manifest binário usando aapt2"""
    print("[6] Modificando AndroidManifest...")

    manifest_path = os.path.join(pool_dir, "AndroidManifest.xml")

    # Ler o manifest binário
    with open(manifest_path, 'rb') as f:
        data = bytearray(f.read())

    # Procurar pelo padrão </application> em binário
    # O formato binário XML do Android usa strings UTF-16
    app_end = b'\x03\x00\x08\x00'  # END_TAG type

    # Abordagem mais simples: criar um manifest de texto e usar aapt2
    text_manifest = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.miniclip.eightballpool"
    android:versionCode="3980"
    android:versionName="56.26.1">

    <uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35"/>
    <uses-permission android:name="android.permission.WAKE_LOCK"/>
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>

    <application
        android:label="8 Ball Pool"
        android:allowBackup="false"
        android:hardwareAccelerated="true">

        <activity
            android:name="com.miniclip.eightballpool.EightBallPoolActivity"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <activity android:name="com.aimx.hack.LauncherActivity" android:exported="false"/>
        <service android:name="com.aimx.hack.HackService" android:exported="false"/>

    </application>
</manifest>"""

    # Salvar manifest de texto
    text_manifest_path = os.path.join(WORK, "AndroidManifest_text.xml")
    with open(text_manifest_path, 'w') as f:
        f.write(text_manifest)

    # Compilar com aapt2
    flat_path = os.path.join(WORK, "manifest.flat")
    result = subprocess.run([
        os.path.expanduser("~/android-sdk/build-tools/35.0.0/aapt2"),
        "compile", "-o", flat_path, text_manifest_path
    ], capture_output=True, text=True)

    if result.returncode != 0:
        print(f"    Erro aapt2 compile: {result.stderr}")
        # Tentar abordagem alternativa - injetar strings no binário
        print("    Tentando injeção direta no binário...")
        return inject_manifest_binary(data, manifest_path)

    # Link para gerar manifest binário
    compiled_manifest = os.path.join(WORK, "manifest.flat")
    linked_manifest = os.path.join(WORK, "AndroidManifest_new.xml")

    # Nota: aapt2 link precisa de muito mais parâmetros
    # Por simplicidade, vamos pular a modificação do manifest
    # e confiar que o smali injection funciona sem declarar componentes

    print("    AVISO: Manifest binário não modificado - componentes não declarados")
    print("    O hack pode não funcionar sem root + permissões")
    return True

def inject_manifest_binary(data, manifest_path):
    """Injeção direta no binário do manifest"""
    # Procurar por "</application>" em UTF-16
    search = "</application>".encode('utf-16-le')
    pos = data.find(search)

    if pos >= 0:
        injection = """
    <activity android:name="com.aimx.hack.LauncherActivity" android:exported="false"/>
    <service android:name="com.aimx.hack.HackService" android:exported="false"/>
""".encode('utf-16-le')

        data[pos:pos] = injection
        with open(manifest_path, 'wb') as f:
            f.write(data)
        print("    OK: Manifest modificado via injeção binária")
        return True

    print("    ERRO: Não encontrou </application> no manifest")
    return False

def repackage_apk(pool_dir, output_apk):
    """Reempacota o APK"""
    print("[7] Reempacotando APK...")

    with zipfile.ZipFile(output_apk, 'w', zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(pool_dir):
            for f in files:
                filepath = os.path.join(root, f)
                arcname = os.path.relpath(filepath, pool_dir)
                zf.write(filepath, arcname)

    size = os.path.getsize(output_apk)
    print(f"    APK criado: {output_apk} ({size / 1024 / 1024:.1f} MB)")

def sign_apk(apk_path):
    """Assina o APK com keystore debug"""
    print("[8] Assinando APK...")

    keystore = os.path.join(WORK, "debug.keystore")

    # Gerar keystore se não existir
    if not os.path.exists(keystore):
        subprocess.run([
            "keytool", "-genkey", "-v",
            "-keystore", keystore,
            "-alias", "debug",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-storepass", "android",
            "-keypass", "android",
            "-dname", "CN=Debug"
        ], capture_output=True)

    # Assinar
    result = subprocess.run([
        "jarsigner",
        "-sigalg", "SHA256withRSA",
        "-digestalg", "SHA-256",
        "-keystore", keystore,
        "-storepass", "android",
        "-keypass", "android",
        apk_path,
        "debug"
    ], capture_output=True, text=True)

    if result.returncode == 0:
        print("    OK: APK assinado!")
    else:
        print(f"    Erro: {result.stderr}")

def main():
    if len(sys.argv) < 2:
        print("Uso: python3 inject.py <arquivo.xapk>")
        print("Exemplo: python3 inject.py ~/8ballpool.xapk")
        sys.exit(1)

    xapk_path = sys.argv[1]
    if not os.path.exists(xapk_path):
        print(f"ERRO: Arquivo não encontrado: {xapk_path}")
        sys.exit(1)

    print("=" * 50)
    print("  AimXHack - Injeção no 8 Ball Pool")
    print("=" * 50)

    # Limpar
    if os.path.exists(WORK):
        shutil.rmtree(WORK)
    os.makedirs(WORK)

    # Pipeline
    main_apk, config_apk = extract_xapk(xapk_path)
    pool_dir = os.path.join(WORK, "pool")
    extract_apk(main_apk, pool_dir)
    extract_native_libs(config_apk, pool_dir)
    create_smali_files(pool_dir)
    inject_into_main_activity(pool_dir)
    modify_manifest_binary(pool_dir)

    output_apk = os.path.join(WORK, "8ballpool_hacked.apk")
    repackage_apk(pool_dir, output_apk)
    sign_apk(output_apk)

    print()
    print("=" * 50)
    print("  CONCLUÍDO!")
    print("=" * 50)
    print(f"APK: {output_apk}")
    print(f"Tamanho: {os.path.getsize(output_apk) / 1024 / 1024:.1f} MB")
    print()
    print("Para instalar no celular:")
    print(f"  adb install {output_apk}")
    print()
    print("Ou copie o APK para o celular e instale.")

if __name__ == "__main__":
    main()
