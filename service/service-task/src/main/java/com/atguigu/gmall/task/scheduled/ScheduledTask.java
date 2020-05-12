package com.atguigu.gmall.task.scheduled;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author ldh
 * @create 2020-05-11 9:13
 */
@Component
@EnableScheduling //开启定时任务注解 spring自带的
@Slf4j
public class ScheduledTask {

    @Autowired
    private RabbitService rabbitService;

    /**
     * 每天凌晨1点执行
     * @Scheduled 设定执行时间 分 时 日 周 月 年
     */
    @Scheduled(cron = "0/30 * * * * ?")//每30秒执行一次
    //@Scheduled(cron = "0 0 1 * * ?") //每天的凌晨一点钟执行
    public void task1() {
        //发送消息 将秒杀商品 放入缓存 发送内容为空 重点不是要消息本身数据 就是消息本身有消息了就执行将将秒杀商品 放入缓存
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK,  MqConst.ROUTING_TASK_1, "");
    }

    /**
     * 每天下午18点执行  删除缓存中的秒杀数据
     */
    //@Scheduled(cron = "0/35 * * * * ?")
    @Scheduled(cron = "0 0 18 * * ?")
    public void task18() {

        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK, MqConst.ROUTING_TASK_18, "");
    }


}
