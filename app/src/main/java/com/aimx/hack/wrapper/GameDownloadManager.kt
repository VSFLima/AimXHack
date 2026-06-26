package com.aimx.hack.wrapper

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * GameDownloadManager - Gerencia o download do jogo
 * Permite baixar o jogo após instalar o app
 */
class GameDownloadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "GameDownloadManager"
        private const val GAME_PACKAGE = "com.miniclip.eightballpool"
    }
    
    private var downloadId: Long = -1
    private var onDownloadComplete: ((Boolean, String) -> Unit)? = null
    private var onDownloadProgress: ((Int) -> Unit)? = null
    
    /**
     * Verifica se o jogo já está instalado
     */
    fun isGameInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(GAME_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Abre a página do jogo na Google Play Store
     * Método mais confiável - usuário baixa direto da loja oficial
     */
    fun openPlayStore(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$GAME_PACKAGE")
                setPackage("com.android.vending") // Forçar Play Store
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Play Store aberta para $GAME_PACKAGE")
            true
        } catch (e: Exception) {
            // Fallback: abrir no navegador
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=$GAME_PACKAGE")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "Browser aberto para download")
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Erro ao abrir Play Store: ${e2.message}")
                false
            }
        }
    }
    
    /**
     * Baixa o APK de uma URL direta
     * Usar quando tiver um link direto funcionando
     */
    fun downloadFromUrl(url: String, fileName: String = "8ballpool.apk"): Boolean {
        Log.d(TAG, "Iniciando download de: $url")
        
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("8 Ball Pool")
                setDescription("Baixando jogo...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            
            downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download iniciado com ID: $downloadId")
            
            registerDownloadReceiver()
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar download: ${e.message}")
            return false
        }
    }
    
    /**
     * Registra receiver para monitorar o download
     */
    private fun registerDownloadReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    Log.d(TAG, "Download completo: $id")
                    checkDownloadResult()
                    context.unregisterReceiver(this)
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        context.registerReceiver(receiver, filter)
    }
    
    /**
     * Verifica o resultado do download
     */
    private fun checkDownloadResult() {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)
            
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    Log.d(TAG, "Download bem-sucedido")
                    val fileUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                    onDownloadComplete?.invoke(true, fileUri ?: "")
                }
                DownloadManager.STATUS_FAILED -> {
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonIndex)
                    Log.e(TAG, "Download falhou: razão=$reason")
                    onDownloadComplete?.invoke(false, "Download falhou: código $reason")
                }
            }
        }
        cursor.close()
    }
    
    /**
     * Define callback para quando o download completar
     */
    fun setOnDownloadComplete(callback: (Boolean, String) -> Unit) {
        onDownloadComplete = callback
    }
    
    /**
     * Define callback para progresso do download
     */
    fun setOnDownloadProgress(callback: (Int) -> Unit) {
        onDownloadProgress = callback
    }
    
    /**
     * Obtém o arquivo APK do jogo
     */
    fun getGameApkFile(): File? {
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "8ballpool.apk"
        )
        return if (file.exists()) file else null
    }
}
