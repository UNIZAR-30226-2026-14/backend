package com.rummikub.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:rummiplus_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"server.ssl.enabled=false",
		"server.port=0",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class ServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
