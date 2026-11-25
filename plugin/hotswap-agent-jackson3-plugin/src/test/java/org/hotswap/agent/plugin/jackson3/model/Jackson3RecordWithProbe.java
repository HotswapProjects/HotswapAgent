package org.hotswap.agent.plugin.jackson3.model;

import java.util.List;

/**
 * {@link Jackson3Record} with a component added.
 */
public record Jackson3RecordWithProbe(String login, List<String> roles, String probe) {
}
