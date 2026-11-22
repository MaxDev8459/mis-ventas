# Especificación: Carga del día v2

Pedida por el cliente el 21-nov-2026, después de probar la v1 en Mis Ventas Pruebas. **Estado: validada el 21-nov-2026 (unidad para kilo a nivel cuenta); en prueba en Mis Ventas Pruebas.**

## 1. Panel de cantidades (sin casillas de texto)

- Cada producto muestra: nombre, "hay X" y el control **[ − ] cantidad [ + ]**, con un botón **[Máx]** al lado.
- **Una carga nueva empieza todo en 0.** Si se edita una carga ya guardada, cada producto muestra lo que ya está cargado.
- **Por pieza:** + / − cambia de 1 en 1.
- **Por kilo:** + / − cambia según la **unidad para kilo** (por defecto 100 g = 0.100 kg). La unidad se puede configurar.
- **Tocar el número** abre un cuadro con teclado numérico para escribir la cantidad exacta. Para los productos por kilo se escribe en kg.
- **[Máx]** pone todo lo disponible de ese producto: la existencia de hoy más lo vendido y las mermas de la caja.
- **+** se desactiva al llegar al máximo; **−** se desactiva en 0.
- Se corrige la franja negra que aparecía sobre el teclado.

## 2. Precargas (sustituyen a "Usar carga anterior")

Arriba de la lista, el botón **"Precargas"** abre un panel. Al elegir una, se sustituye lo capturado y después se ajusta con + / −. Nada se guarda hasta tocar "Guardar carga".

El panel, en este orden:
1. **Repetir última carga**: la carga de la caja anterior más reciente.
2. **N de cada producto**: se elige N con + / −. Los productos por pieza reciben N piezas; los productos por kilo reciben N × la unidad para kilo (con 100 g y N = 10, reciben 1 kg). Nunca se pasa de lo disponible.
3. **Precargas guardadas**: ordenadas de la más usada a la menos usada.

**Guardar como precarga:** al guardar la carga, una opción "Guardar también como precarga" pide un nombre (por ejemplo "Sábado" o "Escuela"). Si el nombre ya existe, se actualiza esa precarga.

**Administrar:** al mantener presionada una precarga guardada se puede renombrar o quitar. Quitar = desactivar; no se borra.

**Cantidades al aplicar:** las de la precarga, sin pasar de lo disponible hoy. Los productos inactivos o sin existencia se omiten y se avisa cuáles fueron.

## 3. Unidad para kilo

- Ajuste único de 50 g, 100 g, 250 g o 500 g. Por defecto 100 g.
- Lo usan el + / − de los productos por kilo y "N de cada producto".
- Se cambia desde el panel de Precargas. Se guarda en la cuenta (`configuracion.pasoKiloMilesimas`) y se respalda.

## 4. Aviso "Agregar a la carga" en Vender

En lugar de la casilla de cantidad, usa el mismo control [ − ] cantidad [ + ]. Empieza en 1 pieza o en una unidad para kilo, y se puede tocar el número para escribirla.

## Datos

- **Tabla nueva `precarga`** (id, nombre, cantidades por producto, veces usada, última vez usada, activa) y columna `configuracion.pasoKiloMilesimas` (100 por defecto). Migración v5→v6: solo agrega; verificada con `sqlite3` contra `6.json`.
- Se respalda en Firebase y entra en la recuperación de la cuenta.
- La carga del día (`carga_caja`) no cambia.
