# AimXHack - Instruções para MT Manager

## O que você precisa:
1. MT Manager instalado no celular
2. 8 Ball Pool instalado (da Play Store)
3. Os arquivos do AimXHack (deste repositório)

## Passo a passo:

### 1. Preparar os arquivos
Copie os arquivos do repositório para o celular:
- `smali/LauncherActivity.smali`
- `smali/HackService.smali`
- `lib/armeabi-v7a/libaimxhack.so` (do build do GitHub Actions)

### 2. Abrir o APK no MT Manager
1. Abra o MT Manager
2. Navegue até `/data/app/com.miniclip.eightballpool*/base.apk`
3. Toque no APK → "Visualizar"

### 3. Modificar AndroidManifest.xml
1. Abra `AndroidManifest.xml` no editor
2. Adicione antes de `</application>`:
```xml
<activity android:name="com.aimx.hack.LauncherActivity" android:exported="false"/>
<service android:name="com.aimx.hack.HackService" android:exported="false"/>
```

### 4. Adicionar arquivos smali
1. Navegue até a pasta `smali_classes3` (ou `smali_classes4`, etc.)
2. Crie a pasta `com/aimx/hack/`
3. Copie `LauncherActivity.smali` para lá
4. Copie `HackService.smali` para lá

### 5. Adicionar lib nativa
1. Navegue até `lib/armeabi-v7a/`
2. Copie `libaimxhack.so` para lá

### 6. Injetar launcher no jogo
1. Abra `smali_classes3/com/miniclip/eightballpool/EightBallPoolActivity.smali`
2. Procure pelo método `onCreate`
3. Antes do `return-void` do onCreate, adicione:
```smali
    # AimXHack injection
    new-instance v0, Landroid/content/Intent;
    const-class v1, Lcom/aimx/hack/LauncherActivity;
    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V
    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V
```

### 7. Salvar e instalar
1. Salve todas as modificações
2. O MT Manager vai recompilar o APK
3. Desinstale o 8 Ball Pool original
4. Instale o APK modificado

## Notas:
- O APK modificado NÃO vai receber atualizações da Play Store
- Se o jogo atualizar, você precisa refazer o processo
- O hack pode ser detectado pelo anti-cheat do jogo
