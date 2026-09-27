package cn.bugstack.domain.trade.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Read-only refund-precheck facts. This value object does not initiate a refund command.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundPreviewVO {

    private String orderStatus;
    private String teamStatus;
    private String refundType;
    private Boolean refundProposalAllowed;
    private Boolean requiresManualReview;
    private Date orderUpdateTime;
    private Date teamUpdateTime;

}
