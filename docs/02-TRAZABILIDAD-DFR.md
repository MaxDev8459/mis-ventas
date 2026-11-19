# Matriz de trazabilidad DFR v1.2 → código

Estados: ⬜ pendiente · 🔨 en desarrollo · 🔍 en verificación · ✅ terminado (verificado + probado en teléfono) · ⏳ Sprint 2

| RF | Nombre | Prioridad | Sprint | Pantalla / Caso de uso | Pruebas | Estado |
|----|--------|:---:|:---:|---|---|:---:|
| 1.1 | Registrar vendedor | Alta | MVP (local) / S2 Firebase | `ui/auth/Registro` · `AuthRepoFirebase.registrar` | Validadores registro, sin internet | ✅ |
| 1.2 | Iniciar sesión | Alta | MVP (local) + S2 Firebase y verificación | `ui/auth/Login` · `AuthRepoFirebase` · `ui/auth/VerificacionScreen` | Validadores login, sin internet | ✅ |
| 1.3 | Cerrar sesión | Media | MVP / S2 Firebase | `ui/perfil` · `CerrarSesion` · `AuthRepoFirebase.cerrarSesion` | Bloqueo con pendientes | ✅ |
| 1.4 | Recuperar contraseña | Media | S2 | `ui/auth/Login` (diálogo) · `RecuperarContrasena` · `AuthRepoFirebase` | Validación de correo, mensaje del DFR, sin internet | ✅ |
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
| 3.1 | Registrar venta | Alta | MVP | `ui/venta/PantallaVenta` | Total, tope por existencia, kg×precio | ✅ |
| 3.2 | Cobrar venta | Alta | MVP | `ui/venta/Cobro` · `CobrarVenta` | Cambio, monto insuficiente, atomicidad | ✅ |
| 3.3 | Cancelar venta | Media | MVP | `ui/historial` · `CancelarVenta` | Regreso de existencia, solo caja abierta | ✅ |
| 3.4 | Historial de ventas | Media | MVP | `ui/historial` | | ✅ |
| 3.5 | Formas de pago | Media | MVP | `ui/formaspago` · `GuardarFormaPago` | Nombre obligatorio y único; Efectivo no se renombra ni desactiva; no se eliminan | ✅ |
| 3.6 | Descuentos | Baja | S2 | | | ⏳ |
| 4.1 | Entrada de mercancía | Alta | MVP | `ui/inventario/Entrada` · `RegistrarEntrada` | Suma y movimiento | ✅ |
| 4.2 | Ajustar existencia | Media | MVP (adelantado) | | | ✅ |
| 4.3 | Consultar existencias | Alta | MVP | `ui/inventario/Existencias` | | ✅ |
| 4.4 | Movimientos de inventario | Baja | MVP (registro) / S2 (pantalla) | Room `MovimientoInventario` | | ✅ |
| 4.5 | Alertar existencia baja | Media | MVP | `EvaluarExistenciaBaja` | Promedio 7 días / umbral fijo | ✅ |
| 5.1 | Abrir caja | Alta | MVP | `ui/caja/Abrir` | Una sola caja abierta | ✅ |
| 5.2 | Calcular corte | Alta | MVP | `ui/caja/Corte` · `CalcularCorte` | Esperado, diferencia, excluye canceladas | ✅ |
| 5.3 | Cerrar caja | Alta | MVP | `ui/caja/Corte` · `CerrarCaja` | Requiere corte actualizado | ✅ |
| 5.4 | Notificar caja abierta | Baja | S2 | | | ⏳ |
| 5.5 | Consultar cortes | Media | MVP | `ui/caja/Cortes` | | ✅ |
| 6.1 | Reporte ventas por periodo | Alta | S2 | `ui/reportes` · `GenerarReporteVentas` · `ReglaPeriodos` | Ticket promedio, periodos (semana desde el lunes), solo no canceladas | ✅ |
| 6.2 | Ventas por forma de pago | Media | S2 | `ui/reportes/ReportesDetalle` · `GenerarReporteFormasPago` · `ReglaPorcentaje` | Porcentaje (décimas, mitad hacia arriba), orden | ✅ |
| 6.3 | Productos más vendidos | Media | S2 | `ui/reportes/ReportesDetalle` · `GenerarReporteMasVendidos` · `ReglaMasVendidos` | Orden por importe; por cantidad separado por tipo de venta | ✅ |
| 6.4 | Ventas por categoría | Baja | S2 (adelantado) | `ui/reportes/ReportesDetalle` · `GenerarReporteCategorias` | Porcentaje y orden | ✅ |
| 6.5 | Reporte de inventario | Media | S2 | `ui/reportes/ReportesDetalle` · `ReporteInventario` · `ReglaExistenciaBaja.nivel` | Conteos de activos, baja y agotados; umbral fijo y promedio de 7 días | ✅ |
| 6.6 | Exportar reportes | Media | S2 | | | ⏳ |
| 7.1 | Sincronizar automáticamente | Alta | S2 | `data/sync/ServicioSincronizacion` · `SincronizadorFirestore` · `ReglaSincronizacion` | Wi-Fi / datos móviles / cancelación; idempotencia por id | ✅ |
| 7.2 | Estado de sincronización | Alta | MVP / S2 | `ui/inicio` (tarjeta de estado) · `ReglaSincronizacion.indicador` | Prioridad de estados y textos del DFR | ✅ |
| 7.3 | Sincronizar manualmente | Media | S2 | `ui/inicio` (diálogo del indicador) · `ServicioSincronizacion.sincronizarAhora` | Sin red, ya sincronizada, datos móviles, Wi-Fi | ✅ |
| 7.4 | Recuperar en dispositivo nuevo | Alta | S2 | | | ⏳ |
| 7.5 | Sesión en un solo dispositivo | Alta | S2 | | | ⏳ |
| 7.6 | Notificar falta de sync | Media | S2 | | | ⏳ |

**Avance (26-oct-2026):** 24 de 44 RF terminados (✅), de los cuales 3 se adelantaron del Sprint 2 (2.9, 4.2 y la pantalla de 4.4). Del alcance MVP faltan: 1.2 en verificación, 3.5 parcial (solo semilla, sin pantalla) y 7.2 en desarrollo (indicador sin sincronización real). 2.8 en verificación (adelantado).

**Fase 1 (26-oct-2026):** capa de datos lista para los RF en 🔨 (Room, transacciones y consultas). Pruebas: `DineroTest` (4, JVM) + `VentaCajaTest` y `CatalogoInventarioTest` (23, en el teléfono 2412DPC0AG), todas pasan. Faltan dominio (Fase 2) y pantallas (Fase 3).

**Fase 2 (26-oct-2026):** dominio listo: `core/Mensajes.kt` (literales del DFR + alertas de campo propuestas, pendientes de validar), `Validadores`, `LectorNumeros`, `Formato`, reglas (descuento, cobro, existencia baja, corte), `Carrito` y casos de uso por módulo. 48 pruebas JVM pasan.

**Fase 3A (26-oct-2026):** registro, pantalla principal, perfil y cerrar sesión probados en el teléfono (adb). 1.2 en verificación: no se pudo probar el inicio de sesión en el teléfono porque cerrar sesión siempre se bloquea por registros pendientes (no hay sincronización en el MVP). 56 pruebas JVM.

**Fase 3B (26-oct-2026):** categorías (crear, duplicado, renombrar, eliminar con y sin productos) y catálogo (registro con foto de galería comprimida a 576×1024 y 135 KB, búsqueda, filtros, edición, desactivar/reactivar, bloqueo de cambio de tipo con existencia) probados en el teléfono. 2.8 y 2.9 adelantados del Sprint 2 porque ya estaban hechos en datos y dominio.

**Fase 3C (26-oct-2026):** probado en el teléfono: abrir caja ($100), venta de 1 producto en 4 toques (producto → Cobrar → Exacto → Confirmar), cambio con $100 ($40), venta por peso 0.250 kg × $86.50 = $21.63 con transferencia, aviso de existencia baja (5 pzas), tope por existencia, historial del día y cancelación de la venta de $60 (existencias regresan). 4.5 en verificación: falta probar el aviso tras entrada/ajuste (3D).

**Fase 3D (26-oct-2026):** probado en el teléfono: existencias con baja resaltada, ajuste (validaciones, "Otro" sin especificar, disminución mayor a la existencia, merma −4 con aviso), entrada de 1.5 kg con nueva existencia, movimientos con signo y existencia resultante, corte (esperado $140, contado $150, sobrante $10, excluye cancelada), cierre con confirmación, venta de caja cerrada sin opción de cancelar, lista y detalle de cortes. 4.2 y 4.4 (pantalla) adelantados del Sprint 2.

**RF 3.5 (18-nov-2026):** pantalla "Formas de pago" en el menú principal: agregar, renombrar, activar y desactivar; Efectivo aparece como predeterminada y bloqueada. 7 pruebas JVM nuevas (63 en total). Probado en el teléfono: alerta de campo vacío, "transferencia" rechazada como nombre ya registrado, alta de "Tarjeta" con el mensaje del DFR, desactivar desde la lista, Efectivo sin diálogo ni cambio de estado, renombrar a "Tarjeta de crédito" conservando Inactiva. No se probó en el teléfono que una forma inactiva desaparezca del cobro (habría que abrir caja); lo cubre la consulta `observarActivas` usada por la pantalla de venta.

**Sprint 2 · RF 6.1 (18-nov-2026):** menú "Reportes" y reporte de ventas por periodo con selector compartido (Hoy, Esta semana, Este mes, Rango). Decisión del equipo (el DFR no lo dice): la semana empieza en lunes. 13 pruebas JVM nuevas (76 en total). La carga de demo ahora genera 10 días hábiles de ventas (4 al 17-nov): 51 ventas, 3 canceladas, efectivo y transferencia, cortes con sobrante y faltante. Probado en el teléfono: Esta semana $429.50 / 8 ventas / ticket $53.69; Este mes $2,325.00 / 51 / 3 canceladas por $116.00; rango 16–17; Hoy sin ventas muestra el mensaje del DFR. Todo coincide con la base de datos. Se arregló el encabezado del selector de fechas (también en el historial).

**Sprint 2 · RF 6.2–6.5 (18-nov-2026):** cuatro reportes más en el menú "Reportes", con el mismo selector de periodo (6.5 no usa periodo; filtra por categoría). 6.4 se adelantó del 24-nov porque reusa lo de 6.2. Decisiones del equipo (el DFR no lo dice): porcentajes con un decimal; en "más vendidos" por cantidad van primero los productos por unidad y luego los de peso; en 6.4 el porcentaje es sobre la suma de las líneas vendidas (antes del descuento por venta) para que sume 100 %; en 6.5 "existencia baja" no incluye a los agotados (se cuentan aparte, igual que en 4.3). El nivel de existencia (agotado / baja / normal) pasó al dominio y lo comparten 4.3 y 6.5. 14 pruebas JVM nuevas. Revisados por el usuario en el teléfono con la demo: correctos. Además, Perfil pasó de botón de la cuadrícula a ícono en la barra superior (quedan 8 botones).

**Sprint 2 · RF 1.2 verificación de seguridad (18-nov-2026):** al abrir la app con sesión activa se pide huella, rostro, PIN, patrón o contraseña del teléfono (`BiometricPrompt`, BIOMETRIC_WEAK | DEVICE_CREDENTIAL, sin internet). Si falla o se cancela, queda bloqueada con el botón "Verificar identidad"; si el teléfono no tiene método, pide configurarlo y lleva a Ajustes. Decisión del equipo: "volver a abrir" = arranque nuevo de la app (proceso nuevo); regresar desde segundo plano sin que Android la haya cerrado no la pide, para no interrumpir al elegir una foto. Tras iniciar sesión con correo y contraseña no se pide. Los textos de esta pantalla no vienen del DFR (no los define). Falta: prueba en el teléfono; el inicio de sesión con internet llega con Firebase.

**Sprint 2 · Firebase Authentication, RF 1.1–1.4 (18-nov-2026):** proyecto Firebase `mis-ventas-eb170` (creado por el usuario; `google-services.json` fuera de git). `AuthRepoFirebase` reemplaza a `AuthRepoLocal` detrás de `PuertoAuth`, sin cambiar pantallas. La contraseña ya no se guarda en el teléfono: al primer arranque se borra el hash que dejó el MVP local. Registro, inicio de sesión y recuperar contraseña revisan la conexión antes de llamar a Firebase y muestran el mensaje del DFR de cada caso. Un teléfono que ya tenía cuenta local abre en "Iniciar sesión"; al registrarse en Firebase se conserva el perfil y los datos (una cuenta por dispositivo). El perfil se sube a Firestore con la sincronización (7.1). 5 pruebas JVM nuevas. Falta probar en el teléfono.

**Sprint 2 · Sincronización, RF 7.1–7.3 (18-nov-2026):** Firestore `(default)` en el proyecto `mis-ventas-eb170`, reglas en `firestore.rules` (cada vendedor solo lee y escribe `usuarios/{uid}/…`). Cada registro va a `usuarios/{uid}/{tabla}/{id local}` con `set` (idempotente: reintentar no duplica). Un registro se marca sincronizado solo si su versión (`actualizadoEn`, o `creadoEn` en los inmutables) no cambió mientras se subía. Room pasa a la versión 2 con la tabla `eliminacion_pendiente` (migración sin pérdida de datos) para borrar en la nube las categorías eliminadas (RF 2.9). Con Wi-Fi sincroniza solo; con datos móviles muestra el aviso del DFR; si se cancela no vuelve a preguntar hasta reabrir la app, conectar Wi-Fi o usar "Sincronizar ahora" (en el diálogo del indicador, junto a la última sincronización). Si falla, reintenta al minuto. Decisión del equipo: sin pendientes el indicador dice "Sincronizado" aunque no haya red. Pendiente: imágenes de productos (Storage) y sincronizar con la app cerrada (hoy solo con la app abierta). 15 pruebas JVM nuevas.

**Verificación en teléfono (18-nov-2026):** el usuario probó registro e inicio de sesión con Firebase, cerrar sesión, recuperar contraseña, verificación con huella/PIN, sincronización con Wi-Fi y datos móviles, "Sincronizar ahora" y sin conexión: todo correcto. Se agregó el botón para mostrar u ocultar la contraseña en todos los campos de contraseña.
