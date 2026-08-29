package cn.bugstack.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent order facts request. The caller identity is intentionally not part of this DTO.
 */
@Data
public class AgentOrderFactsRequestDTO implements Serializable {

    private static final long serialVersionUID = 486224347803336216L;

    private String outTradeNo;

}
