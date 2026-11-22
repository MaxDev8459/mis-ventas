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
| 2b | Carga del día (v1 y v2 con + / −, Máx y precargas) | v4→v5→v6 | ✅ 1.3.0 (21-nov-2026) |
| 3 | Fiado + venta rápida sin producto | v6→v7 | ✅ 1.4.0 (22-nov-2026) |
| 4 | Apartados con existencia separada | v7→v8 | ✅ 1.5.0 (22-nov-2026) |
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

## Fase 3 — respuestas del cliente (22-nov-2026)

Preguntas abiertas de `docs/ESTADO.md`, contestadas por el cliente:

| # | Pregunta | Respuesta |
|---|---|---|
| 1 | Forma de pago de los abonos | **Se elige cada vez** (Efectivo, Transferencia, etc.). |
| 2 | Pago parcial al fiar | **Sí, pago inicial opcional** (puede ser $0). Se registra como primer abono de ese día. |
| 3 | Corte con fiado | **"Total vendido" incluye lo fiado.** El efectivo esperado suma solo lo cobrado en efectivo y los abonos en efectivo. Líneas aparte: "Fiado del día" y "Abonos recibidos". |
| 4 | Cancelar una venta fiada con abonos | **Se cancela y se devuelve el dinero.** La devolución sale en el corte de hoy (caja abierta); los cortes cerrados no cambian. |
| 4b | Forma de la devolución | **Siempre en efectivo**: resta al efectivo esperado de hoy. |
| 4c | A qué se aplica un abono | **Al saldo total del cliente, de la venta fiada más vieja a la más nueva.** |
| 5 | Límite de crédito | **Aviso de saldo alto**: un monto para todos los clientes. Si el saldo lo pasa, sale un aviso, pero se puede fiar. |
| 6 | Fiar ventas rápidas | **No.** Si el carrito tiene un renglón "Varios", no aparece la opción de fiar. |
| 7a | Descripción de la venta rápida | **Opcional.** Vacía se guarda como "Varios". En reportes por producto todo se junta en "Varios". |
| 7b | Forma de la venta rápida | **Renglón "Varios" en el carrito**, junto con productos del catálogo; se cobra con el flujo de siempre (con descuento si está habilitado). |
| 8 | Dónde se consulta el fiado | **Botón "Clientes / Fiado" en Inicio**: clientes con saldo y total por cobrar; detalle con ventas fiadas, abonos y "Registrar abono". |
| 9 | Recordar por WhatsApp | **No por ahora** (se puede agregar después sin tocar la base). |

Riesgos avisados al cliente: la devolución por cancelar una venta fiada con abonos solo se registra con la caja abierta y en el corte de hoy, aunque los abonos fueran de días ya cerrados.

## Fase 3 — detalle

Especificación completa en `docs/09-ESPEC-FIADO.md` (aprobada el 22-nov, aviso de saldo alto desde $150).
- Tablas nuevas `cliente`, `abono` (abonos y devoluciones) y `aplicacion_abono` (cuánto de cada abono fue a cada venta).
  `venta.formaPagoId` y `venta_detalle.productoId` pasan a opcionales: la migración 6→7 reconstruye ambas tablas
  y revisa que no se pierdan filas ni se rompan llaves (si no cuadra, se revierte todo).
- Migración verificada con `sqlite3` (script que aplica las sentencias de `MIGRACION_6_7` y compara contra `7.json`)
  sobre una base sintética y sobre una copia de Mis Ventas Pruebas (54 ventas, 83 detalles), y aplicada en el teléfono.
- Cálculos en `domain/regla/ReglaFiado` (45 pruebas en `FiadoTest`); transacciones en `FiadoRepo` y `VentaRepo`
  (14 + 1 pruebas instrumentadas en `FiadoRoomTest`). Respaldo en Firebase, recuperación y cambio de cuenta.
- Corrección durante la prueba: con carga del día, un monto libre cerraba la app al entrar a Vender
  (`CargaDao.observarVendido` agrupaba renglones sin producto). Prueba de regresión agregada.
- Ajuste pedido por el cliente: al calcular el corte se oculta la carga del día; solo queda el resumen
  (en "Cortes anteriores" la carga se sigue viendo).

## Fase 4 — respuestas del cliente (22-nov-2026)

Ya acordado el 21-nov: fecha, hora y lugar de entrega; nombre libre (sin lista de clientes); pago completo al
entregar; el producto se separa (existencia apartada). Preguntas nuevas:

| # | Pregunta | Respuesta |
|---|---|---|
| 1 | Existencia al apartar | **Se separa sin salir**: la existencia total no cambia; lo apartado no se puede vender (disponible = total − apartado). Al entregar baja como venta normal. |
| 2 | Precio | **El del día en que se apartó.** |
| 3 | Cobro al entregar | **Como una venta normal** en la caja abierta (forma de pago, descuento si está habilitado). **No se puede fiar.** |
| 4 | Dónde se crea | **Desde Vender**: botón "Apartar" en lugar de cobrar. Solo productos del catálogo (sin monto libre). |
| 5 | Si no viene el cliente | Sigue apartado hasta marcarlo "Entregado". **Al cerrar la caja** se liberan solos todos los apartados no entregados **de ese día y de días anteriores** (los de días futuros no se tocan). |
| 6 | Modificar | **Fecha, hora, lugar y nombre.** Para cambiar productos: cancelar y apartar de nuevo. |
| 7 | Avisos | **En Inicio y notificaciones**: una **15 minutos antes** ("en 15 minutos tienes que entregar…") y otra **a la hora exacta** ("es hora de entregar…"), con cliente, productos, lugar y hora. |
| 8 | Merma o ajuste por debajo de lo apartado | **No se permite**: primero hay que cancelar o cambiar el apartado. |

## Fase 5 — respuestas del cliente (23-nov-2026)

Ya acordado el 21-nov: comparar periodos, ventas por hora y día, fiado y mermas a precio de venta; "Más vendidos" no se toca.

| # | Pregunta | Respuesta |
|---|---|---|
| 1 | Cómo comparar | **Contra el periodo anterior**, automático en "Ventas por periodo" (hoy vs. ayer, semana vs. semana pasada, mes vs. mes pasado, rango vs. los mismos días antes). |
| 2 | Qué comparar | **Totales y productos**: total, número de ventas y ticket promedio con su diferencia, y los productos que más subieron y bajaron. |
| 3 | Por hora y día | **A qué hora y qué día de la semana se vende más** en el periodo elegido. |
| 4 | Vista | **Barras con su número** (la mejor hora o día resaltada). |
| 5 | Reporte de fiado | **Completo**: del periodo, fiado, abonos (por forma de pago) y devoluciones; además el total por cobrar de hoy y los clientes con saldo. |
| 6 | Precio de las mermas | **El del día de la merma**: se empieza a guardar al registrarla (migración 8→9, una columna). Las anteriores se valoran con el precio actual y se marcan así. |
| 7 | Qué salidas cuentan | **Todas por motivo**: merma, caducado, consumo propio y otro (no la corrección de conteo). |
| 8 | Exportar | **Sí**, PDF y CSV como los demás reportes. |

Aviso dado al cliente: las mermas ya registradas no tienen precio guardado; se valoran con el precio actual.

## Versión 1.4.0 (22-nov-2026)

Instalada en la app real con `install -r` (firma igual a la 1.3.0, indicador en "Sincronizado" antes de instalar).
Abrió sin fallos y con "Sincronizado" después; la base pasó a v7.

## Versión 1.3.0 (21-nov-2026)

Instalada en la app real con `install -r` (con el indicador en "Sincronizado" antes de instalar). Incluye la Fase 2 completa, la corrección del modo oscuro en existencias y la del doble toque en "regresar". Antes se probó en Mis Ventas Pruebas, que pasó por las migraciones v4→v5→v6 en el teléfono. La cadena v4→v6 también se verificó con `sqlite3` contra `6.json`, con datos intactos.
