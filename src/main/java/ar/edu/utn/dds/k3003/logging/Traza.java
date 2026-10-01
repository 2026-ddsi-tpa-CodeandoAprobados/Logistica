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
