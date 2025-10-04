package com.micro.gym.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micro.gym.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AggregatorGatewayFilterFactory extends AbstractGatewayFilterFactory<AggregatorGatewayFilterFactory.Config> {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AggregatorGatewayFilterFactory(WebClient webClient) {
        super(Config.class);
        this.webClient = webClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // Para soportar LocalDateTime
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            log.info("Procesando agregación para path: {}", path);

            if (path.contains("/api/aggregate/classes-with-trainers")) {
                return aggregateClassesWithTrainers(exchange.getResponse());
            } else if (path.contains("/api/aggregate/trainers-with-classes")) {
                return aggregateTrainersWithClasses(exchange.getResponse());
            }

            // Si no es una ruta de agregación, continuar con el filtro normal
            return chain.filter(exchange);
        };
    }

    private Mono<Void> aggregateClassesWithTrainers(ServerHttpResponse response) {
        return webClient.get()
                .uri("http://class-service/clases")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseClasesResponse)
                .doOnNext(clases -> log.info("Clases obtenidas: {}", clases.size()))
                .doOnError(error -> log.error("Error obteniendo clases: ", error))
                .onErrorReturn(List.of()) // Retorna lista vacía en caso de error
                .flatMap(clases -> {
                    String jsonResponse = convertToJson(Map.of(
                            "success", true,
                            "message", "Clases con información de entrenadores",
                            "data", clases
                    ));
                    return writeResponse(response, jsonResponse);
                })
                .onErrorResume(error -> {
                    log.error("Error en agregación de clases: ", error);
                    String errorResponse = convertToJson(Map.of(
                            "success", false,
                            "message", "Error obteniendo información de clases",
                            "error", error.getMessage()
                    ));
                    return writeResponse(response, errorResponse);
                });
    }

    private Mono<Void> aggregateTrainersWithClasses(ServerHttpResponse response) {
        Mono<List<EntrenadorDTO>> trainersMono = webClient.get()
                .uri("http://trainer-service/entrenadores")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseTrainersResponse)
                .doOnNext(trainers -> log.info("Entrenadores obtenidos: {}", trainers.size()))
                .doOnError(error -> log.error("Error obteniendo entrenadores: ", error))
                .onErrorReturn(List.of());

        Mono<List<ClaseDTO>> clasesMono = webClient.get()
                .uri("http://class-service/clases")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseClasesResponse)
                .doOnNext(clases -> log.info("Clases obtenidas para agregación: {}", clases.size()))
                .doOnError(error -> log.error("Error obteniendo clases para agregación: ", error))
                .onErrorReturn(List.of());

        return Mono.zip(trainersMono, clasesMono)
                .map(tuple -> {
                    List<EntrenadorDTO> trainers = tuple.getT1();
                    List<ClaseDTO> clases = tuple.getT2();

                    log.info("Procesando {} entrenadores y {} clases", trainers.size(), clases.size());

                    // Agrupar clases por entrenador ID
                    Map<Long, List<ClaseSimpleDTO>> clasesPorEntrenador = clases.stream()
                            .filter(clase -> clase.getEntrenador() != null)
                            .collect(Collectors.groupingBy(
                                    clase -> clase.getEntrenador().getId(),
                                    Collectors.mapping(clase -> ClaseSimpleDTO.builder()
                                            .id(clase.getId())
                                            .nombre(clase.getNombre())
                                            .horario(clase.getHorario())
                                            .capacidadMaxima(clase.getCapacidadMaxima())
                                            .build(), Collectors.toList())
                            ));

                    // Crear DTOs agregados
                    List<EntrenadorWithClasesDTO> result = trainers.stream()
                            .map(trainer -> EntrenadorWithClasesDTO.builder()
                                    .id(trainer.getId())
                                    .nombre(trainer.getNombre())
                                    .especialidad(trainer.getEspecialidad())
                                    .clases(clasesPorEntrenador.getOrDefault(trainer.getId(), List.of()))
                                    .build())
                            .collect(Collectors.toList());

                    return Map.of(
                            "success", true,
                            "message", "Entrenadores con sus clases asignadas",
                            "data", result
                    );
                })
                .flatMap(aggregatedData -> {
                    String jsonResponse = convertToJson(aggregatedData);
                    return writeResponse(response, jsonResponse);
                })
                .onErrorResume(error -> {
                    log.error("Error en agregación de entrenadores: ", error);
                    String errorResponse = convertToJson(Map.of(
                            "success", false,
                            "message", "Error obteniendo información agregada",
                            "error", error.getMessage()
                    ));
                    return writeResponse(response, errorResponse);
                });
    }

    private Mono<Void> writeResponse(ServerHttpResponse response, String jsonResponse) {
        DataBuffer buffer = response.bufferFactory().wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
        response.getHeaders().add("Content-Type", "application/json");
        response.setStatusCode(HttpStatus.OK);
        return response.writeWith(Mono.just(buffer));
    }

    private List<ClaseDTO> parseClasesResponse(String response) {
        try {
            log.debug("Parseando respuesta de clases: {}", response);
            return objectMapper.readValue(response, new TypeReference<List<ClaseDTO>>() {});
        } catch (Exception e) {
            log.error("Error parseando respuesta de clases: ", e);
            return List.of();
        }
    }

    private List<EntrenadorDTO> parseTrainersResponse(String response) {
        try {
            log.debug("Parseando respuesta de entrenadores: {}", response);
            return objectMapper.readValue(response, new TypeReference<List<EntrenadorDTO>>() {});
        } catch (Exception e) {
            log.error("Error parseando respuesta de entrenadores: ", e);
            return List.of();
        }
    }

    private String convertToJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Error convirtiendo a JSON: ", e);
            return "{\"success\":false,\"message\":\"Error interno\"}";
        }
    }

    public static class Config {
        // Configuración adicional si es necesaria
    }
}