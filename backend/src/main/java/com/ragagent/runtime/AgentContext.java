package com.ragagent.runtime;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.*;

@Data
@Builder
public class AgentContext implements Serializable {

    private String sessionId;
    private Long agentId;
    private Long userId;
    private String userQuery;

    @Builder.Default
    private AgentState currentState = AgentState.INIT;

    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    @Builder.Default
    private List<String> thoughts = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> toolExecutionTrace = new ArrayList<>();

    private String finalResponse;

    public void addThought(String stepThought) {
        this.thoughts.add(stepThought);
    }

    public void recordToolTrace(String toolName, Object input, Object output, long costMs) {
        Map<String, Object> trace = new HashMap<>();
        trace.put("tool", toolName);
        trace.put("input", input);
        trace.put("output", output);
        trace.put("costMs", costMs);
        trace.put("timestamp", System.currentTimeMillis());
        this.toolExecutionTrace.add(trace);
    }
}
