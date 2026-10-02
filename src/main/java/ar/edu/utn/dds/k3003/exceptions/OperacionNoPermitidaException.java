package ar.edu.utn.dds.k3003.exceptions;

/**
 * La operación está bien formada pero el estado actual de los datos no la permite: reportar una
 * entrega que ya se reportó, eliminar un depósito que todavía tiene stock, o bajar la capacidad
 * de un depósito por debajo de lo que ya almacena.
 *
 * <p>Se distingue de {@link SolicitudInvalidaException} porque acá el pedido es válido: lo que
 * lo impide es el estado del sistema, y puede prosperar si ese estado cambia.
 */
public class OperacionNoPermitidaException extends RuntimeException {

  public OperacionNoPermitidaException(String mensaje) {
    super(mensaje);
  }
}
