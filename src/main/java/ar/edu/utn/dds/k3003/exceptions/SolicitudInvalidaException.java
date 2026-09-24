package ar.edu.utn.dds.k3003.exceptions;

/**
 * El pedido llegó mal formado: falta un dato obligatorio, una cantidad no es positiva
 * o un identificador no tiene el formato esperado. Es culpa de quien llama, no del estado
 * del sistema, así que reintentarlo igual no cambia nada.
 */
public class SolicitudInvalidaException extends RuntimeException {

  public SolicitudInvalidaException(String mensaje) {
    super(mensaje);
  }
}
