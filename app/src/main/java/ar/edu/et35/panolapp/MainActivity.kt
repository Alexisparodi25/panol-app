package ar.edu.et35.panolapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.integration.android.IntentIntegrator

class MainActivity : Activity() {

    private lateinit var db: Db
    private lateinit var root: LinearLayout
    private var scanMode = MODE_FICHA
    private var atHome = true

    companion object {
        const val MODE_FICHA = "ficha"
        const val MODE_ALTA = "alta"
        val VERDE = Color.parseColor("#2E7D32")
        val ROJO = Color.parseColor("#C62828")
        val AZUL = Color.parseColor("#1565C0")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        instalarRegistroDeErrores()
        val crash = java.io.File(filesDir, "crash.txt")
        val migas = java.io.File(filesDir, "escaneo.txt")
        if (crash.exists()) mostrarCrash(crash)
        else if (migas.exists()) {
            migas.delete()
            screen("Se cerró al escanear")
            banner("La app se cerró al abrir la cámara y no quedó ningún error registrado.", ROJO)
            text("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", 13f)
            text("Sacale una captura a esta pantalla y mandala.", 13f, Color.DKGRAY)
            button("Continuar") { home() }
        } else home()
    }

    /** Guarda el error si la app se cierra y lo muestra al volver a abrirla. */
    private fun instalarRegistroDeErrores() {
        val previo = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            try {
                java.io.File(filesDir, "crash.txt").writeText(android.util.Log.getStackTraceString(ex))
            } catch (_: Exception) {}
            previo?.uncaughtException(th, ex)
        }
    }

    private fun mostrarCrash(f: java.io.File) {
        screen("La app se cerró por un error")
        text("Sacale una captura a esta pantalla y mandala:", 14f, Color.DKGRAY)
        text(f.readText().take(2500), 11f, ROJO).setTextIsSelectable(true)
        button("Entendido, continuar") { f.delete(); home() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (atHome) super.onBackPressed() else home()
    }

    // ===================== Helpers de UI =====================
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun screen(title: String, home: Boolean = false) {
        atHome = home
        root.removeAllViews()
        root.addView(TextView(this).apply {
            text = title; textSize = 22f; setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(12))
        })
    }

    private fun text(s: String, size: Float = 16f, color: Int = Color.BLACK, bold: Boolean = false): TextView {
        val tv = TextView(this).apply {
            text = s; textSize = size; setTextColor(color); setPadding(0, dp(4), 0, dp(4))
            if (bold) setTypeface(null, Typeface.BOLD)
        }
        root.addView(tv)
        return tv
    }

    private fun banner(s: String, color: Int) {
        root.addView(TextView(this).apply {
            text = s; textSize = 16f; setTextColor(Color.WHITE); setBackgroundColor(color)
            setPadding(dp(12), dp(12), dp(12), dp(12)); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun button(label: String, color: Int = AZUL, onClick: () -> Unit): Button {
        val b = Button(this).apply {
            text = label; setTextColor(Color.WHITE); setBackgroundColor(color); setOnClickListener { onClick() }
        }
        root.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return b
    }

    private fun edit(hint: String, number: Boolean = false): EditText {
        val e = EditText(this).apply {
            this.hint = hint
            if (number) inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(e)
        return e
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    /** Ejecuta una acción de negocio; las violaciones de regla se muestran como alerta visual roja. */
    private fun guarded(onOk: () -> Unit, action: () -> Unit) {
        try {
            action(); onOk()
        } catch (e: RuleException) {
            AlertDialog.Builder(this).setTitle("⚠ Operación no permitida")
                .setMessage(e.message).setPositiveButton("Entendido", null).show()
        }
    }

    private fun ficha(t: Tablet, titulo: String? = null) {
        if (titulo != null) text(titulo, 14f, Color.DKGRAY)
        text("Nº de serie", 12f, Color.GRAY); text(t.serie, 20f, bold = true)
        text("Procesador", 12f, Color.GRAY); text(t.cpu.ifBlank { "-" })
        text("RAM", 12f, Color.GRAY); text(t.ram.ifBlank { "-" })
        text("Almacenamiento", 12f, Color.GRAY); text(t.almacenamiento.ifBlank { "-" })
        text("Pantalla", 12f, Color.GRAY); text(t.pantalla.ifBlank { "-" })
        text("Puertos", 12f, Color.GRAY); text(t.puertos.ifBlank { "-" })
        text("Conectividad", 12f, Color.GRAY); text(t.conectividad.ifBlank { "-" })
    }

    // ===================== Pantallas =====================
    private fun home() {
        screen("Pañol-App", home = true)
        text("Trazabilidad de tablets corporativas · ET N° 35 · v1.2", 14f, Color.DKGRAY)
        button("📷  Escanear QR de tablet") { scan(MODE_FICHA) }
        button("➕  Alta de tablet (escanear QR)") { scan(MODE_ALTA) }
        button("⌨  Ingreso manual (QR dañado)", Color.DKGRAY) { manual() }
        button("📋  Inventario", Color.DKGRAY) { inventario() }
        button("👤  Empleados y tareas", Color.DKGRAY) { empleados() }
    }

    private fun scan(mode: String) {
        scanMode = mode
        try { java.io.File(filesDir, "escaneo.txt").writeText("iniciado") } catch (_: Exception) {}
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 77)
            return
        }
        IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Apuntá al código QR de la tablet")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .initiateScan()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 77) {
            if (res.isNotEmpty() && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) scan(scanMode)
            else toast("Sin permiso de cámara no se puede escanear. Usá el ingreso manual.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        java.io.File(filesDir, "escaneo.txt").delete()
        val res = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (res == null) { super.onActivityResult(requestCode, resultCode, data); return }
        val raw = res.contents
        if (raw == null) { toast("Escaneo cancelado"); return }
        val parsed = QrParser.parse(raw)
        if (parsed == null) { qrInvalido(raw); return }
        if (scanMode == MODE_ALTA) altaPreview(parsed)
        else {
            val existente = db.getTablet(parsed.serie)
            if (existente != null) fichaScreen(existente) else noRegistrada(parsed)
        }
    }

    private fun qrInvalido(raw: String) {
        screen("QR no reconocido")
        banner("El código está dañado o no pertenece a una tablet del pañol.", ROJO)
        text("Contenido leído: ${raw.take(80)}", 12f, Color.GRAY)
        button("Ingresar número de serie a mano") { manual() }
        button("Volver", Color.DKGRAY) { home() }
    }

    /** Flujo alternativo: QR dañado -> búsqueda por serie o carga completa por teclado. */
    private fun manual() {
        screen("Ingreso manual")
        text("Escribí el número de serie impreso en la tablet:")
        val serie = edit("Nº de serie")
        button("Buscar") {
            val t = db.getTablet(serie.text.toString())
            if (t != null) fichaScreen(t) else toast("No hay una tablet registrada con ese serie")
        }
        button("Registrar una tablet nueva a mano", Color.DKGRAY) { altaForm(serie.text.toString()) }
        button("Volver", Color.DKGRAY) { home() }
    }

    private fun noRegistrada(t: Tablet) {
        screen("Tablet no registrada")
        banner("Esta tablet todavía no está dada de alta en el pañol.", ROJO)
        ficha(t)
        button("Dar de alta ahora (CU-01)", VERDE) { altaPreview(t) }
        button("Volver", Color.DKGRAY) { home() }
    }

    private fun fichaScreen(t0: Tablet) {
        val t = db.getTablet(t0.serie) ?: t0
        screen("Ficha de la tablet")
        val color = when (t.estado) {
            Estado.EN_PANOL -> VERDE
            Estado.SERVICIO -> ROJO
            else -> Color.parseColor("#EF6C00")
        }
        banner("Estado: ${t.estado}" + (t.legajo?.let { l ->
            "\nAsignada a: $l · ${db.getEmpleado(l)?.nombre ?: "?"}"
        } ?: ""), color)
        ficha(t)
        when (t.estado) {
            Estado.EN_PANOL -> button("Despachar (CU-02)", VERDE) { despacharForm(t) }
            Estado.USO_LOCAL, Estado.USO_EXTERNO -> button("Recibir devolución (CU-03)", AZUL) { devolverForm(t) }
            Estado.SERVICIO -> button("Reingresar al pañol (reparada)", AZUL) { reingresoForm(t) }
        }
        text("Historial", 16f, bold = true)
        val h = db.historial(t.serie)
        if (h.isEmpty()) text("Sin movimientos", 13f, Color.GRAY) else h.forEach { text(it, 13f, Color.DKGRAY) }
        button("Volver", Color.DKGRAY) { home() }
    }

    // ---------- CU-01 ----------
    private fun altaPreview(t: Tablet) {
        screen("Alta de tablet (CU-01)")
        ficha(t)
        if (db.getTablet(t.serie) != null) {
            banner("Ya existe una tablet con ese número de serie.", ROJO)
            button("Ver ficha") { fichaScreen(t) }
        } else {
            button("Confirmar alta en pañol", VERDE) {
                guarded({ qrGenerado(t) }) { db.altaTablet(t) }
            }
        }
        button("Cancelar", Color.DKGRAY) { home() }
    }

    private fun altaForm(serieInicial: String = "") {
        screen("Alta manual de tablet")
        val sn = edit("Nº de serie").apply { setText(serieInicial) }
        val cpu = edit("Procesador (ej: Snapdragon 680)")
        val ram = edit("RAM (ej: 4 GB)")
        val sto = edit("Almacenamiento (ej: 64 GB)")
        val pan = edit("Pantalla (ej: 10.1\")")
        val pue = edit("Puertos (ej: USB-C, Jack 3.5)")
        val con = edit("Conectividad (ej: Wi-Fi, 4G, Bluetooth)")
        button("Guardar y generar QR", VERDE) {
            val t = Tablet(
                sn.text.toString().trim(), cpu.text.toString().trim(), ram.text.toString().trim(),
                sto.text.toString().trim(), pan.text.toString().trim(), pue.text.toString().trim(),
                con.text.toString().trim()
            )
            guarded({ qrGenerado(t) }) { db.altaTablet(t) }
        }
        button("Cancelar", Color.DKGRAY) { home() }
    }

    /** Generación lógica del QR para imprimir y pegar en el reverso. */
    private fun qrGenerado(t: Tablet) {
        screen("Tablet registrada ✔")
        banner("Estado inicial: ${Estado.EN_PANOL}", VERDE)
        text("QR para pegar en el reverso del equipo:", 14f)
        val bm = qrBitmap(t.toQrJson())
        root.addView(ImageView(this).apply { setImageBitmap(bm); setBackgroundColor(Color.WHITE) },
            LinearLayout.LayoutParams(dp(260), dp(260)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        text(t.serie, 18f, bold = true).gravity = Gravity.CENTER
        text("Contenido: ${t.toQrJson()}", 11f, Color.GRAY)
        button("Dar de alta otra") { scan(MODE_ALTA) }
        button("Inicio", Color.DKGRAY) { home() }
    }

    private fun qrBitmap(content: String): Bitmap {
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 600, 600)
        val bm = Bitmap.createBitmap(600, 600, Bitmap.Config.RGB_565)
        for (x in 0 until 600) for (y in 0 until 600) bm.setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
        return bm
    }

    // ---------- CU-02 ----------
    private fun despacharForm(t: Tablet) {
        screen("Despachar tablet (CU-02)")
        text("Tablet ${t.serie}", 16f, bold = true)
        val legajo = edit("Legajo del empleado", number = true)
        val info = text("", 14f, Color.DKGRAY)
        val grp = RadioGroup(this)
        val rLocal = RadioButton(this).apply { text = "Uso local (oficina)"; id = View.generateViewId() }
        val rHome = RadioButton(this).apply { text = "Home Office (se la lleva)"; id = View.generateViewId() }
        grp.addView(rLocal); grp.addView(rHome); rLocal.isChecked = true
        root.addView(grp)
        text("Tarea asociada:", 14f, bold = true)
        val spinner = Spinner(this)
        root.addView(spinner)
        var tareas = emptyList<Tarea>()
        val alerta = TextView(this).apply {
            setTextColor(Color.WHITE); setBackgroundColor(ROJO); setPadding(dp(12), dp(12), dp(12), dp(12))
            visibility = View.GONE
        }
        root.addView(alerta)

        fun refrescar() {
            val l = legajo.text.toString().trim()
            val emp = if (l.isEmpty()) null else db.getEmpleado(l)
            info.text = when {
                l.isEmpty() -> ""
                emp == null -> "⚠ Legajo no registrado"
                else -> "Empleado: ${emp.nombre}"
            }
            tareas = if (emp != null) db.tareasActivas(l) else emptyList()
            val labels = listOf("(sin tarea)") + tareas.map { it.descripcion }
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            val sinTarea = rHome.isChecked && tareas.isEmpty()
            if (sinTarea) {
                alerta.text = "⚠ Este empleado no tiene una tarea asignada que justifique el Home Office. " +
                    "Cargue una en «Empleados y tareas» o elija uso local."
                alerta.visibility = View.VISIBLE
            } else alerta.visibility = View.GONE
        }
        legajo.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = refrescar()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        grp.setOnCheckedChangeListener { _, _ -> refrescar() }
        refrescar()

        button("Confirmar despacho", VERDE) {
            val sel = spinner.selectedItemPosition - 1
            val tarea = tareas.getOrNull(sel)
            guarded({ toast("Tablet despachada"); fichaScreen(t) }) {
                db.despachar(t.serie, legajo.text.toString(), rHome.isChecked, tarea)
            }
        }
        button("Cancelar", Color.DKGRAY) { fichaScreen(t) }
    }

    // ---------- CU-03 ----------
    private fun devolverForm(t: Tablet) {
        screen("Recibir devolución (CU-03)")
        text("Tablet ${t.serie}", 16f, bold = true)
        text("Estado físico del equipo:", 14f, bold = true)
        val grp = RadioGroup(this)
        val rOk = RadioButton(this).apply { text = "Buen estado → vuelve al pañol"; id = View.generateViewId() }
        val rMal = RadioButton(this).apply { text = "Dañada / falla → servicio técnico"; id = View.generateViewId() }
        grp.addView(rOk); grp.addView(rMal); rOk.isChecked = true
        root.addView(grp)
        val obs = edit("Observaciones")
        button("Confirmar devolución", VERDE) {
            guarded({ toast("Devolución registrada"); fichaScreen(t) }) {
                db.devolver(t.serie, rMal.isChecked, obs.text.toString())
            }
        }
        button("Cancelar", Color.DKGRAY) { fichaScreen(t) }
    }

    private fun reingresoForm(t: Tablet) {
        screen("Reingreso desde servicio técnico")
        val obs = edit("Detalle de la reparación")
        button("Reingresar al pañol", VERDE) {
            guarded({ toast("Reingresada"); fichaScreen(t) }) { db.reingresarDeServicio(t.serie, obs.text.toString()) }
        }
        button("Cancelar", Color.DKGRAY) { fichaScreen(t) }
    }

    // ---------- Listados ----------
    private fun inventario() {
        screen("Inventario")
        val l = db.allTablets()
        if (l.isEmpty()) text("Todavía no hay tablets registradas.", 14f, Color.GRAY)
        l.forEach { t ->
            button("${t.serie}\n${t.estado}", when (t.estado) {
                Estado.EN_PANOL -> VERDE; Estado.SERVICIO -> ROJO; else -> Color.parseColor("#EF6C00")
            }) { fichaScreen(t) }
        }
        button("Volver", Color.DKGRAY) { home() }
    }

    private fun empleados() {
        screen("Empleados y tareas")
        db.allEmpleados().forEach { e ->
            text("${e.legajo} · ${e.nombre}", 16f, bold = true)
            val ts = db.tareasActivas(e.legajo)
            if (ts.isEmpty()) text("   sin tareas activas", 13f, Color.GRAY)
            ts.forEach { text("   • ${it.descripcion}", 13f, Color.DKGRAY) }
        }
        text("Nuevo empleado", 16f, bold = true)
        val lg = edit("Legajo", number = true); val nm = edit("Nombre y apellido")
        button("Agregar empleado", VERDE) {
            guarded({ empleados() }) { db.addEmpleado(lg.text.toString(), nm.text.toString()) }
        }
        text("Nueva tarea (justifica Home Office)", 16f, bold = true)
        val lt = edit("Legajo del empleado", number = true); val ds = edit("Descripción de la tarea")
        button("Asignar tarea", VERDE) {
            guarded({ empleados() }) { db.addTarea(lt.text.toString(), ds.text.toString()) }
        }
        button("Volver", Color.DKGRAY) { home() }
    }
}
