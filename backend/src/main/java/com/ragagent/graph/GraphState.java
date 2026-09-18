package com.ragagent.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * Spring AI Alibaba Graph 统一状态上下文模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphState implements Serializable {

    private String threadId;
    private String topic;
    private String currentNode;

    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    @Builder.Default
    private List<String> planSteps = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> evidences = new ArrayList<>();

    @Builder.Default
    private List<String> thoughts = new ArrayList<>();

    private String finalReport;

    public void put(String key, Object val) {
        this.data.put(key, val);
    }

    public Object get(String key) {
        return this.data.get(key);
    }

    public void addThought(String thought) {
        this.thoughts.add(thought);
    }
}
