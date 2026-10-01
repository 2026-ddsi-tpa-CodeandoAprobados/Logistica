# Revisión de código — Módulo Logística (previa a la Entrega 5)

Inventario del código existente antes de sumar las funcionalidades nuevas (MCP, log
centralizado, métricas y alarmas). La Entrega 5 lo recomienda de forma explícita:
*"identificar y dejar registrado qué hace cada componente, qué funcionalidades se
utilizan, cuáles no y qué responsabilidades tiene cada parte del código"*.

Fecha de la revisión: 2026-09-11 · commit base `fabe2c2`.

---

## 1. Inventario por componente

Estado: **En uso** = lo ejercita un flujo real · **Contrato** = lo exige
`FachadaLogistica` pero nadie lo invoca · **Muerto** = no lo usa nadie.

### Capa de entrada

| Componente | Responsabilidad | Estado |
|---|---|---|
| `LogisticaController` | Único `@RestController`. Traduce HTTP ↔ fachada y mapea excepciones a códigos. | En uso |
| `AsignacionDesdeStockRequest` | Body de la asignación pedida por Donadores. | En uso |
| `StockDisponibleDTO` | Respuesta de la consulta de stock por producto. | En uso |
| `DepositoRequest`, `PaqueteRequest`, `AlgoritmoRequest` | Records anidados en el controller. | En uso |
| import de `DetalleProductoDTO` | — | **Muerto** |

### Fachada

| Operación | Responsabilidad | Estado |
|---|---|---|
| `gestionarDonacion` | Valida espacio contra el total y encola o procesa sync. | En uso |
| `procesarDetalle` | Matchmaking de un producto donado, asignación y sobrante a stock. | En uso |
| `guardarEnStock`, `verificarEspacio`, `ocupadoDe` | Reglas de capacidad del depósito. | En uso |
| `altaAsignacionDesdeWorker` | Alta de asignación pedida por el Worker vía HTTP. | En uso |
| `guardarSobranteEnStock` | Sobrante que le indica el Worker. | En uso |
| `stockDisponible` | Suma el stock de un producto en todos los depósitos. | En uso |
| `asignarDesdeStock` | Consume stock y crea una asignación por donación de origen. | En uso |
| `reportarEntrega` | Notifica a Entidades y Donaciones, marca la asignación completada. | En uso |
| ABM de depósito y consultas | Alta, baja, búsqueda, algoritmo de matchmaking. | En uso |
| `limpiarBaseDeDatos` | Reset para demos. | En uso |
| `ejecutarMatchmaking` | Método de la interfaz de la cátedra. Ningún endpoint lo expone. | Contrato |
| `setFachadaDonaciones`, `setFachadaDonadoresYEntidades` | Cuerpos vacíos. La integración real es por Feign. | Contrato |
| `debugNecesidades` | Andamiaje para diagnosticar el cliente Feign. | **Muerto** |

### Dominio

| Componente | Responsabilidad | Estado |
|---|---|---|
| `Deposito` | Entidad. Capacidad, algoritmo y colección de stock. Sin comportamiento. | En uso |
| `Paquete` | Entidad. Porción de una donación. | En uso |
| `Paquete.externalId` | Campo persistido que nadie escribe ni lee. | **Muerto** |
| `Asignacion` | Entidad. Vínculo paquete ↔ necesidad, con origen y estado. | En uso |
| `Matchmaker` | Algoritmos con acceso a base y variantes stateless para el Worker. | En uso |

### Persistencia e integración

| Componente | Responsabilidad | Estado |
|---|---|---|
| `DepositoRepository`, `PaqueteRepository`, `AsignacionRepository` | CRUD de Spring Data. | En uso |
| `LogisticaDataMapper` | Entidad ↔ DTO de la cátedra. | En uso |
| `EntidadesClient` | Necesidades de un producto y satisfacción de necesidad. | En uso |
| `DonacionesClient.actualizarEstadoDonacion` | Marca la donación aceptada al entregar. | En uso |
| `DonacionesClient.buscarDonacionPorId` | Sobrevive a la validación circular que se removió a propósito. | **Muerto** |
| `LogisticaApiClient` | El Worker escribe contra la propia API. | En uso |
| `RabbitConfig`, `DonacionPublisher`, `DonacionWorker` | Mensajería tras el flag. | En uso |

---

## 2. Responsabilidades mal ubicadas

> Los cuatro puntos de esta sección quedaron resueltos en los pasos 2 y 3 del orden de
> trabajo. Se dejan como registro de qué se encontró y por qué se cambió.

**La fachada concentra cinco responsabilidades.** Son cuatrocientas líneas que mezclan
el alta de donación, la gestión de stock, las asignaciones, las entregas y el registro
de métricas. La Entrega 5 pide código modularizado y atómico, y este es el punto más
visible del módulo.

**El dominio es anémico.** `Deposito` es solamente getters y setters, mientras que
`ocupadoDe`, `verificarEspacio` y `guardarEnStock` son reglas del depósito que viven en
la fachada. Moverlas a la entidad achica la fachada y hace testeable la regla de
capacidad sin levantar Spring.

**El controller hace traducción de errores a mano.** Cada endpoint repite un bloque
`try/catch` que convierte cualquier excepción en 400, con lo cual se pierde el mensaje
original. Un `@RestControllerAdvice` centraliza eso y deja los endpoints en una línea.

**Todos los errores de negocio son `RuntimeException`.** No hay tipos propios, así que
desde afuera no se distingue un depósito inexistente de un depósito sin capacidad. Es la
causa de que el módulo Donaciones reciba un 400 opaco cuando el depósito está lleno.

---

## 3. Duplicación

- La suma de stock por producto está escrita dos veces, en `stockDisponible` y en
  `asignarDesdeStock`.
- `buscarPaquetePorID` y `buscarTodosLosPaquetes` construyen el DTO a mano en lugar de
  usar el mapper, que ya tiene ese método.
- `Matchmaker` mantiene dos familias de algoritmos, una con base y otra stateless, que
  pueden divergir en silencio. Hoy ya difieren: la versión con base descuenta lo ya
  asignado y la stateless confía en lo que reporta Entidades.

---

## 4. Funcionalidades incompletas

- **`PRIORIDAD` no tiene comportamiento propio.** En el `switch` comparte rama con
  `SUB_ATENDIDOS`, así que de los tres algoritmos configurables hay dos efectivos. El
  enunciado pide configurar el matchmaking de cada depósito como funcionalidad interna.
- **El ABM de depósito está a medias.** Hay alta, baja, consulta y cambio de algoritmo,
  pero no hay modificación de nombre, dirección ni capacidad.
- **La baja de depósito no valida nada.** Si el depósito tiene stock, la cascada borra
  los paquetes sin avisar.
- **No hay un solo test.** `src/test/java` está vacío.
- **No hay configuración de OpenAPI.** La dependencia de springdoc está, pero sin
  anotaciones ni bean de configuración, y la especificación de API es un entregable.

---

## 5. Riesgos detectados

**Llamadas HTTP dentro de la transacción.** `Fachada` lleva `@Transactional` a nivel de
clase, de modo que las llamadas Feign a Entidades y Donaciones ocurren con la
transacción abierta. Es la misma familia de problema que provocó el deadlock con
Donaciones, que ya está documentado y no hay que reintroducir.

**`reportarEntrega` marca la entrega completada aunque fallen las notificaciones.** Los
dos bloques `catch` escriben en la salida de error y siguen de largo, así que la
asignación queda completada aunque la necesidad nunca se haya satisfecho. Es el flujo
principal más caro de depurar y hoy no deja rastro en ningún lado.

**El espacio se verifica pero no se reserva.** En modo asincrónico la respuesta sale
antes de que el Worker procese, así que dos donaciones simultáneas pueden pasar la
verificación y superar la capacidad entre las dos.

**No hay logging.** Fuera del Worker, el módulo usa `System.err.println` y
`printStackTrace`. Esto bloquea de forma directa el requisito de log centralizado.

**Configuración local frágil.** `DD_API_KEY`, `DB_URL`, `DB_USER` y `DB_PASSWORD` no
tienen valor por defecto, así que la aplicación no arranca en una máquina limpia. Además
`spring.devtools.restart.enabled=true` queda activo también en el despliegue.

---

## 6. Qué toca cada requisito de la Entrega 5

| Requisito | Dónde impacta |
|---|---|
| MCP Server | Componente nuevo, fuera del módulo. Consume la API REST, sin duplicar reglas. |
| Log centralizado | Reemplazar las salidas por consola, agregar el appender y el filtro de trazas, e instrumentar los flujos principales. |
| Métricas de dominio | Los contadores actuales cuentan invocaciones, no unidades. Conviene medir ocupación de depósito, unidades asignadas y donaciones que no encuentran necesidad. |
| Alarmas | Se configuran sobre las métricas de negocio, no en el código. |
| Completar el core business | ABM de depósito completo, algoritmo `PRIORIDAD`, validación de baja y errores de negocio tipados. |
| Modularización | Partir la fachada y devolverle comportamiento al dominio. |
| Diagramas de secuencia | Seis flujos a nivel de servicios, en `docs/`. |

---

## 7. Orden de trabajo propuesto

1. ~~Limpieza de código muerto~~ **hecho.** Se eliminaron el endpoint de diagnóstico de
   necesidades y su método en la fachada, la búsqueda de donación del cliente de
   Donaciones, el campo `externalId` de paquete, los setters que nadie usaba en asignación
   y depósito, dos imports sin uso y la dependencia de Jackson para XML. Compila limpio y
   ningún símbolo eliminado quedó referenciado.
2. ~~Errores de negocio tipados más manejador global~~ **hecho.** Cuatro excepciones
   propias en `exceptions/`, un `@RestControllerAdvice` que las traduce a códigos HTTP con
   cuerpo RFC 7807, y el controller sin un solo `try/catch`. Cubierto por
   `ManejoDeErroresTest`, los primeros seis tests del módulo.

   | Situación | Antes | Ahora |
   |---|---|---|
   | Depósito, paquete o asignación inexistente | 404 sin cuerpo | 404 con el id que faltó |
   | Falta un dato o la cantidad no es positiva | 400 sin cuerpo | 400 con el motivo |
   | Id no numérico | 500 | 400 con el valor recibido |
   | Depósito sin capacidad | 400 sin cuerpo | 409 con capacidad, ocupado y faltante |

   El cambio de 400 a 409 en la capacidad hay que avisarlo al equipo de Donaciones.
3. ~~Reparto de responsabilidades~~ **hecho.** La fachada pasó de concentrar cinco
   responsabilidades a delegar en siete servicios de `service/`, ninguno de más de 140
   líneas, y el depósito recuperó sus reglas de capacidad y de consumo de stock. Antes del
   cambio se escribieron ocho tests de caracterización sobre los flujos principales, con
   los módulos vecinos simulados; los catorce tests siguen en verde después.
4. ~~Logging centralizado~~ **hecho en el código, falta cargar el token.** Appender de Better
   Stack condicionado a una variable de entorno, filtro y propagación de trazas entre módulos,
   traza a través de la cola y eventos de negocio registrados. Ver
   [logging-y-trazas.md](logging-y-trazas.md).
5. Métricas de dominio y alarmas.
6. Completar ABM y algoritmo de matchmaking.
7. MCP Server contra la API terminada.
8. Documentación y diagramas.
