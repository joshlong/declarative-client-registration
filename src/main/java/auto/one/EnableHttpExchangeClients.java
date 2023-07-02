package auto.one;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(HttpExchangeAbstractDeclarativeClientRegistrar.class)
public @interface EnableHttpExchangeClients {

}
