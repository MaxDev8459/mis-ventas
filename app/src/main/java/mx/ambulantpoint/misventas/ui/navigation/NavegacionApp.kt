package mx.ambulantpoint.misventas.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import mx.ambulantpoint.misventas.ui.components.BarraSuperior
import mx.ambulantpoint.misventas.ui.components.contenedor
import mx.ambulantpoint.misventas.ui.inicio.Destino
import mx.ambulantpoint.misventas.ui.inicio.PantallaInicio
import mx.ambulantpoint.misventas.ui.perfil.PantallaPerfil

private object Ruta {
    const val REGISTRO = "registro"
    const val LOGIN = "login"
    const val INICIO = "inicio"
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
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        c.notificador.mensajes.collect { texto ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(texto)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (sesion) {
            EstadoSesion.ACTIVA -> GrafoPrincipal(alVenderSinCaja = { c.notificador.mostrar(Mensajes.ABRA_CAJA) })
            EstadoSesion.SIN_CUENTA, EstadoSesion.SIN_SESION -> GrafoAcceso(inicioEnRegistro = sesion == EstadoSesion.SIN_CUENTA)
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
        // Módulos de las siguientes entregas de la Fase 3 (3B–3D).
        for (d in listOf(Destino.VENDER, Destino.CAJA, Destino.HISTORIAL, Destino.CATALOGO, Destino.CATEGORIAS, Destino.EXISTENCIAS)) {
            composable(Ruta.de(d)) { PantallaEnConstruccion(d.titulo, regresar) }
        }
    }
}

@Composable
private fun PantallaEnConstruccion(titulo: String, alRegresar: () -> Unit) {
    Scaffold(topBar = { BarraSuperior(titulo, alRegresar) }) { relleno ->
        Box(Modifier.padding(relleno).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("En construcción", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        }
    }
}
