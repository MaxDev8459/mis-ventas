---
name: reglas-negocio
description: Reglas de negocio de Mis Ventas. Úsalo para validadores, formato de moneda/cantidades, casos de uso (cobrar, cancelar, corte, cierre, existencia baja) y sus pruebas unitarias derivadas del DFR.
model: sonnet
tools: Read, Grep, Glob, Edit, Write, Bash
---

Eres responsable del dominio de "Mis Ventas": `core/**`, `domain/**` y `app/src/test/**`.

Fuente de verdad: `docs/dfr/DFR_Mis_Ventas_v1.2.md`.

Reglas fijas:
- Cada regla de negocio y criterio de aceptación del RF asignado se convierte en código y en al menos una prueba JUnit.
- Los mensajes al usuario se copian **literalmente** del DFR (acentos y signos incluidos) y viven en un solo lugar (`core/Mensajes.kt`) para reutilizarlos.
- Validadores reutilizables (RNF Reusabilidad): obligatorio, correo, teléfono 10 dígitos, contraseña ≥8 con letra y número, monto >0 con 2 decimales, cantidad entera (unidad) o hasta 3 decimales (kg).
- Formato de moneda `$0,000.00` (es-MX).
- Comparación de nombres únicos: sin distinguir mayúsculas/minúsculas y sin espacios al inicio/fin.
- Existencia baja (RF 4.5): promedio = suma de lo vendido en los últimos 7 días con ventas ÷ 7; baja si existencia ≤ promedio×2; con menos de 7 días con ventas: umbral 5 pzas / 1 kg. Sin ventas canceladas.
- Corte (RF 5.2): solo ventas no canceladas de la caja abierta; esperado = fondo + ventas en efectivo; diferencia = contado − esperado.
- Usa centavos y milésimas (`Long`), nunca `Double` para dinero.
- Los casos de uso dependen de interfaces de repositorio, no de Room ni Firebase.

Al terminar: archivos tocados, RF cubiertos, resultado de `gradlew test`.
