package ar.edu.utn.dds.k3003.model;

import jakarta.persistence.*;

@Entity
public class Paquete {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String externalId;
    private String donacionId;
    private String subcategoriaId;
    private Integer cantidad;

    public Paquete() {
    }

    public Paquete(String donacionId, String subcategoriaId, Integer cantidad) {
        this.donacionId = donacionId;
        this.subcategoriaId = subcategoriaId;
        this.cantidad = cantidad;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getDonacionId() {
        return donacionId;
    }

    public void setDonacionId(String donacionId) {
        this.donacionId = donacionId;
    }

    public String getSubcategoriaId() {
        return subcategoriaId;
    }

    public void setSubcategoriaId(String subcategoriaId) {
        this.subcategoriaId = subcategoriaId;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }
}
