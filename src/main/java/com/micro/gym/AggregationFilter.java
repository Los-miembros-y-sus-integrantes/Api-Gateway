 package com.micro.gym;

 import java.util.HashMap;
 import java.util.Map;

 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.cloud.gateway.filter.GatewayFilter;
 import org.springframework.cloud.gateway.filter.GatewayFilterChain;
 import org.springframework.http.HttpStatus;
 import org.springframework.http.MediaType;
 import org.springframework.stereotype.Component;
 import org.springframework.web.reactive.function.client.WebClient;
 import org.springframework.web.server.ServerWebExchange;

 import com.fasterxml.jackson.core.JsonProcessingException;
 import com.fasterxml.jackson.databind.ObjectMapper;

 import reactor.core.publisher.Mono;

 @Component
 public class AggregationFilter implements GatewayFilter {

     @Autowired
     private WebClient.Builder webClientBuilder;
    
     @Override
     public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

         if (exchange.getRequest().getPath().value().equals("/aggregated-info")) {
             return aggregateResponses(exchange);
         }

         return chain.filter(exchange);
     }


     private Mono<Void> aggregateResponses(ServerWebExchange exchange) {
         String currentURI = exchange.getRequest().getURI().toString().split("aggregated-info")[0];
         Mono<String> catalogoInfo = webClientBuilder.build().get()
             .uri(currentURI + "api/member/miembros")
                 .headers(httpHeaders -> httpHeaders.addAll(exchange.getRequest().getHeaders()))
             .retrieve()
             .bodyToMono(String.class);

         Mono<String> circulacionInfo = webClientBuilder.build().get()
             .uri(currentURI + "api/class/clases")
                    .headers(httpHeaders -> httpHeaders.addAll(exchange.getRequest().getHeaders()))
             .retrieve()
             .bodyToMono(String.class);

         return Mono.zip(catalogoInfo, circulacionInfo, (catalogo, circulacion) -> {
                 Map<String, String> result = new HashMap<>();
                 result.put("miembros", catalogo);
                 result.put("clases", circulacion);
                 return result;
             })
             .flatMap(result -> {
                 exchange.getResponse().setStatusCode(HttpStatus.OK);
                 exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                 try {
                     return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                         .bufferFactory().wrap(new ObjectMapper().writeValueAsBytes(result))));
                 } catch (JsonProcessingException e) {
                     return Mono.error(e);
                 }
             });
     }
 }