# Especificación — Fase 5: reportes nuevos

> Versión 1 · 23-nov-2026 · Estado: **aprobada por el cliente (23-nov-2026)**.
> Respuestas del cliente en `docs/07-DESARROLLO-INDEPENDIENTE.md` (sección "Fase 5 — respuestas del cliente").
> Versión de la app al liberar: **1.6.0** · Room **v8 → v9** (solo una columna, para el precio de las mermas).

Todos los reportes usan el selector de siempre (Hoy, Semana, Mes, Rango), funcionan sin internet y se exportan a
**PDF y CSV** (respuesta 8). "Más vendidos" no cambia.

## 1. Ventas por periodo + comparación (respuestas 1 y 2)

Al reporte actual se le agrega, debajo del resumen, **"Comparado con …"**:

| Dato | Ejemplo |
|---|---|
| Total vendido | $3,200.00 · **+$340.00 (+12 %)** vs. $2,860.00 |
| Número de ventas | 85 · −3 (−3 %) |
| Ticket promedio | $37.65 · +$5.10 (+16 %) |

- Verde si subió, rojo si bajó. Si el periodo anterior no tuvo ventas, se muestra "sin ventas antes" (sin porcentaje).
- **Productos que más subieron** y **que más bajaron**: hasta 5 de cada uno, por importe ("Carlota: +$480.00").
  Los montos libres cuentan como "Varios".
- **Periodo anterior**: **el mismo tramo** (aprobado), para comparar parejo aunque el periodo
  actual no haya terminado:
  - Hoy (hasta esta hora) vs. **ayer hasta la misma hora**.
  - Semana (lunes a hoy) vs. **la semana pasada de lunes al mismo día y hora**.
  - Mes (día 1 a hoy) vs. **el mes pasado del día 1 al mismo día y hora** (si el mes pasado es más corto, hasta su último día).
  - Rango de fechas ya pasadas vs. **el mismo número de días inmediatamente antes**.

## 2. Ventas por hora y día (nuevo, respuestas 3 y 4)

Del periodo elegido (ventas registradas, incluidas fiadas y entregas de apartados):

- **Por hora del día**: una barra por hora con ventas (de la primera a la última hora con ventas, por ejemplo de
  8:00 a 20:00), con importe y número de ventas. La hora con más ventas va resaltada: "Tu mejor hora: 13:00–14:00".
- **Por día de la semana**: lunes a domingo, con importe y número de ventas; el mejor día resaltado.
  En periodos de varias semanas, se suma lo de todos los lunes, todos los martes, etc.
- Barras horizontales sencillas con el número a un lado. En PDF y CSV sale como tabla (hora/día, ventas, importe, %).

## 3. Fiado y abonos (nuevo, respuesta 5)

- **Del periodo**:
  - Fiado: número de ventas fiadas y su total (no canceladas).
  - Abonos recibidos: total y desglose por forma de pago (no cancelados; incluye pagos iniciales).
  - Devoluciones en efectivo por cancelar ventas fiadas.
- **Hoy** (no depende del periodo): **total por cobrar** y la lista de **clientes con saldo**, del que más debe al
  que menos, con su saldo (los que pasan el aviso de saldo alto, resaltados).

## 4. Mermas y pérdidas (nuevo, respuestas 6 y 7)

- Cuentan las **disminuciones** por ajuste con motivo **Merma, Producto caducado, Consumo propio y Otro**
  (incluye la merma registrada desde Vender). **No cuentan** las correcciones de conteo ni los aumentos.
- Se valoran a **precio de venta del día de la merma**: desde la 1.6.0 se guarda el precio al registrarla.
  Las mermas anteriores no lo tienen: se valoran con el **precio actual** y se marcan con "*" y la nota
  "* Valorado con el precio actual (merma registrada antes de la versión 1.6.0)".
- Muestra:
  - **Total** perdido en el periodo.
  - **Por motivo**: número de registros, valor y porcentaje.
  - **Por producto**: cantidad y valor, del mayor al menor valor.

## 5. Datos (Room v8 → v9)

- `movimiento_inventario`: columna nueva opcional `precioUnitarioCentavos` (se llena en los ajustes desde la 1.6.0;
  null en los anteriores). Migración con un solo `ALTER TABLE … ADD COLUMN`; se verifica con
  `scripts/verificar_migracion.py` (sintética y copia de Mis Ventas Pruebas).
- Respaldo en Firebase: el campo nuevo va en el documento del movimiento; los respaldos anteriores se leen sin precio.
- Todo lo demás se calcula con lo que ya está guardado (ventas, abonos, movimientos).

## 6. Pruebas

- Unitarias: periodo anterior de cada tipo (incluido el fin de mes corto y el cambio de año); diferencias y
  porcentajes (sin ventas antes, bajada, igual); productos que subieron/bajaron (empates, producto nuevo, Varios);
  mejor hora y día (empates, sin ventas); valor de mermas con precio guardado y con precio actual, motivos que
  cuentan y que no; totales del reporte de fiado.
- Instrumentadas con Room: consultas por hora y día de la semana en hora local, mermas con y sin precio guardado,
  fiado y abonos del periodo; el ajuste guarda el precio.
- Migración 8→9 con `scripts/verificar_migracion.py`.

## 7. Respuestas del visto bueno (23-nov-2026)

1. Periodo anterior: **el mismo tramo**.
2. **5 productos** en "más subieron" y "más bajaron".
