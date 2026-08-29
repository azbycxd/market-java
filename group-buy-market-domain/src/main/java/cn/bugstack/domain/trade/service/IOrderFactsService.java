package cn.bugstack.domain.trade.service;

import cn.bugstack.domain.trade.model.valobj.OrderFactsVO;

/**
 * Reads facts for exactly one authenticated user's order. A null return means the order was not found
 * in that user's scope; it does not reveal whether the external trade number belongs to another user.
 */
public interface IOrderFactsService {

    OrderFactsVO getOrderFacts(String authenticatedUserId, String outTradeNo);

}
