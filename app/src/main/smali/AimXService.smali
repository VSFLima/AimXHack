.class public Lcom/aimx/hack/AimXService;
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
    .locals 3
    invoke-super {p0}, Landroid/app/Service;->onCreate()V

    # Start foreground with notification
    const/4 v0, 0x2
    new-instance v1, Landroid/app/Notification$Builder;
    const-string v2, "aimx"
    invoke-direct {v1, p0, v2}, Landroid/app/Notification$Builder;-><init>(Landroid/content/Context;Ljava/lang/String;)V
    const-string v2, "AimX Ativo"
    invoke-virtual {v1, v2}, Landroid/app/Notification$Builder;->setContentTitle(Ljava/lang/CharSequence;)Landroid/app/Notification$Builder;
    move-result-object v1
    invoke-virtual {v1}, Landroid/app/Notification$Builder;->build()Landroid/app/Notification;
    move-result-object v1
    invoke-virtual {p0, v0, v1}, Landroid/app/Service;->startForeground(ILandroid/app/Notification;)V

    # Log
    const-string v0, "AimXHack"
    const-string v1, "Service started"
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    return-void
.end method

.method public onDestroy()V
    .locals 0
    invoke-super {p0}, Landroid/app/Service;->onDestroy()V
    return-void
.end method
