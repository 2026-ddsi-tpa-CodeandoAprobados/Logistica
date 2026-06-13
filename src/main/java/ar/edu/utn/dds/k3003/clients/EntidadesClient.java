package ar.edu.utn.dds.k3003.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ar.edu.utn.dds.k3003.catedra.dtos.donadoresYEntidades.NecesidadMaterialDTO;
import java.util.List;
import java.util.Map;

@FeignClient(name = "entidadesApi", url = "${ENTIDADES_URL}")
public interface EntidadesClient {

    @GetMapping("/necesidades/{productoID}")
    List<NecesidadMaterialDTO> getAllNecesidadesDeUnProducto(@PathVariable("productoID") String productoID);

    @PostMapping("/necesidades/{necesidadID}/satisfaccion")
    NecesidadMaterialDTO postSatisfacerNecesidad(
            @PathVariable("necesidadID") String necesidadID,
            @RequestBody Map<String, Integer> requestBody
    );
}