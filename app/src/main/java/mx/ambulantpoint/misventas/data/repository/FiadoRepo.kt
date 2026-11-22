package mx.ambulantpoint.misventas.data.repository

import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.dao.AbonoConForma
import mx.ambulantpoint.misventas.data.local.dao.ClienteConSaldo
import mx.ambulantpoint.misventas.data.local.dao.VentaFiada
import mx.ambulantpoint.misventas.data.local.entity.Abono
import mx.ambulantpoint.misventas.data.local.entity.AplicacionAbono
import mx.ambulantpoint.misventas.data.local.entity.Cliente
import mx.ambulantpoint.misventas.data.local.entity.Configuracion
import mx.ambulantpoint.misventas.data.local.entity.EstadoAbono
import mx.ambulantpoint.misventas.data.local.entity.EstadoCaja
import mx.ambulantpoint.misventas.data.local.entity.EstadoVenta
import mx.ambulantpoint.misventas.data.local.entity.TipoAbono
import mx.ambulantpoint.misventas.domain.regla.ReglaFiado
import mx.ambulantpoint.misventas.domain.regla.SaldoVenta

/** Fiado (Fase 3, `docs/09-ESPEC-FIADO.md`): clientes, saldos y abonos. */
class FiadoRepo(private val db: MisVentasDb, private val reloj: Reloj) {
    private val dao = db.fiadoDao()
    private val cajaDao = db.cajaDao()

    fun observarClientes(): Flow<List<ClienteConSaldo>> = dao.observarClientesConSaldo()
    fun observarCliente(id: String): Flow<Cliente?> = dao.observarCliente(id)
    fun observarVentasFiadas(clienteId: String): Flow<List<VentaFiada>> = dao.observarVentasFiadas(clienteId)
    fun observarAbonos(clienteId: String): Flow<List<AbonoConForma>> = dao.observarAbonos(clienteId)
    fun observarAbonadoAVenta(ventaId: String): Flow<Long?> = dao.observarAbonadoAVenta(ventaId)

    /** Saldo actual del cliente (mismo cálculo que la lista). */
    suspend fun saldo(clienteId: String): Long = ReglaFiado.saldo(pendientes(clienteId))

    private suspend fun pendientes(clienteId: String): List<SaldoVenta> =
        dao.ventasFiadas(clienteId).filter { it.estado == EstadoVenta.REGISTRADA }
            .map { SaldoVenta(it.ventaId, it.fechaHora, it.totalCentavos, it.abonadoCentavos) }

    /** Crea o edita (con [id]) un cliente. El nombre no se repite. */
    suspend fun guardarCliente(id: String?, nombre: String, telefono: String): Resultado<Cliente> = db.transaccion {
        val limpio = nombre.trim().replace(Regex("\\s+"), " ")
        ReglaFiado.validarNombre(limpio)?.let { fallar(FallaDatos.Regla(it)) }
        ReglaFiado.validarTelefono(telefono)?.let { fallar(FallaDatos.Regla(it)) }
        val normalizado = ReglaFiado.normalizarNombre(limpio)
        val otro = dao.clientePorNombre(normalizado)
        if (otro != null && otro.id != id) fallar(FallaDatos.Regla(Mensajes.Fiado.CLIENTE_YA_REGISTRADO))
        val ahora = reloj.ahora()
        val tel = telefono.trim().ifEmpty { null }
        if (id == null) {
            Cliente(nuevoId(), limpio, normalizado, tel, ahora, ahora).also { dao.insertarCliente(it) }
        } else {
            val actual = dao.cliente(id) ?: fallar(FallaDatos.NoEncontrado)
            actual.copy(nombre = limpio, nombreNormalizado = normalizado, telefono = tel, actualizadoEn = ahora, pendienteSync = true)
                .also { dao.actualizarCliente(it) }
        }
    }

    /**
     * Registra un abono en la caja abierta y lo reparte de la venta fiada más vieja a la más nueva.
     * Invalida el corte (entra al corte de hoy).
     */
    suspend fun registrarAbono(clienteId: String, montoCentavos: Long, formaPagoId: String): Resultado<Abono> = db.transaccion {
        val caja = cajaDao.abierta() ?: fallar(FallaDatos.Regla(Mensajes.Fiado.ABRA_CAJA_ABONO))
        dao.cliente(clienteId) ?: fallar(FallaDatos.NoEncontrado)
        val forma = db.formaPagoDao().porId(formaPagoId)
        if (forma == null || !forma.activa) fallar(FallaDatos.FormaPagoNoDisponible)
        val aplicaciones = when (val r = ReglaFiado.repartir(montoCentavos, pendientes(clienteId))) {
            is Resultado.Exito -> r.valor
            is Resultado.Fallo -> fallar(FallaDatos.Regla((r.falla as? mx.ambulantpoint.misventas.domain.regla.FallaMensaje)?.mensaje ?: Mensajes.Campo.MONTO_NO_VALIDO))
        }
        val ahora = reloj.ahora()
        val abono = Abono(
            id = nuevoId(), clienteId = clienteId, cajaId = caja.id, fechaHora = ahora, tipo = TipoAbono.ABONO,
            montoCentavos = montoCentavos, formaPagoId = forma.id, creadoEn = ahora, actualizadoEn = ahora,
        )
        dao.insertarAbono(abono)
        dao.insertarAplicaciones(aplicaciones.map { AplicacionAbono(nuevoId(), abono.id, it.ventaId, it.montoCentavos, ahora) })
        cajaDao.invalidarCorte(caja.id, ahora)
        abono
    }

    /** ¿Se puede cancelar este abono? (para mostrar el botón). */
    suspend fun esCancelable(abonoId: String): Boolean {
        val a = dao.abono(abonoId) ?: return false
        return cancelable(a)
    }

    private suspend fun cancelable(a: Abono): Boolean {
        val caja = cajaDao.porId(a.cajaId)
        val algunaCancelada = dao.aplicacionesDe(a.id).any { ap ->
            db.ventaDao().porId(ap.ventaId)?.estado == EstadoVenta.CANCELADA
        }
        return ReglaFiado.puedeCancelarAbono(
            esAbono = a.tipo == TipoAbono.ABONO,
            registrado = a.estado == EstadoAbono.REGISTRADO,
            suCajaAbierta = caja?.estado == EstadoCaja.ABIERTA,
            algunaVentaCancelada = algunaCancelada,
        )
    }

    /** Cancela un abono equivocado (no se borra): el saldo del cliente regresa. */
    suspend fun cancelarAbono(abonoId: String): Resultado<Unit> = db.transaccion {
        val a = dao.abono(abonoId) ?: fallar(FallaDatos.NoEncontrado)
        if (!cancelable(a)) fallar(FallaDatos.Regla(Mensajes.Fiado.ABONO_NO_CANCELABLE))
        val ahora = reloj.ahora()
        if (dao.marcarAbonoCancelado(a.id, ahora) == 0) fallar(FallaDatos.Regla(Mensajes.Fiado.ABONO_NO_CANCELABLE))
        cajaDao.invalidarCorte(a.cajaId, ahora)
    }

    /** Monto de aviso de saldo alto (se respalda con la configuración del negocio). */
    suspend fun guardarAviso(avisoCentavos: Long): Resultado<Unit> = db.transaccion {
        if (avisoCentavos <= 0) fallar(FallaDatos.CantidadInvalida)
        val ahora = reloj.ahora()
        val actual = db.configuracionDao().obtener() ?: Configuracion(actualizadoEn = ahora)
        db.configuracionDao().guardar(actual.copy(avisoSaldoCentavos = avisoCentavos, actualizadoEn = ahora, pendienteSync = true))
    }
}
