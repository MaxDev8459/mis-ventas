package mx.ambulantpoint.misventas.ui.venta

import mx.ambulantpoint.misventas.domain.regla.ReglaTransferencia
import mx.ambulantpoint.misventas.domain.regla.DatosTransferencia
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import mx.ambulantpoint.misventas.data.local.entity.TipoDescuento
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import mx.ambulantpoint.misventas.domain.venta.FiltroVenta
import mx.ambulantpoint.misventas.domain.regla.MotivoAvisoCarga
import mx.ambulantpoint.misventas.domain.regla.ReglaCarga
import mx.ambulantpoint.misventas.domain.venta.ProductoListado
import mx.ambulantpoint.misventas.ui.components.IconoEstrellaVacia
import mx.ambulantpoint.misventas.ui.components.ControlCantidad
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.venta.RenglonCarrito
import mx.ambulantpoint.misventas.ui.catalogo.ImagenProducto
import mx.ambulantpoint.misventas.ui.components.ALTO_BOTON
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.vm

/**
 * RF 3.1 / 3.2 — Pantalla de venta. Vender un producto: tocar producto → Cobrar → (forma o monto
 * rápido) → Confirmar = 4 toques como máximo (RNF Usabilidad).
 */
@Composable
fun PantallaVenta(alRegresar: () -> Unit) {
    val modelo = vm { VentaViewModel(it) }
    if (modelo.cobrando) {
        BackHandler { modelo.cerrarCobro() }
        PantallaCobro(modelo)
    } else {
        PantallaCaptura(modelo, alRegresar)
    }
}

@Composable
private fun PantallaCaptura(modelo: VentaViewModel, alRegresar: () -> Unit) {
    val productos by modelo.productos.collectAsStateWithLifecycle()
    val busqueda by modelo.busqueda.collectAsStateWithLifecycle()
    val caja by modelo.caja.collectAsStateWithLifecycle()
    val categorias by modelo.categorias.collectAsStateWithLifecycle()
    val filtro by modelo.filtroVigente.collectAsStateWithLifecycle()
    val carga by modelo.carga.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { BarraSuperior("Vender", alRegresar) },
        bottomBar = { PanelCarrito(modelo) },
    ) { relleno ->
        Column(Modifier.padding(relleno).fillMaxSize()) {
            if (caja == null) {
                // RF 5.1: sin caja abierta no se vende.
                MensajeVacio(Mensajes.ABRA_CAJA)
                return@Column
            }
            OutlinedTextField(
                value = busqueda,
                onValueChange = { modelo.busqueda.value = it },
                placeholder = { Text("Buscar producto") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (categorias.isNotEmpty()) BarraFiltros(categorias, filtro, modelo::elegirFiltro)
            val lista = productos
            when {
                lista == null -> Unit
                // Sin productos para vender, o el chip/búsqueda no encontró nada.
                lista.isEmpty() && categorias.isEmpty() -> MensajeVacio(Mensajes.SIN_PRODUCTOS)
                lista.isEmpty() && filtro == FiltroVenta.Favoritos && busqueda.isBlank() ->
                    MensajeVacio("Toque la estrella de un producto para tenerlo aquí")
                lista.isEmpty() -> MensajeVacio("No hay productos que coincidan")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(lista, key = { it.producto.id }) { t ->
                        val enCarrito = modelo.carrito.cantidadDe(t.producto.id)
                        TarjetaProducto(
                            t, enCarrito,
                            lineaCarga = lineaCarga(carga, t.producto, enCarrito),
                            alTocar = { modelo.tocar(t.producto) },
                            alMantener = { modelo.pedirMerma(t.producto) },
                            alEstrella = { modelo.cambiarFavorito(t) },
                        )
                    }
                }
            }
        }
    }

    modelo.pidiendoPeso?.let { p ->
        AlertDialog(
            onDismissRequest = modelo::cancelarPeso,
            title = { Text(p.nombre) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${Formato.moneda(p.precioCentavos)} / kg · disponible ${Formato.cantidad(p.existenciaMilesimas, p.tipoVenta)}")
                    CampoTexto(
                        modelo.pesoTexto, { modelo.pesoTexto = it }, "Peso de la etiqueta *", error = modelo.pesoError,
                        teclado = KeyboardType.Decimal, sufijo = "kg", imeAction = ImeAction.Done,
                    )
                }
            },
            confirmButton = { TextButton(onClick = modelo::agregarPeso, modifier = Modifier.heightIn(min = 48.dp)) { Text("Agregar") } },
            dismissButton = { TextButton(onClick = modelo::cancelarPeso, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
        )
    }

    modelo.avisoCarga?.let { a -> DialogoAvisoCarga(a, modelo) }

    // Merma desde la venta: mantener presionado el producto.
    modelo.mermaDe?.let { p ->
        AlertDialog(
            onDismissRequest = modelo::cancelarMerma,
            title = { Text("Registrar merma") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${p.nombre} · hay ${Formato.cantidad(p.existenciaMilesimas, p.tipoVenta)}")
                    Text(
                        "Se descuenta del inventario como ajuste por merma (producto roto o perdido).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CampoTexto(
                        modelo.mermaTexto, modelo::cambiarMerma, "Cantidad *", error = modelo.mermaError,
                        teclado = if (p.tipoVenta == TipoVenta.PESO) KeyboardType.Decimal else KeyboardType.Number,
                        sufijo = Formato.unidad(p.tipoVenta), imeAction = ImeAction.Done,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = modelo::registrarMerma, enabled = !modelo.registrandoMerma, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Registrar")
                }
            },
            dismissButton = { TextButton(onClick = modelo::cancelarMerma, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TarjetaProducto(
    t: ProductoListado,
    enCarritoMilesimas: Long,
    lineaCarga: Pair<String, Boolean>?,
    alTocar: () -> Unit,
    alMantener: () -> Unit,
    alEstrella: () -> Unit,
) {
    val p = t.producto
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        // Dentro de la tarjeta para que el efecto del toque respete sus esquinas.
        Column(
            Modifier.combinedClickable(
                onClickLabel = "Agregar a la venta",
                onLongClickLabel = "Registrar merma",
                onLongClick = alMantener,
                onClick = alTocar,
            ),
        ) {
            Box {
                ImagenProducto(t.imagenRuta, Modifier.fillMaxWidth().aspectRatio(1.6f))
                // Favorito: la estrella va sobre la foto, con fondo para verse en cualquier imagen.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                ) {
                    IconButton(onClick = alEstrella, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (t.favorito) Icons.Filled.Star else IconoEstrellaVacia,
                            contentDescription = if (t.favorito) "Quitar de favoritos" else "Agregar a favoritos",
                            tint = if (t.favorito) DoradoFavorito else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(p.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Formato.moneda(p.precioCentavos)}${if (p.tipoVenta == TipoVenta.PESO) " / kg" else ""}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Hay ${Formato.cantidad(p.existenciaMilesimas, p.tipoVenta)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Carga del día: lo que queda de lo cargado (o que no se cargó).
                lineaCarga?.let { (texto, cargado) ->
                    Text(
                        texto,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (cargado) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (cargado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (enCarritoMilesimas > 0) {
                    Text(
                        "En la venta: ${Formato.cantidad(enCarritoMilesimas, p.tipoVenta)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private val DoradoFavorito = Color(0xFFF2A900)

/** Texto de la carga para la tarjeta (y si el producto está cargado); null si hoy no hay carga. */
private fun lineaCarga(
    carga: Map<String, mx.ambulantpoint.misventas.domain.regla.RenglonCarga>,
    p: mx.ambulantpoint.misventas.domain.venta.ProductoEnVenta,
    enCarrito: Long,
): Pair<String, Boolean>? {
    if (carga.isEmpty()) return null
    val r = carga[p.id] ?: return "No lo cargaste" to false
    return "Te quedan ${Formato.cantidad(ReglaCarga.quedanConVenta(r, enCarrito), p.tipoVenta)} de la carga" to true
}

/** No se cargó o ya no queda de la carga: agregar a la carga (fue por más) o vender igual. */
@Composable
private fun DialogoAvisoCarga(a: AvisoCarga, modelo: VentaViewModel) {
    val p = a.producto
    AlertDialog(
        onDismissRequest = modelo::cancelarAvisoCarga,
        title = { Text(if (a.motivo == MotivoAvisoCarga.NO_CARGADO) "No lo cargaste" else "Ya no te queda de la carga") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val r = a.renglon
                Text(
                    if (r == null) {
                        "${p.nombre} no está en la carga del día."
                    } else {
                        "${p.nombre}: cargaste ${Formato.cantidad(r.cargadoMilesimas, p.tipoVenta)}, " +
                            "vendiste ${Formato.cantidad(r.vendidoMilesimas, p.tipoVenta)}" +
                            (if (r.mermasMilesimas > 0) " y hubo ${Formato.cantidad(r.mermasMilesimas, p.tipoVenta)} de merma" else "") + "."
                    },
                )
                Text("¿Cuánto agregas a la carga?", style = MaterialTheme.typography.bodyLarge)
                val pasoKilo by modelo.pasoKilo.collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ControlCantidad(
                        modelo.agregarCargaMilesimas, p.tipoVenta, pasoKilo, modelo.maximoAgregarCarga(a), p.nombre,
                        modelo::cambiarAgregarCarga,
                    )
                    Text(Formato.unidad(p.tipoVenta), style = MaterialTheme.typography.bodyLarge)
                }
                modelo.agregarCargaError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = modelo::venderSinAgregar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Vender sin agregar") }
            }
        },
        confirmButton = {
            TextButton(onClick = modelo::agregarACarga, enabled = !modelo.agregandoCarga, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Agregar")
            }
        },
        dismissButton = { TextButton(onClick = modelo::cancelarAvisoCarga, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
    )
}

/** Chips que se deslizan: Todos, Favoritos y las categorías con productos para vender. */
@Composable
private fun BarraFiltros(
    categorias: List<mx.ambulantpoint.misventas.domain.venta.CategoriaChip>,
    elegido: FiltroVenta,
    alElegir: (FiltroVenta) -> Unit,
) {
    val opciones = listOf(FiltroVenta.Todos to "Todos", FiltroVenta.Favoritos to "★ Favoritos") +
        categorias.map { FiltroVenta.DeCategoria(it.id) to it.nombre }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(opciones, key = { it.first.toString() }) { (f, etiqueta) ->
            FilterChip(
                selected = elegido == f,
                onClick = { alElegir(f) },
                label = { Text(etiqueta, style = MaterialTheme.typography.labelLarge) },
                // 48 dp: área táctil mínima sin quitarle mucho espacio a los productos.
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** Productos agregados y total; el total se actualiza al agregar, cambiar o quitar (RF 3.1). */
@Composable
private fun PanelCarrito(modelo: VentaViewModel) {
    val carrito = modelo.carrito
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding().padding(12.dp)) {
            if (!carrito.estaVacio) {
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(carrito.renglones, key = { it.producto.id }) { r -> FilaCarrito(r, modelo) }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(Formato.moneda(carrito.subtotalCentavos), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.size(8.dp))
            BotonPrincipal("Cobrar", modelo::abrirCobro, habilitado = !carrito.estaVacio)
        }
    }
}

@Composable
private fun FilaCarrito(r: RenglonCarrito, modelo: VentaViewModel) {
    val p = r.producto
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Column(Modifier.weight(1f)) {
            Text(p.nombre, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Formato.cantidad(r.cantidadMilesimas, p.tipoVenta)} × ${Formato.moneda(p.precioCentavos)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (p.tipoVenta == TipoVenta.UNIDAD) {
            TextButton(onClick = { modelo.restar(p) }, modifier = Modifier.size(48.dp)) { Text("−", style = MaterialTheme.typography.titleLarge) }
            TextButton(onClick = { modelo.tocar(p) }, modifier = Modifier.size(48.dp)) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
        Text(Formato.moneda(r.importeCentavos), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(88.dp).padding(start = 4.dp))
        IconButton(onClick = { modelo.quitar(p.id) }) { Icon(Icons.Filled.Close, contentDescription = "Quitar ${p.nombre}") }
    }
}

/** RF 3.2 — Cobro: forma de pago, monto recibido y cambio antes de confirmar. RF 3.6: descuento. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PantallaCobro(modelo: VentaViewModel) {
    val formas by modelo.formas.collectAsStateWithLifecycle()
    val forma = modelo.forma
    val errores = modelo.erroresCobro
    val conDescuentos by modelo.descuentosHabilitados.collectAsStateWithLifecycle()
    val cobro = modelo.cobro
    val total = modelo.totalACobrar
    val datosTransferencia by modelo.datosTransferencia.collectAsStateWithLifecycle()

    Scaffold(topBar = { BarraSuperior("Cobrar", modelo::cerrarCobro) }) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // RF 3.6: con descuento aplicado se muestran subtotal, descuento en pesos y total a cobrar.
            if (cobro != null && cobro.descuentoCentavos > 0) {
                FilaImporte("Subtotal", Formato.moneda(cobro.subtotalCentavos))
                FilaImporte("Descuento", "−" + Formato.moneda(cobro.descuentoCentavos))
            }
            Text("Total a cobrar", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Formato.moneda(total), style = MaterialTheme.typography.displaySmall)

            if (conDescuentos) {
                Text("Descuento", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TipoDescuento.PORCENTAJE to "Porcentaje", TipoDescuento.MONTO_FIJO to "Monto fijo").forEach { (t, etiqueta) ->
                        FilterChip(
                            selected = modelo.tipoDescuento == t,
                            onClick = { modelo.elegirTipoDescuento(t) },
                            label = { Text(etiqueta, style = MaterialTheme.typography.labelLarge) },
                            modifier = Modifier.heightIn(min = ALTO_BOTON),
                        )
                    }
                }
                CampoTexto(
                    modelo.descuentoTexto, modelo::cambiarDescuento,
                    if (modelo.tipoDescuento == TipoDescuento.PORCENTAJE) "Porcentaje de descuento" else "Monto a descontar",
                    error = modelo.errorDescuento, teclado = KeyboardType.Decimal,
                    prefijo = "$".takeIf { modelo.tipoDescuento == TipoDescuento.MONTO_FIJO },
                    sufijo = "%".takeIf { modelo.tipoDescuento == TipoDescuento.PORCENTAJE },
                )
            }

            Text("Forma de pago *", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formas.forEach { f ->
                    FilterChip(
                        selected = forma?.id == f.id,
                        onClick = { modelo.elegirForma(f) },
                        label = { Text(f.nombre, style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.heightIn(min = ALTO_BOTON),
                    )
                }
            }
            errores[Campos.FORMA_PAGO]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (forma?.esEfectivo == true) {
                CampoTexto(
                    modelo.recibido, modelo::cambiarRecibido, "Monto recibido *",
                    error = errores[Campos.MONTO_RECIBIDO], teclado = KeyboardType.Decimal, prefijo = "$", imeAction = ImeAction.Done,
                )
                // Montos rápidos: evitan teclear en la calle (RNF: máximo 4 toques).
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    montosRapidos(total).forEach { (etiqueta, centavos) ->
                        OutlinedButton(
                            onClick = { modelo.cambiarRecibido("${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}") },
                            modifier = Modifier.heightIn(min = ALTO_BOTON),
                        ) { Text(etiqueta) }
                    }
                }
                val cambio = modelo.resumen?.cambioCentavos
                Text(
                    if (cambio != null) "Cambio a entregar: ${Formato.moneda(cambio)}" else "Cambio a entregar: —",
                    style = MaterialTheme.typography.headlineMedium,
                )
            } else if (forma != null) {
                // SC-01: con transferencia, los datos del vendedor para mostrárselos al cliente.
                if (ReglaTransferencia.esTransferencia(forma.nombre)) {
                    if (datosTransferencia != null) {
                        OutlinedButton(
                            onClick = { modelo.mostrandoDatosTransferencia = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_BOTON),
                        ) { Text("Mostrar datos para transferencia") }
                    } else {
                        Text(
                            "Puede capturar sus datos para transferencia en Formas de pago.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Verifique que recibió el pago antes de confirmar.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(4.dp))
            BotonPrincipal("Confirmar cobro", modelo::confirmar, cargando = modelo.confirmando)
        }
    }

    val datos = datosTransferencia
    if (modelo.mostrandoDatosTransferencia && datos != null) {
        DialogoDatosTransferencia(datos, total) { modelo.mostrandoDatosTransferencia = false }
    }
}

/**
 * SC-01 — Datos para transferencia en letra grande, para que el cliente los lea o les tome foto;
 * "Compartir" se los manda (WhatsApp, SMS…) para que los copie en su app del banco.
 */
@Composable
private fun DialogoDatosTransferencia(datos: DatosTransferencia, totalCentavos: Long, alCerrar: () -> Unit) {
    val contexto = LocalContext.current
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Datos para transferencia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DatoGrande("Banco", datos.banco)
                DatoGrande("Beneficiario", datos.beneficiario)
                DatoGrande("CLABE", ReglaTransferencia.clabeEnGrupos(datos.clabe))
                DatoGrande("Total", Formato.moneda(totalCentavos))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val envio = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, ReglaTransferencia.textoParaCompartir(datos, totalCentavos))
                }
                contexto.startActivity(Intent.createChooser(envio, "Compartir datos para transferencia"))
            }) { Text("Compartir") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}

@Composable
private fun DatoGrande(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun FilaImporte(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(etiqueta, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "Exacto" y los siguientes billetes comunes que alcanzan el total. */
private fun montosRapidos(total: Long): List<Pair<String, Long>> {
    val billetes = listOf(2_000L, 5_000L, 10_000L, 20_000L, 50_000L, 100_000L)
    return listOf("Exacto" to total) + billetes.filter { it > total }.take(3).map { Formato.moneda(it) to it }
}
