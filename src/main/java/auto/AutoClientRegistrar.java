package auto;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.aot.BeanRegistrationExcludeFilter;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.javapoet.MethodSpec;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author Josh Long
 */
@Slf4j
class AutoClientRegistrar implements BeanRegistrationExcludeFilter, BeanFactoryInitializationAotProcessor,
		BeanDefinitionRegistryPostProcessor, ResourceLoaderAware, EnvironmentAware {

	private final Map<String, AutoClientAdapter> adapters;

	private Environment environment;

	private ResourceLoader resourceLoader;

	AutoClientRegistrar(Map<String, AutoClientAdapter> adapters) {
		this.adapters = adapters;
	}

	@Override
	public boolean isExcludedFromAotProcessing(RegisteredBean registeredBean) {
		return isCandidateClient(registeredBean.getBeanClass());
	}

	private boolean isCandidateClient(Class<?> clzz) {
		return ClassifierUtils.classifyAdapterBeanNameForClient(this.adapters, clzz) != null;
	}

	@SneakyThrows
	private void registerBeanDefinitionForClient(AutoClientAdapter adapter, BeanDefinitionRegistry registry,
			BeanDefinition beanDefinition) {
		var beanClassName = beanDefinition.getBeanClassName();
		var aClass = Class.forName(beanClassName);
		var supplier = (Supplier<?>) () -> adapter.createClient(registry, aClass);
		var definition = BeanDefinitionBuilder.rootBeanDefinition(ResolvableType.forClass(aClass), supplier)
			.getBeanDefinition();
		registry.registerBeanDefinition(aClass.getSimpleName(), definition);
	}

	private Stream<BeanDefinition> discoverCandidates(BeanFactory beanFactory, Environment environment,
			ResourceLoader resourceLoader) {
		var basePackages = AutoConfigurationPackages.get(beanFactory);
		Assert.state(basePackages.size() >= 1, "there should be at least one package!");
		Assert.notNull(environment, "the Environment must not be null");
		Assert.notNull(resourceLoader, "the ResourceLoader must not be null");
		var scanner = buildScanner(environment, resourceLoader);
		return basePackages.stream()
			.flatMap(bp -> scanner.findCandidateComponents(bp).stream())
			.filter(c -> this.isCandidateClient(ClassUtils.resolveClassName(c.getBeanClassName(), null)));
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		if (registry instanceof ListableBeanFactory beanFactory) {
			// todo make it so that we classify the types and assign them one of possibly
			// many {@link AutoClientAdapter}s
			discoverCandidates(beanFactory, this.environment, this.resourceLoader)//
				.forEach(beanDefinition -> {
					var clazz = ClassUtils.resolveClassName(Objects.requireNonNull(beanDefinition.getBeanClassName()),
							null);
					var adapterBeanName = ClassifierUtils
						.classifyAdapterBeanNameForClient(beanFactory.getBeansOfType(AutoClientAdapter.class), clazz);
					var autoClientAdapter = beanFactory.getBean(adapterBeanName, AutoClientAdapter.class);
					registerBeanDefinitionForClient(autoClientAdapter, registry, beanDefinition);
				});
		} //
		else
			throw new IllegalStateException(
					"Error! The BeanDefinitionRegistry is not an instance of " + BeanFactory.class.getName());
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// noop
	}

	private static ClassPathScanningCandidateComponentProvider buildScanner(Environment environment,
			ResourceLoader resourceLoader) {
		var scanner = new ClassPathScanningCandidateComponentProvider(false, environment) {

			@Override
			protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
				var isCandidate = false;
				if (beanDefinition.getMetadata().isIndependent()) {
					if (!beanDefinition.getMetadata().isAnnotation()) {
						isCandidate = true;
					}
				}
				return isCandidate;
			}

		};
		scanner.addIncludeFilter(new AnnotationTypeFilter(AutoClient.class));
		scanner.setResourceLoader(resourceLoader);
		scanner.clearCache();
		return scanner;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		var candidates = discoverCandidates(beanFactory, this.environment, this.resourceLoader).toList();
		return candidates.isEmpty() ? null : new AutoClientRegistrarAotContribution(candidates,
				beanFactory.getBeansOfType(AutoClientAdapter.class));
	}

}

abstract class ClassifierUtils {

	// todo put this in the registrar and make the contribution an inner class.
	static String classifyAdapterBeanNameForClient(Map<String, AutoClientAdapter> adapters, Class<?> clzz) {
		for (var beanName : adapters.keySet()) {
			var adapter = adapters.get(beanName);
			if (adapter.test(clzz)) {
				return beanName;
			}
		}
		return null;
	}

}

/**
 * Automatically registers clients both at runtime or compile time
 *
 * @author Josh Long
 */
@Slf4j
class AutoClientRegistrarAotContribution implements BeanFactoryInitializationAotContribution {

	private final List<BeanDefinition> candidates;

	private final Map<String, AutoClientAdapter> adapters;

	AutoClientRegistrarAotContribution(List<BeanDefinition> candidates, Map<String, AutoClientAdapter> adapters) {
		this.candidates = candidates;
		this.adapters = adapters;
	}

	@Override
	public void applyTo(GenerationContext generationContext,
			BeanFactoryInitializationCode beanFactoryInitializationCode) {
		var runtimeHints = generationContext.getRuntimeHints();
		var initializingMethodReference = beanFactoryInitializationCode//
			.getMethods()//
			.add("registerAutoClients", methodBuilder -> generateMethod(methodBuilder, runtimeHints))//
			.toMethodReference();
		beanFactoryInitializationCode.addInitializer(initializingMethodReference);
	}

	private void generateMethod(MethodSpec.Builder m, RuntimeHints hints) {
		m.addModifiers(Modifier.PUBLIC);
		m.addJavadoc("Automatically register service clients ");
		m.addParameter(DefaultListableBeanFactory.class, "registry");
		this.candidates.forEach(beanDefinition -> {
			var clazz = ClassUtils.resolveClassName(Objects.requireNonNull(beanDefinition.getBeanClassName()), null);
			hints.proxies().registerJdkProxy(AopProxyUtils.completeJdkProxyInterfaces(clazz));
			var beanName = ClassifierUtils.classifyAdapterBeanNameForClient(this.adapters, clazz);
			var javaCode = """
					 Class<$T> clazz$L = $T.class;
					 $T<$T> supplier$L = () -> registry.getBean($S, $T.class)
					 	.createClient(registry, clazz$L);
					 $T definition$L = $T.rootBeanDefinition(clazz$L, supplier$L).getBeanDefinition();
					 registry.registerBeanDefinition("declarative$LClient", definition$L);
					""";
			var suffix = clazz.getSimpleName();
			m.addCode(javaCode, //
					clazz, suffix, clazz, //
					Supplier.class, clazz, suffix, beanName, AutoClientAdapter.class, suffix, //
					BeanDefinition.class, suffix, BeanDefinitionBuilder.class, suffix, suffix, //
					suffix, suffix);
		});
	}

}
