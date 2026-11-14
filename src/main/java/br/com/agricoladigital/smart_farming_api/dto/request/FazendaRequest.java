package br.com.agricoladigital.smart_farming_api.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FazendaRequest(
        @NotBlank @Size(max = 150) String nome,
        @Size(max = 255) String localizacao,
        @NotNull @Positive Double areaTotal,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 100) String cidade,
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "estado deve ser a sigla da UF com 2 letras") String estado
) {
    @AssertTrue(message = "latitude e longitude devem ser informadas juntas")
    public boolean isCoordenadasCompletas() {
        return (latitude == null) == (longitude == null);
    }
}
