package mx.ambulantpoint.misventas.ui.catalogo

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.usecase.Campos
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.BotonPrincipal
import mx.ambulantpoint.misventas.ui.components.BotonSecundario
import mx.ambulantpoint.misventas.ui.components.CampoTexto
import mx.ambulantpoint.misventas.ui.components.DialogoConfirmacion
import mx.ambulantpoint.misventas.ui.components.contenedor
import mx.ambulantpoint.misventas.ui.components.vm

private const val NUEVA_CATEGORIA = "__nueva__"

/** RF 2.1 Registrar producto / RF 2.2 Editar / RF 2.3 Desactivar / RF 2.4 Reactivar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaProductoForm(productoId: String?, alTerminar: () -> Unit) {
    val modelo = vm(key = productoId ?: "nuevo") { ProductoFormViewModel(it, productoId) }
    val categorias by modelo.categorias.collectAsStateWithLifecycle()
    val f = modelo.formulario
    val e = modelo.errores
    val procesador = contenedor().procesadorImagen
    val tieneCamara = LocalContext.current.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    LaunchedEffect(modelo.terminado) { if (modelo.terminado) alTerminar() }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(modelo::imagen)
    }
    var uriCamara by rememberSaveable { mutableStateOf<String?>(null) }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) uriCamara?.let { modelo.imagen(Uri.parse(it)) }
    }

    Scaffold(topBar = { BarraSuperior(if (modelo.esNuevo) "Nuevo producto" else "Editar producto", alTerminar) }) { relleno ->
        Column(
            Modifier.padding(relleno).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Imagen de referencia *
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ImagenProducto(f.imagenRuta, Modifier.size(160.dp))
                if (modelo.procesandoImagen) CircularProgressIndicator()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tieneCamara) {
                    BotonSecundario("Tomar foto", {
                        val uri = procesador.uriParaCamara()
                        uriCamara = uri.toString()
                        camara.launch(uri)
                    }, Modifier.weight(1f))
                }
                BotonSecundario("Galería", {
                    galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, Modifier.weight(1f))
            }
            e[Campos.IMAGEN]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(4.dp))
            CampoTexto(f.nombre, { modelo.cambiar(f.copy(nombre = it)) }, "Nombre del producto *", error = e[Campos.NOMBRE])

            // Categoría * (con opción de crear una nueva desde aquí)
            var abierto by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
                OutlinedTextField(
                    value = categorias.firstOrNull { it.id == f.categoriaId }?.nombre.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(abierto) },
                    isError = e[Campos.CATEGORIA] != null,
                    supportingText = e[Campos.CATEGORIA]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                    categorias.forEach { cat ->
                        DropdownMenuItem(text = { Text(cat.nombre) }, onClick = {
                            modelo.cambiar(f.copy(categoriaId = cat.id)); abierto = false
                        })
                    }
                    DropdownMenuItem(text = { Text("+ Nueva categoría", color = MaterialTheme.colorScheme.primary) }, onClick = {
                        abierto = false; modelo.nuevaCategoria = NuevaCategoria()
                    })
                }
            }

            // Tipo de venta *
            Text("Tipo de venta *", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TipoVenta.UNIDAD to "Por unidad", TipoVenta.PESO to "Por peso (kg)").forEachIndexed { i, (tipo, texto) ->
                    SegmentedButton(
                        selected = f.tipoVenta == tipo,
                        onClick = { modelo.cambiar(f.copy(tipoVenta = tipo)) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(texto) }
                }
            }
            e[Campos.TIPO_VENTA]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            val unidad = f.tipoVenta?.let { if (it == TipoVenta.UNIDAD) "pza" else "kg" }
            CampoTexto(
                f.precio, { modelo.cambiar(f.copy(precio = it)) },
                if (f.tipoVenta == TipoVenta.PESO) "Precio por kilogramo *" else "Precio de venta *",
                error = e[Campos.PRECIO], teclado = KeyboardType.Decimal, prefijo = "$", sufijo = unidad?.let { "/ $it" },
            )

            val original = modelo.original
            if (modelo.esNuevo) {
                CampoTexto(
                    f.existencia, { modelo.cambiar(f.copy(existencia = it)) },
                    if (f.tipoVenta == TipoVenta.PESO) "Existencia inicial (kg) *" else "Existencia inicial (pzas) *",
                    error = e[Campos.EXISTENCIA],
                    teclado = if (f.tipoVenta == TipoVenta.PESO) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                )
            } else if (original != null) {
                Text(
                    "Existencia: ${Formato.cantidad(original.existenciaMilesimas, original.tipoVenta)} " +
                        "(se modifica en Existencias)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            BotonPrincipal("Guardar", modelo::guardar, cargando = modelo.guardando)
            if (original != null) {
                BotonSecundario(
                    if (original.activo) "Desactivar producto" else "Reactivar producto",
                    { if (original.activo) modelo.confirmarDesactivar = true else modelo.alternarEstado() },
                )
            }
        }
    }

    modelo.nuevaCategoria?.let { nc ->
        AlertDialog(
            onDismissRequest = { modelo.nuevaCategoria = null },
            title = { Text("Nueva categoría") },
            text = {
                CampoTexto(nc.nombre, { modelo.nuevaCategoria = nc.copy(nombre = it, error = null) }, "Nombre de la categoría *", error = nc.error, imeAction = ImeAction.Done)
            },
            confirmButton = { TextButton(onClick = modelo::guardarCategoria) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { modelo.nuevaCategoria = null }) { Text("Cancelar") } },
        )
    }

    if (modelo.confirmarDesactivar) {
        DialogoConfirmacion(
            texto = Mensajes.confirmarDesactivarProducto(modelo.original?.nombre.orEmpty()),
            alConfirmar = modelo::alternarEstado,
            alCancelar = { modelo.confirmarDesactivar = false },
        )
    }
}
