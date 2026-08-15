# Arquitectura integrada — DonaTrack (Entrega 4)

Diagrama de despliegue e integración de los 4 módulos + el bot

```mermaid
flowchart TB
    Tel(["Usuario<br/>Telegram"])

    subgraph BOT["Bot de Telegram · Render"]
        BotApp["Grupo7Bot<br/>+ Gateway"]
    end

    subgraph DON["Donaciones · Render"]
        DonAPI["API REST"]
        DonDB[("PostgreSQL")]
    end

    subgraph ENT["Donadores y Entidades · Render"]
        EntAPI["API REST"]
        EntDB[("PostgreSQL")]
    end

    subgraph LOGI["Logística · Render"]
        LogAPI["API REST"]
        Worker["Worker stateless"]
        LogDB[("PostgreSQL · Neon")]
    end

    subgraph INC["Incentivos · Render"]
        IncAPI["API REST"]
        Cron["Cron-Job misiones"]
        IncDB[("PostgreSQL")]
    end

    MQ{{"RabbitMQ<br/>broker en la nube"}}

    Tel <--> BotApp
    BotApp --> EntAPI
    BotApp -.-> DonAPI
    BotApp -.-> LogAPI
    BotApp -.-> IncAPI

    DonAPI --> DonDB
    EntAPI --> EntDB
    LogAPI --> LogDB
    IncAPI --> IncDB

    DonAPI -- "valida donador" --> EntAPI
    DonAPI -- "registra donación" --> LogAPI

    EntAPI -- "valida producto" --> DonAPI
    EntAPI -- "stock / asignar" --> LogAPI
    EntAPI -- "insignias / misión" --> IncAPI

    LogAPI -- "valida donación" --> DonAPI
    LogAPI -- "necesidades / satisfacción" --> EntAPI
    LogAPI <--> MQ
    Worker <--> MQ
    Worker -- "alta asignación / stock" --> LogAPI

    Cron --> IncAPI
    IncAPI -- "consulta" --> DonAPI
    IncAPI -- "consulta" --> EntAPI
```

**Referencias:** flecha sólida = llamada REST síncrona (Feign/RestClient). Flecha punteada
= ruta de Gateway configurada en el bot pero sin comando que la use hoy.

> Armado leyendo los 4 repos del equipo, no es un diagrama entregado por otro compañero —
> conviene que lo revisen antes de darlo por definitivo en el informe.
