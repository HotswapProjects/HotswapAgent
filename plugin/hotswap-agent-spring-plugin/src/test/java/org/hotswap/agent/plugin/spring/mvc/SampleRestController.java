package org.hotswap.agent.plugin.spring.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SampleRestController {

    @RequestMapping("/hello")
    @ResponseBody
    public String hello() {
        return "Hello World";
    }

    @RequestMapping(value = "/helloRequestMapping", method = RequestMethod.GET)
    @ResponseBody
    public String helloRequestMapping() {
        return "Hello World2";
    }

}
