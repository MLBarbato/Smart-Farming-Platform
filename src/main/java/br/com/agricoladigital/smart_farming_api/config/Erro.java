package br.com.agricoladigital.smart_farming_api.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documenta, no contrato OpenAPI, uma resposta de erro específica de uma operação (por exemplo, 409 de uma
 * transição inválida). É lida por {@link OpenApiConfig}, que a liga ao formato padrão de erro. Existe porque
 * declarar respostas em {@code @Operation} faz o springdoc omitir a resposta de sucesso gerada do retorno.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Erro.Lista.class)
public @interface Erro {
    String codigo();

    String descricao();

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Lista {
        Erro[] value();
    }
}
