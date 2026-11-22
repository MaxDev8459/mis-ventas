package mx.ambulantpoint.misventas.demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.ambulantpoint.misventas.MisVentasApp
import mx.ambulantpoint.misventas.core.Dinero
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.repository.ApartadoRepo
import mx.ambulantpoint.misventas.data.repository.CajaRepo
import mx.ambulantpoint.misventas.data.repository.DatosApartado
import mx.ambulantpoint.misventas.data.repository.DatosFiado
import mx.ambulantpoint.misventas.data.repository.FiadoRepo
import mx.ambulantpoint.misventas.data.repository.InventarioRepo
import mx.ambulantpoint.misventas.data.repository.LineaLibre
import mx.ambulantpoint.misventas.data.repository.LineaVenta
import mx.ambulantpoint.misventas.data.repository.SolicitudCobro
import mx.ambulantpoint.misventas.data.repository.VentaRepo
import mx.ambulantpoint.misventas.domain.puertos.EstadoSesion
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Historial de demostración para probar los reportes (Fase 5) en **Mis Ventas Pruebas**: unas 8 semanas de
 * trabajo (lunes a sábado, terminando ayer) con los productos que ya tiene la app. Usa los mismos repositorios
 * que la app (existencias, cobro, fiado, apartados, corte y cierre), con un reloj que se mueve a días pasados.
 *
 * Seguridad: solo corre en la app `.pruebas`, con una cuenta ya registrada, sin caja abierta, y una sola vez.
 * Los días que ya tienen caja no se tocan. Comando:
 *   ./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
 *     -Pandroid.testInstrumentationRunnerArguments.class=mx.ambulantpoint.misventas.demo.CargarHistorialDemo \
 *     -Pandroid.testInstrumentationRunnerArguments.historial=1
 */
@RunWith(AndroidJUnit4::class)
class CargarHistorialDemo {
    private class RelojDemo(var ms: Long = 0) : Reloj { override fun ahora() = ms }

    private val zona = ZoneId.systemDefault()
    private val azar = Random(2611)

    @Test
    fun cargar() = runBlocking {
        assumeTrue("Solo con -Pandroid.testInstrumentationRunnerArguments.historial=1",
            InstrumentationRegistry.getArguments().getString("historial") == "1")
        val app = ApplicationProvider.getApplicationContext<MisVentasApp>()
        check(app.packageName.endsWith(".pruebas")) { "Este cargador solo es para Mis Ventas Pruebas" }
        val c = app.contenedor
        check(c.auth.estado.value != EstadoSesion.SIN_CUENTA) { "Primero registre la cuenta de pruebas en la app" }
        check(c.cajaRepo.abierta() == null) { "Cierre la caja abierta antes de cargar el historial" }
        val prefs = app.getSharedPreferences("demo", Context.MODE_PRIVATE)
        if (prefs.getBoolean(MARCA, false)) return@runBlocking

        val reloj = RelojDemo()
        val cajas = CajaRepo(c.db, reloj)
        val ventas = VentaRepo(c.db, reloj)
        val inventario = InventarioRepo(c.db, reloj)
        val fiado = FiadoRepo(c.db, reloj)
        val apartados = ApartadoRepo(c.db, reloj) { zona }
        val formas = c.formaPagoRepo.observarActivas().first()
        val efectivo = formas.first { it.esEfectivo }
        val transferencia = formas.firstOrNull { !it.esEfectivo } ?: efectivo

        // Clientes de fiado (se reutiliza el que ya exista con el mismo nombre).
        val existentes = c.db.fiadoDao().clientesConSaldo().map { it.cliente }
        val clientes = listOf("Doña Lupe", "Don Chuy", "Rosy de la tienda", "Memo del taller").map { n ->
            existentes.firstOrNull { it.nombre == n } ?: (fiado.guardarCliente(null, n, "") as Resultado.Exito).valor
        }

        val hoy = LocalDate.now(zona)
        val diasConCaja = c.db.query("SELECT abiertaEn FROM caja", null).use { cur ->
            buildSet { while (cur.moveToNext()) add(java.time.Instant.ofEpochMilli(cur.getLong(0)).atZone(zona).toLocalDate()) }
        }
        val dias = (56L downTo 1L).map { hoy.minusDays(it) }
            .filter { it.dayOfWeek != DayOfWeek.SUNDAY && it !in diasConCaja }

        fun a(dia: LocalDate, hora: Int, minuto: Int = 0) = dia.atTime(hora, minuto).atZone(zona).toInstant().toEpochMilli()
        suspend fun productos(): List<Producto> = c.productoRepo.observar(activo = true).first().map { it.producto }

        // Más ventas al mediodía y en la tarde; viernes y sábado son los mejores días.
        val pesoHora = mapOf(8 to 2, 9 to 4, 10 to 6, 11 to 8, 12 to 11, 13 to 14, 14 to 12, 15 to 8, 16 to 7, 17 to 10, 18 to 9, 19 to 4)
        val pesoDia = mapOf(
            DayOfWeek.MONDAY to 0.8, DayOfWeek.TUESDAY to 0.85, DayOfWeek.WEDNESDAY to 1.0,
            DayOfWeek.THURSDAY to 1.0, DayOfWeek.FRIDAY to 1.3, DayOfWeek.SATURDAY to 1.5,
        )
        fun horaAlAzar(): Int {
            var x = azar.nextInt(pesoHora.values.sum())
            for ((h, p) in pesoHora) { if (x < p) return h; x -= p }
            return 13
        }

        dias.forEachIndexed { i, dia ->
            // 7:30 — surtir lo que quedó corto (entrada de mercancía).
            reloj.ms = a(dia, 7, 30)
            for (p in productos()) {
                val minimo = if (p.tipoVenta == TipoVenta.PESO) 2_000L else 15_000L
                if (p.existenciaMilesimas < minimo) inventario.registrarEntrada(p.id, if (p.tipoVenta == TipoVenta.PESO) 3_000L else 30_000L)
            }

            // 8:00 — abrir caja con $200.
            reloj.ms = a(dia, 8)
            cajas.abrir(20_000)

            // Ventas del día: más en fin de semana y van creciendo un poco cada semana (para que se note al comparar).
            val tendencia = 1.0 + 0.25 * i / dias.size
            val n = ((14 + azar.nextInt(8)) * pesoDia.getValue(dia.dayOfWeek) * tendencia).toInt()
            val horas = List(n) { horaAlAzar() to azar.nextInt(60) }.sortedWith(compareBy({ it.first }, { it.second }))
            val cobradas = mutableListOf<String>()
            for ((h, m) in horas) {
                reloj.ms = a(dia, h, m)
                val disponibles = productos().filter { it.existenciaMilesimas > 0 }
                if (disponibles.isEmpty()) break
                val lineas = disponibles.shuffled(azar).take(1 + azar.nextInt(3)).map { p ->
                    val cant = if (p.tipoVenta == TipoVenta.PESO) 100L * (1 + azar.nextInt(5)) else 1_000L * (1 + azar.nextInt(3))
                    LineaVenta(p.id, minOf(cant, p.existenciaMilesimas))
                }.filter { it.cantidadMilesimas > 0 }
                if (lineas.isEmpty()) continue
                val total = lineas.sumOf { l -> Dinero.importe(l.cantidadMilesimas, disponibles.first { it.id == l.productoId }.precioCentavos) }
                val dado = azar.nextInt(100)
                val solicitud = when {
                    // ~6 %: fiado, a veces dejando algo.
                    dado < 6 -> {
                        val pago = if (azar.nextBoolean() && total > 2_000) (total / 2 / 100) * 100 else 0L
                        SolicitudCobro(lineas, null, fiado = DatosFiado(clientes.random(azar).id, pago, if (pago > 0) efectivo.id else null))
                    }
                    // ~20 %: transferencia.
                    dado < 26 -> SolicitudCobro(lineas, transferencia.id, libres = libre(dado))
                    else -> {
                        val libres = libre(dado)
                        val conLibre = total + libres.sumOf { it.importeCentavos }
                        SolicitudCobro(lineas, efectivo.id, if (azar.nextBoolean()) conLibre else (conLibre / 5_000 + 1) * 5_000, libres = libres)
                    }
                }
                (ventas.cobrar(solicitud) as? Resultado.Exito)?.let { cobradas += it.valor.venta.id }
            }

            // Abonos: algún cliente que debe paga una parte o todo.
            if (azar.nextInt(3) == 0) {
                reloj.ms = a(dia, 12, 30)
                val conSaldo = c.db.fiadoDao().clientesConSaldo().filter { it.saldoCentavos > 0 && it.cliente.id in clientes.map { x -> x.id } }
                conSaldo.randomOrNull(azar)?.let { cs ->
                    val monto = if (azar.nextBoolean()) cs.saldoCentavos else maxOf(100L, cs.saldoCentavos / 2 / 100 * 100)
                    fiado.registrarAbono(cs.cliente.id, monto, if (azar.nextInt(4) == 0) transferencia.id else efectivo.id)
                }
            }

            // Mermas, caducados y consumo propio.
            reloj.ms = a(dia, 15, 10)
            val lista = productos().filter { it.existenciaMilesimas > 0 }
            if (azar.nextInt(3) == 0) lista.filter { it.tipoVenta == TipoVenta.UNIDAD }.randomOrNull(azar)?.let {
                inventario.ajustar(it.id, false, 1_000, MotivoAjuste.MERMA)
            }
            if (azar.nextInt(7) == 0) lista.filter { it.tipoVenta == TipoVenta.PESO && it.existenciaMilesimas >= 200 }.randomOrNull(azar)?.let {
                inventario.ajustar(it.id, false, 200, MotivoAjuste.PRODUCTO_CADUCADO)
            }
            if (azar.nextInt(6) == 0) lista.firstOrNull { it.nombre.contains("Refresco", ignoreCase = true) || it.nombre.contains("Agua", ignoreCase = true) }?.let {
                inventario.ajustar(it.id, false, 1_000, MotivoAjuste.CONSUMO_PROPIO)
            }

            // Apartados: cada semana uno se aparta 2 días antes y se entrega; de vez en cuando uno no se recoge.
            if (i + 2 < dias.size && i % 6 == 0) {
                reloj.ms = a(dia, 11)
                val entrega = dias[i + 2]
                productos().filter { it.tipoVenta == TipoVenta.UNIDAD && it.existenciaMilesimas >= 6_000 }.randomOrNull(azar)?.let { p ->
                    apartados.apartar(
                        DatosApartado(listOf("Sra. Carmen", "Profe Luis", "Fiesta de Ana").random(azar), "", "Parque central", a(entrega, 13)),
                        listOf(LineaVenta(p.id, 5_000)),
                    )
                }
            }
            // Entregar los apartados de hoy (casi todos); los que no, se liberan al cerrar la caja.
            reloj.ms = a(dia, 13, 5)
            apartados.pendientes().filter { java.time.Instant.ofEpochMilli(it.apartado.entregaEn).atZone(zona).toLocalDate() == dia }
                .forEach { ap -> if (azar.nextInt(5) != 0) ventas.cobrar(SolicitudCobro(emptyList(), transferencia.id, apartadoId = ap.apartado.id)) }

            // Una cancelación de vez en cuando.
            if (i % 6 == 3 && cobradas.size > 3) {
                reloj.ms = a(dia, 16, 40)
                ventas.cancelar(cobradas[2])
            }

            // 20:00 — corte (a veces sobra o falta poquito) y cierre.
            reloj.ms = a(dia, 20)
            val esperado = (cajas.calcularCorte(0) as Resultado.Exito).valor.efectivoEsperadoCentavos
            val diferencia = when (i % 7) { 2 -> 1_000L; 5 -> -500L; else -> 0L }
            cajas.calcularCorte(esperado + diferencia)
            reloj.ms = a(dia, 20, 5)
            cajas.cerrar()
        }
        prefs.edit().putBoolean(MARCA, true).commit()
    }

    /** ~5 % de las ventas llevan un monto libre ("Varios" o con descripción). */
    private fun libre(dado: Int): List<LineaLibre> =
        if (dado % 20 == 7) listOf(LineaLibre(if (azar.nextBoolean()) "" else "Hielo", 1_000 + 500L * azar.nextInt(4))) else emptyList()

    private companion object {
        const val MARCA = "historial_demo_cargado"
    }
}
