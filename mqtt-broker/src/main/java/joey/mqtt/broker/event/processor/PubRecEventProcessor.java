package joey.mqtt.broker.event.processor;

import cn.hutool.core.util.StrUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import joey.mqtt.broker.core.message.CommonPublishMessage;
import joey.mqtt.broker.event.listener.EventListenerExecutor;
import joey.mqtt.broker.event.listener.IEventListener;
import joey.mqtt.broker.event.message.PubRecEventMessage;
import joey.mqtt.broker.store.IDupPubMessageStore;
import joey.mqtt.broker.store.IDupPubRelMessageStore;
import joey.mqtt.broker.util.MessageUtils;
import joey.mqtt.broker.util.NettyUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * pubRec事件处理
 *
 * @author Joey
 * @date 2019/7/22
 */
@Slf4j
public class PubRecEventProcessor implements IEventProcessor<MqttMessage> {
    private final IDupPubMessageStore dupPubMessageStore;

    private final IDupPubRelMessageStore dupPubRelMessageStore;

    private final EventListenerExecutor eventListenerExecutor;

    public PubRecEventProcessor(IDupPubMessageStore dupPubMessageStore, IDupPubRelMessageStore dupPubRelMessageStore, EventListenerExecutor eventListenerExecutor) {
        this.dupPubMessageStore = dupPubMessageStore;
        this.dupPubRelMessageStore = dupPubRelMessageStore;
        this.eventListenerExecutor = eventListenerExecutor;
    }

    @Override
    public void process(ChannelHandlerContext ctx, MqttMessage message) {
        Channel channel = ctx.channel();
        String clientId = NettyUtils.clientId(channel);
        String userName = NettyUtils.userName(channel);

        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader)message.variableHeader();
        int messageId = variableHeader.messageId();

        if (StrUtil.isNotBlank(clientId)) {
            CommonPublishMessage pubMsg = dupPubMessageStore.get(clientId, String.valueOf(messageId));
            if (null != pubMsg) {
                dupPubMessageStore.remove(clientId, messageId);
                dupPubRelMessageStore.add(pubMsg.copy());
            }
        }

        MqttMessage pubRelResp = MessageUtils.buildPubRelMessage(messageId, false);
        channel.writeAndFlush(pubRelResp);

        eventListenerExecutor.execute(new PubRecEventMessage(clientId, userName, messageId), IEventListener.Type.PUB_REC);
    }
}
