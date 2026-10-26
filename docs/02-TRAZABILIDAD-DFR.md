# Matriz de trazabilidad DFR v1.2 → código

Estados: ⬜ pendiente · 🔨 en desarrollo · 🔍 en verificación · ✅ terminado (verificado + probado en teléfono) · ⏳ Sprint 2

| RF | Nombre | Prioridad | Sprint | Pantalla / Caso de uso | Pruebas | Estado |
|----|--------|:---:|:---:|---|---|:---:|
| 1.1 | Registrar vendedor | Alta | MVP (local) / S2 Firebase | `ui/auth/Registro` · `AuthRepoLocal.registrar` | Validadores registro | ✅ |
| 1.2 | Iniciar sesión | Alta | MVP (local, sin biometría) | `ui/auth/Login` · `AuthRepoLocal` | Validadores login | 🔍 |
| 1.3 | Cerrar sesión | Media | MVP | `ui/perfil` · `CerrarSesion` | Bloqueo con pendientes | ✅ |
| 1.4 | Recuperar contraseña | Media | S2 | | | ⏳ |
| 1.5 | Consultar perfil | Baja | MVP | `ui/perfil` | | ✅ |
| 1.6 | Editar perfil | Baja | S2 | | | ⏳ |
| 2.1 | Registrar producto | Alta | MVP | `ui/catalogo/FormProducto` · `RegistrarProducto` | Nombre único, precio, existencia por tipo, compresión | ✅ |
| 2.2 | Editar producto | Alta | MVP | `ui/catalogo/FormProducto` · `EditarProducto` | Cambio de tipo solo con existencia 0 | ✅ |
| 2.3 | Desactivar producto | Media | MVP | `ui/catalogo` | | ✅ |
| 2.4 | Reactivar producto | Media | MVP | `ui/catalogo` | | ✅ |
| 2.5 | Consultar productos | Alta | MVP | `ui/catalogo/Lista` | Búsqueda/filtros | ✅ |
| 2.6 | Crear categorías | Media | MVP | `ui/categorias` | Nombre único | ✅ |
| 2.7 | Consultar categorías | Media | MVP | `ui/categorias` | | ✅ |
| 2.8 | Modificar categoría | Media | MVP (adelantado) | | | 🔍 |
| 2.9 | Eliminar categoría | Media | MVP (adelantado) | | | ✅ |
| 3.1 | Registrar venta | Alta | MVP | `ui/venta/PantallaVenta` | Total, tope por existencia, kg×precio | 🔨 |
| 3.2 | Cobrar venta | Alta | MVP | `ui/venta/Cobro` · `CobrarVenta` | Cambio, monto insuficiente, atomicidad | 🔨 |
| 3.3 | Cancelar venta | Media | MVP | `ui/historial` · `CancelarVenta` | Regreso de existencia, solo caja abierta | 🔨 |
| 3.4 | Historial de ventas | Media | MVP | `ui/historial` | | 🔨 |
| 3.5 | Formas de pago | Media | MVP parcial (semilla) / S2 pantalla | semilla Room | Efectivo no desactivable | 🟡 |
| 3.6 | Descuentos | Baja | S2 | | | ⏳ |
| 4.1 | Entrada de mercancía | Alta | MVP | `ui/inventario/Entrada` · `RegistrarEntrada` | Suma y movimiento | 🔨 |
| 4.2 | Ajustar existencia | Media | S2 | | | ⏳ |
| 4.3 | Consultar existencias | Alta | MVP | `ui/inventario/Existencias` | | 🔨 |
| 4.4 | Movimientos de inventario | Baja | MVP (registro) / S2 (pantalla) | Room `MovimientoInventario` | | 🟡 |
| 4.5 | Alertar existencia baja | Media | MVP | `EvaluarExistenciaBaja` | Promedio 7 días / umbral fijo | 🔨 |
| 5.1 | Abrir caja | Alta | MVP | `ui/caja/Abrir` | Una sola caja abierta | 🔨 |
| 5.2 | Calcular corte | Alta | MVP | `ui/caja/Corte` · `CalcularCorte` | Esperado, diferencia, excluye canceladas | 🔨 |
| 5.3 | Cerrar caja | Alta | MVP | `ui/caja/Corte` · `CerrarCaja` | Requiere corte actualizado | 🔨 |
| 5.4 | Notificar caja abierta | Baja | S2 | | | ⏳ |
| 5.5 | Consultar cortes | Media | MVP | `ui/caja/Cortes` | | 🔨 |
| 6.1 | Reporte ventas por periodo | Alta | Stretch / S2 | | Ticket promedio | ⏳ |
| 6.2 | Ventas por forma de pago | Media | S2 | | | ⏳ |
| 6.3 | Productos más vendidos | Media | S2 | | | ⏳ |
| 6.4 | Ventas por categoría | Baja | S2 | | | ⏳ |
| 6.5 | Reporte de inventario | Media | S2 | | | ⏳ |
| 6.6 | Exportar reportes | Media | S2 | | | ⏳ |
| 7.1 | Sincronizar automáticamente | Alta | S2 | | Idempotencia | ⏳ |
| 7.2 | Estado de sincronización | Alta | MVP | `ui/components/IndicadorSync` | | 🔨 |
| 7.3 | Sincronizar manualmente | Media | Stretch / S2 | | | ⏳ |
| 7.4 | Recuperar en dispositivo nuevo | Alta | S2 | | | ⏳ |
| 7.5 | Sesión en un solo dispositivo | Alta | S2 | | | ⏳ |
| 7.6 | Notificar falta de sync | Media | S2 | | | ⏳ |

**Avance MVP:** 11 / 23 RF completos + 2 parciales (3.5, 4.4) en alcance del MVP.

**Fase 1 (26-oct-2026):** capa de datos lista para los RF en 🔨 (Room, transacciones y consultas). Pruebas: `DineroTest` (4, JVM) + `VentaCajaTest` y `CatalogoInventarioTest` (23, en el teléfono 2412DPC0AG), todas pasan. Faltan dominio (Fase 2) y pantallas (Fase 3).

**Fase 2 (26-oct-2026):** dominio listo: `core/Mensajes.kt` (literales del DFR + alertas de campo propuestas, pendientes de validar), `Validadores`, `LectorNumeros`, `Formato`, reglas (descuento, cobro, existencia baja, corte), `Carrito` y casos de uso por módulo. 48 pruebas JVM pasan.

**Fase 3A (26-oct-2026):** registro, pantalla principal, perfil y cerrar sesión probados en el teléfono (adb). 1.2 en verificación: no se pudo probar el inicio de sesión en el teléfono porque cerrar sesión siempre se bloquea por registros pendientes (no hay sincronización en el MVP). 56 pruebas JVM.

**Fase 3B (26-oct-2026):** categorías (crear, duplicado, renombrar, eliminar con y sin productos) y catálogo (registro con foto de galería comprimida a 576×1024 y 135 KB, búsqueda, filtros, edición, desactivar/reactivar, bloqueo de cambio de tipo con existencia) probados en el teléfono. 2.8 y 2.9 adelantados del Sprint 2 porque ya estaban hechos en datos y dominio.
