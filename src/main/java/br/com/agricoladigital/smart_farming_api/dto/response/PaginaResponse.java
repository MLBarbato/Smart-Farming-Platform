package br.com.agricoladigital.smart_farming_api.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/** Página de resultados; {@code pagina} começa em 0. */
public record PaginaResponse<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {
    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }
}
