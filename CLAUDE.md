# Mis Ventas — instrucciones para Claude

App Android de punto de venta offline-first para Max Carlotas (venta ambulante, ITZ). Desarrolla: Ambulant Point.

## Fuente de verdad
- `docs/dfr/DFR_Mis_Ventas_v1.2.md` manda sobre cualquier otra cosa. Si el DFR no lo especifica, pregunta; no inventes reglas, campos ni mensajes.
- Mensajes al usuario: literales del DFR, centralizados en `core/Mensajes.kt`.
- Plan y alcance vigentes: `docs/00-PLAN-MVP.md`. Estado de cada RF: `docs/02-TRAZABILIDAD-DFR.md` (actualízalo al terminar un RF).
- Fuera de alcance (DFR): facturación, terminales bancarias, código de barras, e-commerce, lealtad, multi-vendedor, gráficas, gastos/retiros de caja, comprobantes. Cualquier petición así → Solicitud de Cambio, no código.

## Stack
Kotlin · Jetpack Compose + Material 3 · MVVM (`ui` → `domain` → `data`) · Room · DI manual (`AppContainer`) · minSdk 26 · solo vertical.
**MVP 100 % local (decisión 26-oct-2026):** auth local (`AuthRepoLocal`, hash PBKDF2). Firebase (Auth/Firestore/Storage) entra en Sprint 2 detrás de las mismas interfaces; no agregues dependencias de Firebase antes.
Ruta del proyecto (se trabaja en Windows y en CachyOS; el código viaja solo por git, nunca por Google Drive):
- Windows: `C:\Users\MaxDev\Documents\SEMESTRE AGOSTO DICIEMBRE 2026\GESTION DE PROYECTOS DE SOFTWARE\documentos del proyecto\mis-ventas`
- Linux: `~/SEMESTRE AGOSTO DICIEMBRE 2026/GESTION DE PROYECTOS DE SOFTWARE/documentos del proyecto/mis-ventas`

Al empezar: `git pull`. Al terminar: commit y `git push`.

## Convenciones
- Paquete `mx.ambulantpoint.misventas`. Código y nombres en español (dominio del negocio), convenciones oficiales de Kotlin.
- Dinero en centavos `Long`; cantidades en milésimas `Long` (1 pza = 1000; 0.250 kg = 250). Nunca `Double` para dinero.
- Entidades con `id` UUID, `creadoEn`, `actualizadoEn`, `pendienteSync`.
- Nada se borra salvo categorías sin productos (RF 2.9): productos, ventas y formas de pago cambian de estado.
- Toda regla con cálculo lleva prueba unitaria.
- Commits por RF: `feat(RF-x.y): descripción`.

## Compilar
`./gradlew test assembleDebug`, con JAVA_HOME según el sistema:
- Windows: `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`
- Linux: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` (o el `jbr` de Android Studio si se instaló)

`local.properties` (ruta del SDK) es propio de cada sistema y no se sube.

Pruebas en el teléfono: **siempre** con `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.
Sin esa opción, `connectedDebugAndroidTest` desinstala la app al terminar y **borra todos sus datos**
(pasó el 26-oct-2026). Nunca correrlas sin ella después de cargar datos de demo.

Datos de demo: `./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
-Pandroid.testInstrumentationRunnerArguments.class=mx.ambulantpoint.misventas.demo.CargarDatosDemo
-Pandroid.testInstrumentationRunnerArguments.demo=1` (ver `app/src/androidTest/.../demo/`).

## Código de referencia (proyecto anterior)
Windows: `C:\Users\MaxDev\ClaudeCodeProyects\AmbulantPoint` · Linux: `~/ClaudeCodeProyects/AmbulantPoint` (solo si se copió; si no existe, trabajar sin él) — **solo lectura**. Úsalo como referencia según la tabla de `docs/00-PLAN-MVP.md` §4.1. No lo edites, no copies su modelo de datos (usa `Double` e `Int`), y descarta cualquier regla suya que no esté en el DFR.

## Agentes
Ver `docs/01-ARQUITECTURA-AGENTICA.md` y `.claude/agents/`. Cada agente edita solo su carpeta.
