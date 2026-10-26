# Guion de la exposición de avance (10 min + 5 de preguntas)

Formato: PowerPoint (requisito de la materia) + demo en vivo con el teléfono proyectado (scrcpy o cámara).

| Min | Bloque | Contenido |
|---|---|---|
| 0–1 | Contexto | Problema de Max Carlotas: registro manual, errores de cálculo, sin visibilidad. Objetivo de Mis Ventas |
| 1–3 | Estado del proyecto | Cronograma: Inicio, Planeación, Análisis, Diseño terminados; Codificación en curso. Matriz de trazabilidad: **24 RF terminados y probados en el teléfono** (3 adelantados del Sprint 2), 79 pruebas automáticas |
| 3–4 | Arquitectura | MVVM por capas, offline-first, backend aislado tras interfaces (Room hoy → Firebase en Sprint 2). DFR como fuente de verdad ([04-ARQUITECTURA-APP.md](04-ARQUITECTURA-APP.md)) |
| 4–9 | **Demo en vivo** (modo avión activado para demostrar que funciona sin internet) | ver pasos abajo |
| 9–10 | Siguiente sprint | Firebase (cuentas y sincronización), reportes y exportar, formas de pago, descuentos, biometría, notificaciones. Fin de codificación 13-nov |

## Datos precargados

Cargados con `CargarDatosDemo` (comando en `CLAUDE.md`). Cuenta: **max@gmail.com / ventas2026**, negocio "Max Carlotas".

| Categoría | Producto | Precio | Existencia | Para qué sirve en la demo |
|---|---|---|---|---|
| Botanas | Papas fritas | $18.00 | 20 pzas | Venta 1 |
| Botanas | Cacahuate japonés | $90.00 / kg | 2.500 kg | Venta por peso |
| Botanas | Galletas | $15.00 | **0 (agotado)** | Entrada de mercancía → reaparece en venta |
| Dulces | Paleta de caramelo | $5.00 | 40 pzas | |
| Dulces | Chicles | $3.00 | **4 pzas (baja)** | Resaltado en existencias; tope de existencia |
| Dulces | Gomitas | $120.00 / kg | **0.800 kg (baja)** | Resaltado en kg |
| Bebidas | Refresco 600 ml | $20.00 | 24 pzas | Venta 1 |
| Bebidas | Agua 600 ml | $12.00 | 15 pzas | Venta en 4 toques |

La caja empieza **cerrada**.

## Pasos de la demo

1. Abrir la app (sesión activa) → pantalla principal: **nombre del negocio**, **Caja cerrada**, indicador **"[n] registros pendientes"**. Tocar **Vender** → "Abra caja para comenzar a vender".
2. **Catálogo:** mostrar búsqueda y filtros. **Nuevo producto** con foto de la galería. Intentar el nombre "papas fritas" → alerta "Ese nombre ya está registrado".
3. **Caja → Abrir caja** con fondo $200.00 → "La caja se abrió exitosamente".
4. **Venta en 4 toques:** Agua 600 ml → Cobrar → Exacto → Confirmar cobro → "Venta registrada exitosamente".
5. **Venta 1:** 2 Papas fritas (tocar dos veces) + 1 Refresco → el total cambia en vivo ($56.00) → Cobrar → Efectivo → **$100** → "Cambio a entregar: $44.00" → Confirmar.
6. **Venta por peso:** Cacahuate japonés → peso de la etiqueta 0.250 → $22.50 → **Transferencia** → Confirmar.
7. **Tope de existencia:** tocar Chicles 5 veces → "Solo hay 4 pzas disponibles de Chicles". Quitar del carrito.
8. **Existencias:** Chicles y Gomitas resaltados como **existencia baja**, Galletas como **agotado**.
9. **Entrada de mercancía:** Galletas → +12 pzas → "La entrada se registró exitosamente. Existencia: 12 pzas" → regresar a **Vender**: Galletas ya aparece.
10. **Historial de ventas** → abrir la venta por peso → **Cancelar venta** → confirmación del DFR → la venta queda "Cancelada" y el cacahuate regresa a 2.500 kg.
11. **Corte de caja:** efectivo contado $260 → esperado $268.00 (fondo $200 + $12 + $56) → **Faltante $8.00**, desglose por forma de pago, la venta cancelada no cuenta → **Cerrar caja** → confirmación → "La caja se cerró exitosamente".
12. **Cortes de caja anteriores:** aparece el corte con apertura, cierre, total y diferencia.
13. Indicador "[n] registros pendientes" → explicar que en Sprint 2 se envían a Firebase.

## Reglas para no perder los datos de la demo

- **No correr `connectedDebugAndroidTest` sin `leaveApksInstalledAfterRun=true`**: desinstala la app y borra todo.
- Ensayar una vez completa; después volver a cargar datos (desinstalar la app y correr `CargarDatosDemo`) para empezar limpio.

## Preguntas probables
- *¿Qué pasa si se apaga el teléfono a mitad de una venta?* → La venta se guarda en una transacción: completa o nada (RNF Confiabilidad). Tenemos una prueba automática de eso.
- *¿Por qué no usan Double para el dinero?* → Centavos enteros: cero errores de redondeo, que es justo el problema del cliente.
- *¿Cómo evitan duplicados al sincronizar?* → Cada registro tiene un UUID; reintentar sobrescribe, no duplica.
- *¿Por qué no se puede cerrar sesión?* → El DFR no lo permite con registros pendientes; sin sincronización (Sprint 2) siempre hay pendientes.
- *¿Dónde está la contraseña?* → En el MVP se guarda hasheada (PBKDF2 + sal) en el teléfono; en Sprint 2 la resguarda Firebase, como indica el DFR.
