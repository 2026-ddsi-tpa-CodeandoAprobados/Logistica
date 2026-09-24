package ar.edu.utn.dds.k3003.exceptions;

/**
 * El depósito no tiene espacio para las unidades que se quieren guardar.
 *
 * <p>A diferencia de {@link SolicitudInvalidaException}, el pedido está bien formado: lo que
 * falla es el estado del depósito en este momento. El mismo pedido puede funcionar más tarde,
 * cuando se libere stock, o contra otro depósito.
 */
public class CapacidadInsuficienteException extends RuntimeException {

  private final Integer depositoID;
  private final int capacidad;
  private final int ocupado;
  private final int requerido;

  public CapacidadInsuficienteException(Integer depositoID, int capacidad, int ocupado, int requerido) {
    super("El depósito " + depositoID + " no tiene espacio para la donación"
            + " (capacidad " + capacidad + ", ocupado " + ocupado + ", requiere " + requerido + ")");
    this.depositoID = depositoID;
    this.capacidad = capacidad;
    this.ocupado = ocupado;
    this.requerido = requerido;
  }

  public Integer getDepositoID() {
    return depositoID;
  }

  public int getCapacidad() {
    return capacidad;
  }

  public int getOcupado() {
    return ocupado;
  }

  public int getRequerido() {
    return requerido;
  }

  /** Unidades que faltan liberar para que la donación entre. */
  public int getFaltante() {
    return (ocupado + requerido) - capacidad;
  }
}
