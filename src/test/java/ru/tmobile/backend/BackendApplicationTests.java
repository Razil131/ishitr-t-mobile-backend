package ru.tmobile.backend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class BackendApplicationTests {

	@Test
	void applicationHasPublicStaticMainEntryPoint() throws Exception {
		Method main = BackendApplication.class.getMethod("main", String[].class);
		assertTrue(Modifier.isPublic(main.getModifiers()));
		assertTrue(Modifier.isStatic(main.getModifiers()));
		assertTrue(main.getReturnType().equals(void.class));
	}

	@Test
	void applicationIsMarkedAsSpringBootApplication() {
		assertTrue(BackendApplication.class.isAnnotationPresent(SpringBootApplication.class));
	}
}
