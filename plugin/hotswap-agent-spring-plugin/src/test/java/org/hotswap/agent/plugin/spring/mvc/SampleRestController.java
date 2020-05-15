package org.hotswap.agent.plugin.spring.mvc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SampleRestController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello World";
    }

    @RequestMapping(value = "/helloRequestMapping", method = RequestMethod.GET)
    public String helloRequestMapping() {
        return "Hello World2";
    }

}
