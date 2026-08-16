package com.eventrush.service;

import org.springframework.http.HttpStatus;

public class OrderExpiredException extends BusinessException {

    public OrderExpiredException() {
        super("ORDER_EXPIRED", HttpStatus.CONFLICT, "订单已超过支付时间，库存已释放，请重新购买");
    }
}
