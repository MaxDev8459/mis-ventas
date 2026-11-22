package mx.ambulantpoint.misventas.ui.venta

import mx.ambulantpoint.misventas.data.repository.datosTransferencia
import mx.ambulantpoint.misventas.domain.regla.DatosTransferencia
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
import mx.ambulantpoint.misventas.domain.venta.CategoriaChip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import mx.ambulantpoint.misventas.core.Dinero
import mx.ambulantpoint.misventas.data.repository.FallaDatos
import mx.ambulantpoint.misventas.domain.regla.MotivoAvisoCarga
import mx.ambulantpoint.misventas.domain.regla.ReglaCarga
import mx.ambulantpoint.misventas.domain.regla.RenglonCarga
import mx.ambulantpoint.misventas.domain.venta.FiltroVenta
import mx.ambulantpoint.misventas.domain.venta.ProductoListado
import mx.ambulantpoint.misventas.domain.venta.ReglaFiltroVenta
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.domain.usecase.AjustarExistencia
import mx.ambulantpoint.misventas.domain.usecase.ProductoRef

/** Aviso de carga: el producto no se cargó o ya no queda de lo cargado. */
data class AvisoCarga(
    val producto: ProductoEnVenta,
    val cantidadMilesimas: Long,
    val motivo: MotivoAvisoCarga,
    val renglon: RenglonCarga?,
)

/** RF 3.1 Registrar venta y RF 3.2 Cobrar venta. */
class VentaViewModel(c: AppContainer) : ViewModel() {
    private val cobrarVenta = CobrarVenta(c.ventaRepo, EvaluarExistenciaBaja(c.reporteRepo))
    private val notificador = c.notificador
    private val ajustarExistencia = AjustarExistencia(c.inventarioRepo, EvaluarExistenciaBaja(c.reporteRepo))

    private val productoRepo = c.productoRepo

    val busqueda = MutableStateFlow("")

    /** Chip elegido: Todos, Favoritos o una categoría. */
    private val filtro = MutableStateFlow<FiltroVenta>(FiltroVenta.Todos)

    /** RF 3.1: solo productos activos con existencia mayor a cero (en orden alfabético). */
    private val listados = productoRepo.observarParaVenta().map { l -> l.map(::aListado) }

    /** Chips de categoría: solo las que tienen productos para vender. */
    val categorias: StateFlow<List<CategoriaChip>> = listados.map(ReglaFiltroVenta::categorias)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filtroVigente: StateFlow<FiltroVenta> = combine(filtro, listados) { f, l -> ReglaFiltroVenta.vigente(f, l) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FiltroVenta.Todos)

    /** Lo que se muestra: búsqueda + chip, con los favoritos primero. */
    val productos: StateFlow<List<ProductoListado>?> = combine(listados, busqueda, filtro) { l, texto, f ->
        ReglaFiltroVenta.aplicar(l, texto, ReglaFiltroVenta.vigente(f, l))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun elegirFiltro(f: FiltroVenta) { filtro.value = f }

    /** Marca o desmarca con la estrella; se respalda en Firebase como cualquier cambio del producto. */
    fun cambiarFavorito(p: ProductoListado) {
        viewModelScope.launch { productoRepo.cambiarFavorito(p.producto.id, !p.favorito) }
    }

    /** RF 3.2: solo formas de pago activas. Se carga desde el inicio para preseleccionar Efectivo. */
    val formas: StateFlow<List<FormaPago>> = c.formaPagoRepo.observarActivas()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val caja: StateFlow<Caja?> = c.cajaRepo.observarAbierta()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val cargaRepo = c.cargaRepo

    /** Carga del día por producto (vacía si no se capturó: entonces no hay avisos). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val carga: StateFlow<Map<String, RenglonCarga>> = c.cajaRepo.observarAbierta()
        .flatMapLatest { k -> if (k == null) flowOf(emptyList()) else cargaRepo.observarResumen(k.id, k.abiertaEn) }
        .map { l -> l.associateBy { it.productoId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    var avisoCarga by mutableStateOf<AvisoCarga?>(null)
        private set
    var agregarCargaTexto by mutableStateOf("")
    var agregarCargaError by mutableStateOf<String?>(null)
        private set
    var agregandoCarga by mutableStateOf(false)
        private set

    /** RF 3.6: solo con descuentos habilitados se muestra la opción al cobrar. */
    val descuentosHabilitados: StateFlow<Boolean> = c.configuracionRepo.observar()
        .map { it?.descuentosHabilitados == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** SC-01: datos para recibir transferencias (null si el vendedor no los ha capturado). */
    val datosTransferencia: StateFlow<DatosTransferencia?> = c.configuracionRepo.observar()
        .map { it.datosTransferencia() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** SC-01: ventana con los datos para transferencia, para mostrársela al cliente. */
    var mostrandoDatosTransferencia by mutableStateOf(false)

    /** "Corregir venta": si viene de corregir una venta, el carrito llega ya cargado. */
    var carrito by mutableStateOf(c.carritoPorCorregir.value ?: Carrito())
        private set

    init { c.carritoPorCorregir.value = null }

    /** Merma desde la venta (producto roto): ajuste de inventario con motivo Merma. */
    var mermaDe by mutableStateOf<ProductoEnVenta?>(null)
        private set
    var mermaTexto by mutableStateOf("")
    var mermaError by mutableStateOf<String?>(null)
        private set
    var registrandoMerma by mutableStateOf(false)
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
            agregarRevisandoCarga(p, Dinero.MILESIMAS_POR_UNIDAD) { msg -> notificador.mostrar(msg) }
        }
    }

    /**
     * Agrega a la venta; si hay carga del día y el producto no se cargó o ya no queda, primero avisa
     * (agregar a la carga o vender igual). [alFallar] recibe el mensaje si no hay existencia.
     */
    private fun agregarRevisandoCarga(p: ProductoEnVenta, cantidad: Long, alFallar: (String) -> Unit): Boolean {
        val r = carrito.agregar(p, cantidad)
        if (r is Resultado.Fallo) { (r.falla as? FallaMensaje)?.let { alFallar(it.mensaje) }; return false }
        val mapa = carga.value
        val motivo = ReglaCarga.aviso(mapa.isNotEmpty(), mapa[p.id], carrito.cantidadDe(p.id), cantidad)
        if (motivo == null) {
            carrito = (r as Resultado.Exito).valor
        } else {
            avisoCarga = AvisoCarga(p, cantidad, motivo, mapa[p.id])
            agregarCargaTexto = ""
            agregarCargaError = null
        }
        return true
    }

    fun cambiarAgregarCarga(t: String) { agregarCargaTexto = t; agregarCargaError = null }
    fun cancelarAvisoCarga() { avisoCarga = null }

    /** "Vender sin agregar": se agrega a la venta aunque no esté en la carga. */
    fun venderSinAgregar() {
        val a = avisoCarga ?: return
        avisoCarga = null
        aplicar(carrito.agregar(a.producto, a.cantidadMilesimas))
    }

    /** "Agregar a la carga": suma a lo cargado y agrega el producto a la venta. */
    fun agregarACarga() {
        val a = avisoCarga ?: return
        val k = caja.value ?: return
        if (agregandoCarga) return
        val cantidad = when (val l = LectorNumeros.cantidad(agregarCargaTexto, a.producto.tipoVenta)) {
            is Lectura.Error -> { agregarCargaError = l.mensaje; return }
            is Lectura.Valor -> l.valor
        }
        viewModelScope.launch {
            agregandoCarga = true
            when (val r = cargaRepo.agregar(k.id, a.producto.id, cantidad)) {
                is Resultado.Exito -> {
                    avisoCarga = null
                    aplicar(carrito.agregar(a.producto, a.cantidadMilesimas))
                    notificador.mostrar("Se agregó a la carga del día")
                }
                is Resultado.Fallo -> agregarCargaError =
                    (r.falla as? FallaDatos.CargaExcedeExistencia)?.mensaje ?: "No se pudo agregar a la carga"
            }
            agregandoCarga = false
        }
    }

    fun agregarPeso() {
        val p = pidiendoPeso ?: return
        when (val l = LectorNumeros.cantidad(pesoTexto, TipoVenta.PESO)) {
            is Lectura.Error -> pesoError = l.mensaje
            is Lectura.Valor -> {
                if (agregarRevisandoCarga(p, l.valor) { pesoError = it }) pidiendoPeso = null
            }
        }
    }

    fun cancelarPeso() { pidiendoPeso = null }

    fun pedirMerma(p: ProductoEnVenta) { mermaDe = p; mermaTexto = ""; mermaError = null }
    fun cancelarMerma() { mermaDe = null }
    fun cambiarMerma(t: String) { mermaTexto = t; mermaError = null }

    fun registrarMerma() {
        val p = mermaDe ?: return
        if (registrandoMerma) return
        viewModelScope.launch {
            registrandoMerma = true
            ajustarExistencia(ProductoRef(p.id, p.nombre, p.tipoVenta), false, mermaTexto, MotivoAjuste.MERMA, null)
                .atender(notificador) { mermaError = it.values.firstOrNull() }
                ?.let { hecho ->
                    mermaDe = null
                    // Si en la venta había más de lo que ahora queda, se ajusta el carrito a la existencia nueva.
                    val nueva = hecho.valor.existenciaMilesimas
                    val enCarrito = carrito.cantidadDe(p.id)
                    if (enCarrito > 0) {
                        val actualizado = p.copy(existenciaMilesimas = nueva)
                        carrito = (carrito.fijar(actualizado, minOf(enCarrito, nueva)) as? Resultado.Exito)?.valor
                            ?: carrito.quitar(p.id)
                    }
                    notificador.mostrar((listOf(Mensajes.MERMA_REGISTRADA) + hecho.avisos).joinToString("\n"))
                }
            registrandoMerma = false
        }
    }

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

    private fun aListado(p: ProductoConCategoria) = ProductoListado(
        producto = ProductoEnVenta(p.producto.id, p.producto.nombre, p.producto.tipoVenta, p.producto.precioCentavos, p.producto.existenciaMilesimas),
        categoriaId = p.producto.categoriaId,
        categoriaNombre = p.categoriaNombre,
        favorito = p.producto.favorito,
        imagenRuta = p.producto.imagenRuta,
    )
}
