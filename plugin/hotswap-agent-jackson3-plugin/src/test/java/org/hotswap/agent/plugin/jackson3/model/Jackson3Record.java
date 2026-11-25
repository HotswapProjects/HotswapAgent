package org.hotswap.agent.plugin.jackson3.model;

import java.util.List;

public record Jackson3Record(String login, List<String> roles) {
}
