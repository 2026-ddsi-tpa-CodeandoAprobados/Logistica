package ar.edu.utn.dds.k3003.controllers.docs;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.ProblemDetail;

/** Documenta el 409 de estado: el pedido es válido pero el estado de los datos no lo permite. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "409",
        description = "El pedido es válido, pero el estado actual de los datos no permite "
                + "hacerlo. El campo detail explica por qué.",
        content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
public @interface ErrorOperacionNoPermitida {
}
