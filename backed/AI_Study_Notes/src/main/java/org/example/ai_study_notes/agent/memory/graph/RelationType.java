package org.example.ai_study_notes.agent.memory.graph;

import java.util.Set;

/**
 * 关系受控词表：不收口图会乱，所有关系类型必须出自这里。
 */
public enum RelationType {
    DEPENDS_ON, AFFECTS, CONTAINED_IN, ASSOCIATED_WITH, BELONGS_TO, REFERENCES;

    public static final Set<String> NAMES = Set.of(
            DEPENDS_ON.name(), AFFECTS.name(), CONTAINED_IN.name(),
            ASSOCIATED_WITH.name(), BELONGS_TO.name(), REFERENCES.name());

    public static boolean isValid(String name) {
        return name != null && NAMES.contains(name);
    }
}
