# Guion de la exposición de avance (10 min + 5 de preguntas)

Formato: PowerPoint (requisito de la materia) + demo en vivo con el teléfono proyectado (scrcpy o cámara).

| Min | Bloque | Contenido |
|---|---|---|
| 0–1 | Contexto | Problema de Max Carlotas: registro manual, errores de cálculo, sin visibilidad. Objetivo de Mis Ventas |
| 1–3 | Estado del proyecto | Cronograma: Inicio, Planeación, Análisis, Diseño terminados; Codificación en curso. Matriz de trazabilidad: cuántos RF del MVP están ✅ |
| 3–4 | Arquitectura | MVVM por capas, offline-first (Room → Firebase), backend aislado. Cómo trabajamos con agentes y el DFR como fuente de verdad |
| 4–9 | **Demo en vivo** (modo avión activado para demostrar offline) | ver pasos abajo |
| 9–10 | Siguiente sprint | Reportes + exportar, sincronización completa, biometría, notificaciones. Fecha fin de codificación 13-nov |

## Pasos de la demo (con datos precargados)
1. Abrir la app con sesión activa → pantalla principal: nombre del negocio, **caja cerrada**, indicador "Sin conexión".
2. Catálogo: mostrar categorías (Botanas, Dulces, Bebidas) y registrar un producto nuevo con foto de la galería. Mostrar una validación (nombre repetido → alerta del DFR).
3. **Abrir caja** con fondo $200.00.
4. **Venta 1:** 2 papas + 1 refresco → total se actualiza en vivo → efectivo, recibe $100 → muestra cambio → "Venta registrada exitosamente".
5. **Venta 2 por peso:** cacahuate 0.250 kg → importe = peso × precio/kg → Transferencia.
6. Intentar vender más de la existencia → "Solo hay [n] pzas disponibles de [producto]".
7. Existencias: el producto que bajó aparece **resaltado** + aviso de existencia baja.
8. Historial → **cancelar** la venta 2 → la existencia regresa.
9. Entrada de mercancía de un producto agotado → reaparece en la pantalla de venta.
10. **Corte de caja:** efectivo contado → muestra esperado, diferencia (sobrante/faltante) → **cerrar caja** → aparece en la lista de cortes.
11. Indicador: "[n] registros pendientes" → explicar que en Sprint 2 se sincronizan con Firebase.

## Preguntas probables
- *¿Qué pasa si se apaga el teléfono a mitad de una venta?* → La venta se guarda en una transacción: completa o nada (RNF Confiabilidad).
- *¿Por qué no usan Double para el dinero?* → Centavos enteros: cero errores de redondeo, que es justo el problema del cliente.
- *¿Cómo evitan duplicados al sincronizar?* → Cada registro tiene un UUID; reintentar sobrescribe, no duplica.
