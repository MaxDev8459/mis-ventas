package mx.ambulantpoint.misventas.ui.navigation

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
    const val INVENTARIO = "inventario"
    const val REPORTE = "reporte"
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
    // RF 7.1: el servicio arranca con la app y sincroniza mientras esté abierta.
    val sync = c.sincronizacion
    val aviso by sync.avisoDatosMoviles.collectAsStateWithLifecycle()
    val mensajeSync by sync.mensaje.collectAsStateWithLifecycle()
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
            EstadoSesion.ACTIVA -> if (!verificada) PantallaVerificacion { c.identidadVerificada.value = true } else GrafoPrincipal(alVenderSinCaja = { c.notificador.mostrar(Mensajes.ABRA_CAJA) })
            EstadoSesion.SIN_CUENTA, EstadoSesion.SIN_SESION -> GrafoAcceso(inicioEnRegistro = sesion == EstadoSesion.SIN_CUENTA)
        }
        // RF 7.1 / 7.3: aviso de datos móviles, sobre cualquier pantalla de la app.
        if (aviso != null && sesion == EstadoSesion.ACTIVA && verificada) {
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
    val regresar: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = Ruta.INICIO) {
        composable(Ruta.INICIO) {
            PantallaInicio(alElegir = { nav.navigate(Ruta.de(it)) }, alVenderSinCaja = alVenderSinCaja)
        }
        composable(Ruta.de(Destino.PERFIL)) { PantallaPerfil(regresar) }
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
            )
        }
        composable(Ruta.de(Destino.HISTORIAL)) {
            PantallaHistorial(regresar) { id -> nav.navigate("${Ruta.VENTA}/$id") }
        }
        composable("${Ruta.VENTA}/{id}") { e -> PantallaDetalleVenta(e.arguments?.getString("id").orEmpty(), regresar) }
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
