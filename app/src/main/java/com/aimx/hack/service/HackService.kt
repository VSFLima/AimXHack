package com.aimx.hack.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.aimx.hack.wrapper.MemoryReader
import com.aimx.hack.wrapper.VirtualAppWrapper

/**
 * HackService - Serviço principal do hack
 * Gerencia o overlay, leitura de memória e funções do hack
 */
class HackService : Service() {
    
    companion object {
        private const val TAG = "HackService"
        private const val CHANNEL_ID = "aimx_hack_channel"
        private const val NOTIFICATION_ID = 1001
        
        // Estado do hack
        var isRunning = false
            private set
    }
    
    private lateinit var windowManager: WindowManager
    private lateinit var virtualApp: VirtualAppWrapper
    private lateinit var memoryReader: MemoryReader
    private val handler = Handler(Looper.getMainLooper())
    
    // Views do overlay
    private var overlayView: LinearLayout? = null
    private var statusText: TextView? = null
    private var ballInfoText: TextView? = null
    
    // Estado do hack
    private var isOverlayVisible = true
    private var aimEnabled = true
    private var espEnabled = true
    private var autoPlayEnabled = false
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HackService criado")
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        virtualApp = VirtualAppWrapper(this)
        memoryReader = MemoryReader()
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        initializeHack()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "HackService iniciado")
        isRunning = true
        
        // Iniciar jogo se não estiver rodando
        if (!virtualApp.isRunning()) {
            virtualApp.startGame()
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.d(TAG, "HackService destruído")
        isRunning = false
        
        virtualApp.stopGame()
        removeOverlay()
        
        super.onDestroy()
    }
    
    /**
     * Inicializa o hack
     */
    private fun initializeHack() {
        Log.d(TAG, "Inicializando hack...")
        
        // Inicializar leitor de memória
        if (!memoryReader.initialize()) {
            Log.e(TAG, "Falha ao inicializar MemoryReader")
            return
        }
        
        // Configurar callbacks do VirtualApp
        virtualApp.onGameStarted = {
            Log.d(TAG, "Jogo iniciado")
            handler.post { updateOverlay("Jogo iniciado") }
        }
        
        virtualApp.onGameStopped = {
            Log.d(TAG, "Jogo parado")
            handler.post { updateOverlay("Jogo parado") }
        }
        
        virtualApp.onMemoryRead = { address, value ->
            Log.v(TAG, "Memória lida: 0x${address.toString(16)} = $value")
            handler.post { processMemoryRead(address, value) }
        }
        
        // Criar overlay
        createOverlay()
        
        // Iniciar loop de atualização
        startUpdateLoop()
        
        Log.d(TAG, "Hack inicializado com sucesso")
    }
    
    /**
     * Cria o canal de notificação
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AimX Hack",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Serviço do AimX Hack"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Cria a notificação
     */
    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AimX Hack Ativo")
            .setContentText("Jogando 8 Ball Pool com hack")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * Cria o overlay na tela
     */
    private fun createOverlay() {
        Log.d(TAG, "Criando overlay...")
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 10
            y = 10
        }
        
        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x80000000.toInt()) // Semi-preto
            setPadding(16, 16, 16, 16)
        }
        
        statusText = TextView(this).apply {
            text = "AimX Hack - Iniciando..."
            setTextColor(0xFF00FF00.toInt()) // Verde
            textSize = 14f
        }
        
        ballInfoText = TextView(this).apply {
            text = "Bolas: --"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
        }
        
        overlayView?.addView(statusText)
        overlayView?.addView(ballInfoText)
        
        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay criado com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar overlay: ${e.message}")
        }
    }
    
    /**
     * Remove o overlay
     */
    private fun removeOverlay() {
        try {
            overlayView?.let { windowManager.removeView(it) }
            overlayView = null
            Log.d(TAG, "Overlay removido")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover overlay: ${e.message}")
        }
    }
    
    /**
     * Atualiza o texto do overlay
     */
    private fun updateOverlay(status: String) {
        statusText?.text = "AimX Hack - $status"
    }
    
    /**
     * Inicia o loop de atualização
     */
    private fun startUpdateLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return
                
                updateHackState()
                handler.postDelayed(this, 16) // ~60fps
            }
        })
    }
    
    /**
     * Atualiza o estado do hack
     */
    private fun updateHackState() {
        if (!virtualApp.isRunning()) return
        
        try {
            // Ler informações do jogo
            val isPlayerTurn = memoryReader.isPlayerTurn()
            val aimAngle = memoryReader.readAimAngle()
            val power = memoryReader.readPower()
            
            // Atualizar overlay
            val status = buildString {
                append(if (isPlayerTurn) "SUA VEZ" else "VEZ OPONENTE")
                append("\nÂngulo: ${String.format("%.1f", Math.toDegrees(aimAngle.toDouble()))}°")
                append("\nForça: ${String.format("%.0f", power * 100)}%")
            }
            updateOverlay(status)
            
            // Ler posições das bolas
            val ballPositions = mutableListOf<String>()
            for (i in 0 until 16) {
                val (x, y) = memoryReader.readBallPosition(i)
                val classification = memoryReader.readBallClassification(i)
                if (x != 0f || y != 0f) {
                    ballPositions.add("Bola $i: (${"%.1f".format(x)}, ${"%.1f".format(y)}) - Tipo: $classification")
                }
            }
            
            ballInfoText?.text = "Bolas: ${ballPositions.size}"
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar estado: ${e.message}")
        }
    }
    
    /**
     * Processa leitura de memória
     */
    private fun processMemoryRead(address: Long, value: Any) {
        // Aqui processaríamos os dados lidos da memória
        // para atualizar o overlay e as funções do hack
        
        when {
            // Posição da bola
            address in 0x28..0x30 -> {
                Log.d(TAG, "Posição da bola detectada: $value")
            }
            // Ângulo de mira
            address == 0x18L -> {
                Log.d(TAG, "Ângulo de mira: $value")
            }
            // Força
            address == 0x280L -> {
                Log.d(TAG, "Força: $value")
            }
        }
    }
    
    /**
     * Alterna visibilidade do overlay
     */
    fun toggleOverlay() {
        isOverlayVisible = !isOverlayVisible
        overlayView?.visibility = if (isOverlayVisible) android.view.View.VISIBLE else android.view.View.GONE
        Log.d(TAG, "Overlay visível: $isOverlayVisible")
    }
    
    /**
     * Alterna função AIM
     */
    fun toggleAim() {
        aimEnabled = !aimEnabled
        Log.d(TAG, "AIM: $aimEnabled")
    }
    
    /**
     * Alterna função ESP
     */
    fun toggleESP() {
        espEnabled = !espEnabled
        Log.d(TAG, "ESP: $espEnabled")
    }
    
    /**
     * Alterna Auto-Play
     */
    fun toggleAutoPlay() {
        autoPlayEnabled = !autoPlayEnabled
        Log.d(TAG, "Auto-Play: $autoPlayEnabled")
    }
}
