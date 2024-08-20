package org.hotswap.agent.plugin.mybatisplus;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.mybatis.MyBatisRefreshCommands;
import org.hotswap.agent.util.test.WaitHelper;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.assertTrue;

public class BaseTest {

    protected static void runScript(DataSource ds, String resource) throws IOException, SQLException {
        try (Connection connection = ds.getConnection()) {
            ScriptRunner runner = new ScriptRunner(connection);
            runner.setAutoCommit(true);
            runner.setStopOnError(false);
            runner.setLogWriter(null);
            runner.setErrorLogWriter(null);
            runScript(runner, resource);
        }
    }

    private static void runScript(ScriptRunner runner, String resource)
            throws IOException, SQLException {
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            runner.runScript(reader);
        }
    }

    protected static void swapMapper(String mapperNew)  throws Exception {
        swapMapper(mapperNew, "Mapper.xml");
    }


    protected static void swapMapper(String mapperNew, String dstMapperName) throws Exception {
        MyBatisRefreshCommands.reloadFlag = true;
        File f = Resources.getResourceAsFile(mapperNew);
        Files.copy(f.toPath(), f.toPath().getParent().resolve(dstMapperName), StandardCopyOption.REPLACE_EXISTING);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !MyBatisRefreshCommands.reloadFlag;
            }
        }, 4000 )); // Repository is regenerated within 2*DeltaSpikePlugin.WAIT_ON_REDEFINE

        // TODO do not know why sleep is needed, maybe a separate thread in owb refresh?
        Thread.sleep(100);
    }


    public static void swapClasses(Class original, String swap) throws Exception {
        MyBatisRefreshCommands.reloadFlag = true;
        HotSwapper.swapClasses(original, swap);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !MyBatisRefreshCommands.reloadFlag;
            }
        }, 4000));
    }
}
