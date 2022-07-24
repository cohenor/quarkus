package io.quarkus.test.junit.main;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkus.test.junit.QuarkusMainServerIntegrationTestExtension;

@Target(ElementType.TYPE)
@ExtendWith({ QuarkusMainServerIntegrationTestExtension.class })
@Retention(RetentionPolicy.RUNTIME)
public @interface QuarkusMainServerIntegrationTest {

}
