package auto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.ResolvableType;
import org.springframework.javapoet.MethodSpec;
import org.springframework.util.ClassUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.function.Supplier;

/**
 * Automatically registers clients both at runtime or compile time
 *
 * @author Josh Long
 */
@Slf4j
class AutoClientRegistrarAotContribution implements BeanFactoryInitializationAotContribution {

	private final List<BeanDefinition> candidates;

	private final ConfigurableListableBeanFactory beanFactory;

	AutoClientRegistrarAotContribution(ConfigurableListableBeanFactory beanFactory, List<BeanDefinition> candidates) {
		this.candidates = candidates;
		this.beanFactory = beanFactory;
	}

	@Override
	public void applyTo(GenerationContext generationContext,
			BeanFactoryInitializationCode beanFactoryInitializationCode) {
		var initializingMethodReference = beanFactoryInitializationCode.getMethods()
			.add("autoRegisterClients",
					methodBuilder -> generateMethod(methodBuilder, generationContext.getRuntimeHints()))
			.toMethodReference();
		beanFactoryInitializationCode.addInitializer(initializingMethodReference);
	}

	// todo we need a strategy to, given a valid beanName for a collaborting object like a
	// `WebClient` and a class, produce a resulting object. it needs to be shared across
	// AOT and runtime
	private void generateMethod(MethodSpec.Builder m, RuntimeHints hints) {
		m.addModifiers(Modifier.PUBLIC);
		m.addJavadoc("""
				Automatically register clients in an AOT-compatible way.

				@author Josh Long
				""");

		m.addParameter(DefaultListableBeanFactory.class, "registry");
		this.candidates.forEach(beanDefinition -> {
			var clazz = ClassUtils.resolveClassName(beanDefinition.getBeanClassName(), null);
			hints.proxies().registerJdkProxy(AopProxyUtils.completeJdkProxyInterfaces(clazz));
			var javaCode = """
					 Class<$T> aClass = $T.class ;
					 $T<$T> supplier = ($T<$T>) () -> {
					     return $T.builder( $T.forClient( registry.getBean($T.class))).build().createClient(aClass);
					 };
					 $T definition = $T.rootBeanDefinition( $T.forClass(aClass), supplier).getBeanDefinition();
					 registry.registerBeanDefinition(aClass.getSimpleName(), definition);
					""";

			m.addCode(javaCode, //
					clazz, clazz, Supplier.class, clazz, Supplier.class, clazz, //
					HttpServiceProxyFactory.class, WebClientAdapter.class, WebClient.class, BeanDefinition.class,
					BeanDefinitionBuilder.class, ResolvableType.class);
		});
	}

}
