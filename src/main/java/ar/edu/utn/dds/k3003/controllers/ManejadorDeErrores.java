package ar.edu.utn.dds.k3003.controllers;

import ar.edu.utn.dds.k3003.exceptions.CapacidadInsuficienteException;
import ar.edu.utn.dds.k3003.exceptions.RecursoNoEncontradoException;
import ar.edu.utn.dds.k3003.exceptions.SolicitudInvalidaException;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce las excepciones de dominio a códigos HTTP en un solo lugar, para que los endpoints
 * no repitan bloques try/catch y no se pierda el mensaje del error.
 *
 * <p>Responde con {@link ProblemDetail} (RFC 7807), así el que llama recibe siempre un cuerpo
 * con el motivo en vez de una respuesta vacía. Extiende {@link ResponseEntityExceptionHandler}
 * para que las excepciones propias de Spring MVC (JSON mal formado, ruta inexistente, método no
 * soportado) conserven su tratamiento estándar y no caigan en el 500 genérico de abajo.
 */
@RestControllerAdvice
public class ManejadorDeErrores extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

  /** No existe el depósito, el paquete o la asignación que se pidió. */
  @ExceptionHandler(RecursoNoEncontradoException.class)
  public ProblemDetail noEncontrado(RecursoNoEncontradoException e) {
    return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", e.getMessage());
  }

  /** Falta un dato obligatorio o una cantidad no es válida. */
  @ExceptionHandler(SolicitudInvalidaException.class)
  public ProblemDetail solicitudInvalida(SolicitudInvalidaException e) {
    return problema(HttpStatus.BAD_REQUEST, "Solicitud inválida", e.getMessage());
  }

  /**
   * El depósito está lleno. Es 409 y no 400 porque el pedido está bien formado: lo que falla
   * es el estado actual del depósito, y el mismo pedido puede prosperar más tarde.
   */
  @ExceptionHandler(CapacidadInsuficienteException.class)
  public ProblemDetail capacidadInsuficiente(CapacidadInsuficienteException e) {
    ProblemDetail detalle = problema(HttpStatus.CONFLICT, "Capacidad insuficiente", e.getMessage());
    detalle.setProperty("depositoID", e.getDepositoID());
    detalle.setProperty("capacidad", e.getCapacidad());
    detalle.setProperty("ocupado", e.getOcupado());
    detalle.setProperty("requerido", e.getRequerido());
    detalle.setProperty("faltante", e.getFaltante());
    return detalle;
  }

  /** Red de contención para cualquier orElseThrow que todavía no esté tipado. */
  @ExceptionHandler(NoSuchElementException.class)
  public ProblemDetail elementoInexistente(NoSuchElementException e) {
    return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", e.getMessage());
  }

  /** Un identificador que debía ser numérico no lo era. */
  @ExceptionHandler(NumberFormatException.class)
  public ProblemDetail identificadorInvalido(NumberFormatException e) {
    return problema(HttpStatus.BAD_REQUEST, "Identificador inválido", e.getMessage());
  }

  /**
   * Cualquier otra cosa. El detalle técnico va al log con su stack trace y al cliente sólo le
   * llega un mensaje genérico: el texto de una excepción inesperada expone clases y versiones
   * internas, y sin el log el fallo queda invisible del lado del servidor.
   */
  @ExceptionHandler(Exception.class)
  public ProblemDetail errorInesperado(Exception e) {
    log.error("Error inesperado procesando el pedido", e);
    return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno de Logística",
            "Ocurrió un error inesperado. Revisá los logs del servicio.");
  }

  private ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
    ProblemDetail problema = ProblemDetail.forStatus(estado);
    problema.setTitle(titulo);
    problema.setDetail(detalle);
    return problema;
  }
}
