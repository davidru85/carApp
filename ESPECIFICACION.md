# Especificación — App de seguimiento de gastos de vehículo

> Versión 1.0 · Documento de especificación funcional y técnica del MVP
> Estado: **listo para planificación técnica**

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
| `fuelType` | Enum? | No | `GASOLINE`, `DIESEL`, `LPG`, `CNG`, `ELECTRIC`, `HYBRID`, `OTHER`. Ver decisión abierta D-4. |
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
| `totalCost` | Decimal(9,2) | Sí* | > 0. Ver R-2. |
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

De los tres valores `liters`, `pricePerLiter`, `totalCost`, el usuario introduce **dos cualesquiera** y el tercero se calcula automáticamente:

- `totalCost = liters × pricePerLiter`
- `pricePerLiter = totalCost / liters`
- `liters = totalCost / pricePerLiter`

`liters` es siempre obligatorio (es necesario para el consumo). Redondeo: `totalCost` a 2 decimales, `pricePerLiter` a 3, `liters` a 3. Todo cálculo monetario usa decimal de precisión fija, **nunca `Float`/`Double`**.

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
| Base de datos local | SQLite vía **Room KMP** o **SQLDelight** — ver decisión abierta D-1 |
| Backend | **Firebase SQL Connect** (PostgreSQL gestionado sobre Cloud SQL, antes llamado Firebase Data Connect) |
| Autenticación | **Firebase Authentication** |
| Asincronía | Coroutines + Flow |
| Inyección de dependencias | Koin (compatible KMP) — ver D-3 |
| Serialización | `kotlinx.serialization` |
| Fechas | `kotlinx-datetime` |

### 7.2 Estilo arquitectónico

**Modular Clean Architecture + modularización vertical por funcionalidad.** Cada feature es una porción vertical con sus tres capas en módulos Gradle independientes.

```
:app-android                     ← host Android (Compose, navegación, DI raíz)
/iosApp                          ← host iOS (SwiftUI, navegación, DI raíz)
:shared                          ← umbrella: agrega módulos y expone el XCFramework a iOS

:core:common                     ← Result/AppError, dispatchers, utilidades, UUID
:core:domain-model               ← tipos de dominio transversales (Money, Distance, Volume)
:core:database                   ← driver/DB, transacciones, expect/actual por plataforma
:core:remote                     ← contrato RemoteDataSource, cliente HTTP, manejo de errores
:core:auth                       ← contrato AuthRepository + modelo de sesión
:core:sync                       ← motor de sincronización genérico (cola, cursor, conflictos)
:core:testing                    ← fakes, builders de test, reglas de coroutines

:feature:onboarding:{domain,data,presentation}
:feature:auth:{domain,data,presentation}
:feature:vehicle:{domain,data,presentation}
:feature:fueling:{domain,data,presentation}
:feature:settings:{domain,data,presentation}

:integration:firebase-auth       ← única implementación que conoce Firebase Auth
:integration:firebase-sqlconnect ← única implementación que conoce SQL Connect
```

### 7.3 Reglas de dependencia (invariantes, verificables en CI)

1. `:feature:X:domain` es **Kotlin puro**: no depende de Android, iOS, Firebase, Room/SQLDelight ni de ningún otro feature. Solo de `:core:common` y `:core:domain-model`.
2. `:feature:X:data` depende de `:feature:X:domain`, `:core:database`, `:core:remote`. **Nunca** de `:integration:*`.
3. `:feature:X:presentation` depende de `:feature:X:domain`. Nunca de `data`.
4. Un feature **no depende de otro feature**. Si necesita algo de otro, ese contrato sube a `:core`.
5. Los módulos `:integration:*` implementan interfaces de `:core:*` y **solo los módulos host (`:app-android`, `iosApp`, `:shared`) los conocen**, para cablearlos en el grafo de DI.
6. **Ningún tipo de Firebase cruza la frontera de `data`.** Los mappers viven en `:integration:*`.

> Estas reglas deben validarse automáticamente (test de arquitectura o comprobación de grafo de dependencias en CI). Un agente que introduzca una dependencia prohibida debe fallar el build, no depender de la revisión humana.

### 7.4 Capa de presentación compartida

La lógica de presentación (estado de UI, validación, orquestación de casos de uso) se comparte en `commonMain` mediante *state holders* que exponen `StateFlow<UiState>` y aceptan eventos. Android los consume como `ViewModel`; iOS los envuelve en un `ObservableObject` de SwiftUI. Se evalúa **SKIE** para que los `Flow` y las `sealed class` de Kotlin se consuman de forma idiomática en Swift (ver D-2).

### 7.5 Desacoplamiento del proveedor cloud (requisito P4)

```
domain:        VehicleRepository (interfaz)
                      ▲
data:          VehicleRepositoryImpl ──► VehicleLocalDataSource  (interfaz)
                                    └──► VehicleRemoteDataSource (interfaz)
                                                   ▲
integration:                        SqlConnectVehicleRemoteDataSource
```

Migrar de Firebase a otro proveedor debe consistir en escribir un nuevo módulo `:integration:*` y cambiar una línea del grafo de DI. **Criterio de aceptación verificable:** borrar los módulos `:integration:*` del proyecto debe dejar todos los módulos `:core:*`, `:feature:*:domain` y `:feature:*:data` compilando y con sus tests en verde (usando fakes de `:core:testing`).

### 7.6 Nota crítica sobre Firebase SQL Connect y KMP

Firebase SQL Connect genera SDKs tipados **por plataforma** (Kotlin para Android, Swift para iOS) a partir del esquema GraphQL. **No existe un SDK oficial de Firebase para KMP.** Esto obliga a elegir entre dos estrategias, que es la principal decisión técnica del proyecto (D-5):

- **A) `expect/actual` sobre los SDKs generados nativos.** Se usa el SDK oficial en cada plataforma y se unifica con `expect/actual` en `:integration:firebase-sqlconnect`. Ventaja: SDK soportado, caché de cliente incluida. Coste: se implementa y mantiene dos veces, y el lado iOS requiere puente Swift↔Kotlin.
- **B) Cliente Ktor propio en `commonMain` contra el endpoint HTTP de SQL Connect.** Las operaciones de SQL Connect se despliegan en servidor y se invocan por endpoint, autenticadas con el ID token de Firebase Auth. Ventaja: una sola implementación, 100 % compartida, control total sobre la serialización y la lógica de sincronización. Coste: sin caché oficial (irrelevante, porque el motor de sincronización es propio) y dependencia del contrato del endpoint.

Dado que el requisito de sincronización es **offline-first completo con motor propio**, la caché opcional del SDK oficial aporta poco valor, lo que inclina la balanza hacia B. Decisión pendiente de confirmar en la fase de planificación.

Para **Firebase Auth** la situación es distinta: la obtención de la credencial de Google/Apple es inherentemente nativa (requiere UI del sistema). El patrón es: **la plataforma obtiene el token de la credencial → el módulo común lo intercambia por una sesión**. Se evaluará la librería community `dev.gitlive:firebase-auth` frente a `expect/actual` sobre los SDKs nativos (D-6).

---

## 8. Sincronización offline-first

### 8.1 Principios

1. **La base de datos local es la única fuente de verdad para la UI.** La UI nunca observa la red; observa la base local vía `Flow`.
2. Toda escritura es local, síncrona y confirmada al usuario de inmediato; se encola para envío.
3. La sincronización es un proceso de fondo que puede fallar sin degradar la experiencia.
4. Los identificadores se generan en cliente (UUID v4), de modo que un registro creado offline ya tiene su identidad definitiva.

### 8.2 Mecánica

**Push (cliente → servidor)**

- Cada entidad local lleva `syncState`. Un *outbox* recorre los registros `PENDING` en orden de `updatedAt`.
- Las operaciones remotas son **upserts idempotentes por `id`**, de modo que un reintento tras un fallo de red ambiguo no duplica datos.
- Reintentos con *backoff* exponencial y tope. Tras N fallos → `FAILED` + indicador discreto en UI y acción de reintento manual.

**Pull (servidor → cliente)**

- Cursor por usuario: `lastSyncedAt` (**hora del servidor**, nunca del dispositivo).
- El servidor devuelve todos los registros del usuario con `serverUpdatedAt > lastSyncedAt`, **incluidos los tombstones**.
- Paginación por lotes para el primer sync de un dispositivo nuevo.

**Resolución de conflictos**

- *Last-write-wins* a nivel de registro comparando `updatedAt`, con desempate determinista por `id` (comparación lexicográfica) para que todos los dispositivos converjan al mismo resultado.
- Un tombstone siempre gana frente a una actualización con `updatedAt` anterior.
- Se acepta esta estrategia porque el escenario es monousuario multidispositivo, donde la edición concurrente del mismo registro es rara. Queda documentado como limitación conocida.

**Desfase de reloj**

- El servidor asigna `serverUpdatedAt` de forma autoritativa. El cliente guarda ambos: su `updatedAt` local (para ordenar el outbox) y el `serverUpdatedAt` (para el cursor).

**Disparadores de sincronización**

- Arranque de la app · recuperación de conectividad · tras una escritura local (con *debounce* de unos segundos) · *pull-to-refresh* manual · tarea periódica en segundo plano.

### 8.3 Seguridad de datos

- Un usuario solo puede leer y escribir filas cuyo `ownerId` coincida con su UID. Debe aplicarse **en el servidor** (reglas de autorización de SQL Connect), no solo en el cliente.
- Todas las operaciones viajan autenticadas con el ID token de Firebase Auth.

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

## 10. Decisiones técnicas abiertas

Se resuelven en la fase de planificación, antes de escribir código.

| ID | Decisión | Opciones | Notas |
|----|----------|----------|-------|
| **D-1** | Base de datos local | Room KMP vs SQLDelight | Room: familiar viniendo de Android, migraciones conocidas. SQLDelight: más maduro en KMP, SQL-first, tipado desde el esquema. |
| **D-2** | Interoperabilidad Kotlin↔Swift | SKIE vs wrappers manuales | SKIE mejora mucho el consumo de `Flow` y `sealed class` en Swift, a cambio de una dependencia en el pipeline de build. |
| **D-3** | Inyección de dependencias | Koin vs factories manuales | Koin es el estándar de facto en KMP; las factories manuales evitan la dependencia y los errores en tiempo de ejecución. |
| **D-4** | `fuelType` en el MVP | Incluirlo vs posponerlo | Solo aporta valor si condiciona validaciones o unidades (p. ej. kWh en eléctricos). Si no, es ruido en el formulario. |
| **D-5** | Acceso a SQL Connect | SDKs nativos con `expect/actual` vs cliente Ktor común | Ver 7.6. La inclinación actual es el cliente Ktor común. |
| **D-6** | Firebase Auth en KMP | `dev.gitlive:firebase-auth` vs `expect/actual` propio | Sopesar la dependencia community frente al control total y el mantenimiento. |
| **D-7** | Navegación | Compartida vs nativa por plataforma | Recomendación: nativa (Navigation Compose / NavigationStack). Compartir navegación en KMP con UI nativa suele generar más fricción que valor. |

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
| 5 | "SQL Connect en Firebase" | Se identifica como **Firebase SQL Connect** (antes Data Connect), PostgreSQL sobre Cloud SQL, y se documenta que **no tiene SDK oficial KMP** (7.6) — restricción con impacto arquitectónico directo. |
| 6 | Login anónimo | Se añade el flujo de conversión a cuenta permanente (F-4) y el caso de colisión de credenciales, que era el hueco funcional más peligroso. |
| 7 | Sin mención | Se añaden: "Sign in with Apple" como requisito de App Store, eliminación de cuenta como requisito de ambas tiendas, y reglas de autorización en servidor. |
| 8 | "Desacoplado de Firebase" | Se convierte en criterio de aceptación verificable (7.5), no en una intención. |
