.class public Lcom/aimx/hack/HackService;
.super Landroid/app/Service;
.source "HackService.kt"

.field private nativeBridge:Ljava/lang/Object;
.field private predictionView:Ljava/lang/Object;
.field private isRunning:Z

.method public constructor <init>()V
    .locals 1
    invoke-direct {p0}, Landroid/app/Service;-><init>()V
    const/4 v0, 0x0
    iput-boolean v0, p0, Lcom/aimx/hack/HackService;->isRunning:Z
    return-void
.end method

.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;
    .locals 1
    const/4 v0, 0x0
    return-object v0
.end method

.method public onCreate()V
    .locals 4

    invoke-super {p0}, Landroid/app/Service;->onCreate()V

    const-string v0, "aimxhack"
    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V

    const-string v0, "AimXHack"
    const-string v1, "HackService iniciado - lib nativa carregada"
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    const/4 v0, 0x1
    iput-boolean v0, p0, Lcom/aimx/hack/HackService;->isRunning:Z

    invoke-direct {p0}, Lcom/aimx/hack/HackService;->startForegroundNotification()V

    invoke-direct {p0}, Lcom/aimx/hack/HackService;->startPredictionLoop()V

    return-void
.end method

.method private startForegroundNotification()V
    .locals 5

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I
    const/16 v1, 0x1a

    if-lt v0, v1, :cond_0

    new-instance v0, Landroid/app/NotificationChannel;
    const-string v1, "aimxhack"
    const-string v2, "AimX Hack"
    const/4 v3, 0x0
    invoke-direct {v0, v1, v2, v3}, Landroid/app/NotificationChannel;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;I)V

    const-string v1, "notification"
    invoke-virtual {p0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v1
    check-cast v1, Landroid/app/NotificationManager;
    invoke-virtual {v1, v0}, Landroid/app/NotificationManager;->createNotificationChannel(Landroid/app/NotificationChannel;)V

    :cond_0
    new-instance v0, Landroid/app/Notification$Builder;
    invoke-direct {v0, p0}, Landroid/app/Notification$Builder;-><init>(Landroid/content/Context;)V

    const-string v1, "aimxhack"
    invoke-virtual {v0, v1}, Landroid/app/Notification$Builder;->setChannelId(Ljava/lang/String;)Landroid/app/Notification$Builder;

    move-result-object v0
    const-string v1, "AimX Hack Ativo"
    invoke-virtual {v0, v1}, Landroid/app/Notification$Builder;->setContentTitle(Ljava/lang/CharSequence;)Landroid/app/Notification$Builder;

    move-result-object v0
    const-string v1, "Lendo memória do jogo..."
    invoke-virtual {v0, v1}, Landroid/app/Notification$Builder;->setContentText(Ljava/lang/CharSequence;)Landroid/app/Notification$Builder;

    move-result-object v0
    const v1, 0x1080027
    invoke-virtual {v0, v1}, Landroid/app/Notification$Builder;->setSmallIcon(I)Landroid/app/Notification$Builder;

    move-result-object v0
    invoke-virtual {v0}, Landroid/app/Notification$Builder;->build()Landroid/app/Notification;
    move-result-object v0

    const/4 v1, 0x2
    invoke-virtual {p0, v1, v0}, Landroid/app/Service;->startForeground(ILandroid/app/Notification;)V

    return-void
.end method

.method private startPredictionLoop()V
    .locals 4

    new-instance v0, Ljava/lang/Thread;
    new-instance v1, Lcom/aimx/hack/HackService$1;
    invoke-direct {v1, p0}, Lcom/aimx/hack/HackService$1;-><init>(Lcom/aimx/hack/HackService;)V
    invoke-direct {v0, v1}, Ljava/lang/Thread;-><init>(Ljava/lang/Runnable;)V
    invoke-virtual {v0}, Ljava/lang/Thread;->start()V

    return-void
.end method

.method public onDestroy()V
    .locals 1

    const/4 v0, 0x0
    iput-boolean v0, p0, Lcom/aimx/hack/HackService;->isRunning:Z

    invoke-super {p0}, Landroid/app/Service;->onDestroy()V

    return-void
.end method

.method public native nativeInit()Z
.end method

.method public native nativeIsInGame()Z
.end method

.method public native nativeGetShotResult()[F
.end method

.method public native nativeGetBallsCount()I
.end method

.method public native nativeGetBallPositions()[D
.end method

.method public native nativeGetBallClassifications()[I
.end method
