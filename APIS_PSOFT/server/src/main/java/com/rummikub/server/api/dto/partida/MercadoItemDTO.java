package com.rummikub.server.api.dto.partida;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MercadoItemDTO {
    private String codigo;
    private int valor;
    private int unidadesDisponibles;
}
