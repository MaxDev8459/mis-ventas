package mx.ambulantpoint.misventas.data.red

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import mx.ambulantpoint.misventas.domain.regla.TipoRed

/** Estado de la conexión del teléfono. */
class Conectividad(contexto: Context) {
    private val cm = contexto.applicationContext.getSystemService(ConnectivityManager::class.java)

    /** ¿Hay internet ahora? Para dar el mensaje del DFR sin esperar el tiempo de espera de la red. */
    fun hayInternet(): Boolean = tipoActual() != TipoRed.NINGUNA

    fun tipoActual(): TipoRed = tipo(cm?.getNetworkCapabilities(cm.activeNetwork))

    /** RF 7.1 / 7.2: Wi-Fi, datos móviles o sin conexión, cada vez que cambia. */
    fun observar(): Flow<TipoRed> = callbackFlow {
        trySend(tipoActual())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                trySend(tipo(capacidades))
            }
            override fun onLost(network: Network) { trySend(tipoActual()) }
        }
        cm?.registerDefaultNetworkCallback(callback)
        awaitClose { cm?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun tipo(c: NetworkCapabilities?): TipoRed = when {
        c == null || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> TipoRed.NINGUNA
        // Wi-Fi o cable: sin costo de datos. Cualquier otra (celular) se trata como datos móviles.
        c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> TipoRed.WIFI
        else -> TipoRed.MOVIL
    }
}
