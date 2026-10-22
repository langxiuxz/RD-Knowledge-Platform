package com.studyagent.onboarding.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.onboarding.entity.*;
import java.util.*;

/** Deterministic DAG validation; dependency direction is task -> prerequisites. */
public final class TaskGraph {
    private TaskGraph() {}
    public static Map<Long,List<Long>> prerequisites(List<PathTaskEntity> tasks,List<TaskDependencyEntity> edges) {
        Map<Long,List<Long>> result=new LinkedHashMap<>();
        for (var task:tasks) result.put(task.getId(),new ArrayList<>());
        for (var edge:edges) {
            var list=result.get(edge.getTaskId());
            if (list==null || !result.containsKey(edge.getPrerequisiteTaskId()) ||
                    edge.getTaskId().equals(edge.getPrerequisiteTaskId()) || list.contains(edge.getPrerequisiteTaskId()))
                throw invalid("依赖必须是同一路径中的不同任务，且不得重复");
            list.add(edge.getPrerequisiteTaskId());
        }
        Map<Long,Integer> remaining=new HashMap<>();
        Map<Long,List<Long>> dependents=new HashMap<>();
        ArrayDeque<Long> ready=new ArrayDeque<>();
        result.forEach((id,ids)-> {
            remaining.put(id,ids.size());
            if(ids.isEmpty()) ready.add(id);
            ids.forEach(pre->dependents.computeIfAbsent(pre,k->new ArrayList<>()).add(id));
        });
        int visited=0;
        while(!ready.isEmpty()) {
            Long id=ready.remove(); visited++;
            for(Long next:dependents.getOrDefault(id,List.of()))
                if(remaining.compute(next,(k,v)->v-1)==0) ready.add(next);
        }
        if(visited!=tasks.size()) throw invalid("任务依赖不能包含环");
        return result;
    }
    static BusinessException invalid(String message) { return new BusinessException(ErrorCode.ONBOARDING_PATH_INVALID,message); }
}
