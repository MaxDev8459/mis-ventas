# Especificación — Fase 3: fiado + venta rápida sin producto

> Versión 1 · 22-nov-2026 · Estado: **aprobada por el cliente (22-nov-2026)**.
> Respuestas del cliente en `docs/07-DESARROLLO-INDEPENDIENTE.md` (sección "Fase 3 — respuestas del cliente").
> Versión de la app al liberar: **1.4.0** · Room **v6 → v7**.

## 1. Clientes

- Datos: **nombre** (obligatorio, de 2 a 40 caracteres) y **teléfono** (opcional, 10 dígitos).
- El nombre no se repite (sin distinguir mayúsculas, acentos ni espacios), para no confundir a dos "Juan".
  Si hay dos con el mismo nombre, se distinguen al escribirlo ("Juan tienda", "Juan Pérez").
- Se crea desde la pantalla de clientes o al fiar, escribiendo el nombre. Se puede editar.
- **No se borra** (regla del proyecto). Un cliente con saldo $0 baja al final de la lista.

## 2. Fiar una venta

- En la pantalla de cobro aparece la opción **"Fiar"** junto a las formas de pago.
  **No aparece** si el carrito tiene un renglón "Varios" (respuesta 6).
- Al elegir "Fiar":
  1. Se elige o se crea el cliente. Se muestra su saldo actual.
  2. Si su saldo **más esta venta** pasa el monto de aviso, aparece el aviso
     "Juan ya debe $X. Con esta venta debería $Y." con **Fiar de todos modos** / **Cancelar**. No bloquea.
  3. **Pago inicial** opcional ($0 por defecto), con su forma de pago (Efectivo, Transferencia…).
     Debe ser menor que el total (si paga todo, no es fiado: se cobra normal).
- El descuento funciona igual que hoy (si está habilitado).
- Se descuenta el inventario y cuenta para la carga del día igual que una venta normal.
- La venta queda **sin forma de pago** y con el cliente. El pago inicial se guarda como un abono de ese día
  que se aplica a esta venta.
- Comprobante y detalle de la venta: "Fiado a Juan · pagó $50 · debe $70".

## 3. Abonos

- Desde el detalle del cliente: **"Registrar abono"** con monto y forma de pago (respuesta 1).
- **Requiere la caja abierta**, porque el abono entra en el corte del día en que se paga.
  Si no hay caja abierta, se avisa: "Abre la caja para registrar el abono".
- El monto debe ser mayor que $0 y **no puede pasar lo que debe el cliente** (no hay saldo a favor).
  Botón "Liquidar todo" para llenar el saldo completo.
- Se aplica al saldo total, **de la venta fiada más vieja a la más nueva** (respuesta 4c). La app guarda
  cuánto fue a cada venta.
- Un abono invalida el corte calculado, como una venta (hay que recalcularlo antes de cerrar).
- **Cancelar un abono equivocado:** solo mientras su caja esté abierta, y si ninguna de sus ventas se canceló.
  El abono queda como "Cancelado" (no se borra) y el saldo regresa.

## 4. Cancelar una venta fiada

- Mismas reglas que hoy: solo ventas de la **caja abierta**. Por eso, una venta fiada solo se cancela
  el día en que se fió, y sus abonos son de esa misma caja. **Ningún corte cerrado cambia.**
- El producto regresa al inventario (como hoy).
- Si la venta tenía abonos aplicados (el pago inicial u otros abonos), se registra una **devolución en efectivo**
  por esa suma (respuesta 4b). La confirmación lo avisa: "Se devolverán $50 en efectivo al cliente".
- La devolución resta al efectivo esperado del corte.
- **"Corregir venta"** sobre una venta fiada: se cancela (con su devolución, si hay) y se vuelve a capturar;
  al cobrar hay que volver a elegir "Fiar" y el cliente.

## 5. Corte de caja

| Concepto | Cálculo |
|---|---|
| Total vendido | Todas las ventas registradas de la caja, **incluidas las fiadas** (como hoy). |
| Número de ventas, descuentos | Igual que hoy, incluyen fiadas. |
| Por forma de pago (ventas) | Igual que hoy. Las ventas fiadas no tienen forma de pago: salen en la línea "Fiado del día". |
| **Fiado del día** | Suma del total de las ventas fiadas registradas en la caja. |
| **Abonos recibidos** | Abonos registrados (no cancelados) en la caja, con su desglose por forma de pago. Incluye pagos iniciales. |
| **Devoluciones** | Solo si hay: devoluciones por cancelar ventas fiadas. |
| **Efectivo esperado** | fondo + ventas en efectivo + abonos en efectivo − devoluciones. |
| Diferencia | contado − esperado (como hoy). |

Ejemplo: fondo $200, ventas en efectivo $500, transferencia $100, fiado $120 con pago inicial de $50
en efectivo, un abono de $30 por transferencia → total vendido $720; fiado del día $120;
abonos recibidos $80 (efectivo $50, transferencia $30); efectivo esperado $200 + $500 + $50 = **$750**.

Los cortes anteriores (antes de la 1.4.0) se ven igual que hoy: sin fiado ni abonos.

## 6. Pantalla "Clientes / Fiado" (desde Inicio)

- Arriba: **Total por cobrar** (suma de saldos).
- Lista de clientes: nombre y saldo; primero los que deben (de mayor a menor saldo), después los de $0.
  Buscador por nombre (sin acentos).
- Botón "Nuevo cliente".
- Detalle del cliente: saldo, teléfono, "Registrar abono", "Editar" y el historial con sus ventas fiadas
  (fecha, total, pagado, debe) y sus abonos (fecha, monto, forma de pago, estado).
- **Monto de aviso de saldo alto**: se configura en esta pantalla (ícono de ajustes). Empieza en **$150**. Se respalda con la configuración del negocio.
- Sin WhatsApp por ahora (respuesta 9).

## 7. Venta rápida sin producto ("Varios")

- En Vender, botón **"Monto libre"**: pide **monto** (obligatorio, mayor que $0) y **descripción** opcional
  (hasta 40 caracteres; vacía = "Varios").
- Agrega un renglón al carrito; puede haber varios y mezclarse con productos. Se quita como cualquier renglón.
- No descuenta inventario ni cuenta para la carga del día. Al cancelar la venta no mueve inventario.
- Se cobra con el flujo de siempre (descuento si está habilitado). **No se puede fiar.**
- En reportes por producto todos los renglones sin producto se juntan en **"Varios"**; en el reporte por
  categoría salen como categoría "Varios". El detalle de la venta muestra la descripción.
- "Corregir venta" también regresa los renglones "Varios" al carrito.

## 8. Reportes (lo mínimo en esta fase)

- **Ventas por forma de pago:** se agrega una fila "Fiado" con las ventas fiadas, para que la suma cuadre
  con el total vendido.
- **Productos y categorías:** fila "Varios" (sección 7).
- Historial de ventas: las fiadas muestran "Fiado · Juan" en lugar de la forma de pago.
- Reportes de fiado y abonos por periodo quedan para la **Fase 5**.

## 9. Datos (Room v6 → v7)

Tablas nuevas (todas con `id` UUID, `creadoEn`, `actualizadoEn`, `pendienteSync`):

- `cliente`: nombre, nombreNormalizado (único), telefono (nullable).
- `abono`: clienteId, cajaId, fechaHora, tipo (`ABONO` / `DEVOLUCION`), montoCentavos (siempre positivo),
  formaPagoId (devolución = Efectivo), ventaId (solo devoluciones), estado (`REGISTRADO` / `CANCELADO`), canceladoEn.
- `aplicacion_abono`: abonoId, ventaId, montoCentavos (cuánto del abono fue a cada venta).

Cambios en tablas existentes:

- `venta`: `formaPagoId` pasa a **opcional** (null = fiada) y se agrega `clienteId` (opcional).
  SQLite no permite quitar un `NOT NULL`, así que la tabla se **reconstruye** (crear nueva, copiar todo, cambiar nombre).
- `venta_detalle`: `productoId` pasa a **opcional** (null = "Varios"); el nombre guardado es la descripción.
  También se reconstruye.
- `caja`: columnas nuevas opcionales `fiadoCentavos`, `abonosCentavos` y `devolucionesCentavos` (se guardan
  con el corte). El desglose de abonos por forma de pago se lee de la tabla `abono` (no cambia después de cerrar).
- `configuracion`: `avisoSaldoCentavos` (con valor de inicio de $150).

Seguridad de los datos:

- Migración escrita a mano (nunca destructiva). Se verifica con `sqlite3` sobre una **copia** de la base de
  Mis Ventas Pruebas (v6, con datos): mismas columnas que `7.json`, mismo número de ventas, detalles y
  movimientos antes y después, y `PRAGMA foreign_key_check` sin errores.
- Respaldo en Firebase: colecciones nuevas `cliente`, `abono`, `aplicacion_abono` en `Mapeo`, sincronizador,
  pendientes, recuperación y cambio de cuenta. Los campos nuevos de `venta`, `venta_detalle`, `caja` y
  `configuracion` se leen como vacíos en respaldos anteriores (una venta vieja sin `clienteId` sigue igual).
- Saldo de un cliente = total de sus ventas fiadas registradas − lo aplicado de sus abonos no cancelados.
  Se calcula, no se guarda, para que nunca se descuadre.

## 10. Pruebas unitarias (cálculo crítico)

- Corte: efectivo esperado con ventas en efectivo, fiado sin pago inicial, fiado con pago inicial en efectivo
  y en transferencia, abonos de ventas de días anteriores, abono cancelado, devolución.
- Reparto de un abono de la venta más vieja a la más nueva (exacto, parcial, varias ventas, liquidar todo).
- Validaciones: abono mayor al saldo, abono sin caja, pago inicial ≥ total, fiar con "Varios".
- Saldo del cliente tras cancelar una venta fiada con abonos (devolución correcta).
- Aviso de saldo alto (debajo, igual, encima).
- Venta rápida: importe, descripción vacía = "Varios", no mueve inventario ni la carga.
- Migración 6→7 (prueba instrumentada con `MigrationTestHelper` si es posible, además de `sqlite3`).

## 11. Respuestas del visto bueno (22-nov-2026)

1. El aviso de saldo alto empieza en **$150**.
2. Cancelar un abono equivocado: **solo mientras su caja esté abierta**.
3. El nombre del cliente **no se repite**.
