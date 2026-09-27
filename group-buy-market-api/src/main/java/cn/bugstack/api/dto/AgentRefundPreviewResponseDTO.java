package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * Safe read-only result for deciding whether a refund proposal may be created later.
 * It never represents a completed refund or a state transition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRefundPreviewResponseDTO implements Serializable {

    private static final long serialVersionUID = 8890904979395882667L;

    private String orderStatus;
    private String teamStatus;
    private String refundType;
    private Boolean refundProposalAllowed;
    private Boolean requiresManualReview;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Shanghai")
    private Date orderUpdateTime;

}
