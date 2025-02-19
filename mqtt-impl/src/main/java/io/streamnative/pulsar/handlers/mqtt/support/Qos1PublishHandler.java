/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.streamnative.pulsar.handlers.mqtt.support;

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.streamnative.pulsar.handlers.mqtt.AbstractQosPublishHandler;
import io.streamnative.pulsar.handlers.mqtt.MQTTServerConfiguration;
import io.streamnative.pulsar.handlers.mqtt.utils.MqttMessageUtils;
import io.streamnative.pulsar.handlers.mqtt.utils.NettyUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.broker.PulsarService;
import org.apache.pulsar.broker.service.BrokerServiceException;
import org.apache.zookeeper.KeeperException;
/**
 * Publish handler implementation for Qos 1.
 */
@Slf4j
public class Qos1PublishHandler extends AbstractQosPublishHandler {

    public Qos1PublishHandler(PulsarService pulsarService, MQTTServerConfiguration configuration) {
        super(pulsarService, configuration);
    }

    @Override
    public void publish(Channel channel, MqttPublishMessage msg) {
        final String topic = msg.variableHeader().topicName();
        writeToPulsarTopic(msg).whenComplete((p, e) -> {
            if (e == null) {
                if (log.isDebugEnabled()) {
                    log.debug("[{}] Write {} to Pulsar topic succeed.", topic, msg);
                }
                MqttPubAckMessage pubAckMessage = MqttMessageUtils.pubAck(msg.variableHeader().packetId());
                channel.writeAndFlush(pubAckMessage).addListener(future -> {
                    if (future.isSuccess()) {
                        if (log.isDebugEnabled()) {
                            log.debug("[{}] Send Pub Ack {} to {}", topic, msg.variableHeader().packetId(),
                                    NettyUtils.getClientId(channel));
                        }
                    } else {
                        log.warn("[{}] Failed to send Pub Ack {} to {}", topic, msg.variableHeader().packetId(),
                                NettyUtils.getClientId(channel), future.cause());
                    }
                });
            } else {
                log.error("[{}] Write {} to Pulsar topic failed.", topic, msg, e);
                Throwable cause = e.getCause();
                if (cause instanceof BrokerServiceException.ServerMetadataException) {
                    cause = cause.getCause();
                    if (cause instanceof KeeperException.NoNodeException) {
                        channel.close();
                    }
                }
            }
        });
    }
}
