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
        
        // URLs para download do jogo (v5.8.0 - versão estável)
        // NOTA: Estas URLs podem mudar
        const val GAME_URL_V580 = "https://github.com/OiwexO/PoolPredictor/releases/download/v5.8.0/8ballpool_v5.8.0.apk"
        const val GAME_URL_LATEST = "https://github.com/OiwexO/PoolPredictor/releases/download/latest/8ballpool_latest.apk"
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
     * Inicia o download do jogo
     * @param version Versão do jogo para baixar ("v5.8.0" ou "latest")
     */
    fun downloadGame(version: String = "v5.8.0"): Boolean {
        Log.d(TAG, "Iniciando download do jogo versão: $version")
        
        val url = when (version) {
            "v5.8.0" -> GAME_URL_V580
            "latest" -> GAME_URL_LATEST
            else -> {
                Log.e(TAG, "Versão desconhecida: $version")
                return false
            }
        }
        
        return startDownload(url)
    }
    
    /**
     * Inicia o download usando DownloadManager
     */
    private fun startDownload(url: String): Boolean {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("8 Ball Pool")
                setDescription("Baixando jogo...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    "8ballpool.apk"
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            
            downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download iniciado com ID: $downloadId")
            
            // Registrar receiver para monitorar download
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
     * Verifica se o download está em andamento
     */
    fun isDownloading(): Boolean {
        if (downloadId == -1L) return false
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        var isDownloading = false
        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)
            isDownloading = status == DownloadManager.STATUS_RUNNING || 
                           status == DownloadManager.STATUS_PAUSED ||
                           status == DownloadManager.STATUS_PENDING
        }
        cursor.close()
        
        return isDownloading
    }
    
    /**
     * Obtém o progresso do download (0-100)
     */
    fun getDownloadProgress(): Int {
        if (downloadId == -1L) return 0
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        var progress = 0
        if (cursor.moveToFirst()) {
            val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            
            val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
            val bytesTotal = cursor.getLong(bytesTotalIndex)
            
            if (bytesTotal > 0) {
                progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
            }
        }
        cursor.close()
        
        return progress
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
     * Cancela o download em andamento
     */
    fun cancelDownload() {
        if (downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
            downloadId = -1
            Log.d(TAG, "Download cancelado")
        }
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
