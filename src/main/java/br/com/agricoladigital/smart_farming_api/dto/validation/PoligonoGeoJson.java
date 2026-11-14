package br.com.agricoladigital.smart_farming_api.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Exige que o valor seja uma geometria GeoJSON do tipo {@code Polygon}, com anéis
 * fechados e coordenadas {@code [longitude, latitude]} válidas. Valor nulo é aceito.
 */
@Documented
@Constraint(validatedBy = PoligonoGeoJsonValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface PoligonoGeoJson {
    String message() default "geometria deve ser um GeoJSON do tipo Polygon válido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
