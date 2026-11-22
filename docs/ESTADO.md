# Estado del proyecto — Mis Ventas (desarrollo independiente)

> **Léelo al empezar cada sesión** y actualízalo al terminar: marca lo hecho, anota decisiones y deja claro el siguiente paso.
> Detalle de reglas y fases: `docs/07-DESARROLLO-INDEPENDIENTE.md` · Especificación de la carga v2: `docs/08-ESPEC-CARGA-V2.md`.

**Última actualización:** 22-nov-2026

## Dónde estamos

| Dato | Valor |
|---|---|
| App real en el teléfono | **1.5.0** (versionCode 7), liberación, cuenta `maxicarlotas@gmail.com` |
| Versión de la base Room | **9** en Mis Ventas Pruebas; la app real sigue en 8 hasta liberar la 1.6.0 |
| Pruebas JVM | 296 pasan (+ 53 instrumentadas con Room en el teléfono) |
| Siguiente fase | **Fase 5**: 1.6.0 instalada en Mis Ventas Pruebas, **esperando que el usuario la pruebe y la apruebe** |

## Checklist de fases

- [x] **Fase 0:** correcciones de la revisión contra el DFR y aviso de privacidad (1.0.1)
- [x] **Fase 1:** corregir venta, merma desde Vender, calculadora de billetes, modo oscuro y huella configurable (1.1.0)
- [x] **Fase 2a:** favoritos (★) y chips de categoría en Vender; búsqueda sin acentos (1.2.0, Room v4)
- [x] **Fase 2b:** carga del día v1 y v2 (+ / −, Máx, precargas, unidad para kilo, "te quedan", sobrante en el corte) (1.3.0, Room v5–v6)
- [x] Correcciones: texto legible en existencia baja/agotado con modo oscuro; doble toque en "regresar" (pantalla en blanco). También en la demo.
- [x] **Fase 3:** fiado + venta rápida sin producto (1.4.0, Room v7, 22-nov-2026)
  - [x] Hacer al usuario las preguntas abiertas y anotar sus respuestas en `docs/07` (22-nov)
  - [x] Escribir la especificación (`docs/09-ESPEC-FIADO.md`); aprobada el 22-nov (aviso desde $150)
  - [x] Programar con pruebas unitarias (`FiadoTest`, 45) e instrumentadas (`FiadoRoomTest`, 14)
  - [x] Migración 6→7 verificada con `sqlite3` (base sintética y copia de Pruebas) y ya aplicada en Pruebas
  - [x] Instalada en Mis Ventas Pruebas (1.4.0-pruebas)
  - [x] El usuario la probó y la aprobó (con dos ajustes: cierre en Vender con monto libre + carga; ocultar la carga al calcular el corte)
  - [x] Liberada a la app real (1.4.0) con `install -r`, firma comparada y "Sincronizado" antes y después
- [x] **Fase 4:** apartados con existencia separada (1.5.0, Room v8, 22-nov-2026)
  - [x] Preguntas al usuario y respuestas en `docs/07` (22-nov)
  - [x] Especificación `docs/10-ESPEC-APARTADOS.md`, aprobada el 22-nov (apartar sin caja, teléfono opcional, lugar ≤ 60)
  - [x] Programar con pruebas: `ApartadoTest` (18) y `ApartadoRoomTest` (11); alarmas exactas 15 min antes y a la hora
  - [x] Migración 7→8 verificada con `scripts/verificar_migracion.py` (sintética v6→v8 y copia de Pruebas v7) y aplicada en Pruebas
  - [x] Instalada en Mis Ventas Pruebas (1.5.0-pruebas)
  - [x] El usuario la probó (incluidas las notificaciones) y la aprobó
  - [x] Liberada a la app real (1.5.0): firma igual, "Sincronizado" antes, `install -r`, sin fallos
- [ ] **Fase 5:** reportes nuevos (comparar periodos, ventas por hora/día, fiado y mermas a precio de venta) (Room v8→v9)
  - [x] Preguntas al usuario y respuestas en `docs/07` (23-nov)
  - [x] Especificación `docs/11-ESPEC-REPORTES.md`, aprobada el 23-nov (mismo tramo, 5 productos)
  - [x] Programar con pruebas: `ReportesNuevosTest` (23) y `ReportesNuevosRoomTest` (4)
  - [x] Migración 8→9 (una columna: precio de la merma) verificada con `scripts/verificar_migracion.py` y aplicada en Pruebas
  - [x] Instalada en Mis Ventas Pruebas (1.6.0-pruebas)
  - [ ] El usuario la prueba y la aprueba
  - [ ] Liberar a la app real (1.6.0)

## Fase 3: lo que ya acordó el usuario (entrevista del 21-nov)

- **Fiado:** lista de clientes (nombre; teléfono opcional). Se paga en **abonos parciales o liquidando todo**. **El abono entra al corte del día en que se paga**, no del día en que se fió. El inventario **baja al entregar** el producto, como una venta normal.
- **Venta rápida sin producto:** cobrar un monto libre (algo que no está en el catálogo). No descuenta inventario. En los reportes por producto aparece como "Varios".
- **Cobro:** no cambia el flujo actual de cobrar, salvo lo necesario para fiar.

### Preguntas de Fase 3: contestadas el 22-nov

Las respuestas están en `docs/07` y la regla completa en `docs/09-ESPEC-FIADO.md`.

## Cómo se trabaja (no saltarse)

1. **Al empezar:** `git pull` y leer este archivo.
2. **Cambio grande:** confirmar el alcance con el usuario y escribir la especificación para que la valide **antes** de programar.
3. **Room:** cada cambio de esquema lleva migración. Verificarla con `scripts/verificar_migracion.py` (base sintética y una copia de Mis Ventas Pruebas) contra el `N.json` generado. Nunca usar `fallbackToDestructiveMigration`.
4. **Tablas nuevas que se respaldan:** agregarlas en `Mapeo.kt` (Colecciones, aDocumento, aX), `SincronizadorFirestore.tablas`, `SyncDao` (conteos de pendientes, lectura por lotes, pendienteX, borrarX, insertarX), `ServicioRecuperacion` (bajar, borrar, insertar) y `AuthRepoFirebase` (pendienteX al registrar otra cuenta).
5. **Probar primero en Mis Ventas Pruebas** (`./gradlew test assembleDebug` + `adb install -r app/build/outputs/apk/debug/app-debug.apk`). Cuenta `maxicarlotas+pruebas@gmail.com`, ya con datos ficticios.
6. **Liberar a la app real:** subir versionCode/versionName, `assembleRelease`, comparar la firma con `apksigner`, confirmar que la app real dice **"Sincronizado"** y `adb install -r`. **Nunca desinstalar.** Abrir la app y revisar `adb logcat -b crash`.
7. **Al terminar:** actualizar este archivo y `docs/07`, hacer commit y `git push`.

## Las tres apps del teléfono

| App | Paquete | Firma | Cuenta | Uso |
|---|---|---|---|---|
| Mis Ventas | `mx.ambulantpoint.misventas` | liberación (`firma/`) | `maxicarlotas@gmail.com` | negocio real, **no tocar sin liberar** |
| Mis Ventas Demo | `.demo` (rama `demo-entrega`, `~/LinuxProjects/mis-ventas-demo`) | debug | `comprasenlineamaxdev@gmail.com` | presentación del 27-nov; solo correcciones de errores |
| Mis Ventas Pruebas | `.pruebas` (compilación debug de main) | debug | `maxicarlotas+pruebas@gmail.com` | probar cada fase antes de liberar |

## Notas

- La llave debug de Linux (`~/.android/debug.keystore`) es la de Windows, con copia en `firma/debug-windows.keystore`.
- En Windows hay que copiar a mano `firma/` (completa), `keystore.properties` y el `google-services.json` nuevo (trae las tres apps).
- Pendiente menor: en el teléfono quedó el paquete viejo `mx.ambulantpoint.misventas.test`. Es inofensivo; se puede desinstalar si el usuario lo pide.
