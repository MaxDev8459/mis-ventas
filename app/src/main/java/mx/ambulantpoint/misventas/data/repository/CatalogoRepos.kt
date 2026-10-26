package mx.ambulantpoint.misventas.data.repository

import kotlinx.coroutines.flow.Flow
import mx.ambulantpoint.misventas.core.Dinero
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.normalizarNombre
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.local.dao.ProductoConCategoria
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.data.local.entity.Configuracion
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import mx.ambulantpoint.misventas.data.local.entity.MovimientoInventario
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.TipoMovimiento
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.domain.puertos.PuertoCategorias
import mx.ambulantpoint.misventas.domain.puertos.PuertoProductos

/** RF 2.6 – 2.9. */
class CategoriaRepo(private val db: MisVentasDb, private val reloj: Reloj) : PuertoCategorias {
    private val dao = db.categoriaDao()

    fun observarTodas(): Flow<List<Categoria>> = dao.observarTodas()

    override suspend fun crear(nombre: String): Resultado<Categoria> = db.transaccion {
        val ahora = reloj.ahora()
        val categoria = Categoria(
            id = nuevoId(), nombre = nombre.trim(), nombreNormalizado = normalizarNombre(nombre),
            creadoEn = ahora, actualizadoEn = ahora,
        )
        dao.insertar(categoria)
        categoria
    }

    /** RF 2.8: el cambio se refleja en sus productos porque estos guardan el id, no el nombre. */
    override suspend fun renombrar(id: String, nuevoNombre: String): Resultado<Categoria> = db.transaccion {
        val actual = dao.porId(id) ?: fallar(FallaDatos.NoEncontrado)
        val cambiada = actual.copy(
            nombre = nuevoNombre.trim(), nombreNormalizado = normalizarNombre(nuevoNombre),
            actualizadoEn = reloj.ahora(), pendienteSync = true,
        )
        dao.actualizar(cambiada)
        cambiada
    }

    /** RF 2.9: solo si no tiene productos (activos o inactivos). */
    override suspend fun eliminar(id: String): Resultado<Unit> = db.transaccion {
        if (dao.porId(id) == null) fallar(FallaDatos.NoEncontrado)
        if (dao.contarProductos(id) > 0) fallar(FallaDatos.CategoriaConProductos)
        dao.eliminar(id)
        Unit
    }

    suspend fun contarProductos(id: String): Int = dao.contarProductos(id)
}

/** Datos capturados al registrar un producto (RF 2.1). Ya validados por el dominio. */
data class NuevoProducto(
    val nombre: String,
    val categoriaId: String,
    val tipoVenta: TipoVenta,
    val precioCentavos: Long,
    val existenciaInicialMilesimas: Long,
    val imagenRuta: String,
)

/** Campos editables de un producto (RF 2.2). La existencia no se edita aquí. */
data class CambiosProducto(
    val nombre: String,
    val categoriaId: String,
    val tipoVenta: TipoVenta,
    val precioCentavos: Long,
    val imagenRuta: String,
)

/** RF 2.1 – 2.5. */
class ProductoRepo(private val db: MisVentasDb, private val reloj: Reloj) : PuertoProductos {
    private val dao = db.productoDao()

    fun observar(texto: String = "", categoriaId: String? = null, activo: Boolean? = true): Flow<List<ProductoConCategoria>> =
        dao.observar(texto.trim(), categoriaId, activo)

    fun observarParaVenta(): Flow<List<ProductoConCategoria>> = dao.observarParaVenta()

    fun observarPorId(id: String): Flow<Producto?> = dao.observarPorId(id)

    suspend fun porId(id: String): Producto? = dao.porId(id)

    /** Registra el producto; si trae existencia inicial, queda como primer movimiento (ENTRADA). */
    override suspend fun registrar(nuevo: NuevoProducto): Resultado<Producto> = db.transaccion {
        if (nuevo.precioCentavos <= 0 || nuevo.existenciaInicialMilesimas < 0) fallar(FallaDatos.CantidadInvalida)
        if (!cantidadValida(nuevo.tipoVenta, nuevo.existenciaInicialMilesimas)) fallar(FallaDatos.CantidadInvalida)
        if (db.categoriaDao().porId(nuevo.categoriaId) == null) fallar(FallaDatos.NoEncontrado)
        val ahora = reloj.ahora()
        val producto = Producto(
            id = nuevoId(),
            nombre = nuevo.nombre.trim(),
            nombreNormalizado = normalizarNombre(nuevo.nombre),
            categoriaId = nuevo.categoriaId,
            tipoVenta = nuevo.tipoVenta,
            precioCentavos = nuevo.precioCentavos,
            existenciaMilesimas = nuevo.existenciaInicialMilesimas,
            imagenRuta = nuevo.imagenRuta,
            creadoEn = ahora,
            actualizadoEn = ahora,
        )
        dao.insertar(producto)
        if (producto.existenciaMilesimas > 0) {
            db.movimientoDao().insertar(
                MovimientoInventario(
                    id = nuevoId(), productoId = producto.id, fechaHora = ahora, tipo = TipoMovimiento.ENTRADA,
                    cantidadMilesimas = producto.existenciaMilesimas,
                    existenciaResultanteMilesimas = producto.existenciaMilesimas, creadoEn = ahora,
                ),
            )
        }
        producto
    }

    override suspend fun actualizar(id: String, cambios: CambiosProducto): Resultado<Producto> = db.transaccion {
        val actual = dao.porId(id) ?: fallar(FallaDatos.NoEncontrado)
        if (cambios.precioCentavos <= 0) fallar(FallaDatos.CantidadInvalida)
        if (cambios.tipoVenta != actual.tipoVenta && actual.existenciaMilesimas != 0L) {
            fallar(FallaDatos.TipoVentaNoModificable)
        }
        if (db.categoriaDao().porId(cambios.categoriaId) == null) fallar(FallaDatos.NoEncontrado)
        val nuevo = actual.copy(
            nombre = cambios.nombre.trim(),
            nombreNormalizado = normalizarNombre(cambios.nombre),
            categoriaId = cambios.categoriaId,
            tipoVenta = cambios.tipoVenta,
            precioCentavos = cambios.precioCentavos,
            imagenRuta = cambios.imagenRuta,
            actualizadoEn = reloj.ahora(),
            pendienteSync = true,
        )
        dao.actualizar(nuevo)
        nuevo
    }

    /** RF 2.3 / 2.4. */
    override suspend fun cambiarEstado(id: String, activo: Boolean): Resultado<Unit> = db.transaccion {
        if (dao.cambiarEstado(id, activo, reloj.ahora()) == 0) fallar(FallaDatos.NoEncontrado)
        Unit
    }
}

/** Por unidad: múltiplos de 1 pieza; por peso: cualquier cantidad de gramos. */
internal fun cantidadValida(tipoVenta: TipoVenta, cantidadMilesimas: Long): Boolean = when (tipoVenta) {
    TipoVenta.UNIDAD -> cantidadMilesimas % Dinero.MILESIMAS_POR_UNIDAD == 0L
    TipoVenta.PESO -> true
}

/** RF 3.5. */
class FormaPagoRepo(private val db: MisVentasDb, private val reloj: Reloj) {
    private val dao = db.formaPagoDao()

    fun observarTodas(): Flow<List<FormaPago>> = dao.observarTodas()
    fun observarActivas(): Flow<List<FormaPago>> = dao.observarActivas()

    suspend fun crear(nombre: String, activa: Boolean = true): Resultado<FormaPago> = db.transaccion {
        val ahora = reloj.ahora()
        val forma = FormaPago(
            id = nuevoId(), nombre = nombre.trim(), nombreNormalizado = normalizarNombre(nombre),
            activa = activa, creadoEn = ahora, actualizadoEn = ahora,
        )
        dao.insertar(forma)
        forma
    }

    suspend fun actualizar(id: String, nombre: String, activa: Boolean): Resultado<FormaPago> = db.transaccion {
        val actual = dao.porId(id) ?: fallar(FallaDatos.NoEncontrado)
        if (actual.esEfectivo && (!activa || normalizarNombre(nombre) != actual.nombreNormalizado)) {
            fallar(FallaDatos.EfectivoNoModificable)
        }
        val nueva = actual.copy(
            nombre = nombre.trim(), nombreNormalizado = normalizarNombre(nombre), activa = activa,
            actualizadoEn = reloj.ahora(), pendienteSync = true,
        )
        dao.actualizar(nueva)
        nueva
    }
}

/** RF 1.5 / 1.6. */
class PerfilRepo(private val db: MisVentasDb, private val reloj: Reloj) {
    private val dao = db.perfilDao()

    fun observar(): Flow<Perfil?> = dao.observar()
    suspend fun obtener(): Perfil? = dao.obtener()

    suspend fun guardar(perfil: Perfil): Resultado<Perfil> = db.transaccion {
        val guardado = perfil.copy(actualizadoEn = reloj.ahora(), pendienteSync = true)
        dao.guardar(guardado)
        guardado
    }
}

/** RF 3.6: descuentos deshabilitados por defecto. */
class ConfiguracionRepo(private val db: MisVentasDb, private val reloj: Reloj) {
    private val dao = db.configuracionDao()

    fun observar(): Flow<Configuracion?> = dao.observar()

    suspend fun guardarDescuentos(habilitados: Boolean): Resultado<Unit> = db.transaccion {
        dao.guardar(Configuracion(descuentosHabilitados = habilitados, actualizadoEn = reloj.ahora()))
    }
}
