package demo;


import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
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
    static Definer declarativeHttpInterfaceAutoRegistrar() {
        return new Definer();
    }

}

interface ClientDetectionStrategy extends Predicate<Class<?>> {

}


@Slf4j
class HttpClientClientDetectionStrategy implements ClientDetectionStrategy {

    private final MergedAnnotations.Search mergedAnnotations =
            MergedAnnotations.search(MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);


    @Override
    public boolean test(Class<?> aClass) {
        var match =
                this.mergedAnnotations.from(aClass).get(AutoClient.class).isPresent() &&
                this.mergedAnnotations.from(aClass).get(HttpExchange.class).isPresent();
        return match;
    }
}


// todo make this an abstract class with parameters for candidate detection, method detection, hints
//  registration strategies, and AOT code generation so that we can plugin http, rsocket, graphql, chatgpt, etc.

@Slf4j
class Definer implements
        BeanRegistrationExcludeFilter,
        BeanFactoryInitializationAotProcessor, BeanDefinitionRegistryPostProcessor, ResourceLoaderAware, EnvironmentAware {


    private Environment environment;

    private ResourceLoader resourceLoader;

    //todo parameterize this
    private final ClientDetectionStrategy detectionStrategy = new HttpClientClientDetectionStrategy();

    @Override
    public boolean isExcludedFromAotProcessing(RegisteredBean registeredBean) {
        return this.detectionStrategy.test(registeredBean.getBeanClass());
    }


    // todo make this abstract so that it can be changed for RSocket or HTTP or whatever else we decide upon
    @SneakyThrows
    private boolean isCandidate(BeanDefinition beanDefinition) {
        var beanClassName = beanDefinition.getBeanClassName();
        var aClass = ClassUtils.resolveClassName(beanClassName, null);
        return this.detectionStrategy.test(aClass);

    }

    @SneakyThrows
    private void registerBeanDefinitionForInterface(BeanDefinitionRegistry registry, BeanDefinition beanDefinition) {
        var beanClassName = beanDefinition.getBeanClassName();
        var aClass = Class.forName(beanClassName);
        var supplier = (Supplier<?>) () -> {
            if (registry instanceof BeanFactory bf) {
                return HttpServiceProxyFactory.builder(WebClientAdapter.forClient(bf.getBean(WebClient.class))).build().createClient(aClass);
            }
            return null;
        };
        var definition = BeanDefinitionBuilder.rootBeanDefinition(ResolvableType.forClass(aClass), supplier).getBeanDefinition();
        registry.registerBeanDefinition(aClass.getSimpleName(), definition);
        log.debug("registering bean for " + beanClassName);
    }

    private static Stream<BeanDefinition> discoverCandidates(BeanFactory beanFactory, Environment environment, ResourceLoader resourceLoader, Predicate<BeanDefinition> predicate) {
        var basePackages = (AutoConfigurationPackages.get(beanFactory));
        Assert.state(basePackages.size() >= 1, "there should be at least one package!");
        Assert.notNull(environment, "the Environment must not be null");
        Assert.notNull(resourceLoader, "the ResourceLoader must not be null");
        var scanner = buildScanner(environment, null, resourceLoader);
        return basePackages.stream().flatMap(bp -> scanner.findCandidateComponents(bp).stream()).filter(predicate::test);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (registry instanceof BeanFactory beanFactory) {
            discoverCandidates(beanFactory, this.environment, this.resourceLoader, this::isCandidate)//
                    .forEach(x -> registerBeanDefinitionForInterface(registry, x));
        }//
        else throw new IllegalStateException(
                "Error! The BeanDefinitionRegistry is not an instance of " + BeanFactory.class.getName());
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }


    private static ClassPathScanningCandidateComponentProvider buildScanner(Environment environment, TypeFilter typeFilter, ResourceLoader resourceLoader) {
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
        return candidates.isEmpty() ? null : new AotContribution(beanFactory, candidates);
    }
}

/**
 * We'll have excluded the runtime code and now need to provide an AOT-equivalent of it.
 */
@Slf4j
class AotContribution implements BeanFactoryInitializationAotContribution {

    private final List<BeanDefinition> candidates;

    private final ConfigurableListableBeanFactory beanFactory;

    AotContribution(ConfigurableListableBeanFactory beanFactory, List<BeanDefinition> candidates) {
        this.candidates = candidates;
        this.beanFactory = beanFactory;
    }

    @Override
    public void applyTo(GenerationContext generationContext,
                        BeanFactoryInitializationCode beanFactoryInitializationCode) {
        for (var bdn : this.beanFactory.getBeanDefinitionNames()) {
            log.debug("bdn: " + bdn);
        }
        this.candidates.forEach(bd -> System.out.println("it's my job to register " + bd.getBeanClassName()));

        var initializingMethodReference = beanFactoryInitializationCode
                .getMethods()
                .add("autoRegisterProtocolClients", m -> generateMethod(m, generationContext.getRuntimeHints()))
                .toMethodReference();
        beanFactoryInitializationCode.addInitializer(initializingMethodReference);
    }

    private void generateMethod(MethodSpec.Builder m, RuntimeHints hints) {
        m.addModifiers(Modifier.PUBLIC);
        m.addJavadoc("register declarative clients  ");
        m.addParameter(DefaultListableBeanFactory.class, "registry");

        this.candidates.forEach(beanDefinition -> {
            var clazzName = beanDefinition.getBeanClassName();
            var clazz = ClassUtils.resolveClassName(clazzName, null);
            hints.proxies().registerJdkProxy(
                    AopProxyUtils.completeJdkProxyInterfaces( clazz)
            ) ;
            hints.reflection().registerType(Todos.class, MemberCategory.values());
            hints.reflection().registerType(Todo.class, MemberCategory.values());

            var javaCode =
                    """
                             Class<$T> aClass = (Class<$T>)  $T.resolveClassName( $S , null );
                             $L<$L> supplier = ($L<$L>) () -> {
                                 return $T.builder( $T.forClient( registry.getBean($L.class))).build().createClient(aClass);
                             };
                             $L definition = $L.rootBeanDefinition( $L.forClass(aClass), supplier).getBeanDefinition();
                             registry.registerBeanDefinition(aClass.getSimpleName(), definition);
                            """;

            m.addCode(javaCode,//
                    clazz, clazz,
                    ClassUtils.class, clazzName,//
                    Supplier.class.getName(), clazzName, Supplier.class.getName(), clazzName,//
                    HttpServiceProxyFactory.class, WebClientAdapter.class,
                    WebClient.class.getName(),
                    BeanDefinition.class.getName(), BeanDefinitionBuilder.class.getName(), ResolvableType.class.getName());
        });
    }


}

// todo strawman annotation so we have way to know whether to turn this into a bean (we don't want to conflict if the user decides to register their own)
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
