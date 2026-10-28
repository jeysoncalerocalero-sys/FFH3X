package com.ffh4x.app

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

private val VERDE = Color.parseColor("#00FF66")
private val VERDE_SUAVE = Color.parseColor("#7FD8A0")
private val FONDO = Color.parseColor("#0B0F0C")
private val GRIS = Color.parseColor("#AAAAAA")

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlay: TextView
    private lateinit var tvArchivos: TextView
    private lateinit var tvResultado: TextView
    private lateinit var contenedor: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(FONDO)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        raiz.addView(texto("FFH4X", 30f, VERDE))
        raiz.addView(texto("inyector propio", 14f, VERDE_SUAVE))

        tvOverlay = texto("", 13f, Color.WHITE)
        tvArchivos = texto("", 13f, Color.WHITE)
        tvResultado = texto("", 13f, VERDE)

        raiz.addView(hueco())
        raiz.addView(tvOverlay)
        raiz.addView(boton("1. Dar permiso de menu flotante") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                startService(Intent(this, FloatingMenuService::class.java))
                mostrar("Menu flotante encendido")
            }
        })

        raiz.addView(hueco())
        raiz.addView(tvArchivos)
        raiz.addView(boton("2. Dar acceso a archivos") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()
            ) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                mostrar("Ya tienes acceso a los archivos")
            }
        })

        raiz.addView(hueco())
        raiz.addView(boton("3. Respaldar el original") { mostrar(FileApplier.backup()) })
        raiz.addView(boton("Restaurar el original") { mostrar(FileApplier.restaurar()) })

        raiz.addView(hueco())
        raiz.addView(texto("Perfiles (fase 2)", 13f, VERDE_SUAVE))

        contenedor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(contenedor)
        raiz.addView(hueco())
        raiz.addView(tvResultado)

        setContentView(ScrollView(this).apply { addView(raiz) })
    }

    override fun onResume() {
        super.onResume()
        tvOverlay.text =
            if (Settings.canDrawOverlays(this)) "Menu flotante: CONCEDIDO"
            else "Menu flotante: falta permiso"
        tvArchivos.text =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                Environment.isExternalStorageManager()
            ) "Archivos: CONCEDIDO"
            else "Archivos: falta permiso"
        refrescarPerfiles()
    }

    private fun refrescarPerfiles() {
        contenedor.removeAllViews()
        for (p in FileApplier.perfiles()) {
            contenedor.addView(boton(p) { mostrar(FileApplier.aplicarPerfil(p)) })
        }
    }

    private fun mostrar(m: String) {
        tvResultado.text = m
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun texto(t: String, tam: Float, color: Int) =
        TextView(this).apply {
            text = t
            textSize = tam
            setTextColor(color)
            setPadding(0, dp(8), 0, dp(8))
        }

    private fun boton(t: String, accion: () -> Unit) =
        Button(this).apply {
            text = t
            setOnClickListener { accion() }
        }

    private fun hueco() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(12)
        )
    }
}

object FileApplier {

    private const val RUTA_JUEGO =
        "Android/data/com.dts.freefireth/files/contentcache/Compulsory/android/gameassetbundles/config"

    private const val NUESTRA = "FFH4X"

    private fun externa(): File = Environment.getExternalStorageDirectory()
    private fun dirJuego(): File = File(externa(), RUTA_JUEGO)
    private fun dirNuestra(): File = File(externa(), NUESTRA)
    private fun dirBackup(): File = File(dirNuestra(), "backup_original")
    private fun dirPerfiles(): File = File(dirNuestra(), "perfiles")

    fun perfiles(): List<String> {
        val f = dirPerfiles()
        if (!f.exists()) f.mkdirs()
        val lista = f.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        return if (lista.isEmpty()) listOf("original") else lista
    }

    fun backup(): String {
        val origen = dirJuego()
        if (!origen.exists())
            return "No encontre la carpeta del juego. Abre Free Fire una vez."
        val destino = dirBackup()
        destino.deleteRecursively()
        destino.mkdirs()
        copiar(origen, destino)
        return "Respaldo listo en /FFH4X/backup_original"
    }

    fun restaurar(): String {
        val origen = dirBackup()
        if (!origen.exists()) return "Todavia no hiciste respaldo"
        copiar(origen, dirJuego())
        return "Original restaurado. Reinicia Free Fire."
    }

    fun aplicarPerfil(perfil: String): String {
        val origen = File(dirPerfiles(), perfil)
        if (!origen.exists() || origen.listFiles().isNullOrEmpty())
            return "El perfil '$perfil' esta vacio. Mete archivos en /FFH4X/perfiles/$perfil"
        copiar(origen, dirJuego())
        return "Perfil '$perfil' aplicado. Reinicia Free Fire."
    }

    private fun copiar(origen: File, destino: File) {
        if (!destino.exists()) destino.mkdirs()
        origen.listFiles()?.forEach { f ->
            val d = File(destino, f.name)
            if (f.isDirectory) copiar(f, d) else f.copyTo(d, overwrite = true)
        }
    }
}

class FloatingMenuService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var burbuja: View
    private lateinit var params: WindowManager.LayoutParams
    private var panel: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        burbuja = TextView(this).apply {
            text = "4X"
            textSize = 16f
            setTextColor(VERDE)
            gravity = Gravity.CENTER
            background = circulo()
            setOnTouchListener(Arrastre())
        }

        params = WindowManager.LayoutParams(
            dp(54), dp(54),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 400
        }

        wm.addView(burbuja, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        panel?.let { runCatching { wm.removeView(it) } }
        runCatching { wm.removeView(burbuja) }
    }

    private fun alternarPanel() {
        val abierto = panel
        if (abierto != null) {
            wm.removeView(abierto)
            panel = null
            return
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = caja()
        }

        col.addView(TextView(this).apply {
            text = "FFH4X"
            setTextColor(VERDE)
            textSize = 16f
        })

        col.addView(btn("Respaldar original") { aviso(FileApplier.backup()) })
        col.addView(btn("Restaurar original") { aviso(FileApplier.restaurar()) })

        col.addView(TextView(this).apply {
            text = "Perfiles"
            setTextColor(GRIS)
            textSize = 12f
        })

        for (p in FileApplier.perfiles()) {
            col.addView(btn(p) { aviso(FileApplier.aplicarPerfil(p)) })
        }

        col.addView(btn("Cerrar menu") { stopSelf() })

        val p = WindowManager.LayoutParams(
            dp(240), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 560
        }

        wm.addView(col, p)
        panel = col
    }

    private fun aviso(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun btn(t: String, a: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { a() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun circulo() = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#CC0B0F0C"))
        setStroke(dp(2), VERDE)
    }

    private fun caja() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.parseColor("#EE0B0F0C"))
        setStroke(dp(2), VERDE)
        cornerRadius = dp(12).toFloat()
    }

    private inner class Arrastre : View.OnTouchListener {
        private var x0 = 0
        private var y0 = 0
        private var px = 0f
        private var py = 0f
        private var movido = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = params.x
                    y0 = params.y
                    px = e.rawX
                    py = e.rawY
                    movido = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - px).toInt()
                    val dy = (e.rawY - py).toInt()
                    if (dx != 0 || dy != 0) movido = true
                    params.x = x0 + dx
                    params.y = y0 + dy
                    wm.updateViewLayout(burbuja, params)
                }
                MotionEvent.ACTION_UP -> if (!movido) alternarPanel()
            }
            return true
        }
    }
}
