package com.atguigu.gmall.mq.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.service.RabbitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ldh
 * @create 2020-05-05 22:38
 */
@RestController
@RequestMapping("/mq")
@Slf4j
public class MqController {


    @Autowired
    private RabbitService rabbitService;

    /**
     * 消息发送
     */
    //http://cart.gmall.com/8282/mq/sendConfirm
    @GetMapping("sendConfirm")
    public Result sendConfirm(){
        String message = "hello RabbitMq!";
        rabbitService.sendMessage("exchange.confirm","routing.confirm",message);
        return Result.ok();
    }

}
