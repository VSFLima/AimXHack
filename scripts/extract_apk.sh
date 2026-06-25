#!/bin/bash
# Extrai APK armeabi-v7a ou arm64-v8a de um XAPK
# Uso: ./extract_apk.sh <arquivo.xapk> [arquitetura]
# Exemplo: ./extract_apk.sh 8ball.xapk arm64-v8a

XAPK="$1"
ARCH="${2:-arm64-v8a}"

if [ -z "$XAPK" ]; then
    echo "Uso: $0 <arquivo.xapk> [armeabi-v7a|arm64-v8a]"
    exit 1
fi

WORK_DIR="/tmp/8ball_extract"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

echo "[1] Extraindo XAPK..."
unzip -o "$XAPK" -d "$WORK_DIR"

echo "[2] Procurando APK para $ARCH..."
APK_FILE=$(find "$WORK_DIR" -name "*.apk" | head -1)

if [ -z "$APK_FILE" ]; then
    echo "ERRO: Nenhum APK encontrado no XAPK"
    ls -la "$WORK_DIR"
    exit 1
fi

echo "[3] APK encontrado: $APK_FILE"
echo "[4] Verificando arquiteturas..."

# Verificar se tem a lib nativa para a arquitetura desejada
if [ -d "$WORK_DIR/lib/$ARCH" ]; then
    echo "OK: Encontrada lib para $ARCH"
    ls "$WORK_DIR/lib/$ARCH/"
else
    echo "AVISO: Lib para $ARCH não encontrada. Arquiteturas disponíveis:"
    ls "$WORK_DIR/lib/" 2>/dev/null || echo "Nenhuma lib encontrada"
fi

OUTPUT="$HOME/Downloads/8ballpool_${ARCH}.apk"
cp "$APK_FILE" "$OUTPUT"
echo "[5] APK copiado para: $OUTPUT"
echo "[6] Tamanho: $(du -h "$OUTPUT" | cut -f1)"
