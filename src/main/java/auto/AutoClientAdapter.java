package auto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.Assert;

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

@Slf4j
abstract class AbstractAutoClientAdapter implements AutoClientAdapter {

	protected <T> T resolveDependency(ListableBeanFactory registry, Class<?> clientInterface, Class<T> tClass) {

		var qualifier = MergedAnnotations.from(clientInterface).get(Qualifier.class);
		if (qualifier.isPresent()) {
			var valueOfQualifierAnnotation = qualifier.getString(MergedAnnotation.VALUE);
			var beans = BeanFactoryAnnotationUtils.qualifiedBeansOfType(registry, tClass, valueOfQualifierAnnotation);
			Assert.state(beans.size() == 1, () -> "expected only one qualified instance of " + tClass.getName() + '.');
			for (var e : beans.entrySet()) {
				log.debug("returning bean {} for type {} for interface {}", e.getKey(), tClass.getName(),
						clientInterface.getName());
				return e.getValue();
			}
			Assert.state(false,
					"the interface " + clientInterface.getName()
							+ " has a qualifier but we could not find a qualified instance of " + tClass.getName()
							+ ". Consider defining one.");
		}
		//
		// todo how to support @Primary?
		var map = registry.getBeansOfType(tClass);
		Assert.state(map.size() == 1, "there should be only one unambiguous bean of type " + tClass.getName());
		for (var e : map.entrySet()) {
			log.debug("returning bean {} for type {} for interface {}", e.getKey(), tClass.getName(),
					clientInterface.getName());
			return e.getValue();
		}

		throw new IllegalStateException("we could not resolve any bean of type [" + tClass.getName() + "]");
	}

}