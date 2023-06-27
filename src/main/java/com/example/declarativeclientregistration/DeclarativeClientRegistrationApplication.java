package com.example.declarativeclientregistration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.lang.annotation.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@EnableHttpExchangeClients
@SpringBootApplication
public class DeclarativeClientRegistrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeclarativeClientRegistrationApplication.class, args);
    }

    @Bean
    WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ApplicationRunner applicationRunner(Todos todos) {
        return args -> todos.todos().forEach(System.out::println);
    }


}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(HttpExchangeAbstractDeclarativeClientRegistrar.class)
@interface EnableHttpExchangeClients {
}


class HttpExchangeAbstractDeclarativeClientRegistrar extends AbstractDeclarativeClientRegistrar {

    private final static Logger log = LoggerFactory.getLogger(HttpExchangeAbstractDeclarativeClientRegistrar.class);

    private static class HttpExchangeRegisteringBiConsumer
            implements BiConsumer<AnnotatedBeanDefinition, BeanDefinitionRegistry> {

        @Override
        public void accept(AnnotatedBeanDefinition annotatedBeanDefinition, BeanDefinitionRegistry registry) {

            if (log.isDebugEnabled()) {
                log.debug("need to register a client for " + annotatedBeanDefinition.getBeanClassName());
            }


            Class<DeclarativeClientFactoryBean> declarativeClientFactoryBeanClass = DeclarativeClientFactoryBean.class;
            BeanDefinitionBuilder definition = BeanDefinitionBuilder.genericBeanDefinition(declarativeClientFactoryBeanClass);
            String implementingClassName = annotatedBeanDefinition.getMetadata().getClassName();
            definition.addPropertyValue("type", implementingClassName);
            definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

            AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
            beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, implementingClassName);
            beanDefinition.setPrimary(true);
            beanDefinition.setBeanClass(declarativeClientFactoryBeanClass);
            BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, implementingClassName, new String[0]);
            BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);

            /*
    private void registerClient(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry registry) {
          String className = annotationMetadata.getClassName();
          log.debug("going to build a client for " + className);
          Class<RSocketClientFactoryBean> rSocketClientFactoryBeanClass = RSocketClientFactoryBean.class;
          BeanDefinitionBuilder definition = BeanDefinitionBuilder.genericBeanDefinition(rSocketClientFactoryBeanClass);
          definition.addPropertyValue("type", className);
          definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

          AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
          beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, className);
          beanDefinition.setPrimary(true);
          beanDefinition.setBeanClass(rSocketClientFactoryBeanClass);
          BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className, new String[0]);
          BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
      }
    */
        }
    }

    private static class HttpExchangePredicate implements Predicate<AnnotatedBeanDefinition> {

        @Override
        public boolean test(AnnotatedBeanDefinition beanDefinition) {
            var match = beanDefinition.getMetadata()
                    .getAnnotationTypes()
                    .contains(HttpExchange.class.getName());
            if (match && log.isDebugEnabled()) {
                log.debug("going to return true for " + beanDefinition.getBeanClassName() + '.');
            }
            return match;
        }
    }

    HttpExchangeAbstractDeclarativeClientRegistrar() {
        super(new HttpExchangePredicate(), EnableHttpExchangeClients.class, new HttpExchangeRegisteringBiConsumer());
    }
}

/**
 * Discovers and registers declarative interfaces if the interface matches a particular predicate.
 */
class AbstractDeclarativeClientRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, ResourceLoaderAware {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private Environment environment;

    private ResourceLoader resourceLoader;

    private final Class<? extends Annotation> enableAnnotation;

    private final BiConsumer<AnnotatedBeanDefinition, BeanDefinitionRegistry> registeringBiConsumer;

    private final TypeFilter typeFilter = new AnnotationTypeFilter(HttpExchange.class);

    private final Predicate<AnnotatedBeanDefinition> predicate;


    AbstractDeclarativeClientRegistrar(
            Predicate<AnnotatedBeanDefinition> predicate,
            Class<? extends Annotation> annotation, BiConsumer<AnnotatedBeanDefinition, BeanDefinitionRegistry> registeringBiConsumer) {
        this.enableAnnotation = annotation;
        this.predicate = predicate;
        this.registeringBiConsumer = registeringBiConsumer;
        Assert.notNull(this.enableAnnotation, "the @Enable* annotation must not be null");
        Assert.notNull(this.registeringBiConsumer, "the registeringBiConsumer must not be null");
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry,
                                        BeanNameGenerator importBeanNameGenerator) {

        this.getBasePackages(importingClassMetadata)
                .stream()
                .flatMap(basePackage -> this.buildScanner().findCandidateComponents(basePackage)//
                        .stream()//
                        .filter(cc -> cc instanceof AnnotatedBeanDefinition)//
                        .map(abd -> (AnnotatedBeanDefinition) abd)//
                        .filter(this.predicate::test))
                .forEach(annotatedBeanDefinition -> this.registeringBiConsumer.accept(annotatedBeanDefinition, registry));
    }


    private Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {

        Map<String, Object> attributes = importingClassMetadata
                .getAnnotationAttributes(this.enableAnnotation.getCanonicalName());


        Set<String> basePackages = new HashSet<>();

        if (attributes.containsKey("value"))
            for (String pkg : (String[]) attributes.get("value")) {
                if (StringUtils.hasText(pkg)) {
                    basePackages.add(pkg);
                }
            }
        if (attributes.containsKey("basePackages"))
            for (String pkg : (String[]) attributes.get("basePackages")) {
                if (StringUtils.hasText(pkg)) {
                    basePackages.add(pkg);
                }
            }
        if (attributes.containsKey("basePackageClasses"))
            for (Class<?> clazz : (Class[]) attributes.get("basePackageClasses")) {
                basePackages.add(ClassUtils.getPackageName(clazz));
            }

        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }

        return basePackages;
    }

    private ClassPathScanningCandidateComponentProvider buildScanner() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false,
                this.environment) {

            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isIndependent();
            }

            @Override
            protected boolean isCandidateComponent(MetadataReader metadataReader) {
                return !metadataReader.getClassMetadata().isAnnotation();
            }
        };
        scanner.addIncludeFilter(this.typeFilter);
        scanner.setResourceLoader(this.resourceLoader);
        return scanner;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}


@HttpExchange("https://jsonplaceholder.typicode.com")
interface Todos {

    @GetExchange("/todos")
    List<Todo> todos();

    @GetExchange("/todos/{id}")
    List<Todo> todoById(@PathVariable int id);
}

record Todo(int userId, int id, String title, boolean completed) {
}


class DeclarativeClientFactoryBean<T> implements BeanFactoryAware, FactoryBean<T> {

    private Class<?> type;

    private ListableBeanFactory context;


    private WebClient findWebClient(ListableBeanFactory beanFactory) {
        var wc = beanFactory.getBeansOfType(WebClient.class);
        Assert.state(wc.size() == 1, "there should only be one WebClient!");
        return wc.values().iterator().next();
    }

    /*private static RSocketRequester forInterface(Class<?> clientInterface, ListableBeanFactory context) {
        Map<String, RSocketRequester> rSocketRequestersInContext = context.getBeansOfType(RSocketRequester.class);
        int rSocketRequestersCount = rSocketRequestersInContext.size();
        Assert.state(rSocketRequestersCount > 0, () -> "there should be at least one "
                                                       + RSocketRequester.class.getName() + " in the context. Please consider defining one.");
        RSocketRequester rSocketRequester = null;
        Assert.notNull(clientInterface, "the client interface must be non-null");
        Assert.notNull(context, () -> "the " + ListableBeanFactory.class.getName() + " interface must be non-null");
        MergedAnnotation<Qualifier> qualifier = MergedAnnotations.from(clientInterface).get(Qualifier.class);
        if (qualifier.isPresent()) {
            String valueOfQualifierAnnotation = qualifier.getString(MergedAnnotation.VALUE);
            Map<String, RSocketRequester> beans = BeanFactoryAnnotationUtils.qualifiedBeansOfType(context,
                    RSocketRequester.class, valueOfQualifierAnnotation);
            Assert.state(beans.size() == 1,
                    () -> "I need just one " + RSocketRequester.class.getName() + " but I got " + beans.keySet());
            for (Map.Entry<String, RSocketRequester> entry : beans.entrySet()) {
                rSocketRequester = entry.getValue();
                if (log.isDebugEnabled()) {
                    log.debug("found " + rSocketRequester + " with bean name " + entry.getKey() + " for @"
                              + RSocketClient.class.getName() + " interface " + clientInterface.getName() + '.');
                }
            }
        } else {
            Assert.state(rSocketRequestersCount == 1, () -> "there should be no more and no less than one unqualified "
                                                            + RSocketRequester.class.getName() + " instances in the context.");
            return rSocketRequestersInContext.values().iterator().next();
        }
        Assert.notNull(rSocketRequester, () -> "we could not find an " + RSocketRequester.class.getName()
                                               + " for the @RSocketClient interface " + clientInterface.getName() + '.');
        return rSocketRequester;
    }*/


    public void setType(String type) {
        try {
            this.type = Class.forName(type);
        }//
        catch (Throwable throwable) {
            System.err.println("oops!" + throwable.getMessage());
        }
    }

    @Override
    public T getObject() {
        WebClient wc = this.findWebClient(this.context);
        return (T) HttpServiceProxyFactory
                .builder(WebClientAdapter.forClient(wc))
                .build()
                .createClient(this.type);
//        RSocketRequester rSocketRequester = forInterface(this.type, this.context);
//        RSocketClientBuilder clientBuilder = this.context.getBean(RSocketClientBuilder.class);
        // RSocketClientBuilder clientBuilder = new RSocketClientBuilder();
//        return (T) clientBuilder.buildClientFor(this.type, rSocketRequester);
    }

    @Override
    public Class<?> getObjectType() {
        return this.type;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        Assert.state(beanFactory instanceof ListableBeanFactory, () -> "the " + BeanFactory.class.getName()
                                                                       + " is not an instance of a " + ListableBeanFactory.class.getName());
        this.context = (ListableBeanFactory) beanFactory;
    }

}