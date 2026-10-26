package mx.ambulantpoint.misventas.domain.usecase

import mx.ambulantpoint.misventas.core.LectorNumeros
import mx.ambulantpoint.misventas.core.Mensajes
import mx.ambulantpoint.misventas.core.Resultado
import mx.ambulantpoint.misventas.core.Validadores
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta
import mx.ambulantpoint.misventas.data.repository.CambiosProducto
import mx.ambulantpoint.misventas.data.repository.NuevoProducto
import mx.ambulantpoint.misventas.domain.puertos.PuertoCategorias
import mx.ambulantpoint.misventas.domain.puertos.PuertoProductos

/** Captura del formulario de producto tal como la escribe el vendedor. */
data class FormularioProducto(
    val nombre: String = "",
    val categoriaId: String? = null,
    val tipoVenta: TipoVenta? = null,
    val precio: String = "",
    /** Solo al registrar (RF 2.1); al editar se ignora (RF 2.2). */
    val existencia: String = "",
    val imagenRuta: String? = null,
)

/** RF 2.1. */
class RegistrarProducto(private val productos: PuertoProductos) {
    suspend operator fun invoke(f: FormularioProducto): Resultado<Hecho<Producto>> {
        val e = Errores()
        e.si(Campos.NOMBRE, Validadores.obligatorio(f.nombre))
        e.si(Campos.CATEGORIA, if (f.categoriaId.isNullOrBlank()) Mensajes.Campo.OBLIGATORIO else null)
        e.si(Campos.TIPO_VENTA, if (f.tipoVenta == null) Mensajes.Campo.OBLIGATORIO else null)
        val precio = e.leer(Campos.PRECIO, LectorNumeros.precio(f.precio))
        val existencia = f.tipoVenta?.let { e.leer(Campos.EXISTENCIA, LectorNumeros.cantidad(f.existencia, it, permitirCero = true)) }
        e.si(Campos.IMAGEN, if (f.imagenRuta.isNullOrBlank()) Mensajes.Campo.IMAGEN_OBLIGATORIA else null)
        if (e.hay) return e.falla()

        val nuevo = NuevoProducto(f.nombre, f.categoriaId!!, f.tipoVenta!!, precio!!, existencia!!, f.imagenRuta!!)
        return ejecutar(Mensajes.PRODUCTO_NO_REGISTRADO, operacion = { productos.registrar(nuevo) }) {
            Hecho(it, Mensajes.PRODUCTO_REGISTRADO)
        }
    }
}

/** RF 2.2. La existencia no se edita aquí; el tipo de venta solo cambia con existencia cero. */
class EditarProducto(private val productos: PuertoProductos) {
    suspend operator fun invoke(id: String, f: FormularioProducto): Resultado<Hecho<Producto>> {
        val e = Errores()
        e.si(Campos.NOMBRE, Validadores.obligatorio(f.nombre))
        e.si(Campos.CATEGORIA, if (f.categoriaId.isNullOrBlank()) Mensajes.Campo.OBLIGATORIO else null)
        e.si(Campos.TIPO_VENTA, if (f.tipoVenta == null) Mensajes.Campo.OBLIGATORIO else null)
        val precio = e.leer(Campos.PRECIO, LectorNumeros.precio(f.precio))
        e.si(Campos.IMAGEN, if (f.imagenRuta.isNullOrBlank()) Mensajes.Campo.IMAGEN_OBLIGATORIA else null)
        if (e.hay) return e.falla()

        val cambios = CambiosProducto(f.nombre, f.categoriaId!!, f.tipoVenta!!, precio!!, f.imagenRuta!!)
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { productos.actualizar(id, cambios) }) {
            Hecho(it, Mensajes.PRODUCTO_ACTUALIZADO)
        }
    }
}

/** RF 2.3 / 2.4. La confirmación se muestra con [Mensajes.confirmarDesactivarProducto]. */
class CambiarEstadoProducto(private val productos: PuertoProductos) {
    suspend operator fun invoke(id: String, activar: Boolean): Resultado<Hecho<Unit>> =
        ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { productos.cambiarEstado(id, activar) }) {
            Hecho(Unit, if (activar) Mensajes.PRODUCTO_REACTIVADO else Mensajes.PRODUCTO_DESACTIVADO)
        }
}

/** RF 2.6. */
class CrearCategoria(private val categorias: PuertoCategorias) {
    suspend operator fun invoke(nombre: String): Resultado<Hecho<Categoria>> {
        Validadores.obligatorio(nombre)?.let { return campo(Campos.NOMBRE, it) }
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { categorias.crear(nombre) }) {
            Hecho(it, Mensajes.CATEGORIA_GUARDADA)
        }
    }
}

/** RF 2.8. */
class RenombrarCategoria(private val categorias: PuertoCategorias) {
    suspend operator fun invoke(id: String, nombre: String): Resultado<Hecho<Categoria>> {
        Validadores.obligatorio(nombre)?.let { return campo(Campos.NOMBRE, it) }
        return ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { categorias.renombrar(id, nombre) }) {
            Hecho(it, Mensajes.CATEGORIA_GUARDADA)
        }
    }
}

/** RF 2.9. La confirmación se muestra con [Mensajes.confirmarEliminarCategoria]. */
class EliminarCategoria(private val categorias: PuertoCategorias) {
    suspend operator fun invoke(id: String): Resultado<Hecho<Unit>> =
        ejecutar(Mensajes.Campo.NO_ENCONTRADO, operacion = { categorias.eliminar(id) }) {
            Hecho(Unit, Mensajes.CATEGORIA_ELIMINADA)
        }
}
