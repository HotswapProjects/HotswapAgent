package org.hotswap.agent.plugin.spring.factorybean.annotations;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class AnnoFactoryBeanConfiguration {

    @Bean
    public AnnotationFactoryBean1 annotationFactoryBean1() {
        return new AnnotationFactoryBean1();
    }

    @Bean
    public AnnotationParentBean2 annotationParentBean2() {
        return new AnnotationParentBean2();
    }

    @Bean
    public AnnotationParentBean3 annotationParentBean3(AnnotationBean3 annotationBean3) {
        return new AnnotationParentBean3(annotationBean3);
    }

    @Bean
    public AnnotationParentBean4 annotationParentBean4(AnnotationBean4 annotationBean4) {
        return new AnnotationParentBean4(annotationBean4);
    }

    @Bean
    public AnnotationParentBean5 annotationParentBean5(AnnotationBean5 annotationBean5) {
        return new AnnotationParentBean5(annotationBean5);
    }
}
