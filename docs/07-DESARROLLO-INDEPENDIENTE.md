# Desarrollo independiente (después de la entrega)

Cambios acordados con el cliente en la entrevista del 21-nov-2026. No vienen del DFR: salen de su uso real de la app en el negocio.

## Reglas de negocio acordadas

- **Cobro:** sin cambios por ahora.
- **Corregir venta:** cancelar y recapturar, solo con la caja abierta. Quedan registradas ambas ventas.
- **Encontrar productos en la venta:** el cliente tiene ideas propias. Se le preguntan y se prueban una por una antes de decidir.
- **Venta rápida sin producto:** cobrar un monto libre que en reportes aparece como "Varios".
- **Merma desde la venta:** usa el ajuste de inventario con motivo Merma. En reportes se valora a precio de venta; no se agrega el costo del producto.
- **Fiado:** lista de clientes (nombre; teléfono opcional), abonos parciales o liquidación total. El abono entra al corte del día en que se paga y el inventario baja al entregar.
- **Apartados:** fecha, hora y lugar de entrega; nombre libre, sin lista de clientes; pago completo al entregar. El producto se separa: hay existencia apartada.
- **Carga del día al abrir caja:** opcional. Lista con cantidades y botón "usar carga anterior". No mueve la existencia total; durante el día muestra "te quedan X" y, si se acaba, ofrece agregar a la carga. Al cerrar solo se ve el sobrante.
- **Caja:** calculadora de billetes y monedas que llena el efectivo contado. No se guarda el desglose.
- **Reportes:** comparar periodos, ventas por hora y día, fiado y mermas. "Más vendidos" ya existe y no se toca.
- **Catálogo:** sin cambios.
- **Modo oscuro:** sigue al teléfono, con opción en Perfil.
- **Huella al regresar:** tiempo configurable en Perfil (nunca, 1, 5 o 15 min). Sustituye la reverificación fija de 1 min de `demo-entrega`.

## Fases

| Fase | Contenido | Room | Estado |
|---|---|---|---|
| 0 | Correcciones de la revisión contra el DFR y aviso de privacidad | No | ✅ 1.0.1 (21-nov-2026) |
| 1 | Corregir venta, merma desde la venta, calculadora de billetes, modo oscuro y huella configurable | No | ✅ 1.1.0 (21-nov-2026) |
| 2a | Encontrar productos: favoritos y chips de categoría | v3→v4 | ✅ 1.2.0 (21-nov-2026) |
| 2b | Carga del día al abrir caja | v4→v5 | En prueba (Mis Ventas Pruebas) |
| 3 | Fiado + venta rápida sin producto | v5→v6 | Pendiente |
| 4 | Apartados con existencia separada | v6→v7 | Pendiente |
| 5 | Reportes nuevos | No | Pendiente |

## Fase 1 — detalle

- **Corregir venta** (`ui/historial/DetalleVentaScreen`, `domain/venta/CorreccionVenta`): botón en el detalle de una venta cancelable. Cancela la venta (la existencia regresa) y abre Vender con sus productos, usando el precio y la existencia de hoy. Si un producto está inactivo o ya no alcanza la existencia, queda fuera y se avisa. El descuento se vuelve a capturar al cobrar.
- **Merma desde la venta** (`ui/venta`): al mantener presionado un producto se abre "Registrar merma". Usa `AjustarExistencia` con motivo `MERMA`. Si el carrito tenía más de lo que queda, se ajusta.
- **Calculadora de billetes** (`ui/caja/CorteScreen`, `domain/regla/ReglaConteoEfectivo`): piezas por denominación, de $1000 a $0.50. "Usar total" llena el efectivo contado.
- **Modo oscuro** (`ui/theme/Theme`): esquema oscuro con el verde del logo. En oscuro, la barra superior es una superficie oscura.
- **Huella configurable** (`domain/regla/ReglaVerificacion`, `ui/navigation/NavegacionApp`): por defecto "Nunca", igual que en la 1.0. La verificación se muestra encima de la app sin perder lo capturado.
- Tema y huella se guardan en `data/preferencias/PreferenciasDispositivo` (SharedPreferences, solo en ese teléfono). No tocan Room ni Firebase.
- 15 pruebas nuevas en `FaseUnoTest.kt`, 184 en total.

## Fase 2a — detalle

- **Favoritos** (`Producto.favorito`, migración 3→4, que solo agrega la columna con `DEFAULT 0`): se marcan con la estrella de la foto en Vender. Se respaldan en Firebase (`Mapeo`); los respaldos anteriores se leen como no favorito.
- **Chips deslizables** debajo de la búsqueda: Todos, ★ Favoritos y las categorías que tienen productos para vender. En todas las vistas los favoritos salen primero y después el resto en orden alfabético (`domain/venta/FiltroVenta`).
- La búsqueda ya no distingue acentos ("cafe" encuentra "Café") y se combina con el chip elegido.
- La migración se verificó con `sqlite3`: v3 + `MIGRACION_3_4` tiene las mismas columnas que el esquema `4.json`.
- 7 pruebas nuevas en `FiltroVentaTest.kt`, 191 en total.

## Fase 2b — detalle (carga del día)

Reglas confirmadas el 21-nov-2026:
- Se captura al abrir caja, con "Omitir por hoy". Después se captura o edita desde Caja → "Carga del día".
- No se puede cargar más de lo que había al abrir: existencia de hoy + lo vendido y las mermas de esa caja.
- "Te quedan" = cargado − vendido (sin canceladas) − mermas registradas mientras la caja está abierta.
- En Vender, si un producto no se cargó o ya no queda, un aviso permite "Agregar a la carga" (con cantidad) o "Vender sin agregar". Si no hay carga, no hay avisos.
- En el corte (y en cortes anteriores) se ve por producto: cargó, vendió, merma y sobra.

Implementación:
- Tabla `carga_caja` (llave `cajaId` + `productoId`, migración 4→5 solo con `CREATE TABLE`). No mueve la existencia.
- Se respalda en Firebase (colección `carga_caja`) y se incluye en pendientes, recuperación y cambio de cuenta. Los respaldos anteriores llegan sin carga.
- `domain/regla/ReglaCarga`, `data/repository/CargaRepo`, `data/local/dao/CargaDao`, `ui/caja/CargaScreen`.
- Migración verificada con `sqlite3` sobre una copia de la base de Mis Ventas Pruebas (v4): coincide con `5.json`.
- 12 pruebas nuevas en `CargaTest.kt`, 203 en total.
