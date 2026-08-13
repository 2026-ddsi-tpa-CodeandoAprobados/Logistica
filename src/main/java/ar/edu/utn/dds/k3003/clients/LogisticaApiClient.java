package ar.edu.utn.dds.k3003.clients;

import ar.edu.utn.dds.k3003.catedra.dtos.logistica.AsignacionDTO;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.DepositoDTO;
import ar.edu.utn.dds.k3003.messaging.AltaAsignacionRequest;
import ar.edu.utn.dds.k3003.messaging.GuardarStockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Cliente HTTP contra la propia API de Logística. Lo usa el Worker (stateless, sin BD)
 * para persistir asignaciones y sobrantes de stock. Apunta a ${LOGISTICA_URL}, con lo cual
 * un Worker separado puede levantarse en otro proceso y escribir contra esta instancia.
 */
@FeignClient(name = "logisticaSelfApi", url = "${logistica.url}")
public interface LogisticaApiClient {

    @PostMapping("/asignaciones")
    AsignacionDTO altaAsignacion(@RequestBody AltaAsignacionRequest request);

    @PostMapping("/depositos/{id}/stock")
    DepositoDTO guardarSobrante(@PathVariable("id") String depositoID,
                                @RequestBody GuardarStockRequest request);
}
