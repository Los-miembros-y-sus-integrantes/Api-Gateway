package com.micro.gym.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntrenadorWithClasesDTO {
    private Long id;
    private String nombre;
    private String especialidad;
    private List<ClaseSimpleDTO> clases;
}