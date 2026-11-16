package br.com.agricoladigital.smart_farming_api;

import br.com.agricoladigital.smart_farming_api.integracao.IntegracaoPostgresBase;
import org.junit.jupiter.api.Test;

/** A aplicação sobe por completo (Flyway + validação do esquema JPA) em um PostgreSQL de teste. */
class SmartFarmingApiApplicationTests extends IntegracaoPostgresBase {

	@Test
	void contextLoads() {
	}

}
