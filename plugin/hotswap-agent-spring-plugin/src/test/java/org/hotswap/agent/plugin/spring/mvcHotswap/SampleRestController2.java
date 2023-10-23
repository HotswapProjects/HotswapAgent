package org.hotswap.agent.plugin.spring.mvcHotswap;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SampleRestController2 {

    @RequestMapping("/helloSwapped")
    @ResponseBody
    public String hello() {
        return "Hello World Swapped";
    }

    @RequestMapping(value = "/helloRequestMappingSwapped", method = RequestMethod.GET)
    @ResponseBody
    public String helloRequestMapping() {
        return "Hello World2 Swapped";
    }

}
