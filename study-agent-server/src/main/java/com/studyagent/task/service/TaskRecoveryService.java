package com.studyagent.task.service;

import com.studyagent.config.TaskRecoveryProperties;
import com.studyagent.document.mapper.DocumentVersionMapper;
import com.studyagent.insight.mapper.CapabilityReportMapper;
import com.studyagent.plan.mapper.StudyPlanMapper;
import com.studyagent.quiz.mapper.QuizMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.IntSupplier;

/** Recovers abandoned business states; never replays an unbounded AI request. */
@Service
public class TaskRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(TaskRecoveryService.class);
    public static final String DOCUMENT_TIMEOUT = "文档处理超时或服务中断，请重新处理文档";
    public static final String GENERATION_TIMEOUT = "AI生成超时或服务中断，请重新发起生成";
    private final DocumentVersionMapper documents;
    private final QuizMapper quizzes;
    private final StudyPlanMapper plans;
    private final CapabilityReportMapper reports;
    private final TaskRecoveryProperties properties;

    public TaskRecoveryService(DocumentVersionMapper documents, QuizMapper quizzes, StudyPlanMapper plans,
                               CapabilityReportMapper reports,
                               TaskRecoveryProperties properties) {
        this.documents = documents;
        this.quizzes = quizzes;
        this.plans = plans;
        this.reports = reports;
        this.properties = properties;
    }

    public void recover() {
        if (!properties.enabled()) return;
        // Independent atomic statements: a broken module must not block the other two.
        recover("document", () -> documents.failStaleTasks(properties.documentTimeout().getSeconds(),
                properties.batchSize(), DOCUMENT_TIMEOUT));
        recover("quiz", () -> quizzes.failStaleTasks(properties.generationTimeout().getSeconds(),
                properties.batchSize(), GENERATION_TIMEOUT));
        recover("study_plan", () -> plans.failStaleTasks(properties.generationTimeout().getSeconds(),
                properties.batchSize(), GENERATION_TIMEOUT));
        recover("capability_report", () -> reports.failStale(properties.reportTimeout().getSeconds(),
                properties.batchSize(), "能力报告生成超时或服务中断，请重新发起生成"));
    }

    private void recover(String type, IntSupplier update) {
        try {
            int count = update.getAsInt();
            if (count > 0) log.warn("Recovered expired tasks type={} count={} status=FAILED", type, count);
        } catch (RuntimeException exception) {
            // Do not log SQL connection details or credentials from a driver exception.
            log.error("Task recovery failed type={} exception={}", type, exception.getClass().getSimpleName());
        }
    }
}
