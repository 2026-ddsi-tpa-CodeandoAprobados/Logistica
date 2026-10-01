package ar.edu.utn.dds.k3003.controllers.docs;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.ProblemDetail;

/** Documenta el 409 del módulo: el depósito no tiene espacio para las unidades pedidas. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "409",
        description = "El depósito no tiene espacio. Además de detail, el cuerpo trae depositoID, "
                + "capacidad, ocupado, requerido y faltante. El mismo pedido puede prosperar "
                + "más tarde, cuando se libere stock.",
        content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
public @interface ErrorCapacidadInsuficiente {
}
