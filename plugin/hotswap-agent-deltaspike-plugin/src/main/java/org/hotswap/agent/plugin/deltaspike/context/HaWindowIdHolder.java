package org.hotswap.agent.plugin.deltaspike.context;

import org.apache.deltaspike.core.impl.scope.window.WindowIdHolder;

/**
 * WindowIdHolder proxy class, used for definition of windowId from hotswap code.
 *
 * @author Vladimir Dvorak
 */
public class HaWindowIdHolder extends WindowIdHolder {

    public static String haWindowId;

    private WindowIdHolder windowIdHolder;

    public HaWindowIdHolder(WindowIdHolder windowIdHolder) {
        this.windowIdHolder = windowIdHolder;
    }

    public String getWindowId() {
        return haWindowId != null ? haWindowId : windowIdHolder.getWindowId();
    }

    public void setWindowId(String windowId) {
        windowIdHolder.setWindowId(windowId);
    }
}
