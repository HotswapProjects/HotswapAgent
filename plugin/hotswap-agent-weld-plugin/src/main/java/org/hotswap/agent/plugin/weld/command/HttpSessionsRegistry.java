package org.hotswap.agent.plugin.weld.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

/**
 * The Class HttpSessionsRegistry. Keeps opened sessions
 */
public class HttpSessionsRegistry {

    private static Map<HttpSession, Boolean> seenSessions = new java.util.WeakHashMap<>();

    /**
     * Adds the seen session.
     *
     * @param session the session
     */
    public static void addSeenSession(HttpSession session) {
        seenSessions.put(session, Boolean.TRUE);
    }

    /**
     * Gets the seen sessions.
     *
     * @return the seen sessions
     */
    public static List<HttpSession> getSeenSessions() {
        List<HttpSession> result = new ArrayList<>();
        result.addAll(seenSessions.keySet());
        return result;
    }

}
