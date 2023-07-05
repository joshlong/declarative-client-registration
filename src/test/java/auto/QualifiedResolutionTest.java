package auto;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

class QualifiedResolutionTest {

	static final Logger log = LoggerFactory.getLogger(QualifiedResolutionTest.class);

	static class Foo {

	}

	@SpringBootApplication
	static class Config {

		@Bean
		Foo unqualified() {
			return new Foo();
		}

		@Bean
		@Qualifier("qualified")
		Foo qualified() {
			return new Foo();
		}

		@Bean
		ApplicationRunner both(@Qualifier("qualified") Foo qualified,
				@Autowired(required = false) List<Foo> unqualified1) {
			return a -> {
				log.info("both?");
				log.info(qualified.toString());
				if (null != unqualified1 && unqualified1.size() > 0) {
					log.info(unqualified1.iterator().next().toString());
				}
				else {
					log.info("couldn't find unqualified instance!");
				}
			};
		}

		@Bean
		ApplicationRunner all(Map<String, Foo> fooMap) {
			return a -> {
				log.info("all");
				fooMap.forEach((beanName, foo) -> log.info(beanName + '=' + foo));
			};
		}

	}

	@Test
	void resolve() {
		new SpringApplicationBuilder().web(WebApplicationType.NONE).sources(Config.class).run();
	}

}
