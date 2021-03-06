package com.taiji.boot.web.config.rocketmq;

import cn.hutool.core.util.StrUtil;
import com.google.common.base.Joiner;
import com.taiji.boot.common.beans.exception.BaseException;
import com.taiji.boot.common.rocketmq.TopicBusinessInterface;
import com.taiji.boot.common.rocketmq.annotation.TopicMethod;
import com.taiji.boot.common.rocketmq.constant.RocketBaseConstant;
import com.taiji.boot.common.utils.SpringUtil;
import com.taiji.boot.web.config.rocketmq.consumer.ConsumerProperties;
import com.taiji.boot.web.config.rocketmq.producer.ProducerProperties;
import com.taiji.boot.web.config.rocketmq.topic.TopicProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RocketMQ 配置类
 *
 * @author ydy
 * @date 2020/3/25 19:41
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({RocketProperties.class, ConsumerProperties.class, ProducerProperties.class})
public class RocketConfig extends RocketBaseConstant {

    private static Map YAML_MAP;

    static {
        Yaml yaml = new Yaml();
        YAML_MAP = yaml.loadAs(RocketConfig.class.getClassLoader().getResourceAsStream("application.yml"), ConcurrentHashMap.class);
    }

    @Resource
    private RocketProperties rocketProperties;

    @Resource
    private ConsumerProperties consumerProperties;

    @Resource
    private ProducerProperties producerProperties;


    @Bean(name = "userTopicProperties", initMethod = "init")
    @ConfigurationProperties(prefix = "rocket.topic-manager.user")
    public TopicProperties userTopicProperties() {
        return new TopicProperties();
    }

    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer getRocketMQProducer() {
        if (StrUtil.isBlank(producerProperties.getGroupId())) {
            throw new BaseException("groupId is blank");
        }
        if (StrUtil.isBlank(rocketProperties.getNameSvrAddr())) {
            throw new BaseException("nameServerAddr is blank");
        }
        DefaultMQProducer producer = new DefaultMQProducer(producerProperties.getGroupId());
        try {
            producer.setNamesrvAddr(rocketProperties.getNameSvrAddr());
            producer.setMaxMessageSize(producerProperties.getMaxMessageSize());
            producer.setSendMsgTimeout(producerProperties.getSendMsgTimeoutMillis());
            producer.setRetryTimesWhenSendFailed(producerProperties.getRetryTimeWhenSendFailed());
            producer.start();
            log.info(String.format("producer is start ! groupName:[%s],nameSrvAddr:[%s]", producerProperties.getGroupId(), rocketProperties.getNameSvrAddr()));
        } catch (MQClientException e) {
            e.printStackTrace();
            throw new BaseException(e);
        }
        return producer;
    }

    @Bean(destroyMethod = "shutdown")
    public DefaultMQPushConsumer getRocketMQConsumer() {
        if (StrUtil.isBlank(consumerProperties.getGroupId())) {
            throw new BaseException("groupId is blank");
        }
        if (StrUtil.isBlank(rocketProperties.getNameSvrAddr())) {
            throw new BaseException("nameServerAddr is blank");
        }
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerProperties.getGroupId());
        try {
            consumer.setNamesrvAddr(rocketProperties.getNameSvrAddr());
            consumer.setConsumeThreadMin(consumerProperties.getConsumeThreadMin());
            consumer.setConsumeThreadMax(consumerProperties.getConsumeThreadMax());
            consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> RocketConfig.this.consumeMessage(msgs));
            consumer.setConsumeMessageBatchMaxSize(consumerProperties.getConsumeMessageBatchMaxSize());
            setConsumerSubscribe(consumer);
            consumer.start();
            log.info(String.format("consumer is start ! groupName:[%s],nameSrvAddr:[%s]", consumerProperties.getGroupId(), rocketProperties.getNameSvrAddr()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new BaseException(e);
        }
        return consumer;
    }

    private void setConsumerSubscribe(DefaultMQPushConsumer consumer) {
        for (Map.Entry<String, List<String>> entry : TOPIC_TAG_MAP.entrySet()) {
            try {
                String tags = CollectionUtils.isNotEmpty(entry.getValue()) ? Joiner.on("||").join(entry.getValue()) : "";
                log.info("准备订阅消息：topic={}, tag={}", entry.getKey(), tags);
                consumer.subscribe(entry.getKey(), tags);
            } catch (MQClientException e) {
                e.printStackTrace();
                throw new BaseException(e);
            }
        }

    }

    protected ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs) {
        MessageExt messageExt = msgs.get(0);
        Map<String, TopicBusinessInterface> beans = SpringUtil.getBeanOfType(TopicBusinessInterface.class);
        ConsumeConcurrentlyStatus status;
        try {
            Map topicMap = (LinkedHashMap) ((LinkedHashMap) YAML_MAP.get("rocket")).get("topic-manager");
            for (Map.Entry<String, TopicBusinessInterface> entry : beans.entrySet()) {
                Class<?> clazz = entry.getValue().getClass();
                Method method = clazz.getMethod("doConsume", MessageExt.class);
                String topic = method.getAnnotation(TopicMethod.class).topic();
                if (topic.contains("rocket.topic-manager")) {
                    // rocket.topic-manager.user
                    String[] split = topic.split("\\.");
                    String key = split[split.length - 2];
                    Map targetTopicMap = (LinkedHashMap) topicMap.get(key);
                    String targetTopic = (String) targetTopicMap.get("topic");
                    if (messageExt.getTopic().equalsIgnoreCase(targetTopic)) {
                        status = (ConsumeConcurrentlyStatus) method.invoke(clazz.newInstance(), messageExt);
                        return status;
                    }
                } else {
                    if (messageExt.getTopic().equalsIgnoreCase(topic)) {
                        status = (ConsumeConcurrentlyStatus) method.invoke(clazz.newInstance(), messageExt);
                        return status;
                    }
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        } catch (Exception e) {
            // todo 异常没有抛出
            e.printStackTrace();
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    public static void main(String[] args) {
        String s = "rocket.topic-manager.user.topic";
        String[] split = s.split(".");
        System.out.println(s.substring(s.indexOf("")));
    }


}
