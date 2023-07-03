package auto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.Map;

/**
 * @author Josh Long
 */
@AutoConfiguration
class AutoClientAutoConfiguration {

	@Bean
	@ConditionalOnClass({ HttpServiceProxyFactory.class, WebClient.class })
	static AutoClientAdapter httpAutoClientAdapter() {
		return new HttpAutoClientAdapter();
	}

	@Bean
	static AutoClientRegistrar autoClientRegistrar(Map<String, AutoClientAdapter> strategy) {
		return new AutoClientRegistrar(strategy);
	}

}
