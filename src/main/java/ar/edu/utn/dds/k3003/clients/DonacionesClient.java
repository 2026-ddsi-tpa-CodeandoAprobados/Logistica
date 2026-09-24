package ar.edu.utn.dds.k3003.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;

/**
 * Cliente HTTP hacia el módulo Donaciones.
 *
 * <p>No expone una búsqueda de donación a propósito: Donaciones llama a Logística dentro
 * de su propia transacción, así que un GET de vuelta no ve la fila todavía y termina en
 * un rollback del otro lado. Ver la nota de {@code gestionarDonacion} en la fachada.
 */
@FeignClient(name = "donacionesApi", url = "${DONACIONES_URL}")
public interface DonacionesClient {

    @PatchMapping("/donaciones/{id}/estado")
    DonacionDTO actualizarEstadoDonacion(
            @PathVariable("id") String donacionID,
            @RequestBody EstadoDonacionRequest request
    );
}