package mx.ambulantpoint.misventas.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import mx.ambulantpoint.misventas.core.Reloj
import mx.ambulantpoint.misventas.core.normalizarNombre
import mx.ambulantpoint.misventas.core.nuevoId
import mx.ambulantpoint.misventas.data.local.dao.CajaDao
import mx.ambulantpoint.misventas.data.local.dao.CategoriaDao
import mx.ambulantpoint.misventas.data.local.dao.ConfiguracionDao
import mx.ambulantpoint.misventas.data.local.dao.FormaPagoDao
import mx.ambulantpoint.misventas.data.local.dao.MovimientoDao
import mx.ambulantpoint.misventas.data.local.dao.PerfilDao
import mx.ambulantpoint.misventas.data.local.dao.ProductoDao
import mx.ambulantpoint.misventas.data.local.dao.ReporteDao
import mx.ambulantpoint.misventas.data.local.dao.SyncDao
import mx.ambulantpoint.misventas.data.local.dao.VentaDao
import mx.ambulantpoint.misventas.data.local.entity.Caja
import mx.ambulantpoint.misventas.data.local.entity.Categoria
import mx.ambulantpoint.misventas.data.local.entity.Configuracion
import mx.ambulantpoint.misventas.data.local.entity.CorteFormaPago
import mx.ambulantpoint.misventas.data.local.entity.CargaCaja
import mx.ambulantpoint.misventas.data.local.entity.Precarga
import mx.ambulantpoint.misventas.data.local.dao.PrecargaDao
import mx.ambulantpoint.misventas.data.local.dao.CargaDao
import mx.ambulantpoint.misventas.data.local.entity.FormaPago
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import mx.ambulantpoint.misventas.data.local.entity.EliminacionPendiente
import mx.ambulantpoint.misventas.data.local.entity.MovimientoInventario
import mx.ambulantpoint.misventas.data.local.entity.Perfil
import mx.ambulantpoint.misventas.data.local.entity.Producto
import mx.ambulantpoint.misventas.data.local.entity.Venta
import mx.ambulantpoint.misventas.data.local.entity.VentaDetalle

@Database(
    entities = [
        Perfil::class, Configuracion::class, Categoria::class, Producto::class, FormaPago::class,
        Caja::class, CorteFormaPago::class, Venta::class, VentaDetalle::class, MovimientoInventario::class,
        EliminacionPendiente::class, CargaCaja::class, Precarga::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MisVentasDb : RoomDatabase() {
    abstract fun perfilDao(): PerfilDao
    abstract fun configuracionDao(): ConfiguracionDao
    abstract fun categoriaDao(): CategoriaDao
    abstract fun productoDao(): ProductoDao
    abstract fun formaPagoDao(): FormaPagoDao
    abstract fun cajaDao(): CajaDao
    abstract fun ventaDao(): VentaDao
    abstract fun movimientoDao(): MovimientoDao
    abstract fun reporteDao(): ReporteDao
    abstract fun cargaDao(): CargaDao
    abstract fun precargaDao(): PrecargaDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NOMBRE = "mis_ventas.db"

        fun crear(contexto: Context): MisVentasDb =
            Room.databaseBuilder(contexto, MisVentasDb::class.java, NOMBRE)
                .addMigrations(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6)
                .build()

        /** Precargas y unidad para kilo de la carga del día. Solo agrega; no toca los datos existentes. */
        val MIGRACION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `precarga` (`id` TEXT NOT NULL, `nombre` TEXT NOT NULL, " +
                        "`nombreNormalizado` TEXT NOT NULL, `cantidades` TEXT NOT NULL, `vecesUsada` INTEGER NOT NULL, " +
                        "`ultimoUso` INTEGER, `activa` INTEGER NOT NULL, `creadoEn` INTEGER NOT NULL, " +
                        "`actualizadoEn` INTEGER NOT NULL, `pendienteSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_precarga_nombreNormalizado` ON `precarga` (`nombreNormalizado`)")
                db.execSQL("ALTER TABLE `configuracion` ADD COLUMN `pasoKiloMilesimas` INTEGER NOT NULL DEFAULT 100")
            }
        }

        /** Carga del día: tabla nueva. No toca los datos existentes. */
        val MIGRACION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `carga_caja` (`cajaId` TEXT NOT NULL, `productoId` TEXT NOT NULL, " +
                        "`cantidadMilesimas` INTEGER NOT NULL, `creadoEn` INTEGER NOT NULL, `actualizadoEn` INTEGER NOT NULL, " +
                        "`pendienteSync` INTEGER NOT NULL, PRIMARY KEY(`cajaId`, `productoId`), " +
                        "FOREIGN KEY(`cajaId`) REFERENCES `caja`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                        "FOREIGN KEY(`productoId`) REFERENCES `producto`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_carga_caja_productoId` ON `carga_caja` (`productoId`)")
            }
        }

        /** Favoritos en Vender. Solo agrega una columna: los productos existentes quedan como no favoritos. */
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `producto` ADD COLUMN `favorito` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** SC-01: datos para transferencia en la configuración. No toca los datos existentes. */
        val MIGRACION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `configuracion` ADD COLUMN `clabe` TEXT")
                db.execSQL("ALTER TABLE `configuracion` ADD COLUMN `beneficiario` TEXT")
                db.execSQL("ALTER TABLE `configuracion` ADD COLUMN `banco` TEXT")
            }
        }

        /** Sprint 2 (RF 7.1): tabla de eliminaciones pendientes de enviar. No toca los datos existentes. */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `eliminacion_pendiente` (`coleccion` TEXT NOT NULL, " +
                        "`docId` TEXT NOT NULL, `creadoEn` INTEGER NOT NULL, PRIMARY KEY(`coleccion`, `docId`))",
                )
            }
        }
    }
}

/**
 * Semilla RF 3.5: "Efectivo" (predeterminada, no desactivable) y "Transferencia" (ejemplo del DFR 3.2).
 * Es idempotente: solo inserta si la tabla está vacía. Se llama al iniciar la app.
 */
suspend fun MisVentasDb.sembrarSiHaceFalta(reloj: Reloj) {
    val dao = formaPagoDao()
    if (dao.contar() > 0) return
    val ahora = reloj.ahora()
    dao.insertarTodas(
        listOf(
            FormaPago(
                id = nuevoId(), nombre = "Efectivo", nombreNormalizado = normalizarNombre("Efectivo"),
                esEfectivo = true, creadoEn = ahora, actualizadoEn = ahora,
            ),
            FormaPago(
                id = nuevoId(), nombre = "Transferencia", nombreNormalizado = normalizarNombre("Transferencia"),
                creadoEn = ahora, actualizadoEn = ahora,
            ),
        ),
    )
}
