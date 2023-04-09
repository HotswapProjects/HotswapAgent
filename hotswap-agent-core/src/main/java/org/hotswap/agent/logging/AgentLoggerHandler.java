/*
 * Copyright 2013-2023 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.logging;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Simple handler to log to output stream (default is system.out).
 *
 * @author Jiri Bubnik
 */
public class AgentLoggerHandler {

    // stream to receive the log
    PrintStream outputStream;

    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    /**
     * Setup custom stream (default is System.out).
     *
     * @param outputStream custom stream
     */
    public void setPrintStream(PrintStream outputStream) {
        this.outputStream = outputStream;
    }

    // print a message to System.out and optionally to custom stream
    protected void printMessage(String message) {
        String log = "HOTSWAP AGENT: " + sdf.format(new Date()) +  " " + message;
        System.out.println(log);
        if (outputStream != null)
            outputStream.println(log);
    }

    public void print(Class clazz, AgentLogger.Level level, String message, Throwable throwable, Object... args) {

        // replace {} in string with actual parameters
        String messageWithArgs = message;
        for (Object arg : args) {
            int index = messageWithArgs.indexOf("{}");
            if (index >= 0) {
                messageWithArgs = messageWithArgs.substring(0, index) + String.valueOf(arg) + messageWithArgs.substring(index + 2);
            }
        }

        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(level);
        stringBuffer.append(" (");
        stringBuffer.append(clazz.getName());
        stringBuffer.append(") - ");
        stringBuffer.append(messageWithArgs);

        if (throwable != null) {
            stringBuffer.append("\n");
            stringBuffer.append(formatErrorTrace(throwable));
        }

        printMessage(stringBuffer.toString());
    }

    private String formatErrorTrace(Throwable throwable) {
        StringWriter errors = new StringWriter();
        throwable.printStackTrace(new PrintWriter(errors));
        return errors.toString();
    }

    public void setDateTimeFormat(String dateTimeFormat) {
        sdf = new SimpleDateFormat(dateTimeFormat);
    }
}
