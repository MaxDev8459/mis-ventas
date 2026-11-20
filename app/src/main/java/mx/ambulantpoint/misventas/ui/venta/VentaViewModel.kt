package mx.ambulantpoint.misventas.ui.venta

import mx.ambulantpoint.misventas.domain.regla.FallaCampos
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.DescuentoCapturado
import mx.ambulantpoint.misventas.data.local.entity.TipoDescuento
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Lectura
import mx.ambulantpoint.misventas.core.LectorNumeros
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.dao.ProductoConCategoria
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.regla.FallaMensaje
import mx.ambulantpoint.misventas.domain.usecase.CobrarVenta
import mx.ambulantpoint.misventas.domain.usecase.EvaluarExistenciaBaja
import mx.ambulantpoint.misventas.domain.usecase.PrepararCobro
import mx.ambulantpoint.misventas.domain.usecase.ResumenCobro
import mx.ambulantpoint.misventas.domain.venta.Carrito
import mx.ambulantpoint.misventas.domain.venta.ProductoEnVenta
import mx.ambulantpoint.misventas.ui.components.atender

/** Producto mostrado en la pantalla de venta, con su imagen. */
data class Tarjeta(val producto: ProductoEnVenta, val imagenRuta: String)

/** RF 3.1 Registrar venta y RF 3.2 Cobrar venta. */
class VentaViewModel(c: AppContainer) : ViewModel() {
    private val cobrarVenta = CobrarVenta(c.ventaRepo, EvaluarExistenciaBaja(c.reporteRepo))
    private val notificador = c.notificador

    val busqueda = MutableStateFlow("")

    /** RF 3.1: solo productos activos con existencia mayor a cero. */
    val productos: StateFlow<List<Tarjeta>?> = combine(c.productoRepo.observarParaVenta(), busqueda) { lista, texto ->
        lista.filter { texto.isBlank() || it.producto.nombre.contains(texto.trim(), ignoreCase = true) }.map(::aTarjeta)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** RF 3.2: solo formas de pago activas. Se carga desde el inicio para preseleccionar Efectivo. */
    val formas: StateFlow<List<FormaPago>> = c.formaPagoRepo.observarActivas()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val caja: StateFlow<Caja?> = c.cajaRepo.observarAbierta()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** RF 3.6: solo con descuentos habilitados se muestra la opción al cobrar. */
    val descuentosHabilitados: StateFlow<Boolean> = c.configuracionRepo.observar()
        .map { it?.descuentosHabilitados == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var carrito by mutableStateOf(Carrito())
        private set

    /** Producto por peso esperando que se capture el peso de la etiqueta. */
    var pidiendoPeso by mutableStateOf<ProductoEnVenta?>(null)
        private set
    var pesoTexto by mutableStateOf("")
    var pesoError by mutableStateOf<String?>(null)
        private set

    var cobrando by mutableStateOf(false)
        private set
    var forma by mutableStateOf<FormaPago?>(null)
    var recibido by mutableStateOf("")
    var erroresCobro by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var confirmando by mutableStateOf(false)
        private set

    // RF 3.6: descuento capturado al cobrar.
    var tipoDescuento by mutableStateOf(TipoDescuento.PORCENTAJE)
        private set
    var descuentoTexto by mutableStateOf("")
        private set

    /** Lo capturado, si los descuentos están habilitados y hay un valor. */
    private val descuento: DescuentoCapturado?
        get() = if (descuentosHabilitados.value && descuentoTexto.isNotBlank()) DescuentoCapturado(tipoDescuento, descuentoTexto) else null

    /**
     * RF 3.6: subtotal, descuento en pesos y total a cobrar (sin depender del monto recibido). Si el
     * descuento no es válido, no se aplica y su mensaje del DFR se muestra en el campo.
     */
    private val conDescuento: Pair<ResumenCobro?, String?> by derivedStateOf {
        when (val r = PrepararCobro.calcular(carrito, forma, null, descuento)) {
            is Resultado.Exito -> r.valor to null
            is Resultado.Fallo -> {
                val sin = (PrepararCobro.calcular(carrito, forma, null, null) as? Resultado.Exito)?.valor
                sin to ((r.falla as? FallaCampos)?.errores?.get(Campos.DESCUENTO))
            }
        }
    }
    val cobro: ResumenCobro? get() = conDescuento.first
    val errorDescuento: String? get() = erroresCobro[Campos.DESCUENTO] ?: conDescuento.second

    /** Total a cobrar con el descuento válido aplicado. */
    val totalACobrar: Long get() = cobro?.totalCentavos ?: carrito.subtotalCentavos

    /** Lo que se muestra antes de confirmar: total y, en efectivo, el cambio a entregar. */
    val resumen: ResumenCobro? by derivedStateOf {
        (PrepararCobro.calcular(carrito, forma, recibido.ifBlank { null }, descuento.takeIf { conDescuento.second == null }) as? Resultado.Exito)?.valor
    }

    fun elegirTipoDescuento(t: TipoDescuento) { tipoDescuento = t; erroresCobro = emptyMap() }
    fun cambiarDescuento(texto: String) { descuentoTexto = texto; erroresCobro = emptyMap() }

    /** RF 3.1: un toque agrega 1 pieza; por peso pide el peso de la etiqueta. */
    fun tocar(p: ProductoEnVenta) {
        if (p.tipoVenta == TipoVenta.PESO) {
            pidiendoPeso = p; pesoTexto = ""; pesoError = null
        } else {
            aplicar(carrito.agregar(p))
        }
    }

    fun agregarPeso() {
        val p = pidiendoPeso ?: return
        when (val l = LectorNumeros.cantidad(pesoTexto, TipoVenta.PESO)) {
            is Lectura.Error -> pesoError = l.mensaje
            is Lectura.Valor -> {
                when (val r = carrito.agregar(p, l.valor)) {
                    is Resultado.Exito -> { carrito = r.valor; pidiendoPeso = null }
                    is Resultado.Fallo -> pesoError = (r.falla as? FallaMensaje)?.mensaje
                }
            }
        }
    }

    fun cancelarPeso() { pidiendoPeso = null }

    fun restar(p: ProductoEnVenta) = aplicar(carrito.fijar(p, carrito.cantidadDe(p.id) - 1000))
    fun quitar(id: String) { carrito = carrito.quitar(id) }
    fun vaciar() { carrito = Carrito() }

    private fun aplicar(r: Resultado<Carrito>) {
        when (r) {
            is Resultado.Exito -> carrito = r.valor
            is Resultado.Fallo -> (r.falla as? FallaMensaje)?.let { notificador.mostrar(it.mensaje) }
        }
    }

    fun abrirCobro() {
        if (carrito.estaVacio) { notificador.mostrar(Mensajes.AGREGUE_UN_PRODUCTO); return }
        if (caja.value == null) { notificador.mostrar(Mensajes.ABRA_CAJA); return }
        forma = formas.value.firstOrNull { it.esEfectivo } ?: formas.value.firstOrNull()
        recibido = ""
        descuentoTexto = ""
        tipoDescuento = TipoDescuento.PORCENTAJE
        erroresCobro = emptyMap()
        cobrando = true
    }

    fun cerrarCobro() { cobrando = false }

    fun elegirForma(f: FormaPago) { forma = f; erroresCobro = emptyMap() }
    fun cambiarRecibido(texto: String) { recibido = texto; erroresCobro = emptyMap() }

    /** RF 3.2: guarda la venta (todo o nada) y deja la pantalla lista para la siguiente. */
    fun confirmar() {
        if (confirmando) return
        viewModelScope.launch {
            confirmando = true
            cobrarVenta(carrito, forma, recibido.ifBlank { null }, descuento).atender(notificador) { erroresCobro = it }?.let { hecho ->
                notificador.mostrar((listOfNotNull(hecho.mensaje) + hecho.avisos).joinToString("\n"))
                carrito = Carrito()
                cobrando = false
                recibido = ""
                descuentoTexto = ""
            }
            confirmando = false
        }
    }

    private fun aTarjeta(p: ProductoConCategoria) = Tarjeta(
        ProductoEnVenta(p.producto.id, p.producto.nombre, p.producto.tipoVenta, p.producto.precioCentavos, p.producto.existenciaMilesimas),
        p.producto.imagenRuta,
    )
}
