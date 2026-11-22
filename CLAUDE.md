# Mis Ventas — instrucciones para Claude

App Android de punto de venta offline-first para Max Carlotas (venta ambulante, ITZ). Desarrolla: Ambulant Point.

## Etapa actual: desarrollo independiente (desde 2026-11-21)
- La versión académica ya se entregó en el repo `MaxDev8459/mis-ventas-oficial`, que está **congelado**: no se toca, no se le hace push ni se sincroniza con este repo (hay hooks que lo bloquean).
- Este repo (`MaxDev8459/mis-ventas`) sigue como desarrollo independiente: se pueden agregar funciones y cambiar lo ya hecho aunque **no esté en el DFR**. El usuario ya usa la app en su negocio, así que los cambios se piensan según su uso real.
- Antes de un cambio grande, confirma el alcance con el usuario; no inventes reglas de negocio por tu cuenta.
- Nunca romper los datos ni el respaldo en Firebase: el respaldo en la nube es la forma de recuperar la cuenta si algo falla. Cambios de esquema Room → migración, nunca `fallbackToDestructiveMigration`.
- El DFR (`docs/dfr/DFR_Mis_Ventas_v1.2.md`, solo local) y `docs/02-TRAZABILIDAD-DFR.md` quedan como referencia de la versión entregada. Mensajes al usuario siguen centralizados en `core/Mensajes.kt`.

## Stack
Kotlin · Jetpack Compose + Material 3 · MVVM (`ui` → `domain` → `data`) · Room · DI manual (`AppContainer`) · minSdk 26 · solo vertical.
Offline-first con Room; Firebase (Auth/Firestore) como respaldo en la nube.
Ruta del proyecto (se trabaja en Windows y en CachyOS; el código viaja solo por git, nunca por Google Drive):
- Windows: `C:\Users\MaxDev\Documents\SEMESTRE AGOSTO DICIEMBRE 2026\GESTION DE PROYECTOS DE SOFTWARE\documentos del proyecto\mis-ventas`
- Linux: `~/LinuxProjects/mis-ventas` (fuera de la carpeta del semestre, que se sincroniza con Drive). SDK en `~/Android/Sdk`.

Archivos que no están en git y se copian a mano entre sistemas: `app/google-services.json`, `keystore.properties`, `firma/` (llave de liberación) y `docs/dfr/`.

## Instalar en el teléfono sin perder datos
El teléfono tiene instalada la versión de **liberación** (firmada con la llave de `firma/`). Para actualizarla sin borrar datos, instala siempre un APK firmado con esa misma llave (`assembleRelease` + `adb install -r`). Un APK debug de otra computadora trae otra firma: Android lo rechaza y la única salida sería desinstalar (borra los datos). Si eso pasa, **no desinstales**: avisa al usuario.

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
