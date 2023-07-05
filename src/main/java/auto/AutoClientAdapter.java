package auto;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * Strategy pattern to identify which kind of declarative clients we're dealing with:
 * HTTP, RSocket, etc.
 *
 * @author Josh Long
 */
public interface AutoClientAdapter {

	boolean test(Class<?> c);

	<T> T createClient(BeanDefinitionRegistry registry, Class<T> c);

}
