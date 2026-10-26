package mx.ambulantpoint.misventas.ui.inventario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ambulantpoint.misventas.AppContainer
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.MovimientoInventario
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.TipoMovimiento
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.usecase.AjustarExistencia
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.domain.usecase.CambioExistencia
import mx.ambulantpoint.misventas.domain.usecase.EvaluarExistenciaBaja
import mx.ambulantpoint.misventas.domain.usecase.Hecho
import mx.ambulantpoint.misventas.domain.usecase.ProductoRef
import mx.ambulantpoint.misventas.domain.usecase.RegistrarEntrada
import mx.ambulantpoint.misventas.ui.catalogo.ImagenProducto
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.MensajeVacio
import mx.ambulantpoint.misventas.ui.components.Notificador
import mx.ambulantpoint.misventas.ui.components.atender
import mx.ambulantpoint.misventas.ui.components.vm

/** Diálogo abierto en la pantalla. */
sealed interface Captura {
    data class Entrada(val cantidad: String = "", val error: String? = null) : Captura
    data class Ajuste(
        val aumentar: Boolean? = null,
        val cantidad: String = "",
        val motivo: MotivoAjuste? = null,
        val especificacion: String = "",
        val errores: Map<String, String> = emptyMap(),
    ) : Captura
}

class ProductoInventarioViewModel(c: AppContainer, productoId: String) : ViewModel() {
    private val existenciaBaja = EvaluarExistenciaBaja(c.reporteRepo)
    private val registrarEntrada = RegistrarEntrada(c.inventarioRepo, existenciaBaja)
    private val ajustarExistencia = AjustarExistencia(c.inventarioRepo, existenciaBaja)
    private val notificador: Notificador = c.notificador

    val producto: StateFlow<Producto?> = c.productoRepo.observarPorId(productoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** RF 4.4: movimientos del producto, del más reciente al más antiguo. */
    val movimientos: StateFlow<List<MovimientoInventario>?> = c.inventarioRepo.observarMovimientos(productoId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var captura by mutableStateOf<Captura?>(null)

    private fun ref(): ProductoRef? = producto.value?.let { ProductoRef(it.id, it.nombre, it.tipoVenta) }

    /** RF 4.1. */
    fun guardarEntrada() {
        val e = captura as? Captura.Entrada ?: return
        viewModelScope.launch {
            registrarEntrada(ref(), e.cantidad).atender(notificador) { captura = e.copy(error = it[Campos.CANTIDAD]) }?.let(::terminar)
        }
    }

    /** RF 4.2. */
    fun guardarAjuste() {
        val a = captura as? Captura.Ajuste ?: return
        viewModelScope.launch {
            ajustarExistencia(ref(), a.aumentar, a.cantidad, a.motivo, a.especificacion)
                .atender(notificador) { captura = a.copy(errores = it) }?.let(::terminar)
        }
    }

    /** "La entrada se registró exitosamente" y la nueva existencia (+ aviso de existencia baja). */
    private fun terminar(h: Hecho<CambioExistencia>) {
        captura = null
        notificador.mostrar((listOfNotNull(h.mensaje?.let { "$it. Existencia: ${h.valor.existenciaTexto}" }) + h.avisos).joinToString("\n"))
    }
}

fun etiquetaTipo(t: TipoMovimiento) = when (t) {
    TipoMovimiento.ENTRADA -> "Entrada de mercancía"
    TipoMovimiento.VENTA -> "Venta"
    TipoMovimiento.CANCELACION -> "Cancelación de venta"
    TipoMovimiento.AJUSTE -> "Ajuste"
}

fun etiquetaMotivo(m: MotivoAjuste) = when (m) {
    MotivoAjuste.MERMA -> "Merma"
    MotivoAjuste.PRODUCTO_CADUCADO -> "Producto caducado"
    MotivoAjuste.CONSUMO_PROPIO -> "Consumo propio"
    MotivoAjuste.CORRECCION_CONTEO -> "Corrección de conteo"
    MotivoAjuste.OTRO -> "Otro"
}

/** RF 4.1 Entrada, RF 4.2 Ajuste y RF 4.4 Movimientos de un producto. */
@Composable
fun PantallaProductoInventario(productoId: String, alRegresar: () -> Unit) {
    val modelo = vm(key = productoId) { ProductoInventarioViewModel(it, productoId) }
    val producto by modelo.producto.collectAsStateWithLifecycle()
    val movimientos by modelo.movimientos.collectAsStateWithLifecycle()
    val p = producto

    Scaffold(topBar = { BarraSuperior(p?.nombre ?: "Existencias", alRegresar) }) { relleno ->
        LazyColumn(Modifier.padding(relleno).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            if (p != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ImagenProducto(p.imagenRuta, Modifier.size(72.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Existencia actual", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formato.cantidad(p.existenciaMilesimas, p.tipoVenta), style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    BotonPrincipal("Registrar entrada de mercancía", { modelo.captura = Captura.Entrada() })
                    Spacer(Modifier.height(8.dp))
                    BotonSecundario("Ajustar existencia", { modelo.captura = Captura.Ajuste() })
                    Spacer(Modifier.height(20.dp))
                    Text("Movimientos de inventario", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                }
                val lista = movimientos
                if (lista != null && lista.isEmpty()) item { MensajeVacio(Mensajes.SIN_MOVIMIENTOS_PERIODO) }
                items(lista.orEmpty(), key = { it.id }) { m ->
                    FilaMovimiento(m, p.tipoVenta)
                    HorizontalDivider()
                }
            }
        }
    }

    when (val c = modelo.captura) {
        is Captura.Entrada -> DialogoEntrada(c, p, modelo)
        is Captura.Ajuste -> DialogoAjuste(c, p, modelo)
        null -> Unit
    }
}

/** Fecha y hora, tipo, cantidad con signo, motivo y existencia resultante (RF 4.4). */
@Composable
private fun FilaMovimiento(m: MovimientoInventario, tipoVenta: TipoVenta) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(etiquetaTipo(m.tipo), style = MaterialTheme.typography.bodyLarge)
            Text(Formato.fechaHora(m.fechaHora), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            m.motivo?.let { mot ->
                Text(
                    "Motivo: ${etiquetaMotivo(mot)}${m.especificacionMotivo?.let { " — $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Formato.cantidadConSigno(m.cantidadMilesimas, tipoVenta),
                style = MaterialTheme.typography.bodyLarge,
                color = if (m.cantidadMilesimas < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text("Queda ${Formato.cantidad(m.existenciaResultanteMilesimas, tipoVenta)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DialogoEntrada(c: Captura.Entrada, p: Producto?, modelo: ProductoInventarioViewModel) {
    val kg = p?.tipoVenta == TipoVenta.PESO
    AlertDialog(
        onDismissRequest = { modelo.captura = null },
        title = { Text("Entrada de mercancía") },
        text = {
            CampoTexto(
                c.cantidad, { modelo.captura = c.copy(cantidad = it, error = null) },
                if (kg) "Cantidad que entra (kg) *" else "Cantidad que entra (pzas) *",
                error = c.error, teclado = if (kg) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Done,
            )
        },
        confirmButton = { TextButton(onClick = modelo::guardarEntrada) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { modelo.captura = null }) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoAjuste(c: Captura.Ajuste, p: Producto?, modelo: ProductoInventarioViewModel) {
    val kg = p?.tipoVenta == TipoVenta.PESO
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { modelo.captura = null },
        title = { Text("Ajustar existencia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tipo de ajuste *", style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "Disminuir", true to "Aumentar").forEachIndexed { i, (aumentar, texto) ->
                        SegmentedButton(
                            selected = c.aumentar == aumentar,
                            onClick = { modelo.captura = c.copy(aumentar = aumentar, errores = c.errores - Campos.TIPO_VENTA) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(texto) }
                    }
                }
                c.errores[Campos.TIPO_VENTA]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                CampoTexto(
                    c.cantidad, { modelo.captura = c.copy(cantidad = it, errores = c.errores - Campos.CANTIDAD) },
                    if (kg) "Cantidad (kg) *" else "Cantidad (pzas) *",
                    error = c.errores[Campos.CANTIDAD], teclado = if (kg) KeyboardType.Decimal else KeyboardType.Number,
                )
                ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                    OutlinedTextField(
                        value = c.motivo?.let(::etiquetaMotivo).orEmpty(), onValueChange = {}, readOnly = true,
                        label = { Text("Motivo *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) },
                        isError = c.errores[Campos.MOTIVO] != null,
                        supportingText = c.errores[Campos.MOTIVO]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MotivoAjuste.entries.forEach { m ->
                            DropdownMenuItem(text = { Text(etiquetaMotivo(m)) }, onClick = {
                                modelo.captura = c.copy(motivo = m, errores = c.errores - Campos.MOTIVO); menu = false
                            })
                        }
                    }
                }
                if (c.motivo == MotivoAjuste.OTRO) {
                    CampoTexto(
                        c.especificacion, { modelo.captura = c.copy(especificacion = it, errores = c.errores - Campos.ESPECIFICACION) },
                        "Especifique el motivo *", error = c.errores[Campos.ESPECIFICACION], imeAction = ImeAction.Done,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = modelo::guardarAjuste) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { modelo.captura = null }) { Text("Cancelar") } },
    )
}
