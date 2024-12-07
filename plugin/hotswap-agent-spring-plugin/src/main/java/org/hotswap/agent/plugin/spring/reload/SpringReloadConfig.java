/*
 * Copyright 2013-2024 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.spring.reload;

/**
 * The type Spring reload config.
 */
public class SpringReloadConfig {

    private static final String SPRING_RELOAD_DELAY_MILLIS = "SpringReloadDelayMillis";
    private static final String SPRING_TEST_SLEEP_TIME_FACTOR = "SpringTestSleepTimeFactor";
    public static int reloadDelayMillis = 1600;
    private static boolean isReloadDelayMillisProperty = false;
    private static double testSleepTimeFactor = 1.0;

    static {
        String delayProperty = System.getProperty(SPRING_RELOAD_DELAY_MILLIS);
        if (delayProperty != null) {
            try {
                reloadDelayMillis = Integer.parseInt(delayProperty);
                isReloadDelayMillisProperty = true;
            } catch (NumberFormatException e) {
                System.err.println("Invalid format for -D" + SPRING_RELOAD_DELAY_MILLIS + ". Using default value: " + reloadDelayMillis);
            }
        }
        String fac = System.getProperty(SPRING_TEST_SLEEP_TIME_FACTOR);
        if (fac != null) {
            try {
                testSleepTimeFactor = Double.parseDouble(fac);
            } catch (NumberFormatException e) {
                System.err.println("Invalid format for -D" + SPRING_TEST_SLEEP_TIME_FACTOR + ".");
            }
        }
    }

    public static void setDelayMillis(int delayMillis) {
        if (isReloadDelayMillisProperty) {
            return;
        }
        if (delayMillis > 30000) {
            reloadDelayMillis = 30000;
            return;
        }
        reloadDelayMillis = delayMillis;
    }

    public static long scaleTestSleepTime(long timeMillis) {
        System.out.println("sleeping: " + Math.round(testSleepTimeFactor * timeMillis));
        return Math.round(testSleepTimeFactor * timeMillis);
    }
}
