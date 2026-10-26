package mx.ambulantpoint.misventas.ui.venta

import androidx.activity.compose.BackHandler
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
            val lista = productos
            when {
                lista == null -> Unit
                lista.isEmpty() -> MensajeVacio(Mensajes.SIN_PRODUCTOS)
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(lista, key = { it.producto.id }) { t ->
                        TarjetaProducto(t, modelo.carrito.cantidadDe(t.producto.id)) { modelo.tocar(t.producto) }
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
}

@Composable
private fun TarjetaProducto(t: Tarjeta, enCarritoMilesimas: Long, alTocar: () -> Unit) {
    val p = t.producto
    ElevatedCard(onClick = alTocar, modifier = Modifier.fillMaxWidth()) {
        Column {
            ImagenProducto(t.imagenRuta, Modifier.fillMaxWidth().aspectRatio(1.6f))
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

/** RF 3.2 — Cobro: forma de pago, monto recibido y cambio antes de confirmar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PantallaCobro(modelo: VentaViewModel) {
    val formas by modelo.formas.collectAsStateWithLifecycle()
    val carrito = modelo.carrito
    val total = carrito.subtotalCentavos
    val forma = modelo.forma
    val errores = modelo.erroresCobro

    Scaffold(topBar = { BarraSuperior("Cobrar", modelo::cerrarCobro) }) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Total a cobrar", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Formato.moneda(total), style = MaterialTheme.typography.displaySmall)

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
}

/** "Exacto" y los siguientes billetes comunes que alcanzan el total. */
private fun montosRapidos(total: Long): List<Pair<String, Long>> {
    val billetes = listOf(2_000L, 5_000L, 10_000L, 20_000L, 50_000L, 100_000L)
    return listOf("Exacto" to total) + billetes.filter { it > total }.take(3).map { Formato.moneda(it) to it }
}
