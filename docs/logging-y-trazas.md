# Logging centralizado y convención de trazas

Cómo funciona el log centralizado en Logística, y qué tiene que copiar cada módulo para que los
cuatro se puedan seguir juntos en un único panel. Este documento es autosuficiente: el código
completo está incluido más abajo, listo para copiar.

## Para qué sirve

Una donación atraviesa varios módulos: entra por Donaciones, llama a Logística, que consulta a
Entidades, y si la mensajería está activa pasa además por la cola y vuelve por el Worker. Cada
módulo escribe sus propios logs. Si todos llegan al mismo panel sin una marca común, se ven
mezclados por orden de llegada, sin forma de saber cuáles son de la misma donación.

La marca común es la **traza**: un identificador corto que genera el primer módulo que recibe el
pedido y que viaja en un encabezado HTTP en cada llamada siguiente. Filtrando el panel por ese
valor se ve el flujo completo, en orden, en los cuatro módulos.

## La convención, que tiene que ser idéntica en los cuatro módulos

| Qué | Valor |
|---|---|
| Encabezado HTTP | `X-Trace-Id` |
| Clave del contexto de log (MDC) | `traceId` |
| Otras claves del contexto | `instanceId`, `requestId` |
| Qué rutas no se registran | todo lo que empiece con `/actuator` |
| Valor de la traza | hasta 64 caracteres entre letras, números, punto, guion y guion bajo |

Si un módulo usa otro nombre de encabezado, la cadena se corta en ese salto.

El nombre del componente se manda en `APP_NAME` y es lo que permite filtrar por módulo. Valores
sugeridos, para que no haya variantes: `donaciones`, `donadores-y-entidades`, `incentivos`,
`logistica`.

## Variables de entorno

Se cargan en el panel de Render de cada servicio. Sin `BETTERSTACK_SOURCE_TOKEN` la aplicación
arranca igual y solo escribe por consola, así que no rompe nada ni en local ni en los tests.

| Variable | Qué es |
|---|---|
| `BETTERSTACK_SOURCE_TOKEN` | Token de la fuente de ese módulo. Lo da quien administra la cuenta. |
| `BETTERSTACK_INGEST_URL` | Dirección de ingesta que muestra el panel de esa fuente. Si falta se usa la genérica. |
| `APP_NAME` | Nombre del componente, por ejemplo `logistica`. |

La cuenta de Better Stack es **una sola** para el equipo, con una fuente por módulo. Con cuentas
separadas los logs quedan en paneles distintos y se pierde el sentido de centralizar.

## Qué copiar en cada módulo

Son cinco pasos. Los tres primeros son copiar y pegar. Los archivos Java van en un paquete
`logging` dentro del paquete base del módulo: hay que cambiar la línea `package` de cada uno por
la del módulo. Se asume un módulo Spring Boot con servidor web tradicional y clientes Feign.

### 1. Dependencias en el `pom.xml`

```xml
<!-- Appender de Better Stack para Logback -->
<dependency>
  <groupId>com.logtail</groupId>
  <artifactId>logback-logtail</artifactId>
  <version>0.3.6</version>
</dependency>
<!-- Necesario para el bloque condicional <if> de logback-spring.xml -->
<dependency>
  <groupId>org.codehaus.janino</groupId>
  <artifactId>janino</artifactId>
  <scope>runtime</scope>
</dependency>
```

### 2. `src/main/resources/logback-spring.xml`

Si el módulo ya tiene uno, reemplazarlo. Lo único que se cambia es el valor por defecto de
`APP_NAME`, que acá es `logistica`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <!--
      Cada línea lleva la clase y el método que la escribió, y tres datos de contexto que pone
      RequestLoggingFilter: la traza, que atraviesa todos los módulos, la instancia y el pedido.
      %method:%line obliga a Logback a calcular el origen de cada línea, con un costo chico pero
      real. Para este volumen es aceptable.
    -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36}.%method:%line [trace=%X{traceId} instance=%X{instanceId} req=%X{requestId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <!--
      Logging centralizado en Better Stack. El appender solo se activa si existe la variable de
      entorno BETTERSTACK_SOURCE_TOKEN: sin ella la aplicación arranca igual, sólo por consola,
      así que no hace falta tocar nada para correr en local ni para los tests.

      APP_NAME identifica el componente que generó el evento y es lo que permite filtrar por
      módulo cuando los logs de los cuatro conviven en el mismo panel.

      Logback no soporta anidar un <if> dentro de <root>, por eso hay dos bloques <root> completos.
      El atributo condition de <if> está deprecado desde Logback 1.5.20 y sólo tira un warning
      en el arranque; es lo que usa el repositorio de ejemplo de la cátedra.
    -->
    <if condition='isDefined("BETTERSTACK_SOURCE_TOKEN")'>
        <then>
            <appender name="BETTERSTACK" class="com.logtail.logback.LogtailAppender">
                <appName>${APP_NAME:-logistica}</appName>
                <sourceToken>${BETTERSTACK_SOURCE_TOKEN}</sourceToken>
                <ingestUrl>${BETTERSTACK_INGEST_URL:-https://in.logs.betterstack.com}</ingestUrl>
                <mdcFields>traceId,instanceId,requestId</mdcFields>
                <mdcTypes>string,string,string</mdcTypes>
            </appender>
            <root level="INFO">
                <appender-ref ref="CONSOLE"/>
                <appender-ref ref="BETTERSTACK"/>
            </root>
        </then>
        <else>
            <root level="INFO">
                <appender-ref ref="CONSOLE"/>
            </root>
        </else>
    </if>

</configuration>
```

### 3. Las clases de la traza

Tres archivos en el paquete `logging`. `Traza` guarda la convención, el filtro recibe o genera la
traza en cada pedido entrante, y el interceptor la reenvía en cada llamada saliente.

**`Traza.java`**: las constantes de la convención y la validación del valor recibido.

```java
package ar.edu.utn.dds.k3003.logging;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Convención de traza compartida por los cuatro módulos.
 *
 * <p>Una traza identifica la cadena completa de llamadas que dispara una sola acción del
 * usuario, cruzando todos los módulos. El primer módulo que recibe el pedido genera el
 * identificador y lo manda en el encabezado {@link #ENCABEZADO} en cada llamada saliente. Los
 * demás lo leen, lo ponen en su contexto de log y lo reenvían. Así, todas las líneas de una
 * misma acción quedan marcadas con el mismo valor en el servicio de logging centralizado.
 *
 * <p>El nombre del encabezado es el del repositorio de ejemplo de la cátedra. Tiene que ser
 * exactamente el mismo en los cuatro módulos: con otro nombre la cadena se corta.
 */
public final class Traza {

  /** Encabezado HTTP que transporta el identificador de traza entre módulos. */
  public static final String ENCABEZADO = "X-Trace-Id";

  /** Clave del contexto de log (MDC) donde vive el identificador de traza. */
  public static final String MDC_TRAZA = "traceId";

  /** Clave del contexto de log con el identificador de la instancia que atiende el pedido. */
  public static final String MDC_INSTANCIA = "instanceId";

  /** Clave del contexto de log con el identificador de este pedido puntual, de un solo salto. */
  public static final String MDC_PEDIDO = "requestId";

  private Traza() {}

  /** Identificador corto para una traza o un pedido nuevo. */
  public static String nuevoId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  /** Traza del hilo actual, o nulo si no hay ninguna. */
  public static String actual() {
    return MDC.get(MDC_TRAZA);
  }

  /**
   * Usa el identificador recibido si es válido y, si no, genera uno nuevo.
   *
   * <p>El valor llega de afuera y termina en los logs y en un encabezado de respuesta. Por eso
   * sólo se acepta un identificador corto de caracteres inofensivos: uno con saltos de línea
   * permitiría falsificar líneas de log.
   */
  public static String recibirOGenerar(String recibido) {
    return (recibido != null && VALIDO.matcher(recibido.trim()).matches())
            ? recibido.trim()
            : nuevoId();
  }

  private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");
}
```

**`RequestLoggingFilter.java`**: pone la traza en el contexto de log de cada pedido entrante.

```java
package ar.edu.utn.dds.k3003.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pone la traza, la instancia y el identificador del pedido en el contexto de log de cada
 * request, y deja constancia de la entrada y la salida con el código y el tiempo.
 *
 * <p>Si el pedido trae el encabezado {@link Traza#ENCABEZADO} lo propaga. Si no, este módulo es
 * el punto de entrada de la cadena y genera uno. La traza también se devuelve en la respuesta,
 * para que quien llama pueda buscar sus logs sin tener que adivinar cuál fue.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  private final String instancia;

  public RequestLoggingFilter(@Value("${server.port:8080}") String puerto) {
    this.instancia = resolverNombreDeInstancia() + ":" + puerto;
  }

  /**
   * La salud la consultan Render y el monitor de disponibilidad cada pocos minutos. Registrarla
   * ensucia la consola y consume cuota del servicio de logs con ruido que no ayuda a depurar.
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain cadena) throws ServletException, IOException {
    String traza = Traza.recibirOGenerar(request.getHeader(Traza.ENCABEZADO));
    MDC.put(Traza.MDC_TRAZA, traza);
    MDC.put(Traza.MDC_INSTANCIA, instancia);
    MDC.put(Traza.MDC_PEDIDO, Traza.nuevoId());
    response.setHeader(Traza.ENCABEZADO, traza);

    long inicio = System.currentTimeMillis();
    log.info("--> {} {}", request.getMethod(), request.getRequestURI());
    try {
      cadena.doFilter(request, response);
    } finally {
      log.info("<-- {} {} status={} took={}ms", request.getMethod(), request.getRequestURI(),
              response.getStatus(), System.currentTimeMillis() - inicio);
      MDC.clear();
    }
  }

  /** Render pone RENDER_INSTANCE_ID, INSTANCE_NAME sirve para varias instancias en local. */
  private static String resolverNombreDeInstancia() {
    for (String variable : new String[] {"RENDER_INSTANCE_ID", "INSTANCE_NAME"}) {
      String valor = System.getenv(variable);
      if (valor != null && !valor.isBlank()) {
        return valor;
      }
    }
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "desconocido";
    }
  }
}
```

**`TrazaFeignInterceptor.java`**: reenvía la traza en todas las llamadas salientes. Es lo que
evita que la cadena se corte.

```java
package ar.edu.utn.dds.k3003.logging;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

/**
 * Agrega el encabezado de traza a toda llamada saliente de los clientes Feign: Entidades,
 * Donaciones y la propia API de Logística que usa el Worker. Sin esto, la cadena de llamadas se
 * corta en el primer salto y los logs de los otros módulos no se pueden vincular con los nuestros.
 *
 * <p>Spring Cloud OpenFeign aplica automáticamente todo bean de este tipo a todos los clientes.
 * Si no hay traza en el hilo actual no agrega nada.
 */
@Component
public class TrazaFeignInterceptor implements RequestInterceptor {

  @Override
  public void apply(RequestTemplate plantilla) {
    String traza = Traza.actual();
    if (traza != null) {
      plantilla.header(Traza.ENCABEZADO, traza);
    }
  }
}
```

Si el módulo usa `RestClient` o `RestTemplate` en lugar de Feign, el equivalente es un
`ClientHttpRequestInterceptor` que agregue `request.getHeaders().add(Traza.ENCABEZADO, traza)`
cuando `Traza.actual()` no sea nulo.

### 4. Registrar eventos del dominio, no solo errores

Los que más valen en una demostración son los de las operaciones que cruzan módulos: una
donación recibida, una entrega completada, una necesidad registrada, un donador procesado. Cada
línea tiene que decir qué pasó y con qué identificadores, para poder investigarla sin otro
contexto. Se usa el logger de SLF4J de cada clase:

```java
private static final Logger log = LoggerFactory.getLogger(MiServicio.class);

log.info("Donación {} recibida en el depósito {}: {} unidades", donacionID, depositoID, unidades);
```

Nada de `System.out` ni `System.err`, ni `printStackTrace`: no pasan por Logback y no llegan al
panel.

### 5. Cargar las variables de entorno

Ver la sección anterior. El token de cada fuente lo da quien administra la cuenta de Better Stack.

## Cómo verificar que funciona

1. Llamar a cualquier endpoint y mirar los encabezados de la respuesta: tiene que venir
   `X-Trace-Id`. Con `curl -i` se ve.
2. Llamar de nuevo mandando uno propio, `curl -H "X-Trace-Id: prueba-1" ...`: la respuesta tiene
   que devolver el mismo valor.
3. En el panel de Better Stack, buscar `prueba-1`: tienen que aparecer las líneas de entrada y
   salida del pedido, con el campo `traceId`.
4. Ejecutar un flujo que cruce módulos desde Donaciones, tomar la traza de la respuesta y
   buscarla en el panel: tienen que verse líneas de más de un módulo.

## Qué hace Logística

- El filtro pone `traceId`, `instanceId` y `requestId` en cada pedido y registra la entrada y la
  salida con código y tiempo. No registra `/actuator`, que es ruido del monitor de disponibilidad.
- El interceptor reenvía la traza a Entidades, a Donaciones y a la propia API de Logística, que
  usa el Worker.
- La traza **cruza la cola**: el mensaje que se encola lleva la traza del pedido que lo originó,
  y el Worker la restaura al procesarlo. Sin eso, el trabajo asincrónico quedaría sin vincular.
- Registra los eventos de negocio: donación recibida o rechazada por capacidad, qué se asignó y
  qué fue al stock, asignaciones desde stock a pedido de Donadores, y cada entrega.
- Si un aviso a Entidades o a Donaciones falla al reportar una entrega, queda un `WARN` con el
  identificador de la necesidad o de la donación. La entrega se completa igual, por decisión de
  diseño, pero el fallo ya no pasa desapercibido.
- Una falla del propio servicio de logs no afecta a la aplicación: el appender reintenta y sigue.

## Limitaciones conocidas

- El repositorio de ejemplo de la cátedra publica solo la rama `master`, la de Better Stack. La
  rama `feature/datadog-logging` que menciona el enunciado no existe en el remoto. Si el equipo
  prefiere Datadog hay que armar el appender a partir de su documentación.
- El atributo `condition` del bloque `<if>` de Logback está deprecado y emite un aviso en el
  arranque. Es lo que usa el ejemplo de la cátedra y funciona.
- La traza cruza módulos por HTTP. Un módulo con clientes reactivos, como el bot, no la propaga
  con este mecanismo, porque el contexto de log no sigue al hilo en ese modelo.
