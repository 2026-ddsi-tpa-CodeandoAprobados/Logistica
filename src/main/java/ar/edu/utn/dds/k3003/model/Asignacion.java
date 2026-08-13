package ar.edu.utn.dds.k3003.model;

import jakarta.persistence.*;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.EstadoAsginacionEnum;
import ar.edu.utn.dds.k3003.catedra.dtos.logistica.OrigenAsignacionEnum;
import java.time.LocalDateTime;

@Entity
public class Asignacion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String paqueteID;
    private String necesidadID;
    private LocalDateTime fecha;
    private EstadoAsginacionEnum estado;
    @Enumerated(EnumType.STRING)
    private OrigenAsignacionEnum origen;

    public Asignacion(){
    }

    public Asignacion(String paqueteID, String necesidadID) {
        this(paqueteID, necesidadID, OrigenAsignacionEnum.MATCHMAKING);
    }

    public Asignacion(String paqueteID, String necesidadID, OrigenAsignacionEnum origen) {
        this.paqueteID = paqueteID;
        this.necesidadID = necesidadID;
        this.fecha = LocalDateTime.now();
        this.estado = EstadoAsginacionEnum.ASIGNADA;
        this.origen = origen;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPaqueteID() {
        return paqueteID;
    }

    public void setPaqueteID(String paqueteID) {
        this.paqueteID = paqueteID;
    }

    public String getNecesidadID() {
        return necesidadID;
    }

    public void setNecesidadID(String necesidadID) {
        this.necesidadID = necesidadID;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public EstadoAsginacionEnum getEstado() {
        return estado;
    }

    public void setEstado(EstadoAsginacionEnum estado) {
        this.estado = estado;
    }

    public OrigenAsignacionEnum getOrigen() {
        return origen;
    }

    public void setOrigen(OrigenAsignacionEnum origen) {
        this.origen = origen;
    }
}
