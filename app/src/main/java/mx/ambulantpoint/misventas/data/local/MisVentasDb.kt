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
import mx.ambulantpoint.misventas.data.local.entity.Cliente
import mx.ambulantpoint.misventas.data.local.entity.Abono
import mx.ambulantpoint.misventas.data.local.entity.AplicacionAbono
import mx.ambulantpoint.misventas.data.local.dao.FiadoDao
import mx.ambulantpoint.misventas.data.local.dao.ApartadoDao
import mx.ambulantpoint.misventas.data.local.entity.Apartado
import mx.ambulantpoint.misventas.data.local.entity.ApartadoDetalle

@Database(
    entities = [
        Perfil::class, Configuracion::class, Categoria::class, Producto::class, FormaPago::class,
        Caja::class, CorteFormaPago::class, Venta::class, VentaDetalle::class, MovimientoInventario::class,
        EliminacionPendiente::class, CargaCaja::class, Precarga::class,
        Cliente::class, Abono::class, AplicacionAbono::class,
        Apartado::class, ApartadoDetalle::class,
    ],
    version = 8,
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
    abstract fun fiadoDao(): FiadoDao
    abstract fun apartadoDao(): ApartadoDao

    companion object {
        const val NOMBRE = "mis_ventas.db"

        fun crear(contexto: Context): MisVentasDb =
            Room.databaseBuilder(contexto, MisVentasDb::class.java, NOMBRE)
                .addMigrations(MIGRACION_1_2, MIGRACION_2_3, MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6, MIGRACION_6_7, MIGRACION_7_8)
                .build()

        /** Apartados (SQL tomado de 8.json). Solo tablas nuevas: no toca los datos existentes. */
        val MIGRACION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `apartado` (`id` TEXT NOT NULL, `nombre` TEXT NOT NULL, `telefono` TEXT, " +
                        "`lugar` TEXT NOT NULL, `entregaEn` INTEGER NOT NULL, `totalCentavos` INTEGER NOT NULL, `estado` TEXT NOT NULL, " +
                        "`ventaId` TEXT, `terminadoEn` INTEGER, `creadoEn` INTEGER NOT NULL, `actualizadoEn` INTEGER NOT NULL, " +
                        "`pendienteSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_apartado_estado_entregaEn` ON `apartado` (`estado`, `entregaEn`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `apartado_detalle` (`id` TEXT NOT NULL, `apartadoId` TEXT NOT NULL, " +
                        "`productoId` TEXT NOT NULL, `nombreProducto` TEXT NOT NULL, `tipoVenta` TEXT NOT NULL, " +
                        "`cantidadMilesimas` INTEGER NOT NULL, `precioUnitarioCentavos` INTEGER NOT NULL, `importeCentavos` INTEGER NOT NULL, " +
                        "`creadoEn` INTEGER NOT NULL, `pendienteSync` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`apartadoId`) REFERENCES `apartado`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                        "FOREIGN KEY(`productoId`) REFERENCES `producto`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_apartado_detalle_apartadoId` ON `apartado_detalle` (`apartadoId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_apartado_detalle_productoId` ON `apartado_detalle` (`productoId`)")
            }
        }

        /**
         * Fiado + venta rápida sin producto (SQL tomado de 7.json).
         *
         * - Tablas nuevas: `cliente`, `abono`, `aplicacion_abono`.
         * - `venta.formaPagoId` pasa a opcional (venta fiada) y se agrega `venta.clienteId`;
         *   `venta_detalle.productoId` pasa a opcional (monto libre "Varios"). SQLite no puede quitar un
         *   NOT NULL, así que ambas tablas se reconstruyen: tabla nueva, copia de TODAS las filas,
         *   borrar la vieja y renombrar (procedimiento recomendado por SQLite; las llaves foráneas están
         *   apagadas durante la migración).
         * - Columnas nuevas opcionales en `caja` y el aviso de saldo en `configuracion` ($150).
         *
         * Al final revisa que no se haya perdido ninguna fila ni se haya roto una llave foránea: si algo no cuadra
         * lanza una excepción y Room revierte TODA la migración (los datos quedan como estaban en v6).
         */
        val MIGRACION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val ventasAntes = contar(db, "venta")
                val detallesAntes = contar(db, "venta_detalle")
                val rotasAntes = llavesRotas(db)

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cliente` (`id` TEXT NOT NULL, `nombre` TEXT NOT NULL, " +
                        "`nombreNormalizado` TEXT NOT NULL, `telefono` TEXT, `creadoEn` INTEGER NOT NULL, " +
                        "`actualizadoEn` INTEGER NOT NULL, `pendienteSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cliente_nombreNormalizado` ON `cliente` (`nombreNormalizado`)")

                // venta: formaPagoId opcional + clienteId.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `venta_v7` (`id` TEXT NOT NULL, `cajaId` TEXT NOT NULL, `fechaHora` INTEGER NOT NULL, " +
                        "`subtotalCentavos` INTEGER NOT NULL, `tipoDescuento` TEXT, `valorDescuento` INTEGER, " +
                        "`descuentoCentavos` INTEGER NOT NULL, `totalCentavos` INTEGER NOT NULL, `formaPagoId` TEXT, " +
                        "`montoRecibidoCentavos` INTEGER, `cambioCentavos` INTEGER, `estado` TEXT NOT NULL, `canceladaEn` INTEGER, " +
                        "`clienteId` TEXT, `creadoEn` INTEGER NOT NULL, `actualizadoEn` INTEGER NOT NULL, `pendienteSync` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), FOREIGN KEY(`cajaId`) REFERENCES `caja`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                        "FOREIGN KEY(`formaPagoId`) REFERENCES `forma_pago`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                        "FOREIGN KEY(`clienteId`) REFERENCES `cliente`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                )
                val colVenta = "`id`, `cajaId`, `fechaHora`, `subtotalCentavos`, `tipoDescuento`, `valorDescuento`, " +
                    "`descuentoCentavos`, `totalCentavos`, `formaPagoId`, `montoRecibidoCentavos`, `cambioCentavos`, " +
                    "`estado`, `canceladaEn`, `creadoEn`, `actualizadoEn`, `pendienteSync`"
                db.execSQL("INSERT INTO `venta_v7` ($colVenta) SELECT $colVenta FROM `venta`")
                db.execSQL("DROP TABLE `venta`")
                db.execSQL("ALTER TABLE `venta_v7` RENAME TO `venta`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_venta_cajaId` ON `venta` (`cajaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_venta_formaPagoId` ON `venta` (`formaPagoId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_venta_fechaHora` ON `venta` (`fechaHora`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_venta_clienteId` ON `venta` (`clienteId`)")

                // venta_detalle: productoId opcional.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `venta_detalle_v7` (`id` TEXT NOT NULL, `ventaId` TEXT NOT NULL, `productoId` TEXT, " +
                        "`nombreProducto` TEXT NOT NULL, `tipoVenta` TEXT NOT NULL, `cantidadMilesimas` INTEGER NOT NULL, " +
                        "`precioUnitarioCentavos` INTEGER NOT NULL, `importeCentavos` INTEGER NOT NULL, `creadoEn` INTEGER NOT NULL, " +
                        "`pendienteSync` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`ventaId`) REFERENCES `venta`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                        "FOREIGN KEY(`productoId`) REFERENCES `producto`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                )
                val colDetalle = "`id`, `ventaId`, `productoId`, `nombreProducto`, `tipoVenta`, `cantidadMilesimas`, " +
                    "`precioUnitarioCentavos`, `importeCentavos`, `creadoEn`, `pendienteSync`"
                db.execSQL("INSERT INTO `venta_detalle_v7` ($colDetalle) SELECT $colDetalle FROM `venta_detalle`")
                db.execSQL("DROP TABLE `venta_detalle`")
                db.execSQL("ALTER TABLE `venta_detalle_v7` RENAME TO `venta_detalle`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_venta_detalle_ventaId` ON `venta_detalle` (`ventaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_venta_detalle_productoId` ON `venta_detalle` (`productoId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `abono` (`id` TEXT NOT NULL, `clienteId` TEXT NOT NULL, `cajaId` TEXT NOT NULL, " +
                        "`fechaHora` INTEGER NOT NULL, `tipo` TEXT NOT NULL, `montoCentavos` INTEGER NOT NULL, `formaPagoId` TEXT NOT NULL, " +
                        "`ventaId` TEXT, `estado` TEXT NOT NULL, `canceladoEn` INTEGER, `creadoEn` INTEGER NOT NULL, " +
                        "`actualizadoEn` INTEGER NOT NULL, `pendienteSync` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`clienteId`) REFERENCES `cliente`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                        "FOREIGN KEY(`cajaId`) REFERENCES `caja`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                        "FOREIGN KEY(`formaPagoId`) REFERENCES `forma_pago`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                        "FOREIGN KEY(`ventaId`) REFERENCES `venta`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_abono_clienteId` ON `abono` (`clienteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_abono_cajaId` ON `abono` (`cajaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_abono_formaPagoId` ON `abono` (`formaPagoId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_abono_ventaId` ON `abono` (`ventaId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `aplicacion_abono` (`id` TEXT NOT NULL, `abonoId` TEXT NOT NULL, `ventaId` TEXT NOT NULL, " +
                        "`montoCentavos` INTEGER NOT NULL, `creadoEn` INTEGER NOT NULL, `pendienteSync` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`abonoId`) REFERENCES `abono`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                        "FOREIGN KEY(`ventaId`) REFERENCES `venta`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_aplicacion_abono_abonoId` ON `aplicacion_abono` (`abonoId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_aplicacion_abono_ventaId` ON `aplicacion_abono` (`ventaId`)")

                db.execSQL("ALTER TABLE `caja` ADD COLUMN `fiadoCentavos` INTEGER")
                db.execSQL("ALTER TABLE `caja` ADD COLUMN `abonosCentavos` INTEGER")
                db.execSQL("ALTER TABLE `caja` ADD COLUMN `devolucionesCentavos` INTEGER")
                db.execSQL("ALTER TABLE `configuracion` ADD COLUMN `avisoSaldoCentavos` INTEGER NOT NULL DEFAULT 15000")

                // Defensa: no se perdió ninguna fila y las llaves foráneas siguen bien.
                check(contar(db, "venta") == ventasAntes) { "Migración 6→7: cambió el número de ventas" }
                check(contar(db, "venta_detalle") == detallesAntes) { "Migración 6→7: cambió el número de detalles" }
                // Solo falla si la migración rompió algo; lo que ya estuviera mal antes no la bloquea.
                check(llavesRotas(db) <= rotasAntes) { "Migración 6→7: quedó una llave foránea rota" }
            }

            private fun llavesRotas(db: SupportSQLiteDatabase): Int =
                listOf("venta", "venta_detalle").sumOf { t ->
                    db.query("PRAGMA foreign_key_check(`$t`)").use { c -> c.count }
                }

            private fun contar(db: SupportSQLiteDatabase, tabla: String): Long =
                db.query("SELECT COUNT(*) FROM `$tabla`").use { c -> c.moveToFirst(); c.getLong(0) }
        }

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
