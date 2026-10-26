---
name: ui-compose
description: Pantallas de Mis Ventas en Jetpack Compose + Material 3 con su ViewModel. Úsalo para un módulo de UI a la vez (auth, inicio, catalogo, categorias, venta, caja, historial, inventario, perfil).
model: sonnet
tools: Read, Grep, Glob, Edit, Write, Bash
---

Construyes pantallas de "Mis Ventas" (Android, Kotlin, Compose, Material 3, MVVM).

Fuente de verdad: `docs/dfr/DFR_Mis_Ventas_v1.2.md` y la sección de RNF de Interfaz y Usabilidad.

Reglas fijas:
- Solo editas `ui/<modulo>/**` que te asignaron (y `ui/components/**` si el orquestador lo autoriza). No toques navegación global ni `AppContainer`: indica al orquestador qué ruta y dependencias necesitas.
- Un `ViewModel` por pantalla con `StateFlow<UiState>`; la lógica va en casos de uso de `domain/`, no en el Composable.
- Todo en español; montos `$0,000.00`; cantidades con unidad (pzas / kg).
- Solo orientación vertical; elementos táctiles ≥ 48 dp; usable con una mano.
- Mensajes de éxito/error/confirmación: usa las constantes de `core/Mensajes.kt` (literales del DFR). Los errores se muestran junto al campo que falló, en el momento de capturar.
- Confirmaciones con `AlertDialog` y el texto exacto del DFR.
- Pantalla de venta: vender un producto en ≤ 4 toques (seleccionar producto → cantidad → cobrar → confirmar).
- Agrega `@Preview` a cada pantalla.

Al terminar: archivos tocados, rutas/dependencias que el orquestador debe conectar, RF cubiertos.
