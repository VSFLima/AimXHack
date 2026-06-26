package com.aimx.hack

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.aimx.hack.service.HackService
import com.aimx.hack.wrapper.GameDownloadManager
import com.aimx.hack.wrapper.VirtualAppWrapper

/**
 * MainActivity - Tela principal do hack
 * Gerencia permissões, download do jogo e controle do hack
 */
class MainActivity : Activity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val OVERLAY_PERMISSION_REQUEST = 1001
        private const val STORAGE_PERMISSION_REQUEST = 1002
    }
    
    private lateinit var virtualApp: VirtualAppWrapper
    private lateinit var downloadManager: GameDownloadManager
    
    // Views
    private lateinit var statusText: TextView
    private lateinit var gameStatusText: TextView
    private lateinit var startButton: Button
    private lateinit var downloadButton: Button
    private lateinit var aimSwitch: Switch
    private lateinit var espSwitch: Switch
    private lateinit var autoPlaySwitch: Switch
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        
        virtualApp = VirtualAppWrapper(this)
        downloadManager = GameDownloadManager(this)
        
        setupUI()
        checkPermissions()
        updateGameStatus()
    }
    
    /**
     * Configura a interface do usuário
     */
    private fun setupUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Título
        val titleText = TextView(this).apply {
            text = "AimX Hack"
            textSize = 24f
            setTextColor(0xFF00FF00.toInt())
        }
        layout.addView(titleText)
        
        // Status
        statusText = TextView(this).apply {
            text = "Status: Verificando..."
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }
        layout.addView(statusText)
        
        // Status do jogo
        gameStatusText = TextView(this).apply {
            text = "Jogo: Verificando..."
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(gameStatusText)
        
        // Botão de download
        downloadButton = Button(this).apply {
            text = "BAIXAR 8 BALL POOL"
            setOnClickListener { downloadGame() }
        }
        layout.addView(downloadButton)
        
        // Botão de iniciar
        startButton = Button(this).apply {
            text = "INICIAR HACK"
            setOnClickListener { startHack() }
            isEnabled = false
        }
        layout.addView(startButton)
        
        // Switches para funções
        aimSwitch = Switch(this).apply {
            text = "AIM (Mira automática)"
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                HackService.isRunning.let {
                    // Toggle aim no serviço
                }
            }
        }
        layout.addView(aimSwitch)
        
        espSwitch = Switch(this).apply {
            text = "ESP (Informações na tela)"
            isChecked = true
        }
        layout.addView(espSwitch)
        
        autoPlaySwitch = Switch(this).apply {
            text = "AUTO-PLAY (Jogar automaticamente)"
            isChecked = false
        }
        layout.addView(autoPlaySwitch)
        
        setContentView(layout)
    }
    
    /**
     * Verifica permissões necessárias
     */
    private fun checkPermissions() {
        // Verificar permissão de overlay
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        }
        
        // Verificar permissão de storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST
                )
            }
        }
        
        statusText.text = "Status: Permissões verificadas"
    }
    
    /**
     * Atualiza o status do jogo
     */
    private fun updateGameStatus() {
        val isInstalled = virtualApp.isGameInstalled()
        gameStatusText.text = if (isInstalled) {
            "Jogo: INSTALADO ✅"
        } else {
            "Jogo: NÃO INSTALADO ❌"
        }
        
        startButton.isEnabled = isInstalled
        downloadButton.isEnabled = !isInstalled
    }
    
    /**
     * Inicia o download do jogo
     */
    private fun downloadGame() {
        Log.d(TAG, "Iniciando download do jogo...")
        
        downloadButton.text = "BAIXANDO..."
        downloadButton.isEnabled = false
        
        downloadManager.setOnDownloadComplete { success, message ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Download completo!", Toast.LENGTH_SHORT).show()
                    updateGameStatus()
                } else {
                    Toast.makeText(this, "Erro: $message", Toast.LENGTH_SHORT).show()
                }
                downloadButton.text = "BAIXAR 8 BALL POOL"
                downloadButton.isEnabled = true
            }
        }
        
        // Baixar versão v5.8.0 (mais estável para hack)
        if (!downloadManager.downloadGame("v5.8.0")) {
            Toast.makeText(this, "Erro ao iniciar download", Toast.LENGTH_SHORT).show()
            downloadButton.text = "BAIXAR 8 BALL POOL"
            downloadButton.isEnabled = true
        }
    }
    
    /**
     * Inicia o hack
     */
    private fun startHack() {
        Log.d(TAG, "Iniciando hack...")
        
        // Verificar se o jogo está instalado
        if (!virtualApp.isGameInstalled()) {
            Toast.makeText(this, "Instale o 8 Ball Pool primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Verificar permissão de overlay
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permissão de overlay necessária", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Iniciar serviço do hack
        val serviceIntent = Intent(this, HackService::class.java)
        startForegroundService(serviceIntent)
        
        // Iniciar jogo dentro do VirtualApp
        if (virtualApp.startGame()) {
            statusText.text = "Status: HACK ATIVO ✅"
            startButton.text = "PARAR HACK"
            Toast.makeText(this, "Hack iniciado!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Erro ao iniciar jogo", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Para o hack
     */
    private fun stopHack() {
        Log.d(TAG, "Parando hack...")
        
        val serviceIntent = Intent(this, HackService::class.java)
        stopService(serviceIntent)
        
        virtualApp.stopGame()
        
        statusText.text = "Status: HACK PARADO"
        startButton.text = "INICIAR HACK"
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST -> {
                if (Settings.canDrawOverlays(this)) {
                    statusText.text = "Status: Permissão de overlay concedida"
                } else {
                    statusText.text = "Status: Permissão de overlay negada"
                    Toast.makeText(this, "Permissão de overlay é necessária", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    statusText.text = "Status: Permissão de storage concedida"
                } else {
                    statusText.text = "Status: Permissão de storage negada"
                }
            }
        }
    }
}
