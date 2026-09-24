package ar.edu.utn.dds.k3003.exceptions;

/**
 * Ninguna de las necesidades que reportó Entidades se puede cubrir con esta donación.
 * El caso típico es que sólo haya necesidades recurrentes, que no admiten cobertura parcial.
 *
 * <p>No es un error de cara al usuario: la recepción de la donación la usa como señal para
 * mandar todo al stock. Por eso no tiene un código HTTP asociado en el manejador de errores.
 */
public class SinNecesidadElegibleException extends RuntimeException {

  public SinNecesidadElegibleException(String mensaje) {
    super(mensaje);
  }
}
