# Verificación del MVP contra el DFR v1.2

Fecha: 26-oct-2026 · Versión de la app: 0.1.0 · Teléfono de prueba: Xiaomi 2412DPC0AG (Android 16)

Cómo se verificó:
1. **Mensajes:** script que extrae los 83 textos entre comillas del DFR y los compara contra
   `core/Mensajes.kt` y su uso en el código.
2. **Reglas:** revisión de cada regla de negocio y criterio de aceptación contra el código.
3. **Pruebas automáticas:** 56 en la PC (JUnit) + 23 en el teléfono (Room real), todas pasan.
4. **Recorridos en el teléfono:** registro, catálogo, venta, cobro, cancelación, inventario, corte y cierre.

Detalle de lo probado por fase: [02-TRAZABILIDAD-DFR.md](02-TRAZABILIDAD-DFR.md).

---

## 1. Mensajes del DFR

| Resultado | Cantidad | Detalle |
|---|:---:|---|
| Literales en `Mensajes.kt` y usados | 40 | Todos los mensajes de los RF del MVP |
| Del RF 4.2, revisados a mano | 4 | El script no separa bien esa fila (comillas anidadas en "Otro"); sus dos mensajes están literales y se probaron en el teléfono |
| Etiquetas, no mensajes | 9 | "Por unidad", "Inactivo", "Cancelada", "Efectivo", "Solo existencia baja"… — están en las pantallas |
| Definidos, sin usar, por ser del Sprint 2 | 26 | 1.4, 1.6, 3.5, 3.6, 5.4, 6.6, 7.1, 7.3 – 7.6 |
| Definidos, sin usar, por decisión del MVP | 4 | "Se requiere conexión…" (1.1, 1.2) y "Sincronizando…" / "Sin conexión" (7.2). Ver D1 |
| **Total de textos entre comillas en el DFR** | **83** | |

Ningún mensaje del MVP difiere del DFR.

---

## 2. Requerimientos del MVP

| RF | Resultado | Observación |
|---|:---:|---|
| 1.1 Registrar vendedor | ✅ con desviación | D1, D2 |
| 1.2 Iniciar sesión | ⚠️ parcial | Sin verificación biométrica al reabrir (D3). Lógica probada en PC; no se pudo probar en teléfono porque cerrar sesión siempre se bloquea (D4) |
| 1.3 Cerrar sesión | ✅ | Se bloquea con pendientes, como pide el DFR (ver D4) |
| 1.5 Consultar perfil | ✅ | |
| 2.1 – 2.7 Catálogo y categorías | ✅ | HEIC puede no leerse en Android 8 – 8.1 (lo decodifica Android 9+); se muestra "No se pudo usar la imagen, elija otra" |
| 2.8 – 2.9 Modificar / eliminar categoría | ✅ adelantado | Eran Sprint 2 |
| 3.1 – 3.4 Ventas | ✅ | Descuento oculto porque está deshabilitado por defecto (RF 3.6 es Sprint 2) |
| 3.5 Formas de pago | ✅ | Probada en teléfono (18-nov-2026): agregar, renombrar, activar/desactivar; Efectivo bloqueada; alertas de campo vacío y nombre registrado; mensaje "La forma de pago se guardó exitosamente" |
| 4.1 – 4.3, 4.5 Inventario | ✅ | |
| 4.2 Ajustar existencia | ✅ adelantado | Era Sprint 2 |
| 4.4 Movimientos | ✅ | Falta el filtro opcional por rango de fechas en la pantalla (la consulta ya lo admite) |
| 5.1 – 5.3, 5.5 Caja | ✅ | |
| 7.2 Estado de sincronización | ⚠️ parcial | Muestra "Sincronizado" / "[n] registros pendientes"; "Sincronizando…" y "Sin conexión" llegan con Firebase |

---

## 3. Desviaciones conocidas (decisiones del MVP local)

| # | DFR dice | El MVP hace | Por qué / cuándo se corrige |
|---|---|---|---|
| D1 | Registro e inicio de sesión requieren internet (RF 1.1, 1.2) | Funcionan sin internet | MVP 100 % local (decisión 26-oct). Sprint 2 con Firebase Auth |
| D2 | La contraseña no se guarda en el dispositivo (RF 1.1, RNF Confiabilidad) | Se guarda **hasheada** (PBKDF2 + sal), nunca en texto plano | Temporal; se elimina al integrar Firebase Auth |
| D3 | Al reabrir, verificar identidad con huella/PIN (RF 1.2) | No se pide | Biometría planeada para Sprint 2 |
| D4 | No cerrar sesión con registros pendientes (RF 1.3) | Se cumple, pero como no hay sincronización **siempre** hay pendientes | Se resuelve solo al tener sincronización (Sprint 2) |

---

## 4. Lo que la app hace y el DFR no pide

Revisado para no introducir cambios no autorizados. Ninguno agrega reglas de negocio:

| Comportamiento | Motivo |
|---|---|
| Botones de monto rápido en el cobro ("Exacto", $50, $100…) | Cumplir el RNF de 4 toques; el monto sigue capturándose |
| Textos de las alertas de campo ("Este campo es obligatorio"…) | El DFR pide las alertas pero no su texto. ✅ **Validados por el cliente el 18-nov-2026** (las 24 aprobadas sin cambios; validación sin firma) |
| Aviso de privacidad | El DFR pide mostrarlo pero no da el texto. Texto completo aprobado por el cliente el 18-nov-2026. El 21-nov-2026 el cliente definió el correo de contacto (maxicarlotas@gmail.com) y pidió no publicar domicilio. La app ya muestra el texto final |
| Reverificar al volver de segundo plano | RF 1.2: además del arranque, se pide huella/PIN si la app estuvo en segundo plano 1 minuto o más. El margen (decisión del equipo) evita pedirla al tomar una foto o compartir un reporte |
| Confirmación al reactivar | RF 2.4: "¿Desea reactivar el producto [nombre]?" (texto del equipo; el DFR pide la confirmación sin dar texto) |
| Fallo al exportar | RF 6.6: "El reporte no se exportó exitosamente, intente de nuevo" (texto del equipo) |
| La existencia inicial queda como primer movimiento "Entrada de mercancía" | Que el historial cuadre con la existencia |
| No se puede renombrar "Efectivo" | El DFR solo permite renombrar las formas de pago adicionales |
| Textos de ayuda: "Verifique que recibió el pago antes de confirmar", "(se modifica en Existencias)" | Recordatorios de reglas del DFR (RF 3.2 y 2.2) |

---

## 5. Requerimientos no funcionales

| RNF | Resultado |
|---|---|
| Español, Material 3, colores del logo, vertical | ✅ |
| Montos `$0,000.00` y cantidades con unidad | ✅ (`core/Formato.kt`, con pruebas) |
| Pantalla principal con negocio, estado de caja e indicador | ✅ |
| Venta completa o nada | ✅ (prueba en teléfono: venta con un producto sin existencia no deja nada) |
| Vender 1 producto en ≤ 4 toques | ✅ por pieza (producto → Cobrar → Exacto/forma → Confirmar). Por peso se agrega la captura del peso de la etiqueta, que el DFR exige |
| Botones ≥ 48 dp | ✅ (principales 56 dp) |
| Validar al capturar e indicar el campo con error | ✅ (alerta debajo del campo; se quita al corregir) |
| Imagen ≤ 1024 px y ≤ 300 KB | ✅ (foto real: 576 × 1024, 135 KB) |
| MVVM por capas, convenciones Kotlin | ✅ ([04-ARQUITECTURA-APP.md](04-ARQUITECTURA-APP.md)) |
| Git **alojado en GitHub** | ✅ https://github.com/MaxDev8459/mis-ventas (público; el DFR no se publica por datos personales) |
| Versión del APK identificada | ✅ 0.1.0 |
| Android 8.0+ | ✅ minSdk 26 |

---

## 6. Correcciones pendientes, por prioridad

1. ~~Subir el repositorio a GitHub~~ ✅ hecho el 26-oct-2026.
2. ~~Pantalla de formas de pago (RF 3.5)~~ ✅ hecha y probada en el teléfono el 18-nov-2026.
3. ~~Validar con el cliente las alertas de campo~~ ✅ 18-nov-2026: el cliente aprobó las 24 sin cambios (`documentos del proyecto/Validacion_Textos_Cliente_Mis_Ventas.docx`, fuera del repo; sin firma).
4. ~~Aviso de privacidad~~ ✅ 21-nov-2026: texto aprobado, con el correo de contacto del cliente y sin domicilio (decisión del cliente).
5. ~~Filtro de fechas en movimientos de inventario (RF 4.4)~~ ✅ 21-nov-2026: chips "Todos" / "Elegir fechas".

**Revisión contra el DFR (21-nov-2026).** Correcciones aplicadas tras revisar RF por RF:
- RF 1.2: verificación de identidad también al regresar de segundo plano (ver sección 4).
- RF 2.4: confirmación antes de reactivar un producto.
- RF 4.4: rango de fechas opcional en los movimientos de inventario.
- RF 6 (reglas generales): el chip del periodo dice "Rango personalizado", como el DFR.
- RF 6.6: mensaje si falla la generación del archivo.
- SC-01: con letras en la CLABE se muestra "La CLABE solo debe tener números".
- Aviso de privacidad final.
- `allowBackup="false"`: el respaldo automático de Android ya no copia la base local (los datos se respaldan en Firebase, RF 7).
- Se quitó la constante sin uso `Campo.EXISTENCIA_NO_VALIDA`.
