from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator


class DrillCandidateRule(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    code: str = Field(min_length=1, max_length=64, pattern=r"^[A-Z][A-Z0-9_]*$")
    title: str = Field(min_length=1, max_length=128)
    aliases: List[str] = Field(min_length=1, max_length=20)


class DrillNormalizeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    scenario_id: int = Field(alias="scenarioId", gt=0)
    action_text: str = Field(alias="actionText", min_length=1, max_length=2000)
    candidate_rules: List[DrillCandidateRule] = Field(alias="candidateRules", min_length=1, max_length=100)

    @model_validator(mode="after")
    def unique_candidate_codes(self):
        codes = [rule.code for rule in self.candidate_rules]
        if len(codes) != len(set(codes)):
            raise ValueError("candidate rule codes must be unique")
        return self


class DrillNormalizeResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    normalized_action: Optional[str] = Field(default=None, alias="normalizedAction", max_length=64)
    matched_rule_code: Optional[str] = Field(default=None, alias="matchedRuleCode", max_length=64)
    confidence: Optional[float] = Field(default=None, ge=0, le=1)
    explanation: str = Field(min_length=1, max_length=1000)

    @model_validator(mode="after")
    def normalized_value_matches_rule(self):
        if self.matched_rule_code is None and self.normalized_action is not None:
            raise ValueError("normalizedAction requires a matched candidate rule")
        if self.matched_rule_code is not None and self.normalized_action != self.matched_rule_code:
            raise ValueError("normalizedAction must equal matchedRuleCode")
        return self
