# carApp

App móvil multiplataforma (Android + iOS) para llevar el control de los gastos de tus vehículos.

El **MVP se limita al gasto en combustible**: registrar repostajes, consultarlos y conocer el consumo real del vehículo en **L/100 km**. Fases posteriores añadirán reparaciones, seguros, impuestos y otros gastos.

> **Estado del proyecto:** especificación cerrada y backlog listo. Sin código todavía — la implementación arranca en la Fase 0 del backlog.

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [ESPECIFICACION.md](ESPECIFICACION.md) | Referencia **normativa**: dominio, reglas de negocio, arquitectura, sincronización y requisitos no funcionales. |
| [BACKLOG.md](BACKLOG.md) | Plan de implementación en 5 fases, con criterios de aceptación y dependencias por historia. |

Ante cualquier conflicto entre ambos, **manda la especificación**.

## Principios de producto

| # | Principio | Implicación |
|---|-----------|-------------|
| P1 | Fricción mínima en el registro | Registrar un repostaje en menos de 15 s, con el mínimo de campos obligatorios. |
| P2 | Funciona siempre | Las gasolineras tienen mala cobertura: la app es 100 % funcional sin red. |
| P3 | Sin barreras de entrada | Se usa sin crear cuenta (login anónimo) y se convierte a cuenta permanente sin perder datos. |
| P4 | Portabilidad del proveedor cloud | Ninguna decisión de Firebase se filtra fuera de la capa `data`. |

**Métrica de éxito del MVP:** un usuario puede registrar un vehículo y sus repostajes sin conexión y obtener un consumo medio fiable a partir del tercer repostaje a depósito lleno.

## Alcance

**Dentro del MVP:** autenticación (anónima, Google, Apple) y conversión de cuenta · CRUD de vehículos · CRUD de repostajes · cálculo de consumo · persistencia local como fuente de verdad con sincronización offline-first · ajustes mínimos (unidades, moneda, cerrar sesión, eliminar cuenta).

**Fuera del MVP:** otros tipos de gasto · gráficas avanzadas · exportación CSV/PDF y OCR de tickets · recordatorios · compartir vehículo · widgets, Wear OS, watchOS, web · precios oficiales de carburante.

> Cualquier tarea que toque un punto de "fuera del MVP" debe rechazarse o escalarse. El alcance no se amplía sin actualizar la especificación.

## Concepto central: el cálculo de consumo

Método **full-to-full** (regla R-3 de la spec). Un *tramo* es el intervalo entre dos repostajes a depósito lleno consecutivos:

```
litros  = Σ litros de los repostajes del tramo (incluidos los parciales)
km      = odómetro del lleno final − odómetro del lleno inicial
consumo = litros / km × 100        → L/100 km
```

Un tramo **no produce consumo** si: no hay lleno anterior · el repostaje final es parcial · algún repostaje del tramo tiene `hasMissedEntries` · algún repostaje del tramo tiene el odómetro inconsistente · `km <= 0`.

El **consumo medio del vehículo** se pondera por kilómetros (`Σ litros / Σ km × 100`), *no* es la media aritmética de los consumos por tramo.

## Arquitectura

**Kotlin Multiplatform** para lógica de dominio, datos y presentación; **UI nativa** en cada plataforma.

| Capa | Tecnología |
|------|------------|
| Lógica compartida | Kotlin Multiplatform (`commonMain`, `androidMain`, `iosMain`) |
| UI Android | Jetpack Compose (nativa) |
| UI iOS | SwiftUI (nativa) |
| Build | Gradle Kotlin DSL + version catalog + convention plugins en `build-logic` |
| BD local | SQLite vía Room KMP o SQLDelight (decisión D-1) |
| Backend | Firebase SQL Connect (PostgreSQL sobre Cloud SQL) |
| Autenticación | Firebase Authentication |
| Otros | Coroutines/Flow · Koin · `kotlinx.serialization` · `kotlinx-datetime` |

### Estructura de módulos

Clean Architecture con **modularización vertical por funcionalidad**: cada feature aporta sus tres capas en módulos Gradle independientes.

```
:app-android                     ← host Android (Compose, navegación, DI raíz)
/iosApp                          ← host iOS (SwiftUI, navegación, DI raíz)
:shared                          ← umbrella: agrega módulos y expone el XCFramework

:core:common | :core:domain-model | :core:database | :core:remote
:core:auth   | :core:sync         | :core:testing

:feature:{onboarding,auth,vehicle,fueling,settings}:{domain,data,presentation}

:integration:firebase-auth       ← única implementación que conoce Firebase Auth
:integration:firebase-sqlconnect ← única implementación que conoce SQL Connect
```

### Reglas de dependencia (verificadas en CI)

1. `:feature:X:domain` es Kotlin puro: sin Android, iOS, Firebase ni base de datos.
2. `:feature:X:data` depende de su `domain`, `:core:database` y `:core:remote`. **Nunca** de `:integration:*`.
3. `:feature:X:presentation` depende de su `domain`, nunca de `data`.
4. Un feature no depende de otro feature; el contrato compartido sube a `:core`.
5. Solo los módulos host conocen `:integration:*`, para cablearlos en el grafo de DI.
6. Ningún tipo de Firebase cruza la frontera de `data`.

Estas reglas **fallan el build**, no dependen de la revisión humana.

**Criterio de portabilidad (P4):** borrar los módulos `:integration:*` debe dejar todos los `:core:*`, `:feature:*:domain` y `:feature:*:data` compilando y con sus tests en verde usando fakes.

## Sincronización offline-first

- La **base local es la única fuente de verdad para la UI**; la UI nunca observa la red.
- Toda escritura es local e inmediata; el envío se encola en un *outbox*.
- Los IDs son UUID v4 **generados en cliente**, así un registro creado offline ya tiene identidad definitiva.
- **Push:** upserts idempotentes por `id`, con backoff exponencial y estado `FAILED` + reintento manual.
- **Pull:** cursor `lastSyncedAt` con **hora del servidor**, delta paginado, tombstones incluidos.
- **Conflictos:** *last-write-wins* por `updatedAt`, con desempate determinista por `id`. El tombstone gana frente a actualizaciones anteriores.
- **Seguridad:** la autorización por `ownerId` se aplica **en el servidor**, no solo en el cliente.

## Plan de trabajo

| Fase | Objetivo | Hitos clave |
|------|----------|-------------|
| **0 · Fundamentos** | Esqueleto que compila en ambas plataformas con las reglas de arquitectura blindadas | Bootstrap KMP · convention plugins · `:core` base · guardas de arquitectura en CI · ADRs D-1..D-7 |
| **1 · Persistencia local** | La app guarda y pinta vehículos y repostajes. **Ya es útil** | `:core:database` · dominio y datos de `Vehicle` y `FuelEntry` · ⭐ cálculo de consumo · UI Android e iOS |
| **2 · Autenticación** | Login anónimo, Google y Apple; conversión de cuenta | `:core:auth` · `:integration:firebase-auth` · onboarding · conversión anónima · borrado de cuenta |
| **3 · Backend y sync** | Mayor riesgo técnico; se aborda con el dominio ya estable | Esquema SQL Connect · cliente remoto · ⭐ motor de sincronización · prueba de desacoplamiento |
| **4 · Cierre del MVP** | Ajustes, accesibilidad, rendimiento y publicación | Auditoría TalkBack/VoiceOver · objetivos de rendimiento · builds de release |

Las fases 2 y 3 son parcialmente paralelizables con el final de la fase 1. Detalle completo en [BACKLOG.md](BACKLOG.md).

### Puntos que requieren revisión humana

- Cierre de la fase 0 (las reglas de arquitectura condicionan todo lo demás).
- **E1-05**, regla de consumo: es la definición del producto.
- **E3-01**, autorización en servidor: un error aquí es una brecha de datos.
- **E3-03**, motor de sincronización: un error aquí es pérdida de datos del usuario.

## Decisiones técnicas abiertas

Se resuelven como ADRs en `docs/adr/` antes de escribir código (historia E0-06):

| ID | Decisión | Opciones |
|----|----------|----------|
| D-1 | Base de datos local | Room KMP vs SQLDelight |
| D-2 | Interop Kotlin↔Swift | SKIE vs wrappers manuales |
| D-3 | Inyección de dependencias | Koin vs factories manuales |
| D-4 | `fuelType` en el MVP | Incluirlo vs posponerlo |
| D-5 | Acceso a SQL Connect | SDKs nativos con `expect/actual` vs cliente Ktor común *(inclinación actual: Ktor común)* |
| D-6 | Firebase Auth en KMP | `dev.gitlive:firebase-auth` vs `expect/actual` propio |
| D-7 | Navegación | Compartida vs nativa por plataforma *(recomendación: nativa)* |

> Firebase SQL Connect **no tiene SDK oficial para KMP** — genera SDKs por plataforma. Es la restricción con mayor impacto arquitectónico del proyecto (D-5).

## Requisitos no funcionales

- **Plataformas:** Android `minSdk 26` · iOS 16+
- **Rendimiento:** arranque en frío < 2 s · lista de 1.000 repostajes fluida · consumo de 1.000 repostajes < 100 ms
- **Offline:** 100 % de las funciones del MVP sin red
- **Accesibilidad:** tamaño de fuente del sistema, etiquetas de contenido, contraste AA
- **i18n:** español e inglés desde el día 1, sin cadenas hardcodeadas
- **Calidad:** ktlint + detekt en CI · build + tests + lint en cada PR para ambas plataformas
- **Privacidad:** política publicada, fichas de tienda cumplimentadas, eliminación de cuenta accesible en la app

## Convenciones para contribuir (y para agentes)

- La referencia normativa es `ESPECIFICACION.md`.
- Ninguna historia se da por terminada sin sus tests en verde y el lint limpio.
- Prohibido implementar nada del apartado "fuera del MVP".
- Prohibido introducir dependencias que violen las reglas de dependencia.
- Toda historia que altere el modelo de datos debe incluir su migración.
- Cálculos monetarios con decimal de precisión fija, **nunca** `Float`/`Double`.
