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
package org.hotswap.agent.plugin.deltaspike.jsf;

import java.util.ArrayList;
import java.util.List;

import org.apache.deltaspike.core.spi.config.view.ViewConfigNode;

/**
 * ViewConfigResolverUtils.
 *
 * @author Vladimir Dvorak
 */
public class ViewConfigResolverUtils {

    public static List findViewConfigRootClasses(ViewConfigNode configNode) {
        List result = new ArrayList<>();
        if (configNode != null) {
            if (configNode.getSource() != null) {
                result.add(configNode.getSource());
            } else {
                for (ViewConfigNode childNode : configNode.getChildren()) {
                    if (childNode.getSource() != null) {
                        result.add(childNode.getSource());
                    }
                }
            }
        }
        return result;
    }
}
