package ar.edu.utn.dds.k3003.controllers.docs;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.ProblemDetail;

/** Documenta el 400 estándar del módulo: el pedido llegó mal formado. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(
        responseCode = "400",
        description = "Falta un dato obligatorio, una cantidad no es positiva, un id no es "
                + "numérico o el cuerpo no es un JSON válido. El campo detail indica el motivo.",
        content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
public @interface ErrorSolicitudInvalida {
}
