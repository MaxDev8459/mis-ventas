# Plan de trabajo — MVP funcional de *Mis Ventas*

> **Fuente de verdad:** `docs/dfr/DFR_Mis_Ventas_v1.2.md` (copia del DFR v1.2, Tema 2).
> Ninguna regla, mensaje o campo se inventa: si el DFR no lo dice, se pregunta.
> Fecha del plan: 26-oct-2026 · Meta: MVP demostrable el 27-oct-2026 (exposición de avance, semana 10).

---

## 1. Objetivo del MVP

Demostrar **en un teléfono Android real** el flujo completo de la *operación diaria* descrito en
el Escenario de Operación del DFR:

```
Registrarse / Iniciar sesión → Crear categoría → Registrar producto (con foto)
→ Abrir caja → Registrar venta → Cobrar en efectivo (cambio) → existencia baja / se descuenta
→ Historial (cancelar una venta y ver que regresa la existencia)
→ Entrada de mercancía → Calcular corte de caja → Cerrar caja → Consultar cortes
```

Todo **sin conexión** (offline-first con Room) y con el indicador de sincronización visible en la
pantalla principal, que es la promesa central del producto.

Esto coincide con el cronograma: Codificación 17-oct → 13-nov; *Reportes* y *Persistencia/
sincronización* están al 40 %, así que el MVP muestra los módulos 1–5 y deja 6 y 7 como avance.

---

## 2. Alcance del MVP (trazado al DFR)

| RF | Requerimiento | En MVP (27-oct) | Nota |
|----|---------------|:---:|------|
| 1.1 | Registrar vendedor | ✅ | **Local en el MVP** (decisión 26-oct): cuenta guardada en el teléfono con hash de contraseña. Firebase Auth → S2 |
| 1.2 | Iniciar sesión | ✅ | Local, sesión persistente. Mensajes de "requiere internet" se activan con Firebase en S2. **Biometría → S2** |
| 1.3 | Cerrar sesión | ✅ | Con la regla de "registros pendientes" |
| 1.4 | Recuperar contraseña | ⏳ S2 | Solo funciona con Firebase |
| 1.5 | Consultar perfil | ✅ | |
| 1.6 | Editar perfil | ⏳ S2 | |
| 2.1 | Registrar producto | ✅ | Foto desde **galería** (Photo Picker) + compresión ≤1024 px / ≤300 KB. Cámara → S2 |
| 2.2 | Editar producto | ✅ | Sin editar existencia (regla DFR) |
| 2.3 / 2.4 | Desactivar / Reactivar | ✅ | |
| 2.5 | Consultar productos | ✅ | Búsqueda + filtro categoría/estado |
| 2.6 / 2.7 | Crear / Consultar categorías | ✅ | Incluye "crear desde el formulario de producto" |
| 2.8 / 2.9 | Modificar / Eliminar categoría | ⏳ S2 | |
| 3.1 | Registrar venta | ✅ | Unidad y por peso (kg, 3 decimales) |
| 3.2 | Cobrar venta | ✅ | Efectivo con cambio + Transferencia |
| 3.3 | Cancelar venta | ✅ | Regresa existencias, solo caja abierta |
| 3.4 | Historial de ventas | ✅ | Filtro de fecha simple |
| 3.5 | Formas de pago | 🟡 | Sembradas: Efectivo (fija) y Transferencia. Pantalla de config → S2 |
| 3.6 | Descuentos | ⏳ S2 | |
| 4.1 | Entrada de mercancía | ✅ | |
| 4.2 | Ajustar existencia | ⏳ S2 | |
| 4.3 | Consultar existencias | ✅ | Resaltado de existencia baja |
| 4.4 | Movimientos de inventario | 🟡 | Se **registran** desde el día 1 (entrada/venta/cancelación); la pantalla → S2 |
| 4.5 | Alertar existencia baja | ✅ | Fórmula completa (7 días con ventas) + umbral fijo 5 pzas / 1 kg |
| 5.1 | Abrir caja | ✅ | |
| 5.2 | Calcular corte | ✅ | |
| 5.3 | Cerrar caja | ✅ | Con regla "corte actualizado antes de cerrar" |
| 5.4 | Notificar caja abierta 12 h | ⏳ S2 | WorkManager/AlarmManager |
| 5.5 | Consultar cortes | ✅ | |
| 6.1 | Reporte ventas por periodo | 🟡 stretch | Si sobra tiempo, solo en pantalla |
| 6.2–6.6 | Resto de reportes y exportar | ⏳ S2 | |
| 7.2 | Indicador de sincronización | ✅ | Cuenta real de registros pendientes + estado de red |
| 7.3 | Sincronizar manualmente | 🟡 stretch | Subida a Firestore si hay Firebase |
| 7.1, 7.4–7.6 | Sync automática, recuperación, sesión única, aviso 3 días | ⏳ S2 | El modelo de datos ya trae los campos para soportarlo |

✅ = entra · 🟡 = parcial/stretch · ⏳ S2 = Sprint 2 (28-oct → 13-nov)

**Regla de oro del MVP:** todo lo que entra cumple el DFR al pie de la letra (mensajes exactos,
validaciones, reglas). Preferimos menos RF bien hechos que muchos a medias.

---

## 3. Stack técnico (según RNF del DFR)

| Decisión | Elección | Por qué (DFR) |
|---|---|---|
| Lenguaje / IDE | Kotlin + Android Studio | Restricciones de diseño |
| UI | Jetpack Compose + Material 3, solo vertical | RNF Interfaz (Material Design 3, vertical) |
| Arquitectura | MVVM por capas: `ui` → `domain` → `data` | RNF Mantenimiento |
| BD local | Room (SQLite) con transacciones | Offline-first + RNF Confiabilidad (venta atómica) |
| Backend | Firebase Auth / Firestore / Storage detrás de interfaces | RNF Interoperabilidad + Reusabilidad (backend sustituible) |
| DI | Manual (`AppContainer`) | Menos configuración para llegar a mañana; se puede migrar a Hilt |
| Imágenes | Photo Picker + compresión propia + Coil para mostrar | RF 2.1 |
| Navegación | Navigation Compose | |
| minSdk / target | 26 (Android 8.0) / 36 | RNF Portabilidad |
| Versionado | Git + GitHub, `versionName 0.1.0` para el MVP | RNF Mantenimiento |

**Decisiones de modelado que evitan errores de cálculo** (el problema que motiva el proyecto):
- Dinero en **centavos** (`Long`), nunca `Double`. `$12.50` → `1250`.
- Cantidades en **milésimas** (`Long`): 3 pzas → `3000`; 0.250 kg → `250`. Una sola regla para unidad y peso.
- Cada venta guarda **precio al momento** (RF 2.2: el cambio de precio no afecta ventas pasadas).
- Toda tabla lleva `id` UUID, `actualizadoEn`, `pendienteSync` → la sincronización (Módulo 7) no requiere migrar después.

---

## 4. Estructura del repositorio

```
mis-ventas/
├── CLAUDE.md                 ← reglas para los agentes (DFR = verdad)
├── README.md
├── docs/
│   ├── 00-PLAN-MVP.md        ← este archivo
│   ├── 01-ARQUITECTURA-AGENTICA.md
│   ├── 02-TRAZABILIDAD-DFR.md ← RF → pantalla → prueba → estado
│   ├── 03-GUION-DEMO.md       ← guion de 10 min para la exposición
│   └── dfr/DFR_Mis_Ventas_v1.2.md
├── .claude/agents/           ← subagentes del proyecto
└── app/                      ← proyecto Android (se crea en Fase 0)
    └── src/main/java/mx/ambulantpoint/misventas/
        ├── MisVentasApp.kt / AppContainer.kt
        ├── core/             ← Validadores, FormatoMoneda, Cantidad, Resultado (RNF Reusabilidad)
        ├── data/
        │   ├── local/        ← Room: entities, dao, MisVentasDb
        │   ├── remote/       ← Firebase (AuthRemoto, SyncRemoto) — interfaces + impl
        │   └── repository/   ← ProductoRepo, VentaRepo, CajaRepo, InventarioRepo, AuthRepo
        ├── domain/usecase/   ← CobrarVenta, CancelarVenta, CalcularCorte, CerrarCaja,
        │                        RegistrarEntrada, EvaluarExistenciaBaja, RegistrarProducto…
        └── ui/
            ├── theme/        ← colores del logo
            ├── navigation/
            ├── auth/  inicio/  catalogo/  categorias/  venta/  caja/  historial/  inventario/  perfil/
            └── components/   ← IndicadorSync, CampoMonto, DialogoConfirmacion…
```

---

## 4.1 Reciclaje desde AmbulantPoint (decisión 26-oct)

Se construye **desde cero** y se **reciclan piezas concretas** del proyecto anterior del equipo
(`C:\Users\MaxDev\ClaudeCodeProyects\AmbulantPoint`, repo `github.com/MaxDev8459/AmbulantPoint`).

**Por qué no se usa como base:** dinero en `Double`, existencias solo enteras (sin venta por kg),
Activity + Service en lugar de MVVM, minSdk 31 (el DFR pide 26), sin login ni sincronización, y un
modelo distinto (jornada, carga diaria, predicción de demanda) que el DFR no pide.

| Pieza de AmbulantPoint | Se usa para | Cómo | Fase |
|---|---|---|---|
| `service/ReporteExportService.kt` (PDF con `PdfDocument` + CSV) | RF 6.6 Exportar reportes | Adaptar casi directo: CSV UTF-8, encabezado con nombre del negocio y periodo | Sprint 2 |
| `service/VentaService.kt` (venta transaccional: validar existencia → venta + detalle → descontar → movimiento) | RF 3.2 / 3.3 | Referencia de lógica; se reescribe con Room `@Transaction` y centavos | Fase 1–2 |
| `service/CatalogService.kt` (duplicados, baja lógica) | RF 2.1–2.4, 2.6 | Referencia de validaciones | Fase 2 |
| `service/DashboardService.kt`, `data/dao/CorteCajaDao.kt` (agregados de ventas y corte) | RF 5.2 y Módulo 6 | Referencia de consultas SQL | Fase 1–2 / S2 |
| `ui/venta/VentaActivity.kt`, `MetodosPagoActivity.kt` (carrito y cobro) | RF 3.1 / 3.2 | Ideas de flujo y UX; se rehace en Compose | Fase 3 |
| `CLAUDE.md` anterior ("cero código sin RF", "transacción siempre") | Reglas de agentes | Ya incorporado | — |

Reglas: el proyecto anterior se lee **solo como referencia** (no se edita ni se copia su modelo de
datos). Cualquier regla que traiga y que no esté en el DFR se descarta. La reutilización se documenta
en *Lecciones aprendidas*.

---

## 5. Plan por fases (hoy → mañana)

Cada fase termina con **compila + se prueba en el teléfono + commit**. Si una fase se atrasa, se
recorta alcance de la siguiente, nunca la calidad de lo ya hecho.

### Fase 0 — Cimientos (≈45 min)
- `git init`, `.gitignore` Android, primer commit con docs.
- Crear proyecto Android (Gradle KTS + version catalog), paquete `mx.ambulantpoint.misventas`, minSdk 26.
- Tema Material 3 con colores del logo (`documentos del proyecto/logo.jpeg`).
- Verificar `assembleDebug` y que corre en tu teléfono.
- **Juntos, al final de la fase:** activar *Depuración USB* en tu teléfono paso a paso e instalar la app.

### Fase 1 — Datos (≈1 h) · agente `datos-room`
- Entidades: `Perfil`, `Categoria`, `Producto`, `FormaPago`, `Caja`, `Venta`, `VentaDetalle`,
  `MovimientoInventario`, `Corte` (o corte embebido en `Caja`).
- DAOs con `Flow` y consultas de reportes básicos (para no rehacer después).
- Transacción `cobrarVenta`: inserta venta + detalle + movimientos + descuenta existencia **todo o nada**.
- Semilla: formas de pago "Efectivo" (no desactivable) y "Transferencia".

### Fase 2 — Dominio y reglas (≈1 h) · agente `reglas-negocio`
- `core/Validadores` (correo, teléfono 10 dígitos, contraseña ≥8 letra+número, montos, cantidades por tipo).
- Casos de uso con los **mensajes exactos del DFR**.
- Pruebas unitarias JUnit derivadas de los criterios de aceptación: cambio, corte (esperado/
  diferencia), cancelación, existencia baja, nombre duplicado sin distinguir mayúsculas/espacios.

### Fase 3 — Pantallas (≈3 h) · agente `ui-compose` (2 instancias en paralelo)
- **3A:** Registro, Inicio de sesión, Inicio (nombre del negocio, estado de caja, indicador sync), Perfil, Cerrar sesión.
- **3B:** Categorías, Catálogo (lista/búsqueda/filtros), Formulario de producto con foto.
- **3C:** Abrir caja, **Pantalla de venta** (≤4 toques para vender un producto, botones ≥48 dp),
  Cobro (efectivo con cambio / transferencia), Historial + detalle + cancelar.
- **3D:** Existencias (resaltado bajo/agotado) + Entrada de mercancía; Corte → Cerrar caja → Lista de cortes.

### Fase 4 — Autenticación local (≈30 min) · orquestador
- **Decisión 26-oct: el MVP es 100 % local, sin Firebase.**
- `AuthRepo` (interfaz) + `AuthRepoLocal`: registro e inicio de sesión contra Room/DataStore, contraseña
  con hash PBKDF2 + sal (nunca en texto plano), sesión persistente.
- En la exposición se explica que la capa está aislada (RNF Reusabilidad): en Sprint 2 se cambia a
  `AuthRepoFirebase` sin tocar pantallas. Nota: el DFR indica que la contraseña la resguarda Firebase;
  el hash local es temporal del MVP y se elimina al integrar Firebase.
- Firebase (Auth, Firestore, Storage, Módulo 7) → Sprint 2, agente `firebase-sync`.

### Fase 5 — Verificación y demo (≈1 h) · agentes `verificador-dfr` + `qa-android`
- Auditoría RF por RF contra el DFR (mensajes, validaciones) → actualizar `02-TRAZABILIDAD-DFR.md`.
- `./gradlew test assembleDebug`, instalar APK `0.1.0`.
- Cargar datos de demo realistas (botanas, dulces, bebidas, cacahuate por kg).
- Ensayar el guion `03-GUION-DEMO.md` (10 min + 5 de preguntas).

**Total estimado de construcción: 7–8 h.** Ruta crítica: Fase 0 → 1 → 3C (venta/cobro). Si el
tiempo aprieta, el orden de recorte es: 3D cortes-lista → 3B editar → Fase 4 → 3A perfil.

---

## 6. Riesgos para mañana

| Riesgo | Prob. | Mitigación |
|---|:---:|---|
| Gradle dentro de Google Drive | — | **Resuelto 26-oct:** el proyecto vive en `C:\Users\MaxDev\Documents\…\documentos del proyecto\mis-ventas` (disco local). GitHub será la copia oficial |
| No hay emulador configurado | Media | Probar en tu teléfono por USB (Depuración USB); plan B crear AVD Pixel API 34 |
| Firebase fuera del MVP | — | Decisión 26-oct: auth local detrás de la misma interfaz; Firebase en Sprint 2 |
| Errores de redondeo en montos/kg | Baja | Centavos y milésimas en `Long` + pruebas unitarias |
| Alcance excesivo | Media | Tabla §2 es el límite; cambios nuevos → *Solicitud de Cambio* (entregable semana 10–11) |
| El DFR v1.2 tiene inconsistencias | — | Ver §7 |

---

## 7. Observaciones del DFR detectadas al planear

1. El archivo se llama **Versión 1.2**, pero la portada dice **"Versión 1.1"** y el *Control de
   Cambios* no tiene la fila 1.2. Conviene corregirlo antes de mostrarlo.
2. RF 2.7 *Consultar categorías* indica como dato de entrada "Nombre del producto"; parece un error
   de copia (debería ser "Ninguno"). En el MVP se implementa como listado sin entrada.
3. RF 1.2 pide *verificación de seguridad* en la entrada de login; el resto del texto la describe
   como biometría/PIN del teléfono al reabrir la app. Se implementa así (Sprint 2).

---

## 8. Cómo vamos a trabajar (rutina diaria)

1. Tú dices qué fase arrancamos → el orquestador (Claude principal) reparte a los subagentes.
2. Cada subagente trabaja **solo en su carpeta** y entrega: archivos tocados + RF cubiertos.
3. El orquestador integra, compila y hace commit (`feat(RF-3.2): cobrar venta en efectivo`).
4. `verificador-dfr` revisa contra el DFR antes de dar un RF por terminado.
5. Tú pruebas en el teléfono y das el visto bueno → se marca ✅ en la trazabilidad.
6. Cualquier cambio de requerimiento se registra como SCR (plantilla oficial de la materia).
