# Especificación — Fase 4: apartados con existencia separada

> Versión 1 · 22-nov-2026 · Estado: **aprobada por el cliente (22-nov-2026)**.
> Respuestas del cliente en `docs/07-DESARROLLO-INDEPENDIENTE.md` (sección "Fase 4 — respuestas del cliente").
> Versión de la app al liberar: **1.5.0** · Room **v7 → v8**.

## 1. Qué es un apartado

Un pedido que un cliente encarga para recogerlo después: **nombre** (libre, sin lista de clientes), **fecha y hora**
de entrega, **lugar** y los productos con su cantidad. Se paga **completo al entregar**; al apartar no se cobra nada.

Estados: **Pendiente** → **Entregado** (se cobró), **Cancelado** (lo canceló el vendedor) o **Liberado** (no se
entregó y se cerró la caja de ese día). Nada se borra.

## 2. Apartar (desde Vender)

- Se arma el carrito como siempre. Junto a "Cobrar" aparece **"Apartar"** (no aparece si el carrito tiene un monto
  libre: solo productos del catálogo).
- Pide: **nombre** (obligatorio, de 2 a 40 caracteres), **teléfono** (opcional, 10 dígitos), **fecha** y **hora** de
  entrega (no pueden ser pasadas) y **lugar** (obligatorio, hasta 60 caracteres).
- **No requiere caja abierta** (no mueve dinero): sin caja, Vender deja armar el carrito, pero solo con "Apartar";
  "Cobrar" sigue pidiendo abrir caja.
- El **precio queda fijo** al apartar: al entregar se cobra ese precio aunque el producto cambie de precio.
- No mueve dinero ni la existencia total; el producto queda **separado** (ver 3).
- Al guardar se programan sus dos notificaciones (ver 6).

## 3. Existencia separada

- **Existencia total** = lo que tienes (no cambia al apartar).
- **Apartado** = suma de lo que está en apartados **pendientes**.
- **Disponible** = total − apartado. Es lo único que se puede vender.
- Dónde se nota:
  - **Vender**: muestra y deja vender solo lo disponible ("Hay 5 · 3 apartadas"). Un producto con todo apartado
    no aparece para vender.
  - **Existencias** y detalle del producto: total y, si hay, "Apartado: 3"; la alerta de existencia baja usa lo disponible.
  - **Merma o ajuste para disminuir**: **no se permite** dejar la existencia por debajo de lo apartado
    ("Hay 3 apartadas de Carlota. Cancele o cambie el apartado primero"). Respuesta 8.
  - **Carga del día**: sin cambios. Puedes cargar también lo apartado (lo llevas para entregarlo); al entregar
    cuenta como vendido de la carga.
- La regla se revisa dentro de la misma transacción que guarda la venta o el ajuste, para que nunca se venda lo apartado.

## 4. Entregar

- Pantalla **Apartados** → el apartado → **"Entregar"**. Requiere **caja abierta**.
- Abre el **cobro de siempre** con los productos y precios del apartado: forma de pago, monto recibido y cambio,
  y descuento si está habilitado. **No aparece "Fiar"** (respuesta 3).
- Al confirmar, en una sola transacción: se registra la venta en la caja abierta (baja la existencia, movimiento de
  inventario, entra al corte), el apartado pasa a **Entregado** y guarda qué venta fue.
- Se puede entregar aunque el producto se haya desactivado del catálogo después de apartarlo.
- Si después se cancela esa venta (mismo día), el producto regresa al inventario como cualquier venta; el apartado
  queda como Entregado (para volver a separarlo, se aparta de nuevo).

## 5. Cambiar, cancelar y liberar

- **Editar** (mientras está pendiente): nombre, fecha, hora y lugar. Se reprograman sus notificaciones. Para cambiar
  productos o cantidades: cancelar y apartar de nuevo.
- **Cancelar** (mientras está pendiente, con confirmación): el producto vuelve a estar disponible. No requiere caja.
- **Liberar al cerrar caja** (respuesta 5): al **cerrar la caja**, todos los apartados **pendientes** con entrega
  **ese día o antes** (el día de la caja = el día en que se abrió) pasan a **Liberado** y su producto vuelve a estar
  disponible. Los de días futuros no se tocan. El aviso de "Cerrar caja" lo dice antes de confirmar:
  "Se liberarán 2 apartados no entregados (Juan, Ana)".
- Mientras no se cierra la caja, un apartado cuya hora ya pasó sigue separado y aparece como **"Atrasado"** en rojo.

## 6. Notificaciones (respuesta 7)

Por cada apartado pendiente, dos notificaciones aunque la app esté cerrada:

1. **15 minutos antes**: "En 15 minutos tienes que entregar: Juan · 3 pzas Carlota, 1 kg Queso · Parque central · 11:00 · $160.00".
2. **A la hora exacta**: "Es hora de entregar: Juan · 3 pzas Carlota, 1 kg Queso · Parque central · 11:00 · $160.00".

- Al tocarlas se abre el apartado.
- Se programan con **alarmas exactas** del sistema (permiso `USE_EXACT_ALARM`, se concede al instalar). Se reprograman
  al editar, al abrir la app, al reiniciar el teléfono y después de una recuperación. Al entregar, cancelar o liberar
  se quitan las que falten.
- Si se aparta con menos de 15 minutos de anticipación, solo llega la de la hora exacta.
- **Xiaomi / HyperOS** puede frenar apps en segundo plano: después de instalar hay que revisar en Ajustes de la app
  "Inicio automático" activado y Batería "Sin restricciones". Se prueba en Mis Ventas Pruebas antes de liberar.

## 7. Pantallas

- **Inicio**: botón **"Apartados"** en el menú; la tarjeta de estado muestra "Apartados hoy: 2 · Atrasados: 1"
  cuando hay.
- **Apartados**: pendientes ordenados por fecha y hora de entrega (atrasados en rojo, hoy resaltados), cada uno con
  nombre, hora, lugar, productos y total. Abajo, "Historial" con entregados, cancelados y liberados (más recientes primero).
- **Detalle del apartado**: datos, productos con precio fijo y total; botones "Entregar", "Editar" y "Cancelar apartado".

## 8. Datos (Room v7 → v8)

Solo tablas nuevas (no se reconstruye ninguna tabla existente):

- `apartado`: id, nombre, telefono (opcional), lugar, entregaEn (fecha y hora, epoch ms), totalCentavos, estado
  (`PENDIENTE` / `ENTREGADO` / `CANCELADO` / `LIBERADO`), ventaId (al entregar), terminadoEn, creadoEn,
  actualizadoEn, pendienteSync.
- `apartado_detalle`: id, apartadoId, productoId, nombreProducto, tipoVenta, cantidadMilesimas,
  precioUnitarioCentavos, importeCentavos, creadoEn, pendienteSync.

Seguridad de los datos:

- Migración solo con `CREATE TABLE` / índices; se verifica con `sqlite3` contra `8.json` sobre una copia de Mis Ventas
  Pruebas (mismos conteos antes y después).
- Respaldo en Firebase: colecciones `apartado` y `apartado_detalle` en `Mapeo`, sincronizador, pendientes,
  recuperación y cambio de cuenta. Los respaldos anteriores llegan sin apartados.
- El apartado por producto no se guarda: se calcula de los apartados pendientes (no se puede descuadrar).

## 9. Pruebas

- Unitarias (`ReglaApartado`): disponible = total − apartado; validaciones (nombre, lugar, fecha no pasada);
  cuáles se liberan al cerrar (ese día y anteriores, no futuros, solo pendientes, límite de medianoche);
  horas de las dos notificaciones (y cuando faltan menos de 15 min); textos de las notificaciones; total con precio fijo.
- Instrumentadas con Room: no se vende lo apartado; entregar cobra el precio fijo aunque cambie el precio y deja la
  venta en el corte; ajuste/merma por debajo de lo apartado se rechaza; cerrar caja libera los correctos; cancelar
  libera; todo o nada al entregar.
- En el teléfono (Pruebas): notificaciones a los 15 minutos y a la hora exacta, con la app cerrada.

## 10. Respuestas del visto bueno (22-nov-2026)

1. **Apartar con la caja cerrada: sí** (Cobrar sigue pidiendo caja).
2. **Teléfono opcional** del cliente: sí.
3. Lugar hasta **60 caracteres**: sí.
