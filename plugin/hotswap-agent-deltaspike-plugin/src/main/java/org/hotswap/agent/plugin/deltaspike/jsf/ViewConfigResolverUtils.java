package org.hotswap.agent.plugin.deltaspike.jsf;

import java.util.ArrayList;
import java.util.List;

import org.apache.deltaspike.core.spi.config.view.ViewConfigNode;

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
