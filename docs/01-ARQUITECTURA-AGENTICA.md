# Arquitectura agéntica de desarrollo — *Mis Ventas*

Cómo usamos Claude Code y sus subagentes para construir la app sin perder la trazabilidad con el DFR.
La arquitectura del código (capas, decisiones y su origen) está en [04-ARQUITECTURA-APP.md](04-ARQUITECTURA-APP.md).

## 1. Vista general

```
                         ┌──────────────────────────────┐
     Tú (Líder/Cliente)  │  ORQUESTADOR (Claude princ.) │  lee DFR + plan, reparte trabajo,
     aprueba / prueba ──►│  integra, compila, commitea  │  resuelve conflictos, habla contigo
                         └──────────────┬───────────────┘
            ┌──────────────┬────────────┼─────────────┬───────────────┐
            ▼              ▼            ▼             ▼               ▼
     ┌────────────┐ ┌────────────┐ ┌──────────┐ ┌─────────────┐ ┌─────────────┐
     │ datos-room │ │  reglas-   │ │ ui-      │ │ firebase-   │ │ verificador-│
     │ (data/     │ │  negocio   │ │ compose  │ │ sync        │ │ dfr (solo   │
     │  local)    │ │ (domain,   │ │ (ui/)    │ │ (data/      │ │  lectura)   │
     │            │ │  core,test)│ │ x2 paral.│ │  remote)    │ │             │
     └────────────┘ └────────────┘ └──────────┘ └─────────────┘ └──────┬──────┘
                                                                        ▼
                                                                ┌─────────────┐
                                                                │ qa-android  │ gradle test,
                                                                │             │ assembleDebug
                                                                └─────────────┘
```

## 2. Agentes

| Agente | Modelo | Dueño de | Entrega | Cuándo |
|---|---|---|---|---|
| **Orquestador** | sesión principal | `docs/`, Gradle, `AppContainer`, navegación, integración | Commits, estado | Siempre |
| `datos-room` | sonnet | `data/local/**`, `data/repository/**` | Entidades, DAOs, transacciones, semilla | Fase 1 |
| `reglas-negocio` | sonnet | `core/**`, `domain/**`, `src/test/**` | Validadores, casos de uso, pruebas desde criterios de aceptación | Fase 2 |
| `ui-compose` | sonnet | `ui/<modulo>/**` asignado | Pantallas + ViewModels | Fase 3 (2 en paralelo, módulos distintos) |
| `firebase-sync` | sonnet | `data/remote/**` | Auth Firebase, sync Firestore, reglas de seguridad | Sprint 2 (el MVP es local) |
| `verificador-dfr` | sonnet | — (solo lectura) | Reporte RF por RF: cumple / falla con cita del DFR | Al cerrar cada fase |
| `qa-android` | haiku | — | Corre `gradlew test assembleDebug`, resume errores | Después de cada integración |

Definiciones en `.claude/agents/*.md`.

## 3. Reglas de colaboración

1. **El DFR manda.** Cada agente recibe los RF que le tocan (número + texto) y debe usar los
   mensajes literalmente. Si algo no está en el DFR, pregunta al orquestador; no inventa.
2. **Carpetas exclusivas.** Dos agentes nunca editan la misma carpeta a la vez → se puede paralelizar
   sin conflictos. Los contratos compartidos (interfaces de repositorio, modelos de dominio) los
   define primero el orquestador / `datos-room`, y los demás solo los consumen.
3. **Secuencia de dependencias (MVP):** Fase 0 → `datos-room` → `reglas-negocio` → `ui-compose` (A/B/C/D
   en paralelo de dos en dos) → auth local → `verificador-dfr` → `qa-android`. `firebase-sync` entra en Sprint 2.
4. **Definición de terminado de un RF:** compila, tiene prueba si tiene cálculo/regla, el verificador
   lo aprobó, tú lo probaste en el teléfono, y está marcado en `02-TRAZABILIDAD-DFR.md`.
5. **Commits por RF:** `feat(RF-3.2): cobrar venta en efectivo con cambio`. Facilita el reporte de avance.
6. **Cambios de requerimiento:** si durante la prueba surge algo nuevo, no se codifica directo:
   se redacta una SCR (plantilla de la materia) y se actualiza el DFR primero.

## 4. Por qué no usamos la skill `/iniciar-proyecto` completa

Esa skill genera los 6 documentos de vibecoding (PRD, TRD, UI/UX, etc.) y los entregables de gestión.
Ya tienen Charter, Estimación, Riesgos, Contrato, EDT, Cronograma y DFR v1.2 validado: regenerarlos
duplicaría y podría contradecir la documentación oficial. Tomamos solo dos ideas de ella:
subagentes especializados por proyecto y un archivo de estado/trazabilidad para retomar el trabajo.
