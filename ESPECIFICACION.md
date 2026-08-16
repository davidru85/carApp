# Especificación — App de seguimiento de gastos de vehículo

> Versión 1.1 · Documento de especificación funcional y técnica del MVP
> Estado: **decisiones técnicas cerradas · listo para implementación**
>
> Cambio principal en la v1.1: el backend pasa de Firebase SQL Connect a **Cloud Firestore**. Motivo en 7.6.

---

## 1. Visión y objetivo

Aplicación móvil multiplataforma (Android + iOS) que permite a una persona llevar el control de los gastos asociados a sus vehículos.

El **MVP se limita al gasto en combustible**: registrar repostajes, consultarlos y conocer el consumo real del vehículo en L/100 km. Fases posteriores añadirán reparaciones, seguros, impuestos y otros gastos.

**Métrica de éxito del MVP:** un usuario puede registrar un vehículo y sus repostajes sin conexión a internet, y obtener un consumo medio fiable a partir del tercer repostaje a depósito lleno.

### 1.1 Principios de producto

| # | Principio | Implicación |
|---|-----------|-------------|
| P1 | **Fricción mínima en el registro** | Registrar un repostaje debe ser posible en menos de 15 segundos y con el mínimo de campos obligatorios. |
| P2 | **Funciona siempre** | Las gasolineras suelen tener mala cobertura. La app es 100 % funcional sin red. |
| P3 | **Sin barreras de entrada** | Se puede usar sin crear cuenta (login anónimo) y convertir a cuenta permanente sin perder datos. |
| P4 | **Portabilidad del proveedor cloud** | Ninguna decisión de Firebase debe filtrarse fuera de la capa `data`. |

---

## 2. Alcance

### 2.1 Dentro del MVP

- Onboarding y autenticación (anónima, Google, Apple).
- Conversión de cuenta anónima a cuenta permanente sin pérdida de datos.
- Gestión de vehículos: crear, listar, editar, eliminar (1..N vehículos).
- Gestión de repostajes: crear, listar, editar, eliminar.
- Cálculo de consumo (L/100 km) por repostaje y consumo medio del vehículo.
- Persistencia local como fuente de verdad + sincronización offline-first con backend.
- Ajustes mínimos: unidades, moneda, cerrar sesión, eliminar cuenta.

### 2.2 Fuera del MVP (fases posteriores)

- Otros tipos de gasto (reparaciones, seguros, ITV, impuestos, peajes, parking).
- Gráficas y estadísticas avanzadas (evolución de precio, comparativas entre vehículos).
- Exportación (CSV/PDF), adjuntar fotos de tickets, OCR de tickets.
- Recordatorios y notificaciones (revisiones, cambio de aceite).
- Compartir un vehículo entre varias cuentas.
- Widgets, Wear OS, watchOS, versión web.
- Integración con precios oficiales de carburante.

> **Regla para agentes:** cualquier tarea que toque un punto de 2.2 debe rechazarse o escalarse. El MVP no se amplía sin actualizar este documento.

---

## 3. Actores y modelo de usuario

| Actor | Descripción |
|-------|-------------|
| **Usuario anónimo** | Ha entrado sin cuenta. Sus datos existen en local y se sincronizan contra una identidad anónima del backend. Riesgo asumido: si desinstala sin convertir la cuenta, pierde los datos. |
| **Usuario autenticado** | Ha iniciado sesión con Google (Android/iOS) o Apple (iOS). Sus datos son recuperables en otro dispositivo. |

Un usuario tiene N vehículos. Un vehículo pertenece a exactamente un usuario (sin compartición en el MVP).

---

## 4. Modelo de dominio

### 4.1 `Vehicle`

| Campo | Tipo | Oblig. | Reglas |
|-------|------|--------|--------|
| `id` | UUID (String) | Sí | Generado **en cliente** (v4). Nunca lo asigna el servidor. |
| `ownerId` | String | Sí | UID del usuario propietario. |
| `name` | String | Sí | 1..40 caracteres, no vacío tras `trim`. Único por usuario (case-insensitive). |
| `initialOdometer` | Long | Sí | Kilometraje en el momento del alta. 0..2_000_000. |
| `currentOdometer` | Long | Sí | Derivado: `max(initialOdometer, odómetro del último repostaje)`. |
| `brand` | String? | No | 0..40 caracteres. |
| `model` | String? | No | 0..40 caracteres. |
| `fuelType` | Enum | Sí | `GASOLINE`, `DIESEL`, `LPG`, `CNG`, `ELECTRIC`, `HYBRID`, `OTHER`. Por defecto `GASOLINE`. **Presente en el esquema desde el día 1, sin selector en la UI del MVP** (D-4): añadir un campo a un esquema ya sincronizado con usuarios reales es caro; arrastrar una columna es gratis. |
| `createdAt` | Instant | Sí | UTC. |
| `updatedAt` | Instant | Sí | UTC. Usado en la resolución de conflictos. |
| `deletedAt` | Instant? | No | Borrado lógico (tombstone). |
| `syncState` | Enum | Sí | `PENDING`, `SYNCED`, `FAILED`. Solo local, nunca se envía. |

> `initialOdometer` sustituye al "kilometraje actual" de la spec original. El campo original era ambiguo (¿se actualiza con cada repostaje?). Se separa el dato inmutable de alta (`initialOdometer`) del dato derivado (`currentOdometer`).

### 4.2 `FuelEntry` (repostaje)

| Campo | Tipo | Oblig. | Reglas |
|-------|------|--------|--------|
| `id` | UUID (String) | Sí | Generado en cliente. |
| `vehicleId` | UUID (String) | Sí | FK a `Vehicle`. |
| `date` | Instant | Sí | Por defecto `now`. No puede ser futura (margen de 1 h por desfase de reloj). |
| `odometer` | Long | Sí | Kilometraje en el momento del repostaje. Ver R-1. |
| `liters` | Decimal(7,3) | Sí | > 0 y ≤ 500. |
| `pricePerLiter` | Decimal(6,3) | Sí* | > 0. Ver R-2. |
| `totalCostMinor` | Long | Sí* | Importe en **unidades menores** (céntimos). > 0. Ver R-2. Nunca `Float`/`Double`. |
| `currency` | ISO-4217 | Sí | Heredada de ajustes; por defecto `EUR`. |
| `isFullTank` | Boolean | Sí | Por defecto `true`. Clave para el cálculo de consumo. |
| `hasMissedEntries` | Boolean | Sí | Por defecto `false`. El usuario marca que hubo repostajes no registrados antes de este. Invalida el tramo. |
| `notes` | String? | No | 0..280 caracteres. |
| `createdAt` / `updatedAt` / `deletedAt` / `syncState` | — | — | Igual que en `Vehicle`. |

### 4.3 `UserSettings`

`currency` (por defecto según `Locale`), `distanceUnit` (`KM` fijo en MVP, `MILES` preparado), `volumeUnit` (`LITER` fijo en MVP, `GALLON` preparado).

---

## 5. Reglas de negocio

### R-1 · Coherencia del odómetro

- El odómetro de un repostaje debe ser **estrictamente mayor** que el del repostaje anterior (por fecha) del mismo vehículo, y **mayor o igual** que `initialOdometer`.
- Si el usuario introduce un valor incoherente, la app lo **avisa pero permite guardarlo** marcando el registro como `odometerInconsistent`. Un tramo con un registro inconsistente no produce consumo.
  - *Justificación:* bloquear el guardado en una gasolinera con prisa viola P1. Es mejor guardar y avisar.
- Al guardar un repostaje, `vehicle.currentOdometer = max(currentOdometer, entry.odometer)`.

### R-2 · Precio y coste total

De los tres valores `liters`, `pricePerLiter` y `totalCostMinor`, el usuario introduce **dos cualesquiera** y el tercero se calcula automáticamente:

- `totalCostMinor = redondear(liters × pricePerLiter × 100)`
- `pricePerLiter = totalCostMinor / 100 / liters`
- `liters = totalCostMinor / 100 / pricePerLiter`

`liters` es siempre obligatorio (es necesario para el consumo). Redondeo: `pricePerLiter` a 3 decimales, `liters` a 3.

**Los importes se almacenan y se operan como enteros en unidades menores (céntimos), nunca como `Float`/`Double`.** El redondeo se aplica una sola vez, al convertir a entero. La división para derivar `pricePerLiter` o `liters` sí produce un decimal: se redondea al almacenar y **nunca se reutiliza el valor redondeado para recalcular otro de los tres campos**, para no acumular error.

### R-3 · Cálculo de consumo (método *full-to-full*)

Es la regla más importante del MVP. Definición formal:

Sea `E` un repostaje con `isFullTank = true`, y sea `P` el repostaje anterior más reciente con `isFullTank = true` del mismo vehículo.

```
tramo   = { repostajes X del vehículo : P.odometer < X.odometer <= E.odometer }
litros  = Σ X.liters  para X ∈ tramo        (incluye repostajes parciales del tramo)
km      = E.odometer - P.odometer
consumo = litros / km × 100                  → L/100 km
```

Un tramo **no produce consumo** (se muestra "—") si:

1. No existe `P` (es el primer repostaje a depósito lleno del vehículo).
2. `E.isFullTank = false` (los repostajes parciales no producen consumo propio; aportan litros al siguiente tramo lleno).
3. Algún repostaje del tramo tiene `hasMissedEntries = true`.
4. Algún repostaje del tramo tiene `odometerInconsistent = true`.
5. `km <= 0`.

**Consumo medio del vehículo:** `Σ litros de todos los tramos válidos / Σ km de todos los tramos válidos × 100`. **No** es la media aritmética de los consumos por tramo (sería estadísticamente incorrecto al ponderar igual tramos de distinta longitud).

**Presentación:** consumo redondeado a 2 decimales. Si no hay ningún tramo válido, se muestra un estado vacío explicativo ("Registra dos repostajes a depósito lleno para ver tu consumo").

> Esta regla debe implementarse en un *use case* puro en `commonMain`, sin dependencias de framework, y estar cubierta por tests unitarios con al menos los 5 casos de exclusión anteriores más el caso feliz y el caso con repostaje parcial intermedio.

### R-4 · Borrado

- Todo borrado es **lógico** (`deletedAt`), nunca físico, para poder propagar la baja en la sincronización.
- Borrar un vehículo marca en cascada sus repostajes como borrados.
- La purga física de tombstones locales confirmados como sincronizados se hace tras 90 días.

---

## 6. Flujos funcionales

### F-1 · Primer arranque y autenticación

1. Pantalla de bienvenida con dos acciones: **"Iniciar sesión"** y **"Continuar sin cuenta"**.
2. *Continuar sin cuenta* → login anónimo en el proveedor de auth. Se persiste el UID local.
3. *Iniciar sesión* → proveedores disponibles según plataforma:
   - **Android:** Google.
   - **iOS:** Google y Apple.
   - *Nota de cumplimiento:* App Store exige ofrecer "Sign in with Apple" en iOS si se ofrecen otros logins sociales. Es un requisito de publicación, no una opción.
4. Tras autenticarse, si el usuario no tiene ningún vehículo → flujo F-2. Si tiene → lista de vehículos.

### F-2 · Alta del primer vehículo

Formulario con `name` e `initialOdometer` obligatorios y `brand`, `model`, `fuelType` opcionales. No se puede saltar: la app necesita al menos un vehículo para ser útil. Tras guardar → detalle del vehículo con estado vacío invitando a registrar el primer repostaje.

### F-3 · Registrar repostaje

Formulario optimizado para velocidad (P1): fecha prerellenada a hoy, odómetro prerellenado con `currentOdometer` como sugerencia editable, `isFullTank` activado por defecto, moneda de ajustes. El usuario introduce litros y (precio/L o total). Guardar es una escritura **local e inmediata**; la sincronización es asíncrona y transparente.

### F-4 · Conversión de cuenta anónima → permanente

1. Desde Ajustes, "Crear cuenta / iniciar sesión".
2. Se enlaza la credencial (Google/Apple) a la identidad anónima existente → **los datos se conservan**.
3. **Caso de colisión:** si la credencial ya pertenece a otra cuenta existente, se presenta al usuario una elección explícita:
   - *Entrar en mi cuenta existente y descartar los datos de esta sesión anónima* (con confirmación destructiva y recuento de lo que se perderá), o
   - *Cancelar*.
   - La fusión automática de datos de dos cuentas queda **fuera del MVP**.

### F-5 · Cierre de sesión y eliminación de cuenta

- **Cerrar sesión:** advierte si hay cambios pendientes de sincronizar y ofrece esperar. Limpia la base local.
- **Eliminar cuenta:** requerido por las políticas de ambas tiendas. Borra datos remotos y locales previa confirmación con doble paso.

---

## 7. Arquitectura técnica

### 7.1 Stack

| Capa | Tecnología |
|------|------------|
| Lógica compartida | **Kotlin Multiplatform** (`commonMain`, `androidMain`, `iosMain`) |
| UI Android | **Jetpack Compose** (nativa, no Compose Multiplatform) |
| UI iOS | **SwiftUI** (nativa) |
| Build | **Gradle con Kotlin DSL** + version catalog (`libs.versions.toml`) + convention plugins en `build-logic` |
| Base de datos local | **Room 3.0 KMP** (`androidx.room3`) con `androidx.sqlite:sqlite-bundled` |
| Backend | **Cloud Firestore** (réplica remota; **no** es la fuente de verdad) |
| Autenticación | **Firebase Authentication** vía `dev.gitlive:firebase-auth` 2.6.x |
| Asincronía | Coroutines + Flow |
| Inyección de dependencias | **Composition root manual**, inyección por constructor |
| Interop iOS | **SKIE**, aplicado solo en `:shared` |
| Serialización | `kotlinx.serialization` |
| Fechas | `kotlinx-datetime` |

### 7.2 Estilo arquitectónico

**Modular Clean Architecture + modularización vertical por funcionalidad.** Cada feature es una porción vertical. Sus tres capas viven en **paquetes internos de un único módulo Gradle**, no en tres módulos: con dos entidades, tres módulos por feature dan ~18 módulos cuyo coste (tiempo de build y, sobre todo, linkado del framework de iOS) se paga cada día a cambio de un beneficio teórico. Las reglas de capa se garantizan con un test de arquitectura, no con la frontera de módulo. Los convention plugins se dejan escritos para que partirlos sea trivial el día que haya una razón real.

```
build-logic/                     ← convention plugins
gradle/libs.versions.toml        ← única fuente de versiones

:core:model                      ← modelos puros, Money (enteros en unidades menores), Result
:core:common                     ← DispatcherProvider, Clock inyectable, Uuid, AppError, backoff
:core:database                   ← Room 3.0: entidades, DAOs, migraciones, driver expect/actual
:core:auth                       ← interfaces AuthClient / TokenProvider / AuthState
:core:sync                       ← motor: outbox, cursor, push/pull, interfaz RemoteSyncSource
:core:testing                    ← fakes, RemoteSyncSource en memoria, simulador determinista

:integration:firebase-auth       ← única implementación que conoce Firebase Auth
:integration:firebase-firestore  ← única implementación que conoce Firestore

:feature:vehicle                 ← paquetes internos domain / data / presentation
:feature:fuel                    ← ídem
:feature:session                 ← ídem (onboarding, login, conversión de cuenta, ajustes)

:shared                          ← framework iOS (SKIE aquí). Expone createAppGraph(remote, auth)
:wiring:firebase                 ← ÚNICO módulo que nombra :integration:*
:androidApp                      ← Compose, Navigation, ensamblado
iosApp/                          ← Xcode/SwiftUI, consume Shared.framework vía SPM local
firestore/                       ← firestore.rules + firestore.indexes.json
```

### 7.3 Reglas de dependencia (invariantes, verificables en CI)

1. El paquete `domain` de un feature es **Kotlin puro**: no depende de Android, iOS, Firebase, Room ni de ningún otro feature. Solo de `:core:model` y `:core:common`.
2. El paquete `data` depende de `domain`, `:core:database` y `:core:sync`. **Nunca** de `:integration:*`.
3. El paquete `presentation` depende de `domain`. Nunca de `data`.
4. Un feature **no depende de otro feature**. Si necesita algo de otro, ese contrato sube a `:core`.
5. `:core:sync` no depende de **ningún** `:integration:*`.
6. `:shared` no depende de `:integration:*`. Solo `:wiring:firebase` los nombra.
7. **Ningún tipo de Firebase cruza la frontera de `data`.** Los mappers viven en `:integration:*`.

> Estas reglas deben validarse automáticamente (test de arquitectura o comprobación de grafo de dependencias en CI). Un agente que introduzca una dependencia prohibida debe fallar el build, no depender de la revisión humana.

### 7.4 Capa de presentación compartida

La lógica de presentación (estado de UI, validación, orquestación de casos de uso) se comparte en `commonMain` mediante *state holders* que exponen `StateFlow<UiState>` y aceptan eventos. Android los consume como `ViewModel`; iOS los envuelve en un `ObservableObject` de SwiftUI. **Este es el mayor retorno de KMP en el proyecto**, y lo que justifica SKIE.

Regla para el lado iOS: **cero lógica de negocio en Swift.** SwiftUI pinta y envía intenciones. La superficie que `:shared` expone a Swift debe ser **pequeña y plana** (un `StateFlow<UiState>` por pantalla + funciones de intención), evitando genéricos y jerarquías `sealed` anidadas: así, si SKIE dejara de mantenerse, sustituirlo por wrappers manuales es un trabajo de días, no un rediseño.

### 7.5 Desacoplamiento del proveedor cloud (requisito P4)

```
domain:        VehicleRepository (interfaz)
                      ▲
data:          VehicleRepositoryImpl ──► VehicleLocalDataSource (interfaz, Room)
                                    └──► RemoteSyncSource       (interfaz, :core:sync)
                                                   ▲
integration:                        FirestoreRemoteSyncSource
```

`:shared` expone `fun createAppGraph(remote: RemoteSyncSource, auth: AuthClient): AppGraph`. Solo `:wiring:firebase` construye las implementaciones de Firebase.

**Corrección al enunciado original:** "borrar `:integration:*` y que todo lo demás compile" no es literalmente alcanzable — algo tiene que instanciar las implementaciones. El criterio correcto y verificable es: **borrar `:integration:*` + `:wiring:firebase` deja compilando y en verde todo lo demás**, usando el wiring local-only de `:core:testing`, que hay que escribir de todos modos porque es el doble de prueba.

### 7.6 Por qué Cloud Firestore y no Firebase SQL Connect

La especificación original apuntaba a "SQL Connect en Firebase", identificado como **Firebase SQL Connect** (antes Firebase Data Connect): PostgreSQL gestionado sobre Cloud SQL. Se descartó por tres razones:

1. **Coste fijo.** Cloud SQL es una instancia que corre 24/7 y factura aunque la app tenga cero usuarios. Firestore, con este modelo de datos (documentos por usuario, sin joins), cabe holgadamente en el *free tier*.
2. **Sin SDK oficial para KMP**, y sus SDKs generados son por plataforma (Kotlin para Android, Swift para iOS), lo que obligaba a mantener dos implementaciones o a depender de un contrato HTTP no documentado como API pública de cliente.
3. **Las primitivas que necesita el motor de sincronización las da Firestore de fábrica:** escritura idempotente por ID generado en cliente y `serverTimestamp()` autoritativo. En SQL Connect había que expresarlas con un `INSERT ... ON CONFLICT ... WHERE` en SQL nativo.

Firestore se usa **exclusivamente como réplica remota**, no como fuente de verdad: la persistencia offline propia de Firestore queda **desactivada** para no tener dos cachés con políticas de invalidación distintas. El *outbox* propio ya encola las escrituras offline.

**Acceso desde KMP:** no existe SDK oficial de Firestore para KMP. Se usa el wrapper community `dev.gitlive:firebase-firestore` 2.6.x, siempre **detrás de la interfaz `RemoteSyncSource`**. *Fallback documentado:* la API REST de Firestore desde Ktor en `commonMain`, que solo afectaría a `:integration:firebase-firestore`.

**Firebase Auth:** la obtención de la credencial de Google/Apple es inherentemente nativa (requiere UI del sistema). El patrón es: **la plataforma obtiene el token de la credencial → el módulo común lo intercambia por una sesión**, usando `dev.gitlive:firebase-auth` 2.6.x tras la interfaz `AuthClient`. **No usar `3.0.0-alpha01`.**

---

## 8. Sincronización offline-first

### 8.1 Principios

1. **La base de datos local es la única fuente de verdad para la UI.** La UI nunca observa la red; observa la base local vía `Flow`.
2. Toda escritura es local, síncrona y confirmada al usuario de inmediato; se encola para envío.
3. La sincronización es un proceso de fondo que puede fallar sin degradar la experiencia.
4. Los identificadores se generan en cliente (UUID v4), de modo que un registro creado offline ya tiene su identidad definitiva.

### 8.2 Dos decisiones que gobiernan todo el motor

1. **Sincronización basada en estado, no en operaciones.** El *outbox* guarda el **snapshot completo** de la fila, no un delta. Aplicar el mismo snapshot dos veces produce el mismo resultado ⇒ **idempotencia por construcción**, sin tabla de deduplicación.
2. **El outbox tiene `UNIQUE(entityType, entityId)`** con `ON CONFLICT DO UPDATE` que conserva el `seq` original. El outbox nunca crece más allá del número de entidades tocadas, y se preserva el orden causal.

Tablas de control: `outbox` (`seq`, `entityType`, `entityId`, `payload`, `localRevision`, `attemptCount`, `nextAttemptAt`, `lastError`) y `sync_cursor` (`entityType`, `lastServerUpdatedAt`).

Cada entidad sincronizable añade: `serverUpdatedAt` (timestamp **autoritativo** del servidor; `NULL` = nunca sincronizada), `deleted`, `syncState` y `localRevision` (se incrementa en cada edición local; detecta ediciones ocurridas durante un push en vuelo).

### 8.3 Push (cliente → servidor)

```
1. SELECT outbox WHERE nextAttemptAt <= now ORDER BY seq LIMIT 50
2. Ordenar por dependencia: TODOS los vehicle antes que cualquier fuel_entry
3. Por item: doc(users/{uid}/{col}/{id}).set(snapshot + updatedAt: serverTimestamp())
   y releer el documento para conocer el updatedAt autoritativo asignado
4. Éxito, EN UNA TRANSACCIÓN LOCAL:
   - si outbox.localRevision == entidad.localRevision:
        borrar del outbox; syncState = SYNCED; serverUpdatedAt = respuesta.updatedAt
   - si NO (el usuario editó mientras el push estaba en vuelo):
        CONSERVAR la fila de outbox (ya coalescida) y actualizar solo serverUpdatedAt
5. Fallos:
   - PERMISSION_DENIED / token caducado → refrescar token, 1 reintento, luego backoff
   - error de validación → marcar poisoned, mostrar al usuario, NO reintentar en bucle
   - red / indisponible → backoff exponencial con jitter (1 s, 2 s, 4 s … tope 15 min)
```

**Idempotencia:** la escritura es un `set` cuyo ID lo generó el cliente. Si la respuesta se pierde y se reintenta, se reescribe el mismo valor. No hace falta clave de idempotencia ni *exactly-once*.

### 8.4 Pull (servidor → cliente)

```
1. since = max(0, sync_cursor[entityType] - VENTANA_SOLAPE)   // VENTANA_SOLAPE = 30 s
2. collection(users/{uid}/{col}).where(updatedAt > since).orderBy(updatedAt).limit(200)
   Incluye tombstones: son documentos normales con deleted = true, no borrados.
3. Aplicar la página EN UNA SOLA TRANSACCIÓN LOCAL. Para cada documento R:
   - si existe entrada de outbox para R.id → NO tocar las columnas de datos;
     actualizar solo serverUpdatedAt. El cambio local pendiente se reenvía y el servidor arbitra.
   - si no hay pendiente → aplicar R si (R.updatedAt, R.id) > (local.serverUpdatedAt, local.id)
   - R.deleted = true → marcar tombstone local, no borrar la fila
4. Avanzar el cursor al updatedAt del último documento; repetir mientras la página venga llena
```

**Por qué la ventana de solape es obligatoria:** es el fallo clásico de todo *delta sync* por timestamp. Si un documento se confirma con un timestamp anterior a un cursor ya avanzado, **se pierde para siempre y en silencio**. Como el *apply* es idempotente, refetchear documentos no cuesta más que unas lecturas.

**Retención de tombstones:** purga a 90 días. Un cliente offline durante más tiempo debe hacer resync completo.

### 8.5 Resolución de conflictos y convergencia

- *Last-write-wins* a nivel de **documento entero**, comparando el `updatedAt` sellado por el servidor, con desempate determinista por `id` (comparación lexicográfica). `max` sobre un orden total es un *join-semilattice*: el estado converge sea cual sea el orden de llegada.
- Un tombstone participa en el mismo orden: gana frente a una actualización con `updatedAt` anterior.
- **Desfase de reloj:** el `updatedAt` local es puramente provisional (solo ordena el outbox). El autoritativo lo sella siempre el servidor.

> **Limitación aceptada conscientemente:** LWW es a nivel de documento, no de campo. Si un dispositivo edita el precio y otro el odómetro a la vez, uno de los dos cambios se pierde entero. Aceptable para un usuario con 1-2 dispositivos. Queda documentado; no se descubre en producción.

**Disparadores:** app a *foreground* · recuperación de conectividad · tras una escritura local (debounce 2 s) · *pull-to-refresh* · tarea periódica (WorkManager / BGTaskScheduler). El motor **no conoce ninguna de estas APIs**: vive entero en `commonMain` y es 100 % testeable sin red ni iOS.

### 8.6 Seguridad de datos

Estructura en Firestore: `users/{uid}/vehicles/{id}` y `users/{uid}/fuelEntries/{id}`. Las subcolecciones bajo el documento de usuario hacen que toda consulta esté ya acotada al propietario por la propia ruta.

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

- `request.auth != null` **incluye a los usuarios anónimos**, que son el flujo principal del MVP.
- `request.resource.data.updatedAt == request.time` **impide que el cliente escriba su propio timestamp**: un dispositivo con la fecha mal no puede escribir `updatedAt` en 2099 y ganar todos los LWW para siempre.
- Deben existir **tests de reglas contra el emulador**: el usuario A no puede leer ni escribir bajo `users/B`.

---

## 9. Requisitos no funcionales

| Área | Requisito |
|------|-----------|
| Plataformas | Android `minSdk 26`, `targetSdk` actual · iOS 16+ |
| Rendimiento | Arranque en frío a contenido < 2 s · lista de 1.000 repostajes con scroll fluido · cálculo de consumo en un vehículo con 1.000 repostajes < 100 ms |
| Offline | 100 % de las funciones del MVP disponibles sin red |
| Accesibilidad | Soporte de tamaño de fuente del sistema, etiquetas de contenido en todos los controles, contraste AA |
| Internacionalización | Español e inglés desde el día 1. Formato de números, fechas y moneda según `Locale`. Sin cadenas hardcodeadas |
| Tests | `domain`: cobertura obligatoria de todos los use cases · `data`: tests de repositorios y mappers con fakes · `sync`: tests del motor con escenarios de conflicto · UI: tests de los flujos críticos F-1 a F-3 |
| Calidad | ktlint + detekt en CI · build fallido ante violación de las reglas de dependencia (7.3) |
| CI | Build + tests + lint en cada PR, para ambas plataformas |
| Privacidad | Política de privacidad publicada · fichas de privacidad de App Store y Play cumplimentadas · eliminación de cuenta accesible en la app |

---

## 10. Decisiones técnicas cerradas

Todas resueltas en la fase de planificación. Cada una debe quedar registrada como ADR en `docs/adr/`.

| ID | Decisión | Elección | Razón |
|----|----------|----------|-------|
| **D-0** | Backend | **Cloud Firestore** | Coste ≈ 0 a esta escala. Upsert idempotente por ID + `serverTimestamp()` de fábrica. Ver 7.6. |
| **D-1** | Base de datos local | **Room 3.0 KMP** con `androidx.sqlite:sqlite-bundled` | El SQLite *bundled* garantiza la misma versión de SQLite en Android e iOS y habilita la sintaxis `UPSERT` (SQLite ≥ 3.24), ausente en el SQLite del sistema con `minSdk 26`. |
| **D-2** | Interop Kotlin↔Swift | **SKIE**, solo en `:shared` | Swift export sigue en Alpha y no soporta *sealed classes*. Fijar Kotlin y SKIE juntos en el version catalog y no perseguir releases de Kotlin. |
| **D-3** | Inyección de dependencias | **Composition root manual** | ~25 bindings. Koin falla en *runtime*, y el peor modo de fallo posible aquí es un crash en el simulador de iOS. Inyección por constructor en todos los módulos; **ninguna anotación de DI en `domain`/`data`/`presentation`**. |
| **D-4** | `fuelType` | **En `Vehicle`, en el esquema desde el día 1, sin selector en la UI** | Va en el vehículo, no en cada repostaje. Añadir un campo a un esquema ya sincronizado es caro; arrastrar la columna es gratis. |
| **D-5** | Acceso a Firestore desde KMP | **`dev.gitlive:firebase-firestore` 2.6.x** tras `RemoteSyncSource` | La API de Firestore es amplia y con estado; reimplementarla con `expect/actual` no sale a cuenta. *Fallback:* API REST desde Ktor. |
| **D-6** | Firebase Auth en KMP | **`dev.gitlive:firebase-auth` 2.6.x** tras `AuthClient` | Coherente con D-5. **No usar `3.0.0-alpha01`.** |
| **D-7** | Navegación | **Nativa por plataforma** | Navigation Compose / `NavigationStack`. No se comparte nada, ni siquiera un `sealed class Destination`. |
| **D-8** | Capa de presentación | **Compartida en KMP** | Es el mayor retorno de KMP en el proyecto y lo que justifica SKIE. |
| **D-9** | Persistencia offline de Firestore | **Desactivada** | Dos cachés con políticas de invalidación distintas es una fuente de bugs. El *outbox* propio ya encola las escrituras offline. |

---

## 11. Glosario

| Término | Definición |
|---------|------------|
| **Repostaje / fuel entry** | Un evento de repostaje registrado por el usuario. |
| **Depósito lleno / full tank** | Repostaje en el que se llena el depósito hasta el corte del surtidor. Es el ancla del cálculo de consumo. |
| **Tramo** | Intervalo entre dos repostajes a depósito lleno consecutivos. Unidad de cálculo del consumo. |
| **Tombstone** | Marca de borrado lógico que se propaga a los demás dispositivos. |
| **Outbox** | Cola local de cambios pendientes de enviar al servidor. |
| **LWW** | *Last-write-wins*, estrategia de resolución de conflictos por marca temporal. |

---

## Anexo · Cambios respecto a la especificación original

| # | Punto original | Cambio y motivo |
|---|----------------|-----------------|
| 1 | "Kilometraje actual" del vehículo | Se separa en `initialOdometer` (inmutable) y `currentOdometer` (derivado). El campo original era ambiguo. |
| 2 | Sin modelo de repostaje | Se define `FuelEntry` completo, que era la entidad central ausente en la spec original. |
| 3 | Sin regla de consumo | Se formaliza el método *full-to-full* (R-3) con sus 5 casos de exclusión. Es el núcleo funcional del MVP. |
| 4 | "Se sincronizará con la base remota" | Se especifica la mecánica completa offline-first: outbox, cursor, tombstones, LWW, desfase de reloj. |
| 5 | "SQL Connect en Firebase" | Identificado como **Firebase SQL Connect** (antes Data Connect), PostgreSQL sobre Cloud SQL. **Descartado** por coste fijo 24/7, ausencia de SDK oficial KMP y complejidad innecesaria del upsert LWW. Se sustituye por **Cloud Firestore** como réplica remota (7.6). |
| 6 | Login anónimo | Se añade el flujo de conversión a cuenta permanente (F-4) y el caso de colisión de credenciales, que era el hueco funcional más peligroso. |
| 7 | Sin mención | Se añaden: "Sign in with Apple" como requisito de App Store, eliminación de cuenta como requisito de ambas tiendas, y reglas de seguridad en servidor con tests contra el emulador. |
| 8 | "Desacoplado de Firebase" | Se convierte en criterio de aceptación verificable (7.5), no en una intención — y se corrige su enunciado, que no era literalmente alcanzable. |
| 9 | "3 módulos por feature" | Se reduce a **1 módulo por feature** con paquetes internos. Con dos entidades, 18 módulos penalizan el build y el linkado del framework iOS a cambio de un beneficio teórico; las reglas de capa se verifican con un test de arquitectura. |
| 10 | Importes como decimal | Se fijan como **enteros en unidades menores** (céntimos) + código de moneda. Nunca `Float`/`Double`. |
