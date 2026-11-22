package mx.ambulantpoint.misventas.domain.regla

import mx.ambulantpoint.misventas.core.Formato
import mx.ambulantpoint.misventas.core.LectorNumeros
import mx.ambulantpoint.misventas.core.Lectura
import mx.ambulantpoint.misventas.data.local.entity.TipoVenta

/** Un producto de la carga del día con lo que ha pasado en la caja. */
data class RenglonCarga(
    val productoId: String,
    val nombre: String,
    val tipoVenta: TipoVenta,
    val cargadoMilesimas: Long,
    val vendidoMilesimas: Long,
    val mermasMilesimas: Long,
) {
    /** Cargado − vendido − mermas. Puede ser negativo si se vendió sin agregar a la carga. */
    val quedanMilesimas: Long get() = cargadoMilesimas - vendidoMilesimas - mermasMilesimas

    /** Lo que regresa a casa al cerrar (nunca negativo). */
    val sobranteMilesimas: Long get() = maxOf(0, quedanMilesimas)
}

/** Producto que se puede capturar en la carga. */
data class ProductoParaCarga(val id: String, val nombre: String, val tipoVenta: TipoVenta, val existenciaMilesimas: Long)

/** Por qué se avisa al agregar a la venta. */
enum class MotivoAvisoCarga { NO_CARGADO, SE_ACABO }

/**
 * Carga del día (desarrollo independiente). Lo cargado no puede pasar de la existencia del
 * inventario; "te quedan" = cargado − vendido (sin cancelaciones) − mermas de esa caja.
 */
object ReglaCarga {

    /**
     * Valida lo capturado (texto por producto; vacío = 0). Devuelve las cantidades o, si algo no es
     * válido, el mensaje por producto.
     */
    fun leerCaptura(
        capturas: Map<String, String>,
        productos: List<ProductoParaCarga>,
    ): Pair<Map<String, Long>, Map<String, String>> {
        val cantidades = mutableMapOf<String, Long>()
        val errores = mutableMapOf<String, String>()
        for (p in productos) {
            val texto = capturas[p.id].orEmpty()
            if (texto.isBlank()) { cantidades[p.id] = 0; continue }
            when (val l = LectorNumeros.cantidad(texto, p.tipoVenta, permitirCero = true)) {
                is Lectura.Error -> errores[p.id] = l.mensaje
                is Lectura.Valor -> {
                    val msg = excedeExistencia(l.valor, p)
                    if (msg != null) errores[p.id] = msg else cantidades[p.id] = l.valor
                }
            }
        }
        return cantidades to errores
    }

    /** "Solo hay X de [producto]" si lo cargado pasa de la existencia; null si cabe. */
    fun excedeExistencia(cargadoMilesimas: Long, p: ProductoParaCarga): String? =
        if (cargadoMilesimas > p.existenciaMilesimas) {
            "Solo hay ${Formato.cantidad(p.existenciaMilesimas, p.tipoVenta)} de ${p.nombre}"
        } else {
            null
        }

    /**
     * Al agregar [agregarMilesimas] a la venta: si hay carga en la caja y el producto no se cargó o ya
     * no alcanza lo que queda (contando lo que ya está en la venta), se avisa. Sin carga, nunca.
     */
    fun aviso(
        hayCarga: Boolean,
        renglon: RenglonCarga?,
        enVentaMilesimas: Long,
        agregarMilesimas: Long,
    ): MotivoAvisoCarga? = when {
        !hayCarga -> null
        renglon == null || renglon.cargadoMilesimas <= 0 -> MotivoAvisoCarga.NO_CARGADO
        renglon.quedanMilesimas - enVentaMilesimas - agregarMilesimas < 0 -> MotivoAvisoCarga.SE_ACABO
        else -> null
    }

    /** Cantidad para prellenar un campo (sin separador de miles): 12000 pzas → "12", 1500 kg → "1.500". */
    fun textoCaptura(milesimas: Long, tipoVenta: TipoVenta): String = when {
        milesimas <= 0 -> ""
        tipoVenta == TipoVenta.UNIDAD -> (milesimas / 1000).toString()
        else -> "${milesimas / 1000}.${(milesimas % 1000).toString().padStart(3, '0')}"
    }

    /** Lo que queda de la carga descontando lo que ya está en la venta (para la tarjeta en Vender). */
    fun quedanConVenta(renglon: RenglonCarga, enVentaMilesimas: Long): Long =
        maxOf(0, renglon.quedanMilesimas - enVentaMilesimas)
}
