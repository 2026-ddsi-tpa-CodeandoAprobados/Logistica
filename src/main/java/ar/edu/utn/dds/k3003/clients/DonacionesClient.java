package ar.edu.utn.dds.k3003.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import ar.edu.utn.dds.k3003.catedra.dtos.donaciones.DonacionDTO;

@FeignClient(name = "donacionesApi", url = "${DONACIONES_URL}")
public interface DonacionesClient {

    // NUEVO ENDPOINT PARA VALIDAR LA DONACIÓN
    @GetMapping("/donaciones/{id}")
    DonacionDTO buscarDonacionPorId(@PathVariable("id") String id);

    @PatchMapping("/donaciones/{id}/estado")
    DonacionDTO actualizarEstadoDonacion(
            @PathVariable("id") String donacionID,
            @RequestBody EstadoDonacionRequest request
    );
}