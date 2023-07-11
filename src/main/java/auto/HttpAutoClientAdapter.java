package auto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Handles creating HTTP clients
 *
 * @author Josh Long
 */
@Slf4j
class HttpAutoClientAdapter extends AbstractAutoClientAdapter {

	private final MergedAnnotations.Search mergedAnnotations = MergedAnnotations
		.search(MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);

	@Override
	public boolean test(Class<?> aClass) {
		return this.mergedAnnotations.from(aClass).get(AutoClient.class).isPresent()
				&& this.mergedAnnotations.from(aClass).get(HttpExchange.class).isPresent();
	}

	@Override
	public <T> T createClient(BeanDefinitionRegistry registry, Class<T> c) {
		Assert.isInstanceOf(ListableBeanFactory.class, registry, "the " + BeanDefinitionRegistry.class.getName()
				+ " is not an instance of " + BeanFactory.class.getName());
		var factory = (ListableBeanFactory) registry;
		var webClient = resolveDependency(factory, c, WebClient.class);
		var wca = WebClientAdapter.forClient(webClient);
		return (T) HttpServiceProxyFactory.builder(wca).build().createClient((Class<?>) c);
	}

}
