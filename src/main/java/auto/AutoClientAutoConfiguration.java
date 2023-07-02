package auto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Josh Long
 */
@AutoConfiguration
class AutoClientAutoConfiguration {

	@Bean
	@ConditionalOnClass({ WebClient.class })
	static HttpClientClientDetectionStrategy detectionStrategy() {
		return new HttpClientClientDetectionStrategy();
	}

	@Bean
	static AutoClientRegistrar autoClientRegistrar(ClientDetectionStrategy strategy) {
		return new AutoClientRegistrar(strategy);
	}

}
