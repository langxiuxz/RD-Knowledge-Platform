package com.studyagent.onboarding;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.onboarding.entity.PathTaskEntity;
import com.studyagent.onboarding.entity.TaskDependencyEntity;
import com.studyagent.onboarding.service.TaskGraph;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class TaskGraphTest {
    @Test void acceptsUnorderedDiamondAndIndependentTasks() {
        var graph=TaskGraph.prerequisites(List.of(task(4),task(2),task(1),task(3),task(5)),
            List.of(edge(4,2),edge(4,3),edge(2,1),edge(3,1)));
        assertThat(graph.get(4L)).containsExactly(2L,3L);
        assertThat(graph.get(1L)).isEmpty();
        assertThat(graph.get(5L)).isEmpty();
    }
    @Test void rejectsCyclesSelfForeignAndDuplicateEdges() {
        var tasks=List.of(task(1),task(2),task(3));
        for(var edges:List.of(List.of(edge(1,2),edge(2,3),edge(3,1)),List.of(edge(1,1)),
                List.of(edge(1,9)),List.of(edge(9,1)),List.of(edge(2,1),edge(2,1)))) {
            assertThatThrownBy(()->TaskGraph.prerequisites(tasks,edges))
                .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.errorCode()).isEqualTo(ErrorCode.ONBOARDING_PATH_INVALID));
        }
    }
    private PathTaskEntity task(long id) {var t=new PathTaskEntity();t.setId(id);return t;}
    private TaskDependencyEntity edge(long task,long pre) {
        var e=new TaskDependencyEntity();e.setTaskId(task);e.setPrerequisiteTaskId(pre);return e;
    }
}
