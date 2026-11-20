# Plan del Sprint 2 — *Mis Ventas*

> Fecha del plan: 18-nov-2026 · Entrega final: **viernes 27-nov-2026**.
> El Sprint 2 estaba planeado del 28-oct al 13-nov (`00-PLAN-MVP.md`); arranca con 5 días de retraso.
> Registrar el cambio de fechas en el control de cambios de la materia.

## 1. Alcance y estimación

Estimación en horas de sesión de trabajo (código + pruebas JVM + prueba en el teléfono).

### Bloque A — local, sin Firebase

| RF | Actividad | Prioridad DFR | Horas | Base existente |
|---|---|---|---|---|
| 6.1 | Reporte de ventas por periodo + selector de periodo compartido | Alta | 2–3 | `ReporteRepo` |
| 6.2 | Ventas por forma de pago | Media | 1 | Consultas del corte |
| 6.3 | Productos más vendidos | Media | 1 | |
| 6.4 | Ventas por categoría | Baja | 1 | |
| 6.5 | Reporte de inventario | Media | 1.5–2 | Regla de promedio de 7 días (RF 4.5) |
| 6.6 | Exportar PDF / CSV | Media | 3–4 | `ReporteExportService` de AmbulantPoint |
| 1.2 | Verificación de seguridad al reabrir (huella / PIN del teléfono) | Alta | 2 | |
| 5.4 | Notificar caja abierta 12 h | Baja | 2–3 | |
| 3.6 | Descuentos (habilitar + capturar al cobrar) | Baja | 2–3 | Reglas, `ConfiguracionRepo` y cobro ya aceptan descuento |
| 1.6 | Editar perfil | Baja | 1.5–2 | Validadores |
| 2.1 (extra) | Foto con cámara | — | 1–1.5 | Photo Picker |

### Bloque B — Firebase

| RF | Actividad | Prioridad DFR | Horas | Nota |
|---|---|---|---|---|
| — | Proyecto Firebase (Auth, Firestore, Storage) + `google-services.json` | — | 1–2 | **Lo crea el usuario** con su cuenta de Google |
| 1.1–1.3 | `AuthRepoFirebase` detrás de `PuertoAuth` | Alta | 3–4 | Pantallas sin cambios |
| 1.4 | Recuperar contraseña | Media | 1 | |
| 7.1 / 7.2 | Sincronización automática idempotente, aviso de datos móviles, estados del indicador | Alta | 8–10 | La más grande |
| 7.3 | Sincronizar ahora | Media | 1 | |
| 7.4 | Recuperar en dispositivo nuevo (incluye imágenes) | Alta | 4–6 | Ideal: segundo teléfono |
| 7.5 | Sesión en un solo dispositivo | Alta | 3–4 | Ideal: segundo teléfono |
| 7.6 | Notificar 3 días sin respaldo | Media | 1.5 | Reusa 5.4 |

Verificación contra el DFR, demo y documentación: 3–4 h. **Total: 45–58 h.**

## 2. Calendario

| Día | Trabajo | Usuario |
|---|---|---|
| Mié 18-nov | 6.1 + selector de periodo | — |
| Jue 19-nov | 6.2, 6.3, 6.5 | **Crear el proyecto Firebase** |
| Vie 20-nov | Firebase Auth (1.1–1.4) + 1.2 verificación de seguridad | |
| Sáb 21-nov | 7.1 / 7.2 sincronización | |
| Dom 22-nov | 7.1 cierre + 7.3 + 7.4 | Conseguir un segundo teléfono si es posible |
| Lun 23-nov | 7.5 + 5.4 + 7.6 (notificaciones) | |
| Mar 24-nov | 6.4 + 6.6 exportar | **Punto de control**: ¿vamos a tiempo? |
| Mié 25-nov | 3.6 + 1.6 + cámara · colchón | |
| Jue 26-nov | Verificación DFR, demo, trazabilidad, entregables de cierre | |
| Vie 27-nov | **Entrega** | |

## 2.1 Avance

| Fecha | Hecho | Siguiente |
|---|---|---|
| Mié 18-nov | ✅ RF 6.1 (menú Reportes, selector de periodo compartido, reporte de ventas por periodo) probado en teléfono. La demo ahora genera ventas del 4 al 17-nov. También se cerraron RF 3.5 (formas de pago) y la validación de alertas con el cliente. | Jue 19: RF 6.2, 6.3, 6.5 reusando `SelectorPeriodo` y las consultas de `ReporteRepo` (ya existen en `ReporteDao`). Agregar cada reporte al enum `Reporte` de `ui/reportes/ReportesScreen.kt` y a su ruta en `NavegacionApp`. Pedir al usuario que cree el proyecto Firebase. |
| Mié 18-nov (2) | ✅ RF 6.2, 6.3, 6.4 (adelantado) y 6.5, probados en el teléfono. Perfil pasó a ícono en la barra superior. | Jue 19: crear el proyecto Firebase (usuario) y empezar 1.1–1.4 con Firebase + 1.2 verificación de seguridad. |
| Mié 18-nov (3) | ✅ Adelantado del 19 al 22-nov: proyecto Firebase creado; RF 1.1–1.4 con Firebase Auth; RF 1.2 verificación con huella/PIN; RF 7.1–7.3 sincronización con Firestore (migración Room v2). Probado en teléfono. | 7.4 recuperar en dispositivo nuevo (imágenes: decidir Storage/Blaze), 7.5, notificaciones (5.4, 7.6), 6.6 exportar, 3.6, 1.6. |
| Mié 18-nov (4) | ✅ RF 6.6 exportar PDF/CSV, ✅ 5.4 notificación de caja abierta, ✅ 3.6 descuentos (interruptor en Perfil + cobro). 🔍 1.6 editar perfil y 🔍 7.6 aviso de 3 días sin respaldo (implementados, falta verlos en el teléfono). | Ver §2.2. |
| Jue 19-nov | ✅ RF 7.4 recuperar en dispositivo nuevo con imágenes (Storage) y ✅ 7.5 sesión en un solo dispositivo, probados con dos teléfonos. Reglas de Storage publicadas. Corregidos 7.1 (imagen que falla no detiene la sync) y 7.6 (aviso falso tras borrar datos). | Confirmar 2.8 y 7.6 en teléfono, sync con la app cerrada, APK firmado. |

## 2.2 Pendientes al cierre del 18-nov (para retomar)

**Código**
1. RF 7.4 Recuperar en dispositivo nuevo (Alta), **con fotos**: el usuario activó el plan Blaze el 19-nov. Falta (si no lo hizo) crear Storage con el bucket en `us-central1`, `us-east1` o `us-west1` (únicas regiones con nivel gratuito) y una alerta de presupuesto de 1 USD. Hay que escribir las reglas de Storage (`usuarios/{uid}/…`), subir las imágenes de productos en la sincronización (7.1) y bajarlas en 7.4. Simular el segundo teléfono borrando los datos de la app **solo después** de confirmar que todo está en Firestore.
2. RF 7.5 Sesión en un solo dispositivo (Alta). Mensajes del DFR ya en `Mensajes.kt` (`CUENTA_SE_CERRARA_EN_OTRO`, `SESION_EN_OTRO_DISPOSITIVO`).
3. Confirmar en teléfono: 1.6 editar perfil, 2.8 modificar categoría, 7.6 (dejar una forma de forzarlo para la demo).
4. Sincronización con la app cerrada (WorkManager con Wi-Fi). Hoy solo sincroniza con la app abierta.
5. APK de liberación firmado (para el Acta de liberación).

**Entregables de la materia (Actividad III, semana 15, entrega vie 27-nov, en `../../Plantillas`)**
1. Solicitud de cambio a requerimiento (candidato: retraso de 5 días del Sprint 2).
2. Minuta de reunión 3 (reunión real con el cliente para validar el Sprint 2).
3. Lecciones aprendidas.
4. Acta de liberación/entrega (firma del cliente).
5. Presentación final con recorrido de la app + rúbrica impresa; reentregar documentos de Actividades I y II.

**Documentos internos:** actualizar `05-VERIFICACION-DFR.md` y `03-GUION-DEMO.md` con el Sprint 2; recargar datos de demo con descuentos y ventas del día.

**Calendario propuesto:** jue 19 – vie 20: 7.4 + 7.5 · sáb 21: sync en segundo plano + APK firmado · dom 22 – lun 23: los 4 documentos · mar 24: punto de control, verificación DFR, guion · mié 25 – jue 26: presentación y ensayo · vie 27: entrega.

**Datos técnicos:** Firebase `mis-ventas-eb170` (Auth correo/contraseña, Firestore `(default)` con reglas de `firestore.rules`). `app/google-services.json` no está en git: en CachyOS hay que copiarlo a mano o descargarlo de la consola. Room va en la versión 2. Para forzar un trabajo de WorkManager en el teléfono: `adb shell cmd jobscheduler run -f -n androidx.work.systemjobscheduler mx.ambulantpoint.misventas <id>` (ids con `adb shell dumpsys jobscheduler mx.ambulantpoint.misventas`).

## 3. Qué se recorta si hay retraso (en este orden)

1. Foto con cámara (no es RF; la galería ya cumple).
2. 1.6 Editar perfil (Baja).
3. 6.4 Ventas por categoría (Baja).
4. 3.6 Descuentos (Baja).
5. 6.6 Exportar: solo CSV, sin PDF.
6. 5.4 y 7.6 notificaciones.

No se recortan los RF de prioridad Alta (1.2, 6.1, 7.1, 7.4, 7.5).

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| 7.4 y 7.5 necesitan dos dispositivos; solo hay un teléfono y no hay emulador | Conseguir un segundo teléfono; si no, simularlo borrando datos de la app y volviendo a iniciar sesión |
| Firebase depende de que el usuario cree el proyecto | Pedirlo el 19-nov; mientras, avanzar el bloque A |
| Sincronización con más trabajo del estimado | Punto de control el 24-nov y lista de recorte |
| Probar en el teléfono borra datos si se usa `connectedDebugAndroidTest` sin la opción del CLAUDE.md | Instalar solo el APK con `adb install -r` |
