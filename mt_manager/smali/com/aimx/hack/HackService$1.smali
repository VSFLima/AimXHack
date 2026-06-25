.class Lcom/aimx/hack/HackService$1;
.super Ljava/lang/Object;
.source "HackService.kt"

# annotations
.implements Ljava/lang/Runnable;

.field final synthetic this$0:Lcom/aimx/hack/HackService;

.method constructor <init>(Lcom/aimx/hack/HackService;)V
    .locals 0
    iput-object p1, p0, Lcom/aimx/hack/HackService$1;->this$0:Lcom/aimx/hack/HackService;
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public run()V
    .locals 6

    :try_start_0
    iget-object v0, p0, Lcom/aimx/hack/HackService$1;->this$0:Lcom/aimx/hack/HackService;
    invoke-virtual {v0}, Lcom/aimx/hack/HackService;->nativeInit()Z
    move-result v1

    if-nez v1, :cond_0
    const-string v2, "AimXHack"
    const-string v3, "Falha ao inicializar - tentando novamente..."
    invoke-static {v2, v3}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    const-wide/16 v4, 0x3e8
    invoke-static {v4, v5}, Ljava/lang/Thread;->sleep(J)V

    goto :try_start_0

    :cond_0
    const-string v2, "AimXHack"
    const-string v3, "Memória inicializada com sucesso!"
    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :goto_0
    iget-object v0, p0, Lcom/aimx/hack/HackService$1;->this$0:Lcom/aimx/hack/HackService;
    # getter for: Lcom/aimx/hack/HackService;->isRunning:Z
    iget-boolean v0, v0, Lcom/aimx/hack/HackService;->isRunning:Z

    if-nez v0, :cond_1
    return-void

    :cond_1
    iget-object v0, p0, Lcom/aimx/hack/HackService$1;->this$0:Lcom/aimx/hack/HackService;
    invoke-virtual {v0}, Lcom/aimx/hack/HackService;->nativeIsInGame()Z
    move-result v1

    if-eqz v1, :cond_2
    iget-object v0, p0, Lcom/aimx/hack/HackService$1;->this$0:Lcom/aimx/hack/HackService;
    invoke-virtual {v0}, Lcom/aimx/hack/HackService;->nativeGetShotResult()[F
    move-result-object v2

    if-eqz v2, :cond_2
    const-string v3, "AimXHack"
    new-instance v4, Ljava/lang/StringBuilder;
    invoke-direct {v4}, Ljava/lang/StringBuilder;-><init>()V
    const-string v5, "Resultado: "
    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    array-length v5, v2
    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    const-string v5, " floats"
    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v4
    invoke-static {v3, v4}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :cond_2
    const-wide/16 v4, 0x32
    invoke-static {v4, v5}, Ljava/lang/Thread;->sleep(J)V

    goto :goto_0

    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :catch_0
    move-exception v0
    const-string v2, "AimXHack"
    invoke-virtual {v0}, Ljava/lang/Exception;->getMessage()Ljava/lang/String;
    move-result-object v3
    invoke-static {v2, v3, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    return-void
.end method
