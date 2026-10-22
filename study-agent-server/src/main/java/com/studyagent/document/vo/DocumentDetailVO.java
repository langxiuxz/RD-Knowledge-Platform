package com.studyagent.document.vo;
import java.util.List;
public record DocumentDetailVO(DocumentVO document,List<DocumentVersionVO> versions) {}
