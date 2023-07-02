package auto;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationExcludeFilter;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author Josh Long
 */
@Slf4j
class AutoClientRegistrar implements BeanRegistrationExcludeFilter, BeanFactoryInitializationAotProcessor,
		BeanDefinitionRegistryPostProcessor, ResourceLoaderAware, EnvironmentAware {

	private final ClientDetectionStrategy detectionStrategy;

	private Environment environment;

	private ResourceLoader resourceLoader;

	AutoClientRegistrar(ClientDetectionStrategy detectionStrategy) {
		this.detectionStrategy = detectionStrategy;
	}

	@Override
	public boolean isExcludedFromAotProcessing(RegisteredBean registeredBean) {
		return this.detectionStrategy.test(registeredBean.getBeanClass());
	}

	@SneakyThrows
	private boolean isCandidate(BeanDefinition beanDefinition) {
		var beanClassName = beanDefinition.getBeanClassName();
		var aClass = ClassUtils.resolveClassName(beanClassName, null);
		return this.detectionStrategy.test(aClass);
	}

	@SneakyThrows
	private void registerBeanDefinitionForClient(BeanDefinitionRegistry registry, BeanDefinition beanDefinition) {
		var beanClassName = beanDefinition.getBeanClassName();
		var aClass = Class.forName(beanClassName);
		var supplier = (Supplier<?>) () -> {
			if (registry instanceof BeanFactory bf) {
				return HttpServiceProxyFactory.builder(WebClientAdapter.forClient(bf.getBean(WebClient.class)))
					.build()
					.createClient(aClass);
			}
			return null;
		};
		var definition = BeanDefinitionBuilder.rootBeanDefinition(ResolvableType.forClass(aClass), supplier)
			.getBeanDefinition();
		registry.registerBeanDefinition(aClass.getSimpleName(), definition);
	}

	private static Stream<BeanDefinition> discoverCandidates(BeanFactory beanFactory, Environment environment,
			ResourceLoader resourceLoader, Predicate<BeanDefinition> predicate) {
		var basePackages = (AutoConfigurationPackages.get(beanFactory));
		Assert.state(basePackages.size() >= 1, "there should be at least one package!");
		Assert.notNull(environment, "the Environment must not be null");
		Assert.notNull(resourceLoader, "the ResourceLoader must not be null");
		var scanner = buildScanner(environment, resourceLoader);
		return basePackages.stream().flatMap(bp -> scanner.findCandidateComponents(bp).stream()).filter(predicate);
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
		if (registry instanceof BeanFactory beanFactory) {
			discoverCandidates(beanFactory, this.environment, this.resourceLoader, this::isCandidate)//
				.forEach(x -> registerBeanDefinitionForClient(registry, x));
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
		var candidates = discoverCandidates(beanFactory, this.environment, this.resourceLoader, this::isCandidate)
			.toList();
		return candidates.isEmpty() ? null : new AutoClientRegistrarAotContribution(beanFactory, candidates);
	}

}
