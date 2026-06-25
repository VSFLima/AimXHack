#!/bin/bash
echo "========================================="
echo "  AimXHack - Setup Termux"
echo "========================================="

# 1. Instalar dependências
echo "[1/8] Instalando dependências..."
pkg update -y 2>/dev/null
pkg install -y openjdk-17 git wget unzip 2>/dev/null

# 2. Clonar repositório
echo "[2/8] Baixando AimXHack..."
cd ~
rm -rf AimXHack
git clone https://github.com/VSFLima/AimXHack.git
cd AimXHack

# 3. Baixar apktool
echo "[3/8] Baixando apktool..."
mkdir -p ~/tools
wget -q -O ~/tools/apktool.jar "https://github.com/nicedayzhu/apktool/releases/download/v2.9.3/apktool_2.9.3.jar" 2>/dev/null
if [ ! -s ~/tools/apktool.jar ]; then
    wget -q -O ~/tools/apktool.jar "https://bitbucket.org/nicedayzhu/apktool/downloads/apktool_2.9.3.jar" 2>/dev/null
fi
cat > ~/tools/apktool << 'WRAPPER'
#!/bin/bash
java -jar ~/tools/apktool.jar "$@"
WRAPPER
chmod +x ~/tools/apktool

# 4. Encontrar XAPK
echo "[4/8] Procurando XAPK do 8 Ball Pool..."
XAPK=$(find /sdcard /storage/emulated/0 ~/storage/shared -name "*8*Ball*Pool*.xapk" -o -name "*8ball*.xapk" -o -name "*eightball*.xapk" 2>/dev/null | head -1)
if [ -z "$XAPK" ]; then
    XAPK=$(find /sdcard /storage/emulated/0 ~/storage/shared ~/Downloads -name "*.xapk" 2>/dev/null | head -1)
fi
if [ -z "$XAPK" ]; then
    echo "ERRO: XAPK não encontrado!"
    echo "Coloque o arquivo .xapk na pasta Downloads e rode novamente."
    exit 1
fi
echo "    Encontrado: $XAPK"

# 5. Extrair
echo "[5/8] Extraindo APK..."
WORK="/data/data/com.termux/files/home/aimx_work"
rm -rf "$WORK"
mkdir -p "$WORK/xapk"
unzip -o "$XAPK" -d "$WORK/xapk" >/dev/null 2>&1

MAIN_APK=$(find "$WORK/xapk" -name "*.apk" ! -name "config.*" | head -1)
CONFIG_APK=$(find "$WORK/xapk" -name "config.*.apk" | head -1)

# 6. Decompilar
echo "[6/8] Decompilando APK..."
~/tools/apktool d "$MAIN_APK" -o "$WORK/pool" -f 2>/dev/null

# Extrair lib nativa
if [ -n "$CONFIG_APK" ]; then
    mkdir -p "$WORK/pool/lib/arm64-v8a"
    mkdir -p "$WORK/pool/lib/armeabi-v7a"
    unzip -o "$CONFIG_APK" "lib/*" -d "$WORK/pool" 2>/dev/null
fi

# 7. Injetar
echo "[7/8] Injetando AimXHack..."

# Copiar smali
mkdir -p "$WORK/pool/smali_classes5/com/aimx/hack"
cp ~/AimXHack/mt_manager/smali/com/aimx/hack/*.smali "$WORK/pool/smali_classes5/com/aimx/hack/"

# Copiar lib nativa
cp ~/AimXHack/mt_manager/lib/arm64-v8a/libaimxhack.so "$WORK/pool/lib/arm64-v8a/" 2>/dev/null
cp ~/AimXHack/mt_manager/lib/armeabi-v7a/libaimxhack.so "$WORK/pool/lib/armeabi-v7a/" 2>/dev/null

# Modificar AndroidManifest.xml
MANIFEST="$WORK/pool/AndroidManifest.xml"
if [ -f "$MANIFEST" ]; then
    # Adicionar componentes antes de </application>
    sed -i 's|</application>|    <activity android:name="com.aimx.hack.LauncherActivity" android:exported="false"/>\n    <service android:name="com.aimx.hack.HackService" android:exported="false"/>\n</application>|' "$MANIFEST"
    # Adicionar permissões antes de </manifest>
    sed -i 's|</manifest>|    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>\n</manifest>|' "$MANIFEST"
    echo "    Manifest atualizado"
fi

# Injetar launcher no main activity
MAIN_SMALI=$(find "$WORK/pool" -name "EightBallPoolActivity.smali" | head -1)
if [ -n "$MAIN_SMALI" ]; then
    sed -i '/return-void$/i \
    # AimXHack injection\
    new-instance v0, Landroid/content/Intent;\
    const-class v1, Lcom/aimx/hack/LauncherActivity;\
    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V\
    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V' "$MAIN_SMALI"
    echo "    Launcher injetado"
fi

# 8. Recompilar e assinar
echo "[8/8] Recompilando e assinando..."
~/tools/apktool b "$WORK/pool" -o "$WORK/8ballpool_hacked.apk" 2>/dev/null

# Gerar keystore e assinar
keytool -genkey -v -keystore "$WORK/debug.keystore" -alias debug -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Debug" 2>/dev/null
jarsigner -sigalg SHA256withRSA -digestalg SHA-256 -keystore "$WORK/debug.keystore" -storepass android "$WORK/8ballpool_hacked.apk" debug 2>/dev/null

# Copiar para Downloads
cp "$WORK/8ballpool_hacked.apk" /sdcard/Download/ 2>/dev/null
cp "$WORK/8ballpool_hacked.apk" ~/storage/shared/Download/ 2>/dev/null

echo ""
echo "========================================="
echo "  CONCLUÍDO!"
echo "========================================="
echo "APK modificado: $WORK/8ballpool_hacked.apk"
echo "Também copiado para: Downloads/8ballpool_hacked.apk"
echo ""
echo "Para instalar:"
echo "  Abra o arquivo 8ballpool_hacked.apk nos Downloads"
echo ""
echo "NOTA: Desinstale o 8 Ball Pool original primeiro!"
