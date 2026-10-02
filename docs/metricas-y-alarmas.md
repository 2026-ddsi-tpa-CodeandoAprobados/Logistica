# Métricas y alarmas de Logística

La Entrega 5 pide "extender el sistema de métricas con casos propios del dominio" y "configurar un
sistema de alarmas para la aplicación, especialmente en métricas del negocio, que reporte el
comportamiento inusual del sistema". Este documento describe lo que mide Logística y cómo se
configuran las alarmas en Datadog.

El código vive en `MetricasLogistica.java`. Todas las métricas llevan la etiqueta `modulo:logistica`,
de modo que un único tablero de Datadog puede mostrar los cuatro módulos filtrando por esa etiqueta.

## Qué se mide

Hay dos clases de métricas, y se usan para cosas distintas.

**Contadores:** dicen qué pasó. Sirven para ver actividad y para alarmar sobre eventos anormales.

| Métrica | Etiquetas | Cuándo suma |
|---|---|---|
| `logistica.donaciones_recibidas` | | Una donación entra a un depósito (hay espacio). |
| `logistica.unidades_recibidas` | | Las unidades de esa donación. |
| `logistica.donaciones_rechazadas` | `motivo:capacidad` | Una donación no entra: el depósito está lleno. |
| `logistica.asignaciones_matchmaking` | | El matchmaking asigna un paquete a una necesidad. |
| `logistica.asignaciones_solicitud_donadores` | | Donadores pide stock para una necesidad. |
| `logistica.unidades_asignadas` | `origen:matchmaking` o `origen:solicitud_donadores` | Las unidades de cada asignación. |
| `logistica.paquetes_en_stock` | | Se guarda un paquete (el sobrante) en el stock. |
| `logistica.entregas_completadas` | | Se reporta la entrega de un paquete. |
| `logistica.unidades_entregadas` | | Las unidades de esa entrega. |
| `logistica.notificaciones_fallidas` | `destino:entidades` o `destino:donaciones` | Un aviso a otro módulo falló. |

**Indicadores de estado:** dicen cómo está el sistema en este momento. Son los que sirven para
detectar comportamiento inusual, porque un contador sólo dice que algo ocurrió.

| Métrica | Qué es |
|---|---|
| `logistica.unidades_en_stock` | Unidades guardadas en todos los depósitos. |
| `logistica.capacidad_total` | Suma de las capacidades máximas. Los depósitos sin capacidad definida no suman. |
| `logistica.ocupacion_maxima` | Ocupación del depósito más lleno, de 0 a 1. |

Los contadores se registran en 0 desde el arranque. Sin eso, una alarma sobre un contador que
todavía no ocurrió no tendría datos para evaluar y Datadog la mostraría como "sin información".

## Alarmas

Se configuran tres monitores de tipo *Metric* en Datadog (Monitors > New Monitor > Metric). Cada
uno responde a una situación distinta del negocio:

| Alarma | Qué avisa | Consulta | Umbral |
|---|---|---|---|
| Depósito casi lleno | Un depósito está por quedarse sin espacio. Es el aviso anticipado. | `max(last_5m):max:logistica.ocupacion_maxima{modulo:logistica} > 0.9` | crítico > 0.9 |
| Donaciones rechazadas | Se están rechazando donaciones por falta de capacidad. Ya hay donaciones que no se pueden recibir. | `sum(last_10m):sum:logistica.donaciones_rechazadas{modulo:logistica}.as_count() > 2` | crítico > 2 |
| Avisos entre módulos fallidos | Una entrega se completó pero Entidades o Donaciones no se enteraron: los módulos quedaron desincronizados. | `sum(last_15m):sum:logistica.notificaciones_fallidas{modulo:logistica}.as_count() > 0` | crítico > 0 |

Para el mensaje de cada monitor conviene indicar qué hacer. Por ejemplo, para el primero: "Un
depósito supera el 90 % de su capacidad. Revisar `GET /depositos` y ampliar la capacidad con
`PUT /depositos/{id}` o derivar donaciones a otro depósito".

## Cómo comprobar que una alarma funciona

La forma más directa es provocar el evento y mirar que el monitor cambie de estado. Con la API
desplegada:

1. Crear un depósito de capacidad 10: `POST /depositos` con `capacidadMaxima: 10`.
2. Enviar tres donaciones de 50 unidades a ese depósito con `POST /donaciones`. Cada una responde
   409 y suma uno a `logistica.donaciones_rechazadas`.
3. Esperar un par de minutos: el exportador de Datadog envía por intervalos de un minuto. El
   monitor de donaciones rechazadas pasa a estado *Alert*.

Para la alarma de ocupación: guardar 9 unidades en ese depósito (`POST /donaciones` de 9 unidades)
y esperar al siguiente minuto: `ocupacion_maxima` pasa a 0.9.

## Cómo se verifica en el código

`MetricasDeDominioTest` comprueba que cada contador suma lo que corresponde (con su etiqueta) y que
los tres indicadores reflejan el estado de los depósitos, incluido el caso de un depósito sin
capacidad. Si un contador dejara de incrementarse, la alarma que depende de él no saltaría nunca
y nadie lo notaría; por eso se prueba.

## Detalles del exportador

- El registro de Datadog reporta por intervalos de un minuto. `/actuator/metrics` muestra el valor
  del último intervalo, no el acumulado, y vuelve a 0 si no hubo actividad. No es un error.
- Los indicadores de estado leen los depósitos de la base. Para no repetir la consulta tres veces
  por intervalo, la lectura se reutiliza durante `logistica.metricas.vigencia-segundos` (10 por
  defecto; 0 en los tests).
