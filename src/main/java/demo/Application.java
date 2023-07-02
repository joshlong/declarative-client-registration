package demo;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.aot.BeanRegistrationExcludeFilter;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.javapoet.MethodSpec;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import javax.lang.model.element.Modifier;
import java.lang.annotation.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	WebClient webClient(WebClient.Builder builder) {
		return builder.build();
	}

	@Bean
	ApplicationRunner applicationRunner(Todos todos) {
		return a -> todos.todos().forEach(System.out::println);
	}

	@Bean
	static ClientDetectionStrategy detectionStrategy() {
		return new HttpClientClientDetectionStrategy();
	}

	@Bean
	static AutoClientRegistrar declarativeHttpInterfaceAutoRegistrar(ClientDetectionStrategy strategy) {
		return new AutoClientRegistrar(strategy);
	}

}

/**
 * Strategy pattern to identify which kind of declarative clients we're dealing with:
 * HTTP, RSOcket, etc.
 */
interface ClientDetectionStrategy extends Predicate<Class<?>> {

}

@Slf4j
class HttpClientClientDetectionStrategy implements ClientDetectionStrategy {

	private final MergedAnnotations.Search mergedAnnotations = MergedAnnotations
		.search(MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);

	@Override
	public boolean test(Class<?> aClass) {
		return this.mergedAnnotations.from(aClass).get(AutoClient.class).isPresent()
				&& this.mergedAnnotations.from(aClass).get(HttpExchange.class).isPresent();
	}

}

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
		var scanner = buildScanner(environment, null, resourceLoader);
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
			TypeFilter typeFilter, ResourceLoader resourceLoader) {
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

/**
 * We'll have excluded the runtime code and now need to provide an AOT-equivalent of it.
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

// todo strawman annotation so we have way to know whether to turn this into a bean (we
// don't want to conflict if the user decides to register their own)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@interface AutoClient {

}

@AutoClient
@HttpExchange("https://jsonplaceholder.typicode.com")
interface Todos {

	@GetExchange("/todos")
	List<Todo> todos();

	@GetExchange("/todos/{id}")
	List<Todo> todoById(@PathVariable int id);

}

record Todo(int userId, int id, String title, boolean completed) {
}
