package mx.ambulantpoint.misventas.domain.puertos

import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.data.local.dao.VentasRecientes
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.data.local.entity.MotivoAjuste
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.repository.CambiosProducto
import mx.ambulantpoint.misventas.data.repository.CorteCaja
import mx.ambulantpoint.misventas.data.repository.NuevoProducto
import mx.ambulantpoint.misventas.data.repository.SolicitudCobro
import mx.ambulantpoint.misventas.data.repository.VentaCobrada

/*
 * Lo que los casos de uso necesitan de la capa de datos. Los repositorios de Room los implementan
 * hoy; en Sprint 2 pueden implementarlos otros (Firebase) sin tocar el dominio (RNF Reusabilidad).
 * En pruebas se usan versiones falsas en memoria.
 */

interface PuertoProductos {
    suspend fun registrar(nuevo: NuevoProducto): Resultado<Producto>
    suspend fun actualizar(id: String, cambios: CambiosProducto): Resultado<Producto>
    suspend fun cambiarEstado(id: String, activo: Boolean): Resultado<Unit>
}

interface PuertoCategorias {
    suspend fun crear(nombre: String): Resultado<Categoria>
    suspend fun renombrar(id: String, nuevoNombre: String): Resultado<Categoria>
    suspend fun eliminar(id: String): Resultado<Unit>
}

interface PuertoCaja {
    suspend fun abrir(fondoCentavos: Long): Resultado<Caja>
    suspend fun calcularCorte(efectivoContadoCentavos: Long): Resultado<CorteCaja>
    suspend fun cerrar(): Resultado<Caja>
}

interface PuertoVentas {
    suspend fun cobrar(solicitud: SolicitudCobro): Resultado<VentaCobrada>
    suspend fun cancelar(ventaId: String): Resultado<Venta>
}

interface PuertoInventario {
    suspend fun registrarEntrada(productoId: String, cantidadMilesimas: Long): Resultado<Long>
    suspend fun ajustar(
        productoId: String,
        aumentar: Boolean,
        cantidadMilesimas: Long,
        motivo: MotivoAjuste,
        especificacion: String? = null,
    ): Resultado<Long>
}

/** Estado de la cuenta en el dispositivo (RF 1.1 – 1.3). */
enum class EstadoSesion { SIN_CUENTA, SIN_SESION, ACTIVA }

/** Datos ya validados del registro (RF 1.1). */
data class DatosRegistro(
    val nombres: String,
    val apellidoPaterno: String,
    val apellidoMaterno: String,
    val telefono: String,
    val nombreNegocio: String,
    val correo: String,
    val contrasena: String,
)

/** MVP: AuthRepoLocal. Sprint 2: AuthRepoFirebase, sin cambiar pantallas ni dominio. */
interface PuertoAuth {
    val estado: kotlinx.coroutines.flow.StateFlow<EstadoSesion>
    suspend fun registrar(datos: DatosRegistro): Resultado<mx.ambulantpoint.misventas.data.local.entity.Perfil>
    suspend fun iniciarSesion(correo: String, contrasena: String): Resultado<Unit>
    suspend fun cerrarSesion(): Resultado<Unit>
}

interface PuertoVentasRecientes {
    suspend fun ventasRecientes(productoId: String): VentasRecientes
}
