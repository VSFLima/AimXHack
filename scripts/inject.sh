#!/bin/bash
# AimXHack - Script de injeção no APK do 8 Ball Pool
# Uso: ./inject.sh <caminho_do_xapk>
# Requisitos: Java, wget, unzip

set -e

XAPK="$1"
WORK="/tmp/aimx_inject"
APKTOOL="/tmp/apktool.jar"

if [ -z "$XAPK" ]; then
    echo "Uso: $0 <arquivo.xapk>"
    echo "Exemplo: $0 ~/Downloads/8ballpool.xapk"
    exit 1
fi

echo "========================================="
echo "  AimXHack - Injeção no 8 Ball Pool"
echo "========================================="

# 1. Baixar apktool se necessário
if [ ! -f "$APKTOOL" ]; then
    echo "[1] Baixando apktool..."
    wget -q -O "$APKTOOL" "https://github.com/nicedayzhu/apktool/releases/download/v2.9.3/apktool_2.9.3.jar" 2>/dev/null || \
    wget -q -O "$APKTOOL" "https://bitbucket.org/nicedayzhu/apktool/downloads/apktool_2.9.3.jar" 2>/dev/null || \
    curl -sL -o "$APKTOOL" "https://github.com/nicedayzhu/apktool/releases/download/v2.9.3/apktool_2.9.3.jar" 2>/dev/null

    if [ ! -f "$APKTOOL" ] || [ $(stat -f%z "$APKTOOL" 2>/dev/null || stat -c%s "$APKTOOL" 2>/dev/null) -lt 1000 ]; then
        echo "ERRO: Não foi possível baixar o apktool."
        echo "Baixe manualmente de: https://github.com/nicedayzhu/apktool/releases"
        echo "E salve como: $APKTOOL"
        exit 1
    fi
fi

# 2. Extrair XAPK
echo "[2] Extraindo XAPK..."
rm -rf "$WORK"
mkdir -p "$WORK/xapk"
unzip -o "$XAPK" -d "$WORK/xapk" > /dev/null

MAIN_APK=$(find "$WORK/xapk" -name "*.apk" ! -name "config.*" | head -1)
CONFIG_APK=$(find "$WORK/xapk" -name "config.*.apk" | head -1)

if [ -z "$MAIN_APK" ]; then
    echo "ERRO: APK principal não encontrado"
    exit 1
fi

echo "    APK principal: $MAIN_APK"
echo "    Config APK: $CONFIG_APK"

# 3. Decompilar APK principal
echo "[3] Decompilando APK principal..."
java -jar "$APKTOOL" d "$MAIN_APK" -o "$WORK/pool" -f 2>/dev/null

# 4. Extrair lib nativa do config APK
if [ -n "$CONFIG_APK" ]; then
    echo "[4] Extraindo lib nativa..."
    mkdir -p "$WORK/pool/lib/armeabi-v7a"
    unzip -o "$CONFIG_APK" "lib/armeabi-v7a/*.so" -d "$WORK/pool" 2>/dev/null || true
fi

# 5. Copiar lib do AimXHack
echo "[5] Injetando libaimxhack.so..."
HACK_SO="$WORK/pool/lib/armeabi-v7a/libaimxhack.so"
if [ ! -f "$HACK_SO" ]; then
    echo "AVISO: libaimxhack.so não encontrada. Compile o projeto AimXHack primeiro."
    echo "Ou copie manualmente para: $WORK/pool/lib/armeabi-v7a/"
fi

# 6. Criar smali do LauncherActivity
echo "[6] Criando smali do launcher..."
mkdir -p "$WORK/pool/smali_classes5/com/aimx/hack"

cat > "$WORK/pool/smali_classes5/com/aimx/hack/LauncherActivity.smali" << 'SMALI'
.class public Lcom/aimx/hack/LauncherActivity;
.super Landroid/app/Activity;

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V
    return-void
.end method

.method public onCreate(Landroid/os/Bundle;)V
    .locals 3
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    new-instance v0, Landroid/content/Intent;
    const-class v1, Lcom/aimx/hack/HackService;
    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {p0, v0}, Landroid/content/Context;->startForegroundService(Landroid/content/Intent;)Landroid/content/ComponentName;

    invoke-virtual {p0}, Landroid/app/Activity;->finish()V
    return-void
.end method
SMALI

# 7. Criar smali do HackService (stub - carrega a lib nativa)
cat > "$WORK/pool/smali_classes5/com/aimx/hack/HackService.smali" << 'SMALI'
.class public Lcom/aimx/hack/HackService;
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
SMALI

# 8. Injetar launcher no onCreate do jogo
echo "[7] Injetando launcher no jogo..."
MAIN_ACTIVITY="$WORK/pool/smali_classes3/com/miniclip/eightballpool/EightBallPoolActivity.smali"

if [ -f "$MAIN_ACTIVITY" ]; then
    # Encontrar o método onCreate e injetar antes do return-void
    sed -i '/return-void$/i \
    \n    # AimXHack injection\
    new-instance v0, Landroid/content/Intent;\
    const-class v1, Lcom/aimx/hack/LauncherActivity;\
    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V\
    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V\
    # End AimXHack' "$MAIN_ACTIVITY"
    echo "    OK: Launcher injetado"
else
    echo "    AVISO: EightBallPoolActivity.smali não encontrado"
    echo "    Caminhos alternativos:"
    find "$WORK/pool/smali"* -name "*Activity*.smali" 2>/dev/null | head -5
fi

# 9. Adicionar permissões ao AndroidManifest
echo "[8] Atualizando AndroidManifest..."
MANIFEST="$WORK/pool/AndroidManifest.xml"
if [ -f "$MANIFEST" ]; then
    # Adicionar permissões antes de </manifest>
    sed -i 's|</manifest>|    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>\n</manifest>|' "$MANIFEST"

    # Adicionar Activity e Service antes de </application>
    sed -i 's|</application>|    <activity android:name="com.aimx.hack.LauncherActivity" android:exported="false"/>\n    <service android:name="com.aimx.hack.HackService" android:exported="false" android:foregroundServiceType="specialUse"/>\n</application>|' "$MANIFEST"
    echo "    OK: Manifest atualizado"
fi

# 10. Recompilar
echo "[9] Recompilando APK..."
java -jar "$APKTOOL" b "$WORK/pool" -o "$WORK/8ballpool_modded.apk" 2>/dev/null

# 11. Assinar
echo "[10] Assinando APK..."
KEYSTORE="$WORK/debug.keystore"
keytool -genkey -v -keystore "$KEYSTORE" -alias debug -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Debug" 2>/dev/null

jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$KEYSTORE" -storepass android -keypass android "$WORK/8ballpool_modded.apk" debug 2>/dev/null

# 12. Alinhar
if command -v zipalign &> /dev/null; then
    zipalign -v 4 "$WORK/8ballpool_modded.apk" "$WORK/8ballpool_final.apk" 2>/dev/null
    mv "$WORK/8ballpool_final.apk" "$WORK/8ballpool_modded.apk"
fi

echo ""
echo "========================================="
echo "  CONCLUÍDO!"
echo "========================================="
echo "APK modificado: $WORK/8ballpool_modded.apk"
echo "Tamanho: $(du -h "$WORK/8ballpool_modded.apk" | cut -f1)"
echo ""
echo "Para instalar:"
echo "  adb install $WORK/8ballpool_modded.apk"
echo ""
echo "Ou copie o APK para o celular e instale manualmente."
