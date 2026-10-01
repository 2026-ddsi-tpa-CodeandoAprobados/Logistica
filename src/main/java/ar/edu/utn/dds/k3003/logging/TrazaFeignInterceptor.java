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
