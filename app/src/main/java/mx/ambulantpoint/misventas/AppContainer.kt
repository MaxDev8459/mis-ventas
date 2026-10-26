package mx.ambulantpoint.misventas

import android.content.Context
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.RelojSistema
import mx.ambulantpoint.misventas.data.local.MisVentasDb
import mx.ambulantpoint.misventas.data.repository.CajaRepo
import mx.ambulantpoint.misventas.data.repository.CategoriaRepo
import mx.ambulantpoint.misventas.data.repository.ConfiguracionRepo
import mx.ambulantpoint.misventas.data.repository.FormaPagoRepo
import mx.ambulantpoint.misventas.data.repository.InventarioRepo
import mx.ambulantpoint.misventas.data.repository.PerfilRepo
import mx.ambulantpoint.misventas.data.repository.ProductoRepo
import mx.ambulantpoint.misventas.data.repository.ReporteRepo
import mx.ambulantpoint.misventas.data.repository.VentaRepo

/** DI manual: la base de datos y los repositorios se crean una sola vez. */
class AppContainer(contexto: Context, val reloj: Reloj = RelojSistema) {
    val db: MisVentasDb = MisVentasDb.crear(contexto.applicationContext)

    val perfilRepo by lazy { PerfilRepo(db, reloj) }
    val configuracionRepo by lazy { ConfiguracionRepo(db, reloj) }
    val categoriaRepo by lazy { CategoriaRepo(db, reloj) }
    val productoRepo by lazy { ProductoRepo(db, reloj) }
    val formaPagoRepo by lazy { FormaPagoRepo(db, reloj) }
    val cajaRepo by lazy { CajaRepo(db, reloj) }
    val ventaRepo by lazy { VentaRepo(db, reloj) }
    val inventarioRepo by lazy { InventarioRepo(db, reloj) }
    val reporteRepo by lazy { ReporteRepo(db) }
}
