# Diagramas de secuencia de las funcionalidades integradas (Entrega 5)

La Entrega 5 pide un diagrama de secuencia por cada una de las seis funcionalidades principales, a nivel de
servicios o componentes: la interacción entre los módulos, el orden en que ocurren y lo que hace cada uno.

| # | Funcionalidad | Dónde está |
|---|---|---|
| 1 | Realizar una donación | `api-y-secuencias-logistica.md`, diagrama 1 |
| 2 | Reportar la entrega de un paquete | `api-y-secuencias-logistica.md`, diagrama 2 |
| 3 | Registrar una queja sobre una donación ya entregada | Este documento |
| 4 | Procesar un donador (incentivos) | Este documento |
| 5 | Registrar una nueva necesidad | Este documento, flujo completo. El lado de Logística, en detalle, está en `api-y-secuencias-logistica.md`, diagrama 3 |
| 6 | Obtener las estadísticas de un donador | Este documento |

Las rutas y las reglas de cada diagrama salen del código de cada módulo. Las llamadas entre módulos son REST síncronas.

---

## 3 — Registrar una queja sobre una donación ya entregada

Donadores es quien recibe la queja y orquesta. La donación tiene que estar en estado ACEPTADA, que es el estado al que
la lleva Logística cuando se reporta su entrega (diagrama 2). Si no lo está, Donaciones rechaza el cambio y la queja
no se guarda.

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente
    participant DyE as Donadores y Entidades
    participant Don as Donaciones

    Cli->>DyE: POST /quejas {id, donadorID, donacionID}
    Note over DyE: valida que la queja no exista ya<br/>y que el donador exista
    DyE->>Don: GET /donaciones/{donacionID}
    DyE->>Don: POST /donaciones/{donacionID}/queja
    Note over Don: cambia el estado a CONQUEJA,<br/>solo válido desde ACEPTADA
    alt la donación no existe o no está ACEPTADA
        Don-->>DyE: error
        DyE-->>Cli: error, la queja no se guarda
    else cambio de estado válido
        Don-->>DyE: 201 donación en CONQUEJA
        Note over DyE: guarda la queja
        opt el donador llega a 5 quejas
            Note over DyE: pasa a SOSPECHOSO
        end
        opt el donador llega a 10 quejas
            Note over DyE: pasa a BANEADO
        end
        DyE-->>Cli: 201 queja registrada
    end
```

Un donador BANEADO ya no puede donar: Donaciones consulta `puede-donar` antes de registrar cada donación (diagrama 1).

---

## 4 — Procesar un donador (incentivos)

Incentivos evalúa la misión en curso del donador contra sus donaciones y, si la cumplió, le da la insignia y sube su
categoría en Donadores. Se dispara de dos formas, con el mismo trabajo: a pedido con `POST /incentivos-donador/{id}/procesar`,
o por un cron que corre cada un minuto (configurable) sobre los donadores que tienen una misión en curso o ya completadas.

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente o Cron
    participant Inc as Incentivos
    participant DyE as Donadores y Entidades
    participant Don as Donaciones

    Cli->>Inc: POST /incentivos-donador/{id}/procesar
    Inc->>DyE: GET /donadores/{id}
    Note over Inc: si el donador no existe, termina con error
    Inc->>Don: GET /donaciones/search/{donadorID}
    Don-->>Inc: donaciones del donador, con su estado
    Note over Inc: primero verifica si perdió el progreso<br/>de una misión que ya había completado
    alt dejó de cumplir una misión completada
        Note over Inc: le quita la insignia y la misión vuelve a quedar en curso
        Inc->>DyE: PATCH /donadores/{id}/categoria (vuelve a la categoría inicial)
        Note over Inc: no sigue evaluando en esta ejecución
    else mantiene lo ya logrado
        Note over Inc: evalúa la misión en curso.<br/>Donaciones exitosas: cuenta las donaciones ACEPTADAS<br/>y las compara con la cantidad requerida
        opt la misión se cumplió
            Note over Inc: asigna la insignia
            Inc->>DyE: PATCH /donadores/{id}/categoria (sube a la categoría final)
            Note over Inc: si Donadores no confirma, se revierte todo<br/>y la misión no queda cumplida
            Note over Inc: marca la misión como completada y libera la misión en curso
        end
    end
    Inc-->>Cli: 200
```

Las donaciones llegan a ACEPTADA cuando Logística reporta la entrega del paquete (diagrama 2). Por eso el avance de las
misiones depende de ese flujo. La cantidad requerida de donaciones exitosas es configurable por misión.

---

## 5 — Registrar una nueva necesidad

Donadores registra la necesidad de una entidad y, de inmediato, intenta cubrirla con el stock que Logística tiene guardado.

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente
    participant DyE as Donadores y Entidades
    participant Don as Donaciones
    participant Log as Logística

    Cli->>DyE: POST /necesidades {id, entidadID, productoID, cantidadObjetivo}
    Note over DyE: valida que la cantidad sea positiva,<br/>que la necesidad no exista y que la entidad exista
    DyE->>Don: GET /productos/{productoID}
    Note over Don: confirma que el producto existe
    DyE->>Log: GET /stock/{productoID}
    Log-->>DyE: unidades disponibles, sumando todos los depósitos
    Note over DyE: guarda la necesidad
    alt el stock alcanza la cantidad objetivo
        DyE->>Log: POST /stock/{productoID}/asignaciones {cantidad objetivo, necesidadID}
    else el stock no alcanza
        DyE->>Log: POST /stock/{productoID}/asignaciones {stock disponible, necesidadID}
    end
    Note over Log: asigna el mínimo entre lo disponible y lo pedido.<br/>Crea una asignación por cada donación de origen.
    alt había algo para asignar
        Log-->>DyE: 201 lista de asignaciones
    else no había stock
        Log-->>DyE: 204 sin contenido, no es un error
    end
    DyE-->>Cli: 201 necesidad registrada
```

Las asignaciones quedan con origen SOLICITUD_DONADORES, para distinguirlas de las que decide el matchmaking cuando
llega una donación (diagrama 1). La entrega de esos paquetes se reporta con el diagrama 2.

---

## 6 — Obtener las estadísticas de un donador

Donadores arma las estadísticas con sus propios datos y las completa con lo que sabe Incentivos.

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente
    participant DyE as Donadores y Entidades
    participant Inc as Incentivos

    Cli->>DyE: GET /donadores/{id}/estadisticas
    Note over DyE: busca el donador, si no existe responde error
    DyE->>Inc: GET /incentivos-donador/{id}/insignias
    Inc-->>DyE: insignias del donador
    DyE->>Inc: GET /incentivos-donador/{id}/mision
    Inc-->>DyE: misión en curso, si tiene
    Note over DyE: arma las estadísticas: datos personales, estado,<br/>categoría, misión en curso e insignias
    DyE-->>Cli: 200 estadísticas del donador
```
