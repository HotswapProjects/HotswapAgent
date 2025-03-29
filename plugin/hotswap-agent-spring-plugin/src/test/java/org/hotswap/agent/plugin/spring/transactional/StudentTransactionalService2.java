package org.hotswap.agent.plugin.spring.transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.text.ParseException;


@Service
public class StudentTransactionalService2 {
    @Autowired
    private StudentMapper studentMapper;

    @Transactional(rollbackFor = ParseException.class)
    public void changeName(String ori_name, String name, Exception toThrow) throws Exception {
        studentMapper.update(ori_name, name);
        throw toThrow;
    }
}

