package learning.rabbitmq.rpc;

import com.rabbitmq.client.*;
import learning.rabbitmq.utils.ConnectionUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * 生产者
 */
@Slf4j
public class Send {

    private static final String QUEUE_NAME = "rpc_queue";

    public static void main(String[] args) throws Exception {
        // 创建连接
        Connection connection = ConnectionUtils.getConnection();
        // 创建通道
        Channel channel = connection.createChannel();
        // 声明一个临时队列
        channel.queueDeclare().getQueue();
        // 设置同时最多只能获取一个消息
        channel.basicQos(1);

        // 定义消息的回调处理类
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                log.info("收到回调结果:{},id:{}", new String(body), properties.getCorrelationId());
                // 生成返回的结果，关键是设置correlationId值
                AMQP.BasicProperties replyProps = new AMQP.BasicProperties
                        .Builder()
                        .correlationId(properties.getCorrelationId())
                        .build();
                // 生成返回
                String response = generateResponse(body);
                // 回复消息，通知已经收到请求
                channel.basicPublish("", properties.getReplyTo(), replyProps, response.getBytes("UTF-8"));
                // 对消息进行应答
                channel.basicAck(envelope.getDeliveryTag(), false);
                // 唤醒正在消费者所有的线程
                synchronized (this) {
                    this.notify();
                }
            }
        };
        // 消费消息
        channel.basicConsume(QUEUE_NAME, false, consumer);
        // 在收到消息前，本线程进入等待状态
        while (true) {
            synchronized (consumer) {
                try {
                    consumer.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 暂停10s，并返回结果
     *
     * @param body
     * @return
     */
    private static String generateResponse(byte[] body) {
        try {
            Thread.sleep(1000 * 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return new String(body) + "-" + System.currentTimeMillis();
    }
}