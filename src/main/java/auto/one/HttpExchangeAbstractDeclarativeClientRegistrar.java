package auto.one;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

class HttpExchangeAbstractDeclarativeClientRegistrar extends AbstractDeclarativeClientRegistrar {

	private final static Logger log = LoggerFactory.getLogger(HttpExchangeAbstractDeclarativeClientRegistrar.class);

	// todo extract this out to take the class for a FactoryBean that descends from a
	// AbstractFactoryBean
	// that we provide that accepts the interface type and expects a valid result. that
	// is, this could be reused
	static class HttpExchangeRegisteringBiConsumer
			implements BiConsumer<AnnotatedBeanDefinition, BeanDefinitionRegistry> {

		@Override
		public void accept(AnnotatedBeanDefinition annotatedBeanDefinition, BeanDefinitionRegistry registry) {
			Class<DeclarativeClientFactoryBean> declarativeClientFactoryBeanClass = DeclarativeClientFactoryBean.class;
			BeanDefinitionBuilder definition = BeanDefinitionBuilder
				.genericBeanDefinition(declarativeClientFactoryBeanClass);
			String implementingClassName = annotatedBeanDefinition.getMetadata().getClassName();
			definition.addPropertyValue("type", implementingClassName);
			definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

			AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
			beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, implementingClassName);
			beanDefinition.setPrimary(true);
			beanDefinition.setBeanClass(declarativeClientFactoryBeanClass);
			BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, implementingClassName,
					new String[0]);
			BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
		}

	}

	// todo create a reusable, parameterized version of this so that implementing it for
	// other technologies is a matter of swaping on the annotations
	// todo expand this to use MergedAnnotations to look for the annotations on the type
	// OR any of its methods
	static class HttpExchangePredicate implements Predicate<AnnotatedBeanDefinition> {

		@Override
		public boolean test(AnnotatedBeanDefinition beanDefinition) {

			boolean match = beanDefinition.getMetadata().getAnnotationTypes().contains(HttpExchange.class.getName());
			if (match && log.isDebugEnabled()) {
				log.debug("going to return true for " + beanDefinition.getBeanClassName() + '.');
			}
			return match;
		}

	}

	HttpExchangeAbstractDeclarativeClientRegistrar() {
		super(new HttpExchangeAbstractDeclarativeClientRegistrar.HttpExchangePredicate(),
				EnableHttpExchangeClients.class,
				new HttpExchangeAbstractDeclarativeClientRegistrar.HttpExchangeRegisteringBiConsumer());
	}

}

class ManualHintsRegistrar implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		try {
			hints.proxies().registerJdkProxy(AopProxyUtils.completeJdkProxyInterfaces(Class.forName("demo.Todos")));
		}
		catch (ClassNotFoundException e) {
			///
		}
	}

}

@ImportRuntimeHints(ManualHintsRegistrar.class)
@Configuration
class DeclarativeClientAotConfiguration {

	@Bean
	static DeclarativeClientAotProcessor exchangeAotPostProcessor() {
		return new DeclarativeClientAotProcessor();
	}

}

class DeclarativeClientAotProcessor implements BeanFactoryInitializationAotProcessor {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		return null;
		/*
		 *
		 * var list = new ArrayList<String>(); var beansWithAnnotation =
		 * beanFactory.getBeanNamesForAnnotation(HttpExchange.class); for (var bn :
		 * beansWithAnnotation) { var bd = beanFactory.getBeanDefinition(bn); var clazz =
		 * bd.getBeanClassName(); if (log.isDebugEnabled()) {
		 * log.debug("getting class name " + clazz); }
		 *
		 * list.add(clazz); }
		 *
		 * if (list.size() > 0) { return (generationContext,
		 * beanFactoryInitializationCode) -> list .forEach(cn -> { try {
		 * generationContext.getRuntimeHints().proxies().registerJdkProxy(AopProxyUtils.
		 * completeJdkProxyInterfaces(Class.forName(cn))); }// catch
		 * (ClassNotFoundException e) { log.error("oops!", e); } }); }
		 */

	}

}
