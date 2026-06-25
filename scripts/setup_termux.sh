#!/bin/bash
# AimXHack - Setup no Termux (Android)
# Instala todas as dependências e faz a injeção

echo "========================================="
echo "  AimXHack - Setup Termux"
echo "========================================="

# 1. Instalar dependências
echo "[1] Instalando dependências..."
pkg update -y
pkg install -y java-openjdk wget unzip

# 2. Baixar apktool
echo "[2] Baixando apktool..."
mkdir -p ~/tools
APKTOOL_URL="https://github.com/nicedayzhu/apktool/releases/download/v2.9.3/apktool_2.9.3.jar"
wget -q -O ~/tools/apktool.jar "$APKTOOL_URL" || \
curl -sL -o ~/tools/apktool.jar "$APKTOOL_URL"

# Criar wrapper
cat > ~/tools/apktool << 'EOF'
#!/bin/bash
java -jar ~/tools/apktool.jar "$@"
EOF
chmod +x ~/tools/apktool

# 3. Baixar XAPK do 8 Ball Pool
echo "[3] Baixe o XAPK do 8 Ball Pool e coloque em ~/Downloads/"
echo "    Link: https://apkpure.com/8-ball-pool-android-1/com.miniclip.eightballpool/download"
echo ""
read -p "Pressione Enter quando o XAPK estiver em ~/Downloads/..."

XAPK=$(find ~/Downloads -name "*.xapk" | head -1)
if [ -z "$XAPK" ]; then
    echo "ERRO: Nenhum XAPK encontrado em ~/Downloads/"
    exit 1
fi

# 4. Extrair
echo "[4] Extraindo..."
WORK="/data/data/com.termux/files/home/aimx_work"
rm -rf "$WORK"
mkdir -p "$WORK/xapk"
unzip -o "$XAPK" -d "$WORK/xapk"

MAIN_APK=$(find "$WORK/xapk" -name "*.apk" ! -name "config.*" | head -1)
CONFIG_APK=$(find "$WORK/xapk" -name "config.*.apk" | head -1)

# 5. Decompilar
echo "[5] Decompilando..."
~/tools/apktool d "$MAIN_APK" -o "$WORK/pool" -f

# 6. Extrair lib
if [ -n "$CONFIG_APK" ]; then
    echo "[6] Extraindo lib nativa..."
    mkdir -p "$WORK/pool/lib/armeabi-v7a"
    unzip -o "$CONFIG_APK" "lib/*" -d "$WORK/pool"
fi

# 7. Baixar AimXHack
echo "[7] Baixando AimXHack..."
git clone https://github.com/VSFLima/AimXHack.git "$WORK/hack"

# 8. Compilar AimXHack (se possível)
echo "[8] Compilando AimXHack..."
cd "$WORK/hack"
if [ -f "gradlew" ]; then
    chmod +x gradlew
    ./gradlew assembleDebug 2>&1 | tail -5
    HACK_APK=$(find . -name "*.apk" -path "*/debug/*" | head -1)
    if [ -n "$HACK_APK" ]; then
        # Extrair smali e .so do nosso APK
        mkdir -p "$WORK/hack_extracted"
        unzip -o "$HACK_APK" -d "$WORK/hack_extracted"
        # Copiar .so
        cp "$WORK/hack_extracted/lib/armeabi-v7a/libaimxhack.so" "$WORK/pool/lib/armeabi-v7a/"
        # Decompilar nosso APK para pegar smali
        ~/tools/apktool d "$HACK_APK" -o "$WORK/hack_smali" -f
        cp -r "$WORK/hack_smali/smali/com" "$WORK/pool/smali_classes5/"
    fi
fi

# 9. Injetar launcher
echo "[9] Injetando no jogo..."
MAIN_SMALI=$(find "$WORK/pool" -path "*/miniclip/eightballpool/EightBallPoolActivity.smali" | head -1)
if [ -n "$MAIN_SMALI" ]; then
    # Adicionar chamada para nosso launcher no onCreate
    sed -i '/return-void$/i \
    # AimXHack injection\
    new-instance v0, Landroid/content/Intent;\
    const-class v1, Lcom/aimx/hack/LauncherActivity;\
    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V\
    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V' "$MAIN_SMALI"
    echo "    OK"
fi

# 10. Modificar manifest
echo "[10] Modificando AndroidManifest..."
MANIFEST="$WORK/pool/AndroidManifest.xml"
# Adicionar componentes do hack
sed -i 's|</application>|    <activity android:name="com.aimx.hack.LauncherActivity" android:exported="false"/>\n    <service android:name="com.aimx.hack.HackService" android:exported="false"/>\n</application>|' "$MANIFEST"
sed -i 's|</manifest>|    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>\n</manifest>|' "$MANIFEST"

# 11. Recompilar
echo "[11] Recompilando APK..."
~/tools/apktool b "$WORK/pool" -o "$WORK/8ballpool_hacked.apk"

# 12. Assinar
echo "[12] Assinando..."
keytool -genkey -v -keystore "$WORK/debug.keystore" -alias debug -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Debug" 2>/dev/null
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$WORK/debug.keystore" -storepass android "$WORK/8ballpool_hacked.apk" debug

echo ""
echo "========================================="
echo "  CONCLUÍDO!"
echo "========================================="
echo "APK: $WORK/8ballpool_hacked.apk"
echo ""
echo "Para instalar:"
echo "  pm install $WORK/8ballpool_hacked.apk"
echo ""
echo "Ou use: termux-open $WORK/8ballpool_hacked.apk"
