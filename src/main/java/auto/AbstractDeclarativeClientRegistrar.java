package auto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
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
import org.springframework.web.service.annotation.HttpExchange;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

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
