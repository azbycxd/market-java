package cn.bugstack.domain.trade.service;

import cn.bugstack.domain.trade.model.valobj.RefundPreviewVO;

/**
 * Reads refund precheck facts for one authenticated user's order. A null return keeps missing and
 * other-user orders externally indistinguishable.
 */
public interface IRefundPreviewService {

    RefundPreviewVO getRefundPreview(String authenticatedUserId, String outTradeNo);

}
