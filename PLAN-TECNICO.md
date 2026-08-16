# Plan — App de seguimiento de gastos de vehículo (MVP: combustible)

## Contexto

Proyecto greenfield. Partimos de una especificación *rough* que ha sido reescrita en `ESPECIFICACION.md` y troceada en `BACKLOG.md` (ambos ya entregados). Este plan cierra las decisiones técnicas abiertas y define la ruta de implementación.

**Qué cambió durante la planificación, y por qué importa:**

1. **El backend pasa de Firebase SQL Connect a Cloud Firestore.** SQL Connect es Cloud SQL: una instancia PostgreSQL que factura 24/7 con cero usuarios, sin SDK oficial para KMP, y que obligaba a implementar el upsert condicional LWW en SQL nativo. Firestore encaja en el *free tier* para esta escala, tiene wrapper KMP maduro, y sus escrituras son upserts idempotentes por ID con timestamp de servidor — exactamente las dos primitivas que necesita el motor de sincronización.
2. **La base local sigue siendo la fuente de verdad.** Firestore es la réplica remota / copia de seguridad, no la fuente de verdad. La UI nunca observa la red.
3. **1 módulo Gradle por feature**, no 3. Con dos entidades, 18 módulos penalizan el build y sobre todo el linkado del framework iOS, a cambio de un beneficio teórico. Las reglas de capa se verifican con un test de arquitectura.
4. **Room 3.0 KMP** como base local (decisión del usuario; Room 3.0.1 estable desde julio de 2026 con soporte iOS).

**Resultado esperado:** una app Android + iOS con la que registrar repostajes sin conexión y obtener el consumo real en L/100 km, con los datos replicados en la nube y recuperables en otro dispositivo.

---

## Decisiones cerradas

| ID | Decisión | Elección | Razón |
|----|----------|----------|-------|
| D-0 | Backend | **Cloud Firestore** | Coste ≈ 0 a esta escala. Upsert idempotente por ID + `serverTimestamp()` de fábrica. |
| D-1 | DB local | **Room 3.0 KMP** (`androidx.room3`) con `androidx.sqlite:sqlite-bundled` | El SQLite *bundled* garantiza la **misma versión de SQLite en Android e iOS** y habilita la sintaxis `UPSERT` (SQLite ≥ 3.24), que el SQLite del sistema en `minSdk 26` no tiene. |
| D-2 | Interop Kotlin↔Swift | **SKIE**, aplicado solo en `:shared` | Swift export sigue en Alpha y no soporta *sealed classes*. Fijar Kotlin+SKIE juntos y no perseguir releases. |
| D-3 | Inyección de dependencias | **Composition root manual** | ~25 bindings. Koin falla en *runtime*, y el peor modo de fallo posible es un crash en el simulador de iOS para alguien que no sabe depurar iOS. Inyección por constructor en todos los módulos. |
| D-4 | `fuelType` | **En `Vehicle`, en el esquema desde el día 1, sin selector en la UI del MVP** | Añadir un campo a un esquema ya sincronizado con usuarios reales es caro; arrastrar una columna nullable es gratis. |
| D-5 | Acceso a Firestore desde KMP | **`dev.gitlive:firebase-firestore` 2.6.x** tras interfaz propia | La API de Firestore es amplia y con estado; reimplementarla con `expect/actual` no sale a cuenta. *Fallback documentado:* API REST de Firestore desde Ktor en `commonMain`. |
| D-6 | Firebase Auth en KMP | **`dev.gitlive:firebase-auth` 2.6.x** tras interfaz propia | Coherente con D-5. **No usar `3.0.0-alpha01`.** La obtención de credencial Google/Apple es UI nativa en ambos casos. |
| D-7 | Navegación | **Nativa por plataforma** | Navigation Compose / `NavigationStack`. No se comparte nada, ni siquiera el `sealed class Destination`. |
| D-8 | Capa de presentación | **Compartida en KMP** | ViewModels comunes exponiendo un `StateFlow<UiState>` + funciones de intención. Es el mayor retorno de KMP aquí y lo que justifica SKIE. |
| D-9 | Persistencia offline de Firestore | **Desactivada** | Dos cachés con políticas de invalidación distintas es una fuente de bugs. Nuestro *outbox* ya encola las escrituras offline. |

---

## Arquitectura de módulos

```
build-logic/                    convention plugins: kmp.library, kmp.feature,
                                android.application, android.compose, room, skie
gradle/libs.versions.toml       única fuente de versiones

:core:model         KMP  modelos puros, Money (enteros en céntimos), Result
:core:common        KMP  DispatcherProvider, Clock inyectable, Uuid, AppError, backoff
:core:database      KMP  Room 3.0: entidades, DAOs, migraciones, driver expect/actual
:core:auth          KMP  interfaces AuthClient / TokenProvider / AuthState   ← CERO Firebase
:core:sync          KMP  motor: outbox, cursor, push/pull, interfaz RemoteSyncSource
:core:testing       KMP  fakes, RemoteSyncSource en memoria, simulador determinista

:integration:firebase-auth       KMP  implementa :core:auth sobre GitLive + credencial nativa
:integration:firebase-firestore  KMP  implementa RemoteSyncSource sobre GitLive Firestore

:feature:vehicle    KMP  paquetes internos domain / data / presentation
:feature:fuel       KMP  ídem
:feature:session    KMP  ídem (onboarding, login, conversión de cuenta, ajustes)

:shared             KMP  framework iOS (SKIE aquí). Expone createAppGraph(remote, auth)
:wiring:firebase    KMP  ÚNICO módulo que nombra :integration:*
:androidApp         Android  Compose, Navigation, ensamblado
iosApp/             Xcode/SwiftUI, consume Shared.framework vía SPM local
firestore/          firestore.rules + firestore.indexes.json (no es módulo Gradle)
```

### Reglas de dependencia (verificadas por test de arquitectura en CI)

| Módulo | Puede depender de | Prohibido |
|--------|-------------------|-----------|
| `:core:model`, `:core:common` | nada | todo |
| `:feature:*` | `:core:model`, `:core:common`, `:core:database`, `:core:sync` | **otro `:feature:*`**, `:integration:*` |
| paquete `domain` de un feature | solo `:core:model`, `:core:common` | Room, Ktor, Firebase, Android, `data`, `presentation` |
| paquete `presentation` | `domain` de su feature | el `data` de su propio feature |
| `:core:sync` | `:core:database`, `:core:auth`, `:core:common` | **cualquier `:integration:*`** |
| `:integration:*` | interfaces de `:core:*` | features |
| `:shared` | `:core:*`, `:feature:*` | **`:integration:*`** |
| `:wiring:firebase` | `:integration:*`, `:shared` | — |

### Cómo se cumple el requisito de desacoplamiento

`:shared` expone `fun createAppGraph(remote: RemoteSyncSource, auth: AuthClient): AppGraph`. Solo `:wiring:firebase` construye las implementaciones de Firebase.

> **Corrección al enunciado original de la spec.** "Borrar `:integration:*` y que todo lo demás compile" no es literalmente alcanzable: algo tiene que instanciar las implementaciones. El criterio correcto y verificable es: **borrar `:integration:*` + `:wiring:firebase` deja compilando y en verde todo lo demás**, usando el wiring local-only de `:core:testing` — que hay que escribir de todos modos porque es el doble de prueba.

---

## Modelo de datos local (Room 3.0)

Columnas de control de sincronización, idénticas en toda entidad sincronizable:

| Columna | Semántica |
|---------|-----------|
| `id` TEXT PK | **UUID generado en cliente**. Habilita la creación offline y la idempotencia del upsert. |
| `ownerId` TEXT | uid de Firebase. |
| `updatedAt` INTEGER | epoch ms del reloj local. **Provisional**: solo ordena el outbox. |
| `serverUpdatedAt` INTEGER? | Timestamp **autoritativo** del servidor. `NULL` = nunca sincronizado. |
| `deleted` INTEGER | Tombstone. Nunca se borra físicamente. |
| `syncState` TEXT | `PENDING` \| `SYNCED` \| `FAILED`. Solo local, nunca se envía. |
| `localRevision` INTEGER | Se incrementa en cada edición local. Detecta ediciones ocurridas durante un push en vuelo. |

**Tablas:** `vehicle`, `fuel_entry`, `outbox`, `sync_cursor`.

```sql
-- outbox: una fila por entidad tocada, no una por operación
CREATE TABLE outbox (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  entityType TEXT NOT NULL,
  entityId TEXT NOT NULL,
  payload TEXT NOT NULL,          -- SNAPSHOT COMPLETO de la fila, no un delta
  localRevision INTEGER NOT NULL,
  attemptCount INTEGER NOT NULL DEFAULT 0,
  nextAttemptAt INTEGER NOT NULL DEFAULT 0,
  lastError TEXT,
  UNIQUE(entityType, entityId)    -- coalescing
);

CREATE TABLE sync_cursor (
  entityType TEXT NOT NULL PRIMARY KEY,
  lastServerUpdatedAt INTEGER NOT NULL DEFAULT 0
);
```

**Dos decisiones que gobiernan todo el motor:**

1. **Sincronización basada en estado, no en operaciones.** El outbox guarda el *snapshot completo* de la fila. Aplicar el mismo snapshot dos veces da el mismo resultado ⇒ **idempotencia por construcción**, sin tabla de deduplicación.
2. **`UNIQUE(entityType, entityId)` con `ON CONFLICT DO UPDATE` conservando el `seq` original.** El outbox nunca crece más allá del número de entidades tocadas, y se preserva el orden causal.

**Dinero:** `totalCostMinor` como `INTEGER` (céntimos) + `currency`. **Nunca `Float`/`Double` para importes.**

---

## Firestore

### Estructura

```
users/{uid}/vehicles/{vehicleId}
users/{uid}/fuelEntries/{entryId}
users/{uid}/meta/settings
```

Subcolecciones bajo el documento de usuario: las reglas de seguridad quedan triviales y toda consulta está ya acotada al propietario por la propia ruta.

### Reglas de seguridad

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    match /users/{uid}/{document=**} {
      allow read: if request.auth != null && request.auth.uid == uid;
      allow write: if request.auth != null
                   && request.auth.uid == uid
                   && request.resource.data.updatedAt == request.time;  // fuerza serverTimestamp
    }
  }
}
```

Tres puntos críticos:

- `request.auth != null` **incluye a los usuarios anónimos**, que son el flujo principal del MVP. (El equivalente en SQL Connect, `@auth(level: USER)`, los habría excluido silenciosamente — trampa evitada al cambiar de backend.)
- `request.resource.data.updatedAt == request.time` **impide que el cliente escriba su propio timestamp**. Un dispositivo con la fecha mal no puede escribir `updatedAt` en 2099 y ganar todos los LWW para siempre.
- Se deben escribir **tests de reglas** (emulador de Firestore) que verifiquen que el usuario A no puede leer ni escribir bajo `users/B`.

### Índices

La consulta de *delta pull* es `orderBy(updatedAt).where(updatedAt > cursor).limit(n)` dentro de una subcolección: el índice de campo único que Firestore crea automáticamente basta. `firestore.indexes.json` queda prácticamente vacío en el MVP.

---

## Motor de sincronización (`:core:sync`)

Vive **entero en `commonMain`, sin una sola API de plataforma**. Es 100 % testeable en `commonTest` con un `RemoteSyncSource` en memoria: la parte más peligrosa del proyecto es también la más barata de verificar, y no necesita ni red ni iOS.

### PUSH

```
1. SELECT outbox WHERE nextAttemptAt <= now ORDER BY seq LIMIT 50
2. Ordenar por dependencia: TODOS los vehicle antes que cualquier fuel_entry
3. Por item:
   a. Obtener ID token (refresco si caduca en < 60 s)
   b. doc(users/{uid}/{col}/{id}).set(snapshot + updatedAt: serverTimestamp(), merge = false)
   c. Releer el documento para conocer el updatedAt autoritativo asignado
4. Éxito, EN UNA TRANSACCIÓN LOCAL:
   - si outbox.localRevision == entidad.localRevision:
        borrar fila de outbox; syncState = SYNCED
        serverUpdatedAt = updatedAt = respuesta.updatedAt
   - si NO (el usuario editó mientras el push estaba en vuelo):
        CONSERVAR la fila de outbox (ya coalescida con la edición nueva)
        actualizar solo serverUpdatedAt  → se reenviará con la base correcta
5. Fallos:
   - PERMISSION_DENIED / token caducado → refrescar token, 1 reintento, luego backoff
   - error de validación                → marcar poisoned, mostrar al usuario, NO reintentar en bucle
   - red / indisponible                 → attemptCount++, backoff exponencial con jitter
                                          (1 s, 2 s, 4 s … tope 15 min)
```

**Idempotencia:** la escritura es un `set` cuyo ID lo generó el cliente. Si la respuesta se pierde y se reintenta, se reescribe el mismo valor. No hace falta clave de idempotencia ni *exactly-once*.

### PULL

```
1. cursor = sync_cursor[entityType]
2. since = max(0, cursor - VENTANA_SOLAPE)        // VENTANA_SOLAPE = 30 s
3. collection(users/{uid}/{col})
     .where(updatedAt > since).orderBy(updatedAt).limit(200)
   Incluye tombstones (deleted = true): son documentos normales, no borrados.
4. Aplicar la página EN UNA SOLA TRANSACCIÓN LOCAL. Para cada documento R:
   - si existe entrada de outbox para R.id → NO tocar las columnas de datos;
     actualizar solo serverUpdatedAt. El cambio local pendiente se reenvía y el servidor arbitra.
   - si no hay pendiente → aplicar R si (R.updatedAt, R.id) > (local.serverUpdatedAt, local.id)
   - R.deleted = true → marcar tombstone local, no borrar la fila
5. Avanzar cursor al updatedAt del último documento de la página
6. Repetir mientras la página venga llena
```

**Por qué la ventana de solape es obligatoria:** es el bug clásico de todo *delta sync* por timestamp. Si un documento se confirma con un timestamp anterior a un cursor ya avanzado, **se pierde para siempre y en silencio**. Como el *apply* es idempotente, refetchear documentos no cuesta nada más que unas lecturas.

**Retención de tombstones:** purga a 90 días. Un cliente offline más de 90 días debe hacer resync completo (se detecta comparando su cursor con la antigüedad de la purga).

### Convergencia

1. Toda mutación local produce **exactamente un** snapshot pendiente por entidad (coalescing).
2. El push reintenta hasta éxito o *poison* ⇒ todo cambio local acaba llegando.
3. El servidor sella `updatedAt`; el orden total `(updatedAt, id)` convierte el LWW en un *join-semilattice* ⇒ converge sea cual sea el orden de llegada. El desempate por `id` elimina el no-determinismo en empates.
4. Solape + *apply* idempotente ⇒ ninguna fila se pierde.
5. El *apply* local usa la misma comparación LWW ⇒ vaciado el outbox, local ≡ remoto.

> **Limitación aceptada conscientemente:** LWW es a nivel de **documento entero**, no de campo. Si un dispositivo edita el precio y otro el odómetro a la vez, uno de los dos cambios se pierde. Aceptable para un usuario con 1-2 dispositivos. Va documentado; no se descubre en producción.

### Disparadores

`SyncScheduler` como `expect/actual`: app a *foreground*, tras mutación local (debounce 2 s), recuperación de conectividad, *pull-to-refresh*. En segundo plano: WorkManager (Android) / BGTaskScheduler (iOS). **El motor no conoce ninguna de estas APIs.**

---

## Fases de implementación

Corresponden al `BACKLOG.md` entregado, con las historias de la fase 3 reescritas para Firestore.

### Fase 0 · Fundamentos *(bloqueante)*
Bootstrap KMP, convention plugins, `:core:model` / `:core:common` / `:core:testing`, test de arquitectura, ktlint + detekt, **CI con runner macOS desde el primer PR**, y los ADR de D-0 a D-9 en `docs/adr/`.

### Fase 0.5 · Walking skeleton *(antes de cualquier feature)*
Una pantalla que atraviesa las tres capas en **ambas plataformas**: SwiftUI → ViewModel compartido → Room → Firestore, con login anónimo real. Si esto no funciona, nada de lo demás importa. Valida de golpe: framework iOS + SKIE, Room 3.0 en `iosSimulatorArm64`, GitLive Firestore y las reglas de seguridad.

### Fase 1 · Persistencia local *(la app ya es útil al final)*
`:core:database`, dominio y datos de `Vehicle` y `FuelEntry`, **el cálculo de consumo R-3**, UI Compose y UI SwiftUI. Sin red todavía: los `RemoteSyncSource` son *no-op*.

### Fase 2 · Autenticación
`:core:auth`, `:integration:firebase-auth` (anónimo, Google en ambas, Apple en iOS), onboarding F-1, conversión de cuenta anónima F-4 con el caso `credential-already-in-use`, cierre de sesión y eliminación de cuenta F-5.

### Fase 3 · Sincronización
Reglas de Firestore + sus tests con emulador, `:integration:firebase-firestore`, **`:core:sync` con simulación determinista**, cableado en los repositorios, indicador de estado en la UI, y la prueba ejecutable de desacoplamiento del proveedor.

### Fase 4 · Cierre
Ajustes, accesibilidad e i18n (ES/EN), rendimiento, preparación para publicación.

---

## Los tres riesgos y sus mitigaciones

| Riesgo | Prob. / Impacto | Mitigación |
|--------|-----------------|------------|
| **La cadena de herramientas de iOS**, no la arquitectura. Xcode, firma, integración del framework, y un desarrollador que no sabe leer un stack trace de Swift. | Alta / Alto | Walking skeleton en la primera semana. **CI con runner macOS desde el día 1** (un build de iOS que solo se lanza a mano se rompe y no te enteras en dos semanas). Integración SPM directa, nunca CocoaPods. Fijar Kotlin + SKIE + Xcode y no actualizar durante el MVP. Presupuestar 2-3 días de un desarrollador Swift para el cableado de SwiftUI. |
| **Bugs de convergencia en el motor de sync.** La pérdida silenciosa de datos no lanza excepción ni aparece en Crashlytics: el usuario descubre que le faltan tres repostajes. | Alta / Crítico | Motor entero en `commonMain`, testeado en `commonTest` con transporte en memoria. **Simulación determinista**: interleavings aleatorios con semilla fija de (edición local, push, pull, fallo de red, entrega duplicada, respuesta perdida), *asertando* que dos clientes simulados convergen. Nunca borrar del outbox antes de confirmar. **Pantalla de debug** con outbox, cursores y `syncState` por fila. |
| **Room 3.0 en iOS tiene tres semanas de rodaje.** KSP en la ruta native es el punto de fricción probable. | Media / Medio | Se valida en el walking skeleton, antes de escribir una sola feature. La DB vive tras interfaces de repositorio en un único módulo (`:core:database`), así que **migrar a SQLDelight es un cambio local de un día**. Criterio de decisión explícito: si en la primera semana hay fricción de KSP/native, se cambia sin debate. |

---

## Verificación

**Automatizada, en cada PR:**

1. `./gradlew build` — compila Android, `iosSimulatorArm64` y el framework de `:shared`.
2. **Test de arquitectura** — introducir a mano una dependencia de un feature sobre otro, o de un `domain` sobre Room, debe **fallar el build** con un mensaje que nombre la regla violada.
3. **Prueba de desacoplamiento** — con `:integration:*` y `:wiring:firebase` excluidos del *settings*, todo lo demás compila y sus tests pasan con los fakes de `:core:testing`.
4. **Tests del cálculo de consumo** — los 8 casos de E1-05 del backlog, incluido el que demuestra que el consumo medio es ponderado por km y no la media aritmética de los tramos.
5. **Tests de convergencia del sync** — los 8 escenarios de E3-03, más la simulación determinista.
6. **Tests de reglas de Firestore** contra el emulador: el usuario A no puede leer ni escribir bajo `users/B`; una escritura con `updatedAt` distinto de `request.time` es rechazada.
7. Cobertura de los paquetes `domain` y ktlint/detekt limpios.

**Manual, al cerrar cada fase:**

- **Offline extremo:** modo avión, crear vehículo y tres repostajes, cerrar la app, reabrirla (todo sigue ahí), restaurar la red y comprobar que aparece en la consola de Firebase sin intervención.
- **Dos dispositivos:** editar el mismo repostaje en ambos con red intermitente y verificar que convergen al mismo estado.
- **Conversión de cuenta:** crear datos en anónimo, convertir a cuenta Google, verificar que no se pierde nada. Repetir con una credencial ya en uso y comprobar que el diálogo de colisión es claro y no destruye datos por accidente.
- **Reloj adelantado:** poner el dispositivo una hora en el futuro y comprobar que no gana todos los conflictos ni pierde registros en el pull.
- **Accesibilidad:** TalkBack y VoiceOver sobre los flujos F-1 a F-3; usable al 200 % de tamaño de fuente.

---

## Fuera de alcance de este plan

Reparaciones, seguros y otros gastos; gráficas y estadísticas avanzadas; exportación y fotos de tickets; recordatorios; compartir vehículo entre cuentas; App Check (post-MVP); fusión automática de cuentas en la colisión de credenciales; *listeners* en tiempo real de Firestore (optimización posterior al *pull* por cursor).
