package org.hotswap.agent.it.mvc;

import org.hotswap.agent.it.model.TestEntity;
import org.hotswap.agent.it.service.TestEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

/**
 * Created by bubnik on 10.10.13.
 */
@Controller
@RequestMapping
public class IndexController {

    @Autowired
    TestEntityService testEntityService;

    @RequestMapping("/test")
    public String printHello(ModelMap model) {
        TestEntity a = new TestEntity("Ahoj");
        a.setName("povedlo se");
        testEntityService.addTestEntity(a);
        model.addAttribute("entities", testEntityService.loadTestEntities());
        return "test";
    }

    @RequestMapping("/reloadResource")
    public void printHello(Writer writer) throws IOException {
        InputStream is = getClass().getResourceAsStream("/test.resource");
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        writer.write(s.next());
    }
}