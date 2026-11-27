package br.com.xepamais.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Sobe a aplicação inteira com um H2 em memória (não precisa do MySQL nem de DB_URL).
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:contexto;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false"
})
class ApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
