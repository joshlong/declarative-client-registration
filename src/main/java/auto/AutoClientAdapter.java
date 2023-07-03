package auto;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * Strategy pattern to identify which kind of declarative clients we're dealing with:
 * HTTP, RSocket, etc.
 *
 * @author Josh Long
 */
public interface AutoClientAdapter {

	/**
	 * is this adapter applicable to the given interface?
	 */
	boolean test(Class<?> c);

	/**
	 * Given a class type and a {@link BeanDefinitionRegistry}, figure out how to register
	 * the type
	 */
	<T> T createSupplierForInterface(BeanDefinitionRegistry registry, Class<T> c);

}
