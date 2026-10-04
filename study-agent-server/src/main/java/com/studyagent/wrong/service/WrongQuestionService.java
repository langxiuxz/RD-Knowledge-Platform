package com.studyagent.wrong.service;

import com.studyagent.common.exception.BusinessException;
import com.studyagent.common.exception.ErrorCode;
import com.studyagent.common.result.PageResult;
import com.studyagent.wrong.dto.WrongQuestionQuery;
import com.studyagent.wrong.mapper.WrongQuestionMapper;
import com.studyagent.wrong.vo.WrongQuestionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WrongQuestionService {
    private final WrongQuestionMapper mapper;

    public WrongQuestionService(WrongQuestionMapper mapper) { this.mapper = mapper; }

    public PageResult<WrongQuestionVO> list(Long userId, WrongQuestionQuery query) {
        long total = mapper.countByUserId(userId, query.topic(), query.mastered());
        List<WrongQuestionVO> rows = mapper.findPageByUserId(userId, query.topic(), query.mastered(),
                (query.page() - 1) * query.pageSize(), query.pageSize()).stream()
                .map(row -> new WrongQuestionVO(row.getId(), row.getTopic(), row.getWrongCount(), row.getMastered()))
                .toList();
        return new PageResult<>(total, rows);
    }

    @Transactional
    public void updateMastered(Long userId, Long id, boolean mastered) {
        if (mapper.updateMastered(id, userId, mastered) == 0) throw notFound();
    }

    @Transactional
    public void delete(Long userId, Long id) {
        if (mapper.deleteByIdAndUserId(id, userId) == 0) throw notFound();
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.FORBIDDEN, "错题不存在或无权访问");
    }
}
