package ar.edu.et35.panolapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Estados exclusivos del ciclo de vida de una tablet. */
object Estado {
    const val EN_PANOL = "En Pañol"
    const val USO_LOCAL = "En Uso Local (Oficina)"
    const val USO_EXTERNO = "En Uso Externo (Home Office)"
    const val SERVICIO = "En Servicio Técnico"
}

data class Tablet(
    val serie: String,
    val cpu: String,
    val ram: String,
    val almacenamiento: String,
    val pantalla: String,
    val puertos: String,
    val conectividad: String,
    val estado: String = Estado.EN_PANOL,
    val legajo: String? = null,
) {
    /** Contenido del QR (JSON). */
    fun toQrJson(): String = JSONObject().apply {
        put("sn", serie); put("cpu", cpu); put("ram", ram); put("sto", almacenamiento)
        put("pan", pantalla); put("pue", puertos); put("con", conectividad)
    }.toString()
}

data class Empleado(val legajo: String, val nombre: String)
data class Tarea(val id: Long, val legajo: String, val descripcion: String)

class RuleException(msg: String) : Exception(msg)

object QrParser {
    /**
     * Acepta JSON {"sn","cpu","ram","sto","pan","pue","con"} o texto delimitado con "|":
     * serie|cpu|ram|almacenamiento|pantalla|puertos|conectividad
     * Devuelve null si el contenido no es válido (QR dañado / ajeno).
     */
    fun parse(raw: String): Tablet? {
        val s = raw.trim()
        return try {
            if (s.startsWith("{")) {
                val j = JSONObject(s)
                val sn = j.optString("sn").trim()
                if (sn.isEmpty()) null else Tablet(
                    sn, j.optString("cpu"), j.optString("ram"), j.optString("sto"),
                    j.optString("pan"), j.optString("pue"), j.optString("con")
                )
            } else {
                val p = s.split("|").map { it.trim() }
                if (p.size < 7 || p[0].isEmpty()) null
                else Tablet(p[0], p[1], p[2], p[3], p[4], p[5], p[6])
            }
        } catch (e: Exception) {
            null
        }
    }
}

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "panol.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE tablets(serie TEXT PRIMARY KEY, cpu TEXT, ram TEXT, almacenamiento TEXT,
               pantalla TEXT, puertos TEXT, conectividad TEXT,
               estado TEXT NOT NULL DEFAULT '${Estado.EN_PANOL}', legajo TEXT)"""
        )
        db.execSQL("CREATE TABLE empleados(legajo TEXT PRIMARY KEY, nombre TEXT NOT NULL)")
        db.execSQL(
            """CREATE TABLE tareas(id INTEGER PRIMARY KEY AUTOINCREMENT, legajo TEXT NOT NULL,
               descripcion TEXT NOT NULL, activa INTEGER NOT NULL DEFAULT 1)"""
        )
        db.execSQL(
            """CREATE TABLE movimientos(id INTEGER PRIMARY KEY AUTOINCREMENT, serie TEXT NOT NULL,
               tipo TEXT NOT NULL, estado_desde TEXT, estado_hasta TEXT, legajo TEXT, tarea TEXT,
               observacion TEXT, fecha TEXT NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    private fun now() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    private fun mov(
        serie: String, tipo: String, desde: String?, hasta: String?,
        legajo: String?, tarea: String?, obs: String?,
    ) {
        writableDatabase.insert("movimientos", null, ContentValues().apply {
            put("serie", serie); put("tipo", tipo); put("estado_desde", desde); put("estado_hasta", hasta)
            put("legajo", legajo); put("tarea", tarea); put("observacion", obs); put("fecha", now())
        })
    }

    // ---------- Tablets ----------
    private fun rowToTablet(c: android.database.Cursor) = Tablet(
        c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "",
        c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getString(7), c.getString(8)
    )

    fun getTablet(serie: String): Tablet? =
        readableDatabase.rawQuery("SELECT * FROM tablets WHERE serie=?", arrayOf(serie.trim())).use {
            if (it.moveToFirst()) rowToTablet(it) else null
        }

    fun allTablets(): List<Tablet> =
        readableDatabase.rawQuery("SELECT * FROM tablets ORDER BY serie", null).use { c ->
            generateSequence { if (c.moveToNext()) rowToTablet(c) else null }.toList()
        }

    /** CU-01: alta en pañol. */
    fun altaTablet(t: Tablet) {
        if (t.serie.isBlank()) throw RuleException("El número de serie es obligatorio.")
        if (getTablet(t.serie) != null) throw RuleException("Ya existe una tablet con serie ${t.serie}.")
        writableDatabase.insert("tablets", null, ContentValues().apply {
            put("serie", t.serie.trim()); put("cpu", t.cpu); put("ram", t.ram)
            put("almacenamiento", t.almacenamiento); put("pantalla", t.pantalla)
            put("puertos", t.puertos); put("conectividad", t.conectividad)
            put("estado", Estado.EN_PANOL)
        })
        mov(t.serie.trim(), "ALTA", null, Estado.EN_PANOL, null, null, "Alta en pañol")
    }

    // ---------- Empleados y tareas ----------
    fun addEmpleado(legajo: String, nombre: String) {
        if (legajo.isBlank() || nombre.isBlank()) throw RuleException("Legajo y nombre son obligatorios.")
        if (getEmpleado(legajo) != null) throw RuleException("El legajo $legajo ya existe.")
        writableDatabase.insert("empleados", null, ContentValues().apply {
            put("legajo", legajo.trim()); put("nombre", nombre.trim())
        })
    }

    fun getEmpleado(legajo: String): Empleado? =
        readableDatabase.rawQuery("SELECT legajo,nombre FROM empleados WHERE legajo=?", arrayOf(legajo.trim())).use {
            if (it.moveToFirst()) Empleado(it.getString(0), it.getString(1)) else null
        }

    fun allEmpleados(): List<Empleado> =
        readableDatabase.rawQuery("SELECT legajo,nombre FROM empleados ORDER BY legajo", null).use { c ->
            generateSequence { if (c.moveToNext()) Empleado(c.getString(0), c.getString(1)) else null }.toList()
        }

    fun addTarea(legajo: String, desc: String) {
        if (getEmpleado(legajo) == null) throw RuleException("El legajo $legajo no existe.")
        if (desc.isBlank()) throw RuleException("La descripción de la tarea es obligatoria.")
        writableDatabase.insert("tareas", null, ContentValues().apply {
            put("legajo", legajo.trim()); put("descripcion", desc.trim()); put("activa", 1)
        })
    }

    fun tareasActivas(legajo: String): List<Tarea> =
        readableDatabase.rawQuery(
            "SELECT id,legajo,descripcion FROM tareas WHERE legajo=? AND activa=1 ORDER BY id",
            arrayOf(legajo.trim())
        ).use { c ->
            generateSequence { if (c.moveToNext()) Tarea(c.getLong(0), c.getString(1), c.getString(2)) else null }.toList()
        }

    // ---------- Movimientos ----------
    /** CU-02: despachar. Home Office exige una tarea activa del empleado. */
    fun despachar(serie: String, legajo: String, homeOffice: Boolean, tarea: Tarea?) {
        val t = getTablet(serie) ?: throw RuleException("Tablet inexistente.")
        if (t.estado != Estado.EN_PANOL) throw RuleException("La tablet no está disponible (estado: ${t.estado}).")
        val emp = getEmpleado(legajo) ?: throw RuleException("El legajo $legajo no está registrado.")
        if (homeOffice && tarea == null)
            throw RuleException("Home Office requiere una tarea asignada que lo justifique.")
        if (tarea != null && tarea.legajo != emp.legajo)
            throw RuleException("La tarea no pertenece a este empleado.")
        val nuevo = if (homeOffice) Estado.USO_EXTERNO else Estado.USO_LOCAL
        writableDatabase.update("tablets", ContentValues().apply {
            put("estado", nuevo); put("legajo", emp.legajo)
        }, "serie=?", arrayOf(t.serie))
        mov(t.serie, "DESPACHO", t.estado, nuevo, emp.legajo, tarea?.descripcion, null)
    }

    /** CU-03: devolución con verificación de estado físico. */
    fun devolver(serie: String, danada: Boolean, obs: String) {
        val t = getTablet(serie) ?: throw RuleException("Tablet inexistente.")
        if (t.estado != Estado.USO_LOCAL && t.estado != Estado.USO_EXTERNO)
            throw RuleException("La tablet no está prestada (estado: ${t.estado}).")
        if (danada && obs.isBlank()) throw RuleException("Describa el daño en observaciones.")
        val nuevo = if (danada) Estado.SERVICIO else Estado.EN_PANOL
        writableDatabase.update("tablets", ContentValues().apply {
            put("estado", nuevo); putNull("legajo")
        }, "serie=?", arrayOf(t.serie))
        mov(t.serie, "DEVOLUCION", t.estado, nuevo, t.legajo, null, obs.ifBlank { "Sin observaciones" })
    }

    /** Servicio técnico -> pañol, una vez reparada. */
    fun reingresarDeServicio(serie: String, obs: String) {
        val t = getTablet(serie) ?: throw RuleException("Tablet inexistente.")
        if (t.estado != Estado.SERVICIO) throw RuleException("La tablet no está en servicio técnico.")
        writableDatabase.update("tablets", ContentValues().apply { put("estado", Estado.EN_PANOL) }, "serie=?", arrayOf(t.serie))
        mov(t.serie, "REINGRESO", t.estado, Estado.EN_PANOL, null, null, obs.ifBlank { "Reparada" })
    }

    fun historial(serie: String): List<String> =
        readableDatabase.rawQuery(
            "SELECT fecha,tipo,estado_hasta,legajo,tarea,observacion FROM movimientos WHERE serie=? ORDER BY id DESC LIMIT 15",
            arrayOf(serie)
        ).use { c ->
            generateSequence {
                if (!c.moveToNext()) null else buildString {
                    append("${c.getString(0)} · ${c.getString(1)} → ${c.getString(2)}")
                    c.getString(3)?.let { append("\n   legajo $it") }
                    c.getString(4)?.let { append(" · tarea: $it") }
                    c.getString(5)?.let { append("\n   $it") }
                }
            }.toList()
        }
}
