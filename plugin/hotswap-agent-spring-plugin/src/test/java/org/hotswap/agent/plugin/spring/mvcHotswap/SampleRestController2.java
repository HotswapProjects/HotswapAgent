package org.hotswap.agent.plugin.spring.mvcHotswap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SampleRestController2 {

    @GetMapping("/helloSwapped")
    public String hello() {
        return "Hello World Swapped";
    }

}
