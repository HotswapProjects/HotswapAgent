package org.hotswap.agent.plugin.spring.transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class StudentService {
    @Autowired
    private StudentMapper studentMapper;

    public void createTable() {
        studentMapper.createTable();
    }

    public int insertOriginalData(String name) {
        return studentMapper.insert(name);
    }

    public String findName(String name) {
        return studentMapper.find(name);
    }
}

