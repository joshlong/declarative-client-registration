package auto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * @author Josh Long
 */
@AutoConfiguration
class AutoClientAutoConfiguration {

	@Bean
	static AutoClientAdapter httpAutoClientAdapter() {
		return new HttpAutoClientAdapter();
	}

	@Bean
	static AutoClientRegistrar autoClientRegistrar(Map<String, AutoClientAdapter> strategy) {
		return new AutoClientRegistrar(strategy);
	}

}
