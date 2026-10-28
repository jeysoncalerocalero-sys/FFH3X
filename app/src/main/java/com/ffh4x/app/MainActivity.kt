package com.ffh4x.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.File

private val VERDE = Color.parseColor("#00FF66")
private val VERDE_SUAVE = Color.parseColor("#7FD8A0")
private val FONDO = Color.parseColor("#0B0F0C")
private val GRIS = Color.parseColor("#AAAAAA")

class MainActivity : AppCompatActivity() {

    private lateinit var tvCarpeta: TextView
    private lateinit var tvResultado: TextView

    private val elegir = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs().edit().putString("carpeta", uri.toString()).apply()
            mostrar("Carpeta guardada")
            refrescar()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(FONDO)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        raiz.addView(texto("FFH4X", 30f, VERDE))
        raiz.addView(texto("inyector propio", 14f, VERDE_SUAVE))

        tvCarpeta = texto("", 13f, Color.WHITE)
        tvResultado = texto("", 13f, VERDE)

        raiz.addView(hueco())
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
        raiz.addView(tvCarpeta)
        raiz.addView(boton("2. Elegir la carpeta del juego") { elegir.launch(null) })

        raiz.addView(hueco())
        raiz.addView(boton("3. Respaldar el original") {
            tarea { FileApplier.backup(this) }
        })
        raiz.addView(boton("Restaurar el original") {
            tarea { FileApplier.restaurar(this) }
        })
        raiz.addView(boton("Diagnostico") {
            AlertDialog.Builder(this)
                .setTitle("Diagnostico")
                .setMessage(FileApplier.diagnostico(this))
                .setPositiveButton("OK", null)
                .show()
        })

        raiz.addView(hueco())
        raiz.addView(tvResultado)

        setContentView(ScrollView(this).apply { addView(raiz) })
    }

    override fun onResume() {
        super.onResume()
        refrescar()
    }

    private fun refrescar() {
        val tiene = prefs().getString("carpeta", null) != null
        val flotante = if (Settings.canDrawOverlays(this)) "CONCEDIDO" else "falta"
        tvCarpeta.text =
            "Menu flotante: $flotante\nCarpeta del juego: " +
            (if (tiene) "ELEGIDA" else "no elegida")
    }

    private fun prefs() = getSharedPreferences("ffh4x", Context.MODE_PRIVATE)

    private fun tarea(accion: () -> String) {
        Toast.makeText(this, "Trabajando...", Toast.LENGTH_SHORT).show()
        Thread {
            val r = runCatching { accion() }.getOrElse { "Error: ${it.message}" }
            runOnUiThread { mostrar(r) }
        }.start()
    }

    private fun mostrar(m: String) {
        tvResultado.text = m
        Toast.makeText(this, m, Toast.LENGTH_LONG).show()
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

    private val CAMINO =
        listOf("contentcache", "Compulsory", "android", "gameassetbundles", "config")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ffh4x", Context.MODE_PRIVATE)

    private fun raizElegida(ctx: Context): DocumentFile? {
        val s = prefs(ctx).getString("carpeta", null) ?: return null
        return DocumentFile.fromTreeUri(ctx, Uri.parse(s))
    }

    fun carpeta(ctx: Context): DocumentFile? {
        val raiz = raizElegida(ctx) ?: return null
        if (raiz.name == "config") return raiz
        var d: DocumentFile = raiz
        for (p in CAMINO) {
            val h = d.findFile(p) ?: continue
            d = h
        }
        return d
    }

    fun backup(ctx: Context): String {
        val carpeta = carpeta(ctx) ?: return "Primero elige la carpeta del juego"
        val destino = File(ctx.filesDir, "backup")
        destino.deleteRecursively()
        destino.mkdirs()
        var n = 0
        for (d in carpeta.listFiles()) {
            val nombre = d.name ?: continue
            if (d.isDirectory) continue
            val f = File(destino, nombre)
            ctx.contentResolver.openInputStream(d.uri)?.use { entrada ->
                f.outputStream().use { salida -> entrada.copyTo(salida) }
            }
            n++
        }
        return "Respaldo listo: $n archivos"
    }

    fun restaurar(ctx: Context): String {
        val carpeta = carpeta(ctx) ?: return "Primero elige la carpeta del juego"
        val origen = File(ctx.filesDir, "backup")
        if (!origen.exists() || origen.listFiles().isNullOrEmpty())
            return "Todavia no hiciste respaldo"
        var n = 0
        for (f in origen.listFiles()!!) {
            val doc = carpeta.findFile(f.name)
                ?: carpeta.createFile("application/octet-stream", f.name)
            doc ?: continue
            ctx.contentResolver.openOutputStream(doc.uri, "wt")?.use { salida ->
                f.inputStream().use { entrada -> entrada.copyTo(salida) }
            }
            n++
        }
        return "Restaurado: $n archivos"
    }

    fun diagnostico(ctx: Context): String {
        val sb = StringBuilder()
        val guardada = prefs(ctx).getString("carpeta", null)
        sb.appendLine("Carpeta elegida: ${if (guardada == null) "NINGUNA" else "SI"}")
        sb.appendLine(guardada ?: "-")
        sb.appendLine()

        val c = carpeta(ctx)
        if (c == null) {
            sb.appendLine("No se pudo abrir la carpeta")
        } else {
            sb.appendLine("Nombre: ${c.name}")
            sb.appendLine("Puede leer: ${c.canRead()}")
            sb.appendLine("Puede escribir: ${c.canWrite()}")
            val hijos = c.listFiles()
            sb.appendLine("Archivos adentro: ${hijos.size}")
            hijos.take(40).forEach { sb.appendLine(" - ${it.name}") }
        }

        val b = File(ctx.filesDir, "backup")
        sb.appendLine()
        sb.appendLine("Respaldo guardado: ${b.listFiles()?.size ?: 0} archivos")
        return sb.toString()
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

        col.addView(btn("Respaldar original") { tarea { FileApplier.backup(this) } })
        col.addView(btn("Restaurar original") { tarea { FileApplier.restaurar(this) } })
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

    private fun tarea(accion: () -> String) {
        aviso("Trabajando...")
        Thread {
            val r = runCatching { accion() }.getOrElse { "Error: ${it.message}" }
            mainExecutor.execute { aviso(r) }
        }.start()
    }

    private fun aviso(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

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
