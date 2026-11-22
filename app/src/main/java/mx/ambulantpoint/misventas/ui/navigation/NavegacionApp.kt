package mx.ambulantpoint.misventas.ui.navigation

import mx.ambulantpoint.misventas.ui.perfil.PantallaEditarPerfil
import mx.ambulantpoint.misventas.data.notificaciones.DestinoNotificacion
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import mx.ambulantpoint.misventas.domain.regla.ReglaVerificacion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.domain.puertos.EstadoSesion
import mx.ambulantpoint.misventas.ui.auth.PantallaLogin
import mx.ambulantpoint.misventas.ui.auth.PantallaRegistro
import mx.ambulantpoint.misventas.ui.caja.PantallaCaja
import mx.ambulantpoint.misventas.ui.caja.PantallaCarga
import mx.ambulantpoint.misventas.ui.caja.PantallaCorte
import mx.ambulantpoint.misventas.ui.caja.PantallaCortes
import mx.ambulantpoint.misventas.ui.caja.PantallaDetalleCorte
import mx.ambulantpoint.misventas.ui.inventario.PantallaExistencias
import mx.ambulantpoint.misventas.ui.inventario.PantallaProductoInventario
import mx.ambulantpoint.misventas.ui.historial.PantallaDetalleVenta
import mx.ambulantpoint.misventas.ui.historial.PantallaHistorial
import mx.ambulantpoint.misventas.ui.venta.PantallaVenta
import mx.ambulantpoint.misventas.ui.catalogo.PantallaCatalogo
import mx.ambulantpoint.misventas.ui.catalogo.PantallaProductoForm
import mx.ambulantpoint.misventas.ui.categorias.PantallaCategorias
import mx.ambulantpoint.misventas.ui.formaspago.PantallaFormasPago
import mx.ambulantpoint.misventas.ui.auth.PantallaVerificacion
import mx.ambulantpoint.misventas.ui.auth.PantallaRecuperacion
import mx.ambulantpoint.misventas.ui.reportes.PantallaReporteInventario
import mx.ambulantpoint.misventas.ui.reportes.PantallaReporteMasVendidos
import mx.ambulantpoint.misventas.ui.reportes.PantallaReportePorcentajes
import mx.ambulantpoint.misventas.ui.reportes.PantallaReporteVentas
import mx.ambulantpoint.misventas.ui.reportes.PantallaReportes
import mx.ambulantpoint.misventas.ui.reportes.Reporte
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.contenedor
import mx.ambulantpoint.misventas.ui.inicio.Destino
import mx.ambulantpoint.misventas.ui.inicio.PantallaInicio
import mx.ambulantpoint.misventas.ui.perfil.PantallaPerfil

private object Ruta {
    const val REGISTRO = "registro"
    const val LOGIN = "login"
    const val INICIO = "inicio"
    const val PRODUCTO = "producto"
    const val PRODUCTO_NUEVO = "producto_nuevo"
    const val VENTA = "venta"
    const val CORTE = "corte"
    const val CORTES = "cortes"
    const val CARGA = "carga"
    const val INVENTARIO = "inventario"
    const val REPORTE = "reporte"
    const val PERFIL_EDITAR = "perfil_editar"
    const val CLIENTE = "cliente"
    const val APARTADO = "apartado"
    fun de(destino: Destino) = destino.name.lowercase()
}

/**
 * Raíz de la app: según la sesión muestra el acceso (registro / login) o la app. Cerrar o iniciar
 * sesión cambia de grafo automáticamente. Los mensajes del notificador salen en la barra inferior.
 */
@Composable
fun NavegacionApp() {
    val c = contenedor()
    val sesion by c.auth.estado.collectAsStateWithLifecycle()
    val verificada by c.identidadVerificada.collectAsStateWithLifecycle()
    val reverificar by c.reverificacionPendiente.collectAsStateWithLifecycle()
    val actividad = LocalContext.current as? Activity
    // Al regresar de segundo plano después del tiempo elegido en Perfil, se vuelve a verificar la identidad.
    var ausenteDesde by remember { mutableLongStateOf(0L) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { ausenteDesde = SystemClock.elapsedRealtime() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        val pedir = ReglaVerificacion.debeVerificar(
            tiempo = c.preferencias.reverificacion.value,
            sesionActiva = c.auth.estado.value == EstadoSesion.ACTIVA,
            yaBloqueada = !c.identidadVerificada.value || c.reverificacionPendiente.value,
            ausenteDesde = ausenteDesde,
            ahora = SystemClock.elapsedRealtime(),
        )
        ausenteDesde = 0L
        if (pedir) c.reverificacionPendiente.value = true
    }
    // RF 7.1: el servicio arranca con la app y sincroniza mientras esté abierta.
    val sync = c.sincronizacion
    val aviso by sync.avisoDatosMoviles.collectAsStateWithLifecycle()
    val mensajeSync by sync.mensaje.collectAsStateWithLifecycle()
    // RF 7.4 / 7.5.
    val recuperacionRequerida by c.recuperacion.requerida.collectAsStateWithLifecycle()
    val avisoOtroDispositivo by c.recuperacion.avisoOtroDispositivo.collectAsStateWithLifecycle()
    val mensajeSesion by c.controlSesion.mensaje.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        c.notificador.mensajes.collect { texto ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(texto)
        }
    }

    LaunchedEffect(mensajeSync) {
        mensajeSync?.let { c.notificador.mostrar(it); sync.mensajeMostrado() }
    }

    Box(Modifier.fillMaxSize()) {
        when (sesion) {
            // RF 1.2: con sesión activa al abrir la app, primero se verifica la identidad.
            // RF 7.4: después, si falta recuperar la información, no se deja operar hasta terminar.
            EstadoSesion.ACTIVA -> when {
                !verificada -> PantallaVerificacion { c.identidadVerificada.value = true }
                recuperacionRequerida -> PantallaRecuperacion()
                else -> {
                    GrafoPrincipal(alVenderSinCaja = { c.notificador.mostrar(Mensajes.ABRA_CAJA) })
                    // Encima de la app para no perder lo que estaba en pantalla; "Atrás" no la salta.
                    if (reverificar) {
                        BackHandler { actividad?.moveTaskToBack(true) }
                        PantallaVerificacion { c.reverificacionPendiente.value = false }
                    }
                }
            }
            EstadoSesion.SIN_CUENTA, EstadoSesion.SIN_SESION -> GrafoAcceso(inicioEnRegistro = sesion == EstadoSesion.SIN_CUENTA)
        }
        // RF 7.1 / 7.3: aviso de datos móviles, sobre cualquier pantalla de la app.
        // RF 7.5: en el teléfono nuevo, al iniciar sesión.
        if (avisoOtroDispositivo && sesion == EstadoSesion.ACTIVA) {
            AlertDialog(
                onDismissRequest = c.recuperacion::avisoMostrado,
                text = { Text(Mensajes.CUENTA_SE_CERRARA_EN_OTRO) },
                confirmButton = { TextButton(c.recuperacion::avisoMostrado) { Text("Aceptar") } },
            )
        }
        // RF 7.5: en el teléfono anterior, tras enviar sus pendientes (ya en la pantalla de inicio de sesión).
        if (mensajeSesion != null && sesion != EstadoSesion.ACTIVA) {
            AlertDialog(
                onDismissRequest = c.controlSesion::mensajeMostrado,
                text = { Text(mensajeSesion.orEmpty()) },
                confirmButton = { TextButton(c.controlSesion::mensajeMostrado) { Text("Aceptar") } },
            )
        }
        if (aviso != null && sesion == EstadoSesion.ACTIVA && verificada && !recuperacionRequerida) {
            AlertDialog(
                onDismissRequest = { sync.responderDatosMoviles(false) },
                text = { Text(Mensajes.AVISO_DATOS_MOVILES) },
                confirmButton = { TextButton({ sync.responderDatosMoviles(true) }) { Text(Mensajes.BOTON_SINCRONIZAR) } },
                dismissButton = { TextButton({ sync.responderDatosMoviles(false) }) { Text(Mensajes.BOTON_CANCELAR) } },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp))
    }
}

@Composable
private fun GrafoAcceso(inicioEnRegistro: Boolean) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (inicioEnRegistro) Ruta.REGISTRO else Ruta.LOGIN) {
        composable(Ruta.REGISTRO) {
            PantallaRegistro(irALogin = { nav.navigate(Ruta.LOGIN) { popUpTo(Ruta.REGISTRO) { inclusive = true } } })
        }
        composable(Ruta.LOGIN) {
            PantallaLogin(irARegistro = { nav.navigate(Ruta.REGISTRO) })
        }
    }
}

@Composable
private fun GrafoPrincipal(alVenderSinCaja: () -> Unit) {
    val nav = rememberNavController()
    // Doble toque en "regresar": el segundo llegaba durante la animación y sacaba también la pantalla
    // principal (pantalla en blanco sin salida). Solo se regresa desde la pantalla ya visible y si
    // hay una anterior; los toques de más se ignoran.
    val regresar: () -> Unit = {
        val actual = nav.currentBackStackEntry
        if (actual?.lifecycle?.currentState == Lifecycle.State.RESUMED && nav.previousBackStackEntry != null) {
            nav.popBackStack()
        }
    }
    val c = contenedor()

    // RF 5.4: "La aplicación solicitará al vendedor el permiso de notificaciones del teléfono" (Android 13+).
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val contexto = LocalContext.current
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !c.permisoNotificacionesPedido &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            c.permisoNotificacionesPedido = true
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // RF 5.4: tocar la notificación abre el corte de caja (7.6 abre la pantalla principal, que ya es el inicio).
    val destino by c.destinoNotificacion.collectAsStateWithLifecycle()
    LaunchedEffect(destino) {
        if (destino == DestinoNotificacion.CORTE_CAJA) nav.navigate(Ruta.CORTE)
        // Apartados: la notificación abre ese apartado.
        if (destino == DestinoNotificacion.APARTADO) {
            c.apartadoNotificado.value?.let { nav.navigate("${Ruta.APARTADO}/$it") }
            c.apartadoNotificado.value = null
        }
        if (destino != null) c.destinoNotificacion.value = null
    }
    NavHost(nav, startDestination = Ruta.INICIO) {
        composable(Ruta.INICIO) {
            PantallaInicio(alElegir = { nav.navigate(Ruta.de(it)) }, alVenderSinCaja = alVenderSinCaja)
        }
        composable(Ruta.de(Destino.PERFIL)) { PantallaPerfil(regresar) { nav.navigate(Ruta.PERFIL_EDITAR) } }
        composable(Ruta.PERFIL_EDITAR) { PantallaEditarPerfil(regresar) }
        composable(Ruta.de(Destino.CATEGORIAS)) { PantallaCategorias(regresar) }
        composable(Ruta.de(Destino.FORMAS_PAGO)) { PantallaFormasPago(regresar) }
        composable(Ruta.de(Destino.REPORTES)) {
            PantallaReportes(alRegresar = regresar, alAbrir = { nav.navigate("${Ruta.REPORTE}/${it.name}") })
        }
        composable("${Ruta.REPORTE}/{reporte}") { entrada ->
            when (entrada.arguments?.getString("reporte")?.let { n -> Reporte.entries.firstOrNull { it.name == n } }) {
                Reporte.VENTAS_PERIODO -> PantallaReporteVentas(regresar)
                Reporte.POR_FORMA_PAGO -> PantallaReportePorcentajes(Reporte.POR_FORMA_PAGO, regresar)
                Reporte.POR_CATEGORIA -> PantallaReportePorcentajes(Reporte.POR_CATEGORIA, regresar)
                Reporte.MAS_VENDIDOS -> PantallaReporteMasVendidos(regresar)
                Reporte.INVENTARIO -> PantallaReporteInventario(regresar)
                Reporte.HORA_Y_DIA -> mx.ambulantpoint.misventas.ui.reportes.PantallaReporteHorario(regresar)
                Reporte.FIADO -> mx.ambulantpoint.misventas.ui.reportes.PantallaReporteFiado(regresar)
                Reporte.MERMAS -> mx.ambulantpoint.misventas.ui.reportes.PantallaReporteMermas(regresar)
                null -> LaunchedEffect(Unit) { regresar() }
            }
        }
        composable(Ruta.de(Destino.CATALOGO)) {
            PantallaCatalogo(
                alRegresar = regresar,
                alNuevo = { nav.navigate(Ruta.PRODUCTO_NUEVO) },
                alEditar = { id -> nav.navigate("${Ruta.PRODUCTO}/$id") },
            )
        }
        composable(Ruta.PRODUCTO_NUEVO) { PantallaProductoForm(null, regresar) }
        composable("${Ruta.PRODUCTO}/{id}") { entrada ->
            PantallaProductoForm(entrada.arguments?.getString("id"), regresar)
        }
        composable(Ruta.de(Destino.VENDER)) { PantallaVenta(regresar) }
        composable(Ruta.de(Destino.CAJA)) {
            PantallaCaja(
                alRegresar = regresar,
                alVender = { nav.navigate(Ruta.de(Destino.VENDER)) },
                alCorte = { nav.navigate(Ruta.CORTE) },
                alCortes = { nav.navigate(Ruta.CORTES) },
                alCarga = { apertura -> nav.navigate("${Ruta.CARGA}/$apertura") },
            )
        }
        composable("${Ruta.CARGA}/{apertura}") { e ->
            PantallaCarga(desdeApertura = e.arguments?.getString("apertura") == "true", alTerminar = regresar)
        }
        composable(Ruta.de(Destino.HISTORIAL)) {
            PantallaHistorial(regresar) { id -> nav.navigate("${Ruta.VENTA}/$id") }
        }
        composable(Ruta.de(Destino.APARTADOS)) {
            mx.ambulantpoint.misventas.ui.apartados.PantallaApartados(
                alRegresar = regresar,
                alAbrir = { id -> nav.navigate("${Ruta.APARTADO}/$id") },
                // Un apartado se arma en Vender (también con la caja cerrada).
                alNuevo = { nav.navigate(Ruta.de(Destino.VENDER)) },
            )
        }
        composable("${Ruta.APARTADO}/{id}") { e ->
            mx.ambulantpoint.misventas.ui.apartados.PantallaApartado(
                apartadoId = e.arguments?.getString("id").orEmpty(),
                alRegresar = regresar,
                alEntregar = { nav.navigate(Ruta.de(Destino.VENDER)) },
                alVerVenta = { v -> nav.navigate("${Ruta.VENTA}/$v") },
            )
        }
        composable(Ruta.de(Destino.CLIENTES)) {
            mx.ambulantpoint.misventas.ui.fiado.PantallaClientes(regresar) { id -> nav.navigate("${Ruta.CLIENTE}/$id") }
        }
        composable("${Ruta.CLIENTE}/{id}") { e ->
            mx.ambulantpoint.misventas.ui.fiado.PantallaCliente(e.arguments?.getString("id").orEmpty(), regresar) { id ->
                nav.navigate("${Ruta.VENTA}/$id")
            }
        }
        composable("${Ruta.VENTA}/{id}") { e ->
            PantallaDetalleVenta(
                ventaId = e.arguments?.getString("id").orEmpty(),
                alRegresar = regresar,
                // Corregir venta: la venta ya se canceló; se abre la venta con sus productos cargados.
                alCorregir = { nav.navigate(Ruta.de(Destino.VENDER)) { popUpTo(Ruta.INICIO) } },
            )
        }
        composable(Ruta.de(Destino.EXISTENCIAS)) {
            PantallaExistencias(regresar) { id -> nav.navigate("${Ruta.INVENTARIO}/$id") }
        }
        composable("${Ruta.INVENTARIO}/{id}") { e -> PantallaProductoInventario(e.arguments?.getString("id").orEmpty(), regresar) }
        composable(Ruta.CORTE) {
            // Tras cerrar la caja se regresa a la pantalla principal.
            PantallaCorte(regresar) { nav.popBackStack(Ruta.INICIO, inclusive = false) }
        }
        composable(Ruta.CORTES) { PantallaCortes(regresar) { id -> nav.navigate("${Ruta.CORTES}/$id") } }
        composable("${Ruta.CORTES}/{id}") { e -> PantallaDetalleCorte(e.arguments?.getString("id").orEmpty(), regresar) }
    }
}
