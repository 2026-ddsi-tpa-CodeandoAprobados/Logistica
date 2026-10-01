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
