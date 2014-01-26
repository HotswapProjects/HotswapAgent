package org.hotswap.agent.it.mvc;

import org.hotswap.agent.it.model.TestEntity;
import org.hotswap.agent.it.service.TestEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

/**
 * Simple Spring MVC controller.
 *
 * Note, that Spring MVC is not fully supported for reload (e.g. endpoint mapping).
 */
@Controller
@RequestMapping
public class IndexController {

    @Autowired
    TestEntityService testEntityService;

    @Autowired
    ApplicationContext applicationContext;

    /**
     * Experiment with entities and Hibernate.
     */
    @RequestMapping("/test")
    public String printHello(ModelMap model) {
        TestEntity a = new TestEntity("Hello world");
        testEntityService.addTestEntity(a);
        model.addAttribute("entities", testEntityService.loadTestEntities());
        return "test";
    }

    /**
     * Check reload resource behaviour - before and after change,
     */
    @RequestMapping("/reloadResource")
    public void printHello(Writer writer) throws IOException {
        InputStream is = getClass().getResourceAsStream("/test.resource");
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        writer.write(s.next());
    }

    @RequestMapping("/hello")
    public void helloWorld(Writer writer) throws IOException {
        // applicationContext.getBean instead of autowired service, because Spring MVC bean is sometimes
        // not reloaded - will be part of Spring MVC plugin
        writer.write(applicationContext.getBean(TestEntityService.class).helloWorld());
    }
}