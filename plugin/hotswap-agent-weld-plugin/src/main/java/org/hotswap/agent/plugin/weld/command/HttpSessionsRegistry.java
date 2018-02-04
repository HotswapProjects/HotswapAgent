package org.hotswap.agent.plugin.weld.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpSession;

/**
 * The Class HttpSessionsRegistry. Keeps opened sessions
 */
public class HttpSessionsRegistry {

    private static Set<HttpSession> seenSessions = Collections.newSetFromMap(new java.util.WeakHashMap<HttpSession, Boolean>());

    public static void addSession(HttpSession session) {
        if (session != null) {
            seenSessions.add(session);
        }
    }

    public static void removeSession(HttpSession session) {
        if (session != null) {
            seenSessions.add(session);
        }
    }

    public static List<HttpSession> getSessions() {
        List<HttpSession> result = new ArrayList<>();
        result.addAll(seenSessions);
        return result;
    }

}
