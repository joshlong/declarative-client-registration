package demo;


import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    static BeanRegistrationAotProcessor beanRegistrationAotProcessor() {
        return registeredBean -> {
            System.out.println("analyzing bean of type " + registeredBean.getBeanClass().getName() + " at compile time.");
            if (Serializable.class.isAssignableFrom(registeredBean.getBeanClass())) {
                System.out.println("found a bean that is serializable: " + registeredBean.getBeanClass());
                return (generationContext, beanRegistrationCode) -> {
                    RuntimeHints hints = generationContext.getRuntimeHints();
                    hints.serialization().registerType(TypeReference.of(registeredBean.getBeanClass().getName()));
                };
            }
            return null;
        };
    }


    @Bean
    static Definer beanDefinitionRegistryPostProcessor() {
        return new Definer();
    }

    @Bean
    BeanPostProcessor beanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (Cat.class.isAssignableFrom(bean.getClass())) {
                    System.out.println("post processing " + bean.getClass().getName() +
                                       " with bean name " + beanName);
                }
                return bean;
            }
        };
    }


}


class Definer implements BeanDefinitionRegistryPostProcessor,
        ResourceLoaderAware, EnvironmentAware {

    private Environment environment;

    private ResourceLoader resourceLoader;

    private boolean isCandidate(BeanDefinition beanDefinition) throws Exception {
        // todo make sure not to register interfaces that people plan to manually register
        String beanClassName = beanDefinition.getBeanClassName();
        Class<?> aClass = Class.forName(beanClassName);
        MergedAnnotations.Search search = MergedAnnotations.search(MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);
        return search.from(aClass).get(HttpExchange.class).isPresent();
    }

    private void registerBeanDefinitionForInterface(BeanDefinitionRegistry registry, BeanDefinition beanDefinition)
            throws Exception {
        String beanClassName = beanDefinition.getBeanClassName();
        System.out.println("going to register " + beanClassName + " as a HttpServiceProxyFactory declarative client");
        Class<?> aClass = Class.forName(beanClassName);
//        registry.registerBeanDefinition(BeanDefinitionBuilder.genericBeanDefinition( ));

    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // find packages
        List<String> basePackages = new ArrayList<>();
        if (registry instanceof BeanFactory beanFactory) {
            basePackages.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        Assert.state(basePackages.size() >= 1, "there should be at least one package!");
        Assert.notNull(this.environment, "the Environment must not be null");
        Assert.notNull(this.resourceLoader, "the ResourceLoader must not be null");
        TypeFilter typeFilter = new AnnotationTypeFilter(HttpExchange.class);
        ClassPathScanningCandidateComponentProvider scanner = buildScanner(this.environment,
                typeFilter, this.resourceLoader);
        basePackages
                .stream() //
                .flatMap(bp -> scanner.findCandidateComponents(bp).stream())//
                .filter(f -> {
                    try {
                        return isCandidate(f);
                    } //
                    catch (Exception e) {
                        System.out.println("oops!" + e.getMessage());
                    }
                    return false;
                })//
                .forEach(x -> {
                    try {
                        registerBeanDefinitionForInterface(registry, x);
                    } catch (Exception e) {
                        System.out.println("oops!" + e.getMessage());
                    }
                });//


    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    private static ClassPathScanningCandidateComponentProvider buildScanner(Environment environment,
                                                                            TypeFilter typeFilter,
                                                                            ResourceLoader resourceLoader) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false,
                environment) {

            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isIndependent();
            }

            @Override
            protected boolean isCandidateComponent(MetadataReader metadataReader) {
                return !metadataReader.getClassMetadata().isAnnotation();
            }
        };
        scanner.setResourceLoader(resourceLoader);
        scanner.clearCache();
        scanner.addIncludeFilter(typeFilter);
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
}

class Cat implements Serializable {

    Cat() {
        System.out.println("hello cat");
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
