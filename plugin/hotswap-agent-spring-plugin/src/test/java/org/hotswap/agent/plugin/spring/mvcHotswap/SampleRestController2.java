package org.hotswap.agent.plugin.spring.mvcHotswap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SampleRestController2 {

    @GetMapping("/helloSwapped")
    public String hello() {
        return "Hello World Swapped";
    }

    @RequestMapping(value = "/helloRequestMappingSwapped", method = RequestMethod.GET)
    public String helloRequestMapping() {
        return "Hello World2 Swapped";
    }

}
