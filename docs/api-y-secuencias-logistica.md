# Módulo Logística — API y diagramas de secuencia (Entrega 5)

Especificación de la API y diagramas de secuencia del módulo **Logística** (DonaTrack, TPA 2026).
La especificación interactiva está en `/swagger-ui.html` y el contrato en `/v3/api-docs`.

---

## Especificación de la API

Los errores se devuelven con un código según su causa: **400** si la solicitud es inválida,
**404** si el recurso no existe y **409** si la operación choca con el estado actual (por ejemplo,
capacidad insuficiente o una entrega ya reportada).

| Método | Ruta | Descripción | Errores |
|---|---|---|---|
| `POST` | `/depositos` | Crea un depósito. Nombre obligatorio, capacidad máxima no negativa. | 400 |
| `GET` | `/depositos` · `/depositos/{id}` | Lista los depósitos o trae uno, con su stock. | 400, 404 |
| `PUT` | `/depositos/{id}` | Modifica nombre, dirección y capacidad. No toca el stock ni el algoritmo. La capacidad no puede quedar por debajo de lo almacenado. | 400, 404, 409 |
| `DELETE` | `/depositos/{id}` | Elimina un depósito. Sólo si está vacío. | 404, 409 |
| `PATCH` | `/depositos/{id}/algoritmo` | Configura el algoritmo de matchmaking del depósito. Nace sin algoritmo. | 400, 404 |
| `POST` | `/donaciones` | Recibe una donación: verifica que entre en el depósito y la encola (o la procesa en el momento si la mensajería está apagada). | 400, 404, 409 |
| `POST` | `/asignaciones` | Alta de asignación por matchmaking que hace el Worker. Body: `{donacionID, productoID, cantidad, necesidadID}`. | 400 |
| `POST` | `/depositos/{id}/stock` | El Worker guarda el sobrante en el stock. Body: `{donacionID, productoID, cantidad}`. | 400, 404, 409 |
| `GET` | `/stock/{productoID}` | Stock disponible de un producto, sumando todos los depósitos. | |
| `POST` | `/stock/{productoID}/asignaciones` | Asigna desde stock a pedido de Donadores. Body: `{cantidad, necesidadID}`. Responde `201` con la lista de asignaciones o `204` si no hay nada para asignar. | 400 |
| `POST` | `/entregas` | Reporta la entrega de un paquete: satisface la necesidad, acepta la donación y completa la asignación. | 400, 404, 409 |
| `GET` | `/paquetes` | Lista los paquetes. | |
| `GET` | `/asignaciones` · `/asignaciones/{id}` · `/asignaciones/paquete/{paqueteID}` | Consulta de asignaciones, que incluyen su `origen`. | 404 |
| `DELETE` | `/testing/reset` | Limpia la base para reiniciar demostraciones. | |

Logística decide cuánto se asigna desde el stock: consume el mínimo entre lo disponible y lo
solicitado, y el solicitante sólo pide. Devuelve una asignación por cada donación de origen,
porque un paquete pertenece a una sola donación.

---

## Diagramas de secuencia

Los diagramas muestran las interacciones entre módulos, su orden y lo que hace cada uno. Cubren
las dos funcionalidades principales en las que Logística es protagonista, y una tercera que
Logística atiende a pedido de Donadores.

### 1 — Realizar una donación

Participa Donaciones, que orquesta, Donadores y Entidades, que valida al donador y aporta las
necesidades, y Logística con su Worker.

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente
    participant Don as Donaciones
    participant DyE as Donadores y Entidades
    participant Log as Logística API
    participant MQ as Cola (RabbitMQ)
    participant Wk as Worker de Logística

    Cli->>Don: POST /donaciones
    Don->>DyE: GET /donadores/{id}
    Don->>DyE: GET /donadores/{id}/puede-donar
    Note over Don: valida productos y guarda la donación en estado INGRESADA
    Don->>Log: POST /donaciones
    Note over Log: verifica que las unidades entren en el depósito
    alt el depósito no tiene espacio
        Log-->>Don: 409 capacidad insuficiente
        Note over Log: suma donaciones_rechazadas (alarma)
        Don-->>Cli: error, la donación no se registra
    else hay espacio
        Log->>MQ: publica la donación, con la traza
        Log-->>Don: 201
        Don-->>Cli: 201 donación INGRESADA
        MQ->>Wk: entrega el mensaje
        loop por cada producto de la donación
            Wk->>DyE: GET /necesidades/{productoID}
            Note over Wk: matchmaking según el algoritmo del depósito
            opt hay una necesidad elegible
                Wk->>Log: POST /asignaciones
                Note over Log: crea el paquete y la asignación (origen MATCHMAKING)
            end
            opt queda sobrante o no hay necesidad
                Wk->>Log: POST /depositos/{id}/stock
                Note over Log: guarda el paquete en el stock
            end
        end
    end
```

Logística no valida contra Donaciones que la donación exista. Donaciones llama dentro de su propia
transacción, antes de confirmarla: una consulta de vuelta no vería la fila, devolvería 404 y
terminaría en un rollback del otro lado.

Con la mensajería apagada, Logística hace en el momento lo que en el diagrama hace el Worker.

### 2 — Reportar la entrega de un paquete

Participan Logística, Donadores y Entidades, y Donaciones. Los dos avisos se hacen "de la mejor
manera posible": si uno falla, la entrega se completa igual, deja una advertencia en el log
centralizado y suma `notificaciones_fallidas`.

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente
    participant Log as Logística API
    participant DyE as Donadores y Entidades
    participant Don as Donaciones

    Cli->>Log: POST /entregas {paquete}
    Note over Log: busca la asignación del paquete
    alt no existe la asignación
        Log-->>Cli: 404
    else ya estaba COMPLETADA
        Log-->>Cli: 409 la entrega ya fue reportada
    else está ASIGNADA
        Log->>DyE: POST /necesidades/{id}/satisfaccion
        Note over Log: si falla: advertencia en el log y la entrega sigue
        Log->>Don: PATCH /donaciones/{id}/estado ACEPTADA
        Note over Log: si falla: advertencia en el log y la entrega sigue
        Note over Log: asignación pasa a COMPLETADA, suma entregas_completadas
        Log-->>Cli: 201
    end
```

### 3 — Asignación desde stock, a pedido de Donadores

Es la parte de Logística de la funcionalidad "Registrar una nueva necesidad".

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente
    participant DyE as Donadores y Entidades
    participant Log as Logística API

    Cli->>DyE: POST /necesidades
    Note over DyE: guarda la necesidad y obtiene su id
    DyE->>Log: GET /stock/{productoID}
    Log-->>DyE: unidades disponibles, sumando los depósitos
    DyE->>Log: POST /stock/{productoID}/asignaciones {cantidad, necesidadID}
    Note over Log: asigna el mínimo entre lo disponible y lo pedido
    alt hay algo para asignar
        Note over Log: consume stock y crea una asignación por donación de origen (origen SOLICITUD_DONADORES)
        Log-->>DyE: 201 lista de asignaciones
    else sin stock o cantidad 0
        Log-->>DyE: 204 sin contenido
        Note over DyE: no es un error: el alta de la necesidad sigue
    end
    DyE-->>Cli: 201 necesidad
```

---

## Observabilidad

Cada pedido lleva un encabezado `X-Trace-Id`. Logística lo toma del pedido entrante (o genera
uno), lo agrega a todos los logs, lo reenvía en las llamadas a otros módulos y lo guarda dentro
del mensaje de la cola, de modo que el Worker retoma la misma traza. Los logs se envían a Better
Stack y las métricas a Datadog; ver `logging-y-trazas.md` y `metricas-y-alarmas.md`.
