# Eventos de Logística (Entrega 6)

Logística sigue siendo la dueña de sus datos. Cada vez que cambia su estado publica un evento en RabbitMQ
(CloudAMQP) para que el modelo de lectura en MongoDB se mantenga al día. Esta página describe qué se
publica, dónde y cómo consumirlo.

## Dónde se publica

| | |
|---|---|
| Broker | El mismo de la mensajería de Logística (`CLOUDAMQP_URL`) |
| Exchange | `donatrack.exchange`, tipo topic, durable, sin borrado automático (igual que Donadores) |
| Cola de Logística | `logistica.cola`, durable, atada con `logistica.#` |
| Activación | Variable `EVENTOS_ENABLED=true`. Sin ella no se publica nada |

Otras variables, opcionales: `EVENTOS_EXCHANGE` (por defecto `donatrack.exchange`) y `EVENTOS_COLA`
(por defecto `logistica.cola`).

Quien consuma puede leer `logistica.cola` o crear su propia cola atada al exchange con `logistica.#`
(o con `#`, para recibir también los eventos de los demás módulos).

## Mensaje

JSON con el mismo sobre que usa Donadores:

```json
{
  "id": "c9f1…",
  "tipo": "DEPOSITO_STOCK_ACTUALIZADO",
  "timestamp": "2026-11-06T14:03:21.512Z",
  "idReferencia": "3",
  "deposito": { … },
  "asignacion": { … },
  "extra": { "traceId": "abc-123" }
}
```

| Campo | Significado |
|---|---|
| `id` | Identificador único del evento (UUID). También viaja como `messageId` de AMQP |
| `tipo` | Qué pasó, ver la tabla de abajo |
| `timestamp` | Momento en que se confirmó el cambio (UTC). Sirve para ignorar información más vieja |
| `idReferencia` | Id de la entidad afectada: el depósito o la asignación |
| `deposito` / `asignacion` | Estado **completo** de esa entidad después del cambio. Solo viene el que corresponde |
| `extra.traceId` | Traza del pedido que lo originó (`X-Trace-Id`), para seguirlo en los logs |

## Eventos

| Tipo | Routing key | Cuándo | Lleva |
|---|---|---|---|
| `DEPOSITO_CREADO` | `logistica.deposito.creado` | Se crea un depósito | `deposito` |
| `DEPOSITO_MODIFICADO` | `logistica.deposito.modificado` | Se cambian nombre, dirección o capacidad | `deposito` |
| `DEPOSITO_ELIMINADO` | `logistica.deposito.eliminado` | Se elimina un depósito | `deposito` (vacío) |
| `DEPOSITO_ALGORITMO_CONFIGURADO` | `logistica.deposito.algoritmo` | Se configura el algoritmo de matchmaking | `deposito` |
| `DEPOSITO_STOCK_ACTUALIZADO` | `logistica.deposito.stock` | Entra o sale stock de un depósito | `deposito` |
| `ASIGNACION_CREADA` | `logistica.asignacion.creada` | Un paquete se asigna a una necesidad | `asignacion` (estado `ASIGNADA`) |
| `ENTREGA_COMPLETADA` | `logistica.entrega.completada` | Se reporta la entrega de un paquete | `asignacion` (estado `COMPLETADA`, con `fechaEntrega`) |
| `BASE_LIMPIADA` | `logistica.base.limpiada` | Se reinicia la base para una demo | nada; `idReferencia` es `logistica` |

### `deposito`

```json
{
  "id": "3", "nombre": "Central", "direccion": "Medrano 951",
  "capacidadMaxima": 100, "unidadesOcupadas": 10, "algoritmo": "SUB_ATENDIDOS",
  "stock": [ { "paqueteId": "12", "donacionId": "don-1", "productoId": "arroz", "cantidad": 10 } ]
}
```

`algoritmo` es `null` si el depósito todavía no tiene uno configurado.

### `asignacion`

```json
{
  "id": "5d0e…", "paqueteId": "13", "donacionId": "don-1", "productoId": "arroz", "cantidad": 10,
  "necesidadId": "nec-1", "origen": "MATCHMAKING", "estado": "ASIGNADA",
  "fechaAsignacion": "2026-11-06T14:03:21Z"
}
```

`origen` es `MATCHMAKING` o `SOLICITUD_DONADORES`. `fechaEntrega` aparece solo cuando `estado` es `COMPLETADA`.

## Cómo consumirlos de forma idempotente

La consigna pide que los eventos puedan llegar repetidos o fuera de orden. Como cada evento trae el estado
completo de la entidad, alcanza con un upsert por id que ignore lo más viejo:

1. Usar `idReferencia` como `_id` del documento (depósito o asignación).
2. Guardar el `timestamp` del último evento aplicado en ese documento.
3. Si llega un evento con `timestamp` menor o igual al guardado, descartarlo. Si no, reemplazar el estado.

Tres cosas a tener en cuenta:

- Un `DEPOSITO_ELIMINADO` o `BASE_LIMPIADA` borra documentos: conviene guardar la baja con su `timestamp`
  para que un evento viejo no reviva lo que se borró.
- Si dos cambios del mismo depósito se confirman casi a la vez, el orden de los timestamps puede no coincidir
  con el orden de confirmación. Como cada evento lleva el estado completo, el modelo de lectura se corrige solo
  con el siguiente cambio de ese depósito.
- `ENTREGA_COMPLETADA` es el único lugar donde queda el momento de la entrega: la base de Logística no lo guarda.
  Para el tiempo entre el ingreso de una donación y su entrega, usar `asignacion.fechaEntrega`.

## Garantías y límites

- **Se publica después del commit.** Si la operación falla y se revierte, no sale ningún evento.
- **No bloquea ni rompe la operación.** El envío se hace en segundo plano. Si RabbitMQ no responde, el
  cliente no espera y el cambio queda guardado igual: se registra un aviso en el log y el evento se pierde.
  La fuente de verdad es la base de Logística, no MongoDB.
- **Sin reintentos ni outbox.** Un evento perdido se corrige con el siguiente cambio de esa entidad, porque
  cada uno lleva el estado completo.
- Una cola con nadie que la lea acumula mensajes. Si `logistica.cola` no tiene consumidor, conviene vaciarla.
