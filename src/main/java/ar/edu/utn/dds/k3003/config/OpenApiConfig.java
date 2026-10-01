package ar.edu.utn.dds.k3003.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Especificación OpenAPI del módulo Logística.
 *
 * <p>Los DTO de {@code catedra/} son protegidos y no se pueden anotar, así que sus descripciones
 * se agregan acá, sobre el esquema ya generado, en vez de en las clases.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "DonaTrack - Logística",
                version = "5.0",
                description = """
                        Gestiona depósitos, stock de paquetes donados, matchmaking entre donaciones
                        y necesidades, y entregas. Se integra con Donaciones, Donadores y Entidades,
                        e Incentivos.

                        Los errores se devuelven siempre como application/problem+json (RFC 7807),
                        con el motivo en el campo detail."""),
        tags = {
                @Tag(name = "Depósitos", description = "Alta, baja, consulta y configuración del algoritmo de matchmaking."),
                @Tag(name = "Donaciones", description = "Recepción de una donación en un depósito."),
                @Tag(name = "Stock", description = "Stock disponible por producto y asignación a pedido de Donadores."),
                @Tag(name = "Asignaciones", description = "Vínculo entre un paquete y la necesidad a la que se destinó."),
                @Tag(name = "Paquetes", description = "Porciones de donaciones ya asignadas a una necesidad."),
                @Tag(name = "Entregas", description = "Reporte de entrega de un paquete."),
                @Tag(name = "Interno (Worker)", description = "Endpoints que usa el Worker asincrónico, que no tiene base de datos propia."),
                @Tag(name = "Testing", description = "Utilidades para preparar la demostración.")
        })
public class OpenApiConfig {

    /** Descripción de cada DTO de la cátedra y de sus campos, por nombre de esquema. */
    private static final Map<String, Map<String, String>> DESCRIPCIONES = Map.of(
            "DepositoDTO", Map.of(
                    "_", "Depósito donde se almacena el stock donado.",
                    "id", "Identificador numérico, generado por Logística.",
                    "algoritmo", "Algoritmo de matchmaking. Si es nulo se usa SUB_ATENDIDOS.",
                    "nombre", "Nombre del depósito.",
                    "direccion", "Dirección física del depósito.",
                    "capacidadMaxima", "Unidades máximas que puede almacenar. Nulo significa sin límite.",
                    "stockActual", "Paquetes que hoy están en stock en este depósito."),
            "PaqueteDTO", Map.of(
                    "_", "Porción de una donación. Puede estar en stock o asignada a una necesidad.",
                    "id", "Identificador numérico del paquete.",
                    "donacionID", "Donación de la que proviene. Un paquete pertenece a una sola donación.",
                    "producto", "Identificador del producto, el mismo que usa Donaciones.",
                    "cantidad", "Unidades del producto en el paquete."),
            "AsignacionDTO", Map.of(
                    "_", "Destino de un paquete: la necesidad que va a cubrir.",
                    "id", "Identificador de la asignación (UUID).",
                    "paqueteID", "Paquete asignado.",
                    "necesidadID", "Necesidad de Donadores y Entidades que se cubre.",
                    "fecha", "Momento en que se creó la asignación.",
                    "estado", "ASIGNADA hasta que se reporta la entrega, después COMPLETADA.",
                    "origen", "MATCHMAKING si la eligió Logística al recibir la donación, "
                            + "SOLICITUD_DONADORES si la pidió Donadores contra el stock."),
            "DonacionDTO", Map.of(
                    "_", "Donación tal como la envía el módulo Donaciones.",
                    "id", "Identificador de la donación en Donaciones.",
                    "donadorID", "Donador que realiza la donación.",
                    "depositoID", "Depósito de destino. Es obligatorio y tiene que existir.",
                    "descripcion", "Texto libre de la donación.",
                    "detallesProductosDTO", "Productos donados con su cantidad. No puede estar vacío.",
                    "estado", "Estado en Donaciones. Logística no lo modifica al recibirla.",
                    "fechaRegistro", "Fecha en que se registró la donación."),
            "DetalleProductoDTO", Map.of(
                    "_", "Un producto dentro de una donación.",
                    "id", "Identificador del detalle en Donaciones.",
                    "productoID", "Producto donado.",
                    "cantidadProducto", "Unidades donadas. Tiene que ser mayor a cero."));

    @Bean
    public OpenApiCustomizer descripcionesDeLosDtoDeLaCatedra() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            DESCRIPCIONES.forEach((esquema, campos) -> {
                Schema<?> schema = openApi.getComponents().getSchemas().get(esquema);
                if (schema == null) {
                    return;
                }
                schema.setDescription(campos.get("_"));
                if (schema.getProperties() == null) {
                    return;
                }
                campos.forEach((campo, descripcion) -> {
                    Schema<?> propiedad = (Schema<?>) schema.getProperties().get(campo);
                    if (propiedad != null) {
                        propiedad.setDescription(descripcion);
                    }
                });
            });
        };
    }
}
