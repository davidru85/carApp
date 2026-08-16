# Backlog de implementación — App de gastos de vehículo (MVP)

Cada historia está pensada para ser **una unidad de trabajo entregable a un agente**: alcance cerrado, criterios de aceptación verificables y dependencias explícitas.

**Convenciones para agentes**

- Referencia normativa: `ESPECIFICACION.md`. Ante conflicto entre esta lista y la spec, manda la spec.
- Ninguna historia se da por terminada sin sus tests en verde y el lint limpio.
- Prohibido implementar nada de la sección 2.2 de la spec ("fuera del MVP").
- Prohibido introducir dependencias que violen las reglas 7.3 de la spec.
- Toda historia que altere el modelo de datos debe incluir su migración.

Leyenda de tamaño: **S** ≤ medio día · **M** 1–2 días · **L** 3–5 días.

---

## Fase 0 · Fundamentos

> Objetivo: un esqueleto que compila en ambas plataformas, con las reglas de arquitectura ya blindadas. Sin esto, cualquier trabajo paralelo de agentes diverge.

### E0-01 · Bootstrap del proyecto KMP — **M**
Crear el proyecto KMP con targets `android` e `ios{X64,Arm64,SimulatorArm64}`, host `:app-android` (Compose) y `/iosApp` (SwiftUI), y el umbrella `:shared` exportando el XCFramework.

*Criterios de aceptación*
- `./gradlew :app-android:assembleDebug` compila.
- La app iOS compila y arranca en simulador mostrando una pantalla con texto proveniente de `commonMain`.
- Todos los scripts de build en **Kotlin DSL**. Nada de Groovy.
- `gradle/libs.versions.toml` como única fuente de versiones; ningún número de versión hardcodeado en un `build.gradle.kts`.

*Bloquea:* todo lo demás.

### E0-02 · Convention plugins en `build-logic` — **M**
Plugins de convención para los arquetipos de módulo: `kmp.library`, `kmp.feature.domain`, `kmp.feature.data`, `kmp.feature.presentation`, `android.application`.

*Criterios de aceptación*
- Crear un módulo nuevo requiere ≤ 5 líneas en su `build.gradle.kts`.
- El plugin `kmp.feature.domain` **no** aplica el plugin de Android ni ninguna dependencia de framework.
- Configuración de tests y toolchain de Kotlin centralizada en los plugins.

### E0-03 · Módulos `:core` base — **M**
`:core:common` (tipos `Result`/`AppError`, `DispatcherProvider`, generador de UUID `expect/actual`, `Clock`), `:core:domain-model` (`Money`, `Volume`, `Distance` con aritmética decimal), `:core:testing` (fakes, `TestDispatcherRule`, builders).

*Criterios de aceptación*
- `Money` usa aritmética decimal de precisión fija. Existe un test que demuestra que `0.1 + 0.2 == 0.3` en el tipo `Money`.
- `Clock` es inyectable y sustituible en tests (nada de `Clock.System.now()` disperso por el código).
- Cobertura de tests de `:core:domain-model` ≥ 90 %.

### E0-04 · Guardas de arquitectura en CI — **M**
Test o tarea Gradle que valida el grafo de dependencias contra las reglas 7.3 de la spec.

*Criterios de aceptación*
- Añadir a mano una dependencia de `:feature:vehicle:domain` sobre `:core:database` hace **fallar el build**, con un mensaje que explica qué regla se ha violado.
- Añadir una dependencia entre dos features hace fallar el build.
- Añadir una dependencia de un módulo `:feature:*:data` sobre `:integration:*` hace fallar el build.
- La validación corre en cada PR.

### E0-05 · Calidad y CI — **S**
ktlint + detekt + workflow de CI (build, tests, lint, ambas plataformas).

*Criterios de aceptación:* PR con violación de estilo o test rojo → CI en rojo. Tiempo de CI < 15 min.

### E0-06 · Decisiones técnicas D-1 a D-7 (ADRs) — **M**
Resolver las decisiones abiertas de la sección 10 de la spec y documentar cada una como un ADR corto (contexto, opciones, decisión, consecuencias) en `docs/adr/`.

*Criterios de aceptación:* un ADR por decisión, todos con estado "Aceptada", y las versiones elegidas ya presentes en el version catalog.

*Bloquea:* E1-01, E2-01, E3-01.

---

## Fase 1 · Persistencia local

> Objetivo: la app guarda vehículos y repostajes en local y los pinta. Todavía sin red ni login. **Al final de esta fase la app ya es útil.**

### E1-01 · `:core:database` — **M**
Configurar la solución elegida en D-1 con `expect/actual` para el driver por plataforma, esquema inicial, transacciones y estrategia de migraciones.

*Criterios de aceptación*
- La base se instancia y persiste en Android e iOS (test instrumentado por plataforma).
- Existe un test de migración desde el esquema v1 (aunque de momento sea trivial), y está documentado el procedimiento para añadir migraciones.
- Todas las consultas devuelven `Flow` para lo observable y `suspend` para lo puntual.

### E1-02 · Dominio de `Vehicle` — **S**
`:feature:vehicle:domain`: entidad, `VehicleRepository` (interfaz) y use cases `CreateVehicle`, `UpdateVehicle`, `DeleteVehicle`, `ObserveVehicles`, `ObserveVehicle`.

*Criterios de aceptación*
- Módulo de Kotlin puro, sin dependencias de framework (lo verifica E0-04).
- Validaciones de la spec 4.1 implementadas: nombre no vacío y ≤ 40 caracteres, único por usuario, `initialOdometer` en rango.
- Todos los use cases con tests unitarios, incluidos los caminos de error.

### E1-03 · Datos de `Vehicle` (solo local) — **M**
`:feature:vehicle:data`: `VehicleLocalDataSource`, mappers entidad↔dominio, `VehicleRepositoryImpl` con `VehicleRemoteDataSource` inyectado como no-op por ahora.

*Criterios de aceptación*
- Los mappers tienen tests de ida y vuelta (round-trip).
- Todo lo creado se marca `syncState = PENDING`.
- El borrado es lógico y en cascada sobre los repostajes.

### E1-04 · Dominio de `FuelEntry` — **M**
`:feature:fueling:domain`: entidad, repositorio, use cases CRUD y las reglas R-1 y R-2 de la spec.

*Criterios de aceptación*
- R-2 implementada: dados dos de los tres valores, el tercero se deriva con el redondeo especificado.
- R-1 implementada: la inconsistencia de odómetro **avisa pero no bloquea**, y marca el registro.
- Fecha futura (> 1 h de margen) rechazada.

### E1-05 · **Cálculo de consumo (R-3)** — **M** ⭐
Use case `CalculateConsumption` en `:feature:fueling:domain`. Es el corazón funcional del MVP.

*Criterios de aceptación* — tests obligatorios, uno por caso:
1. Caso feliz: dos repostajes a depósito lleno → consumo correcto.
2. Primer repostaje del vehículo → sin consumo.
3. Repostaje parcial intermedio → sus litros se suman al tramo del siguiente lleno; el parcial no muestra consumo propio.
4. Repostaje con `hasMissedEntries = true` en el tramo → tramo inválido.
5. Repostaje con `odometerInconsistent = true` en el tramo → tramo inválido.
6. `km <= 0` → tramo inválido, sin división por cero.
7. Consumo medio del vehículo: ponderado por km, **no** media aritmética de los consumos por tramo. Test con dos tramos de longitud muy distinta que demuestre la diferencia entre ambos cálculos.
8. Rendimiento: 1.000 repostajes procesados en < 100 ms.

### E1-06 · Datos de `FuelEntry` (solo local) — **M**
Equivalente a E1-03 para repostajes, con consultas ordenadas por odómetro y por fecha.

### E1-07 · UI Android: vehículos — **M**
Lista de vehículos, alta/edición, detalle. State holder compartido en `:feature:vehicle:presentation`.

*Criterios de aceptación*
- El state holder vive en `commonMain` y expone `StateFlow<UiState>`.
- Estados de carga, vacío y error contemplados.
- Sin cadenas hardcodeadas; ES/EN completos.
- Test de UI del flujo de alta.

### E1-08 · UI Android: repostajes — **L**
Lista de repostajes de un vehículo con su consumo por tramo, formulario de alta/edición optimizado según F-3, y consumo medio en el detalle del vehículo.

*Criterios de aceptación*
- El formulario aplica los valores por defecto de F-3 (fecha de hoy, odómetro sugerido, depósito lleno activado).
- El campo derivado de R-2 se recalcula en vivo al escribir.
- Los tramos sin consumo muestran "—" con explicación accesible del motivo.
- Estado vacío de consumo con el texto de la spec R-3.

### E1-09 · UI iOS: vehículos y repostajes — **L**
Equivalente SwiftUI consumiendo los mismos state holders compartidos.

*Criterios de aceptación*
- **Cero lógica de negocio duplicada en Swift.** Swift solo pinta y envía eventos.
- Paridad funcional con Android en los flujos F-2 y F-3.

---

## Fase 2 · Autenticación

### E2-01 · `:core:auth` — **S**
`AuthRepository` (interfaz), modelo `AuthSession` (uid, isAnonymous, proveedores) y `AuthError` tipado (cancelado por el usuario, sin red, credencial en uso, etc.).

*Criterios de aceptación:* ningún tipo de Firebase asoma en este módulo.

### E2-02 · `:integration:firebase-auth` — **L**
Implementación real según lo decidido en D-6: anónimo, Google (Android + iOS), Apple (iOS), enlace de credencial, cierre de sesión, eliminación de cuenta, refresco de ID token.

*Criterios de aceptación*
- Login anónimo funcional en ambas plataformas.
- Google funcional en ambas; Apple funcional en iOS.
- Cancelar el diálogo del sistema produce un `AuthError` tipado, no un crash ni un estado colgado.
- La obtención de credencial es nativa; el intercambio por sesión es común.

### E2-03 · Onboarding y flujo F-1 — **M**
Pantalla de bienvenida, elección de proveedor y enrutado posterior según si el usuario ya tiene vehículos.

*Criterios de aceptación*
- iOS ofrece Apple junto a Google (requisito de App Store).
- Reintentar tras un fallo de red no deja la UI bloqueada.
- Tras autenticar sin vehículos → alta de vehículo; con vehículos → lista.

### E2-04 · Conversión de cuenta anónima (F-4) — **M**
Enlace de credencial preservando los datos, con el caso de colisión resuelto.

*Criterios de aceptación*
- Enlace correcto: los vehículos y repostajes creados en anónimo siguen presentes tras convertir la cuenta.
- Colisión: se ofrece elección explícita con recuento de lo que se perderá y confirmación destructiva.
- La fusión automática **no** se implementa (fuera de alcance).

### E2-05 · Cierre de sesión y eliminación de cuenta (F-5) — **M**
*Criterios de aceptación:* cerrar sesión con cambios pendientes avisa y ofrece esperar; eliminar cuenta borra datos remotos y locales tras doble confirmación y está accesible desde Ajustes.

---

## Fase 3 · Backend y sincronización

> La fase de mayor riesgo técnico. Se aborda al final, cuando el dominio ya es estable y está probado.

### E3-01 · Esquema y despliegue de Firebase SQL Connect — **M**
Definir el esquema GraphQL (`vehicles`, `fuel_entries`, `user_settings`) con `serverUpdatedAt` y tombstones, las operaciones (upsert idempotente, delta pull con cursor, paginación) y las reglas de autorización por `ownerId`.

*Criterios de aceptación*
- Un usuario **no** puede leer ni escribir filas de otro. Debe existir un test que lo intente y falle desde el servidor.
- `serverUpdatedAt` lo asigna el servidor, nunca el cliente.
- El delta pull devuelve tombstones.
- Los índices soportan la consulta de delta pull de forma eficiente.

### E3-02 · `:core:remote` + `:integration:firebase-sqlconnect` — **L**
Cliente según lo decidido en D-5, con autenticación por ID token, refresco de token, mapeo de errores de red y contrato `RemoteDataSource`.

*Criterios de aceptación*
- Los errores HTTP y de red se traducen a `AppError` tipado.
- El ID token caducado se refresca de forma transparente y la operación se reintenta una vez.
- Existen tests contra un servidor HTTP simulado.

### E3-03 · `:core:sync` — motor de sincronización — **L** ⭐
Outbox, cursor, push idempotente, delta pull, LWW con desempate por `id`, backoff exponencial y `SyncStatus` observable.

*Criterios de aceptación* — tests obligatorios:
1. Escritura offline → al recuperar red se sincroniza sin intervención del usuario.
2. Reintento tras respuesta ambigua → **no duplica** el registro (idempotencia por `id`).
3. Conflicto de edición en dos dispositivos → ambos convergen al mismo estado.
4. Empate exacto de `updatedAt` → el desempate por `id` es determinista y ambos dispositivos convergen.
5. Tombstone frente a actualización anterior → gana el tombstone.
6. Cursor con hora de servidor: un reloj de dispositivo adelantado 1 h no provoca pérdida de registros en el pull.
7. Primer sync de dispositivo nuevo con 1.000 registros → paginado y correcto.
8. Fallo persistente → estado `FAILED` y acción de reintento manual disponible.

### E3-04 · Cableado de sincronización en los repositorios — **M**
Sustituir los `RemoteDataSource` no-op de la fase 1 por los reales y enganchar los disparadores de sincronización (arranque, reconexión, post-escritura con debounce, pull-to-refresh, tarea periódica).

*Criterios de aceptación:* la UI sigue observando **solo** la base local; ningún cambio en los state holders derivado de esta historia.

### E3-05 · Indicador de estado de sincronización en la UI — **S**
Discreto, no intrusivo (P2): pendiente / sincronizando / error con reintento.

### E3-06 · **Prueba de desacoplamiento del proveedor (7.5)** — **S** ⭐
Verificar el requisito P4 de forma ejecutable.

*Criterios de aceptación:* con los módulos `:integration:*` excluidos del *settings*, todos los módulos `:core:*`, `:feature:*:domain` y `:feature:*:data` compilan y sus tests pasan usando fakes. Esta comprobación corre en CI.

---

## Fase 4 · Cierre del MVP

### E4-01 · Ajustes — **S**
Moneda, idioma (si no se hereda del sistema), sesión, eliminación de cuenta, versión de la app.

### E4-02 · Accesibilidad e i18n — **M**
*Criterios de aceptación:* auditoría con TalkBack y VoiceOver de los flujos F-1 a F-3; la app es usable al 200 % de tamaño de fuente sin recortes; ES/EN completos y revisados.

### E4-03 · Endurecimiento y rendimiento — **M**
*Criterios de aceptación:* se cumplen los objetivos de la sección 9 de la spec, medidos y documentados. Sin fugas de memoria en los flujos principales.

### E4-04 · Preparación para publicación — **M**
Iconos, splash, política de privacidad, fichas de privacidad de ambas tiendas, capturas, firma y builds de release.

*Criterios de aceptación:* build de release instalable en ambas plataformas; checklist de requisitos de tienda completado (incluye eliminación de cuenta en la app y Sign in with Apple en iOS).

---

## Orden de ejecución y paralelismo

```
Fase 0 ─────────────────────────────────► (bloqueante, secuencial)
   │
   ├─ Fase 1 ──► E1-01 ─► E1-02 ─► E1-03 ─┐
   │                └──► E1-04 ─► E1-05 ──┼─► E1-07 ─┐
   │                          └─► E1-06 ──┘   E1-08 ─┼─► E1-09
   │
   ├─ Fase 2 (paralelizable con el final de la fase 1: E2-01/E2-02 no dependen de la UI)
   │
   └─ Fase 3 (E3-01 puede empezar en paralelo a la fase 1; el resto requiere fases 1 y 2)
             │
             └─► Fase 4
```

**Puntos de sincronización humana** — no delegar a un agente sin revisión:

- Cierre de la fase 0 (las reglas de arquitectura condicionan todo lo demás).
- **E1-05** (regla de consumo): es la definición del producto, no un detalle de implementación.
- **E3-01** (autorización en servidor): un error aquí es una brecha de datos.
- **E3-03** (motor de sincronización): un error aquí es pérdida de datos del usuario.
