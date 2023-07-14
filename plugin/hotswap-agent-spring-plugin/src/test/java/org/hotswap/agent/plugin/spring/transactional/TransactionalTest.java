package org.hotswap.agent.plugin.spring.transactional;

import org.hotswap.agent.plugin.spring.ClassSwappingRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodClassKey;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.text.ParseException;



@RunWith(SpringRunner.class)
@ContextHierarchy({@ContextConfiguration(classes = TransactionalApplication.class)})
public class TransactionalTest {
    @Rule
    public ClassSwappingRule swappingRule = new ClassSwappingRule();

    @Autowired
    private StudentService studentService;

    @Autowired
    private StudentTransactionalService1 studentTransactionalService;

    @Test
    public void transactionTest() throws Exception {
        //create table
        studentService.createTable();

        //insert data
        String name1 = "name1";
        Assert.assertEquals(1, studentService.insertOriginalData(name1));

        //change name1 to name2, but expect rollback to name1 because an IOException was thrown
        String name2 = "name2";
        try {
            studentTransactionalService.changeName(name1, name2, new IOException());
        } catch (Exception ignored) {
        }
        Assert.assertEquals(name1, studentService.findName(name1));

        //swap "rollbackFor = IOException.class" to "rollbackFor = ParseException.class"
        swappingRule.swapClasses(StudentTransactionalService1.class, StudentTransactionalService2.class);
        try {
            studentTransactionalService.changeName(name1, name2, new IOException());
        } catch (Exception ignored) {
        }
        Assert.assertEquals(name2, studentService.findName(name2));
        //redefine后，抛ParseException，回滚成功，改成name1失败，还是name2
        try {
            studentTransactionalService.changeName(name2, name1, new ParseException("dummy", 0));
        } catch (Exception ignored) {
        }
        Assert.assertEquals(name2, studentService.findName(name2));
    }

}
