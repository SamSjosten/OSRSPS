package osrsps.net

import net.rsprot.compression.provider.HuffmanCodecProvider
import net.rsprot.crypto.rsa.RsaKeyPair
import net.rsprot.protocol.api.AbstractNetworkServiceFactory
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import net.rsprot.protocol.api.GameConnectionHandler
import net.rsprot.protocol.api.bootstrap.BootstrapBuilder
import net.rsprot.protocol.api.handlers.LoginHandlers
import net.rsprot.protocol.api.js5.Js5GroupProvider
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.incoming.misc.client.Idle
import net.rsprot.protocol.game.incoming.misc.client.MapBuildComplete
import net.rsprot.protocol.game.incoming.misc.client.NoTimeout
import net.rsprot.protocol.game.incoming.misc.client.SoundJingleEnd
import net.rsprot.protocol.game.incoming.misc.client.WindowStatus
import net.rsprot.protocol.game.incoming.buttons.If1Button
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.incoming.buttons.IfButtonD
import net.rsprot.protocol.game.incoming.buttons.IfButtonT
import net.rsprot.protocol.game.incoming.locs.OpLoc6
import net.rsprot.protocol.game.incoming.locs.OpLocV2
import net.rsprot.protocol.game.incoming.messaging.MessagePublic
import net.rsprot.protocol.game.incoming.objs.OpObj6
import net.rsprot.protocol.game.incoming.objs.OpObjV2
import net.rsprot.protocol.game.incoming.misc.user.ClientCheat
import net.rsprot.protocol.game.incoming.misc.user.CloseModal
import net.rsprot.protocol.game.incoming.misc.user.MoveGameClick
import net.rsprot.protocol.game.incoming.misc.user.MoveMinimapClick
import net.rsprot.protocol.game.incoming.misc.user.SetChatFilterSettings
import net.rsprot.protocol.game.incoming.npcs.OpNpc6
import net.rsprot.protocol.game.incoming.npcs.OpNpcT
import net.rsprot.protocol.game.incoming.npcs.OpNpcV2
import net.rsprot.protocol.game.incoming.resumed.ResumePauseButton
import net.rsprot.protocol.game.outgoing.misc.player.ChatFilterSettings
import net.rsprot.protocol.game.outgoing.misc.player.ChatFilterSettingsPrivateChat
import net.rsprot.protocol.loginprot.incoming.pow.NopProofOfWorkProvider
import net.rsprot.protocol.message.codec.incoming.GameMessageConsumerRepositoryBuilder
import net.rsprot.protocol.message.codec.incoming.MessageConsumer
import net.rsprot.protocol.message.codec.incoming.provider.DefaultGameMessageConsumerRepositoryProvider
import net.rsprot.protocol.message.codec.incoming.provider.GameMessageConsumerRepositoryProvider
import osrsps.game.Player
import osrsps.game.World
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/** Wires our cache, keys and login handling into RSProt's network service. */
@OptIn(ExperimentalUnsignedTypes::class)
class NetworkFactory(
    private val port: Int,
    private val rsa: RsaKeyPair,
    private val huffman: HuffmanCodecProvider,
    private val js5: Js5GroupProvider,
    private val world: World,
) : AbstractNetworkServiceFactory<Player>() {
    override val ports: List<Int> get() = listOf(port)

    private val listeners = ServerChannelTracker()

    // RSProt's shutdown only stops the event loops (Netty 4.2 leaves the listening sockets open), so we
    // keep hold of the server channels ourselves and close them in [closeListeners].
    override fun getBootstrapBuilder(): BootstrapBuilder =
        super.getBootstrapBuilder().configureBootstrapExtra(Consumer { it.handler(listeners) })

    /** Closes the listening sockets; call before shutting the network service down. */
    fun closeListeners() = listeners.closeAll()

    override val supportedClientTypes: List<OldSchoolClientType> get() = listOf(OldSchoolClientType.DESKTOP)

    override fun getRsaKeyPair(): RsaKeyPair = rsa

    override fun getHuffmanCodecProvider(): HuffmanCodecProvider = huffman

    override fun getJs5GroupProvider(): Js5GroupProvider = js5

    override fun getGameConnectionHandler(): GameConnectionHandler<Player> = world

    // Proof of work only slows down logins on a private test server.
    override fun getLoginHandlers(): LoginHandlers = LoginHandlers(proofOfWorkProvider = NopProofOfWorkProvider)

    override fun getGameMessageConsumerRepositoryProvider(): GameMessageConsumerRepositoryProvider<Player> {
        val builder = GameMessageConsumerRepositoryBuilder<Player>()
        builder.addListener(MapBuildComplete::class.java, MessageConsumer { _, _ -> })
        builder.addListener(NoTimeout::class.java, MessageConsumer { _, _ -> })
        builder.addListener(Idle::class.java, MessageConsumer { _, _ -> })
        // The client says a jingle finished. Nothing waits on one yet.
        builder.addListener(SoundJingleEnd::class.java, MessageConsumer { _, _ -> })
        builder.addListener(WindowStatus::class.java, MessageConsumer { player, message -> player.windowMode = message.windowMode })
        // Consumers run on the game thread, inside World's processIncomingPackets.
        builder.addListener(MoveGameClick::class.java, MessageConsumer { player, message ->
            world.onMoveClick(player, message.x, message.z, message.keyCombination)
        })
        builder.addListener(MoveMinimapClick::class.java, MessageConsumer { player, message ->
            world.onMoveClick(player, message.x, message.z, message.keyCombination)
        })
        // Autocomplete is the C++ client's console tab key; only a command sent with enter runs.
        builder.addListener(ClientCheat::class.java, MessageConsumer { player, message ->
            if (!message.autocomplete) world.onCommand(player, message.command)
        })
        builder.addListener(MessagePublic::class.java, MessageConsumer { player, message -> world.onPublicChat(player, message) })
        // Inferred: the chat tabs' filter labels change only when the server echoes the setting back. Nothing filters
        // chat on the server yet.
        builder.addListener(SetChatFilterSettings::class.java, MessageConsumer { player, message ->
            player.session.queue(ChatFilterSettings(message.publicChatFilter, message.tradeChatFilter))
            player.session.queue(ChatFilterSettingsPrivateChat(message.privateChatFilter))
        })
        builder.addListener(If1Button::class.java, MessageConsumer { player, message ->
            world.onButton(player, message.interfaceId, message.componentId, -1, 1)
        })
        builder.addListener(If3Button::class.java, MessageConsumer { player, message ->
            world.onButton(player, message.interfaceId, message.componentId, message.sub, message.op, message.obj)
        })
        builder.addListener(IfButtonD::class.java, MessageConsumer { player, message ->
            world.onButtonDrag(
                player, message.selectedInterfaceId, message.selectedComponentId, message.selectedSub, message.selectedObj,
                message.targetInterfaceId, message.targetComponentId, message.targetSub,
            )
        })
        builder.addListener(IfButtonT::class.java, MessageConsumer { player, message ->
            world.onButtonTarget(
                player, message.selectedInterfaceId, message.selectedComponentId, message.selectedSub, message.selectedObj,
                message.targetInterfaceId, message.targetComponentId, message.targetSub, message.targetObj,
            )
        })
        // RSProt gives a sub-option of 0 for none.
        builder.addListener(OpNpcV2::class.java, MessageConsumer { player, message ->
            world.onNpcOp(player, message.index, message.op, if (message.subop == 0) -1 else message.subop, message.controlKey)
        })
        builder.addListener(OpNpcT::class.java, MessageConsumer { player, message ->
            world.onNpcTarget(
                player, message.index, message.selectedInterfaceId, message.selectedComponentId, message.selectedSub,
                message.selectedObj, message.controlKey,
            )
        })
        builder.addListener(OpNpc6::class.java, MessageConsumer { player, message -> world.onNpcExamine(player, message.id) })
        builder.addListener(OpLocV2::class.java, MessageConsumer { player, message ->
            world.onLocOp(player, message.id, message.x, message.z, message.op, message.controlKey)
        })
        builder.addListener(OpLoc6::class.java, MessageConsumer { player, message -> world.onLocExamine(player, message.id) })
        builder.addListener(OpObjV2::class.java, MessageConsumer { player, message ->
            world.onGroundObjOp(player, message.id, message.x, message.z, message.op, message.controlKey)
        })
        builder.addListener(OpObj6::class.java, MessageConsumer { player, message ->
            world.onGroundObjExamine(player, message.id, message.x, message.z)
        })
        builder.addListener(ResumePauseButton::class.java, MessageConsumer { player, message ->
            world.onResumePauseButton(player, message.interfaceId, message.componentId, message.sub)
        })
        builder.addListener(CloseModal::class.java, MessageConsumer { player, _ -> world.onCloseModal(player) })
        return DefaultGameMessageConsumerRepositoryProvider(builder.build())
    }
}

@ChannelHandler.Sharable
private class ServerChannelTracker : ChannelInboundHandlerAdapter() {
    private val channels = CopyOnWriteArrayList<io.netty.channel.Channel>()

    // handlerAdded runs when the server channel registers with its event loop, which Netty completes
    // before the bind future that RSProt's start() waits on. channelActive would be too late: Netty
    // schedules it after the bind future completes, so a fast failure could miss the channel.
    override fun handlerAdded(ctx: ChannelHandlerContext) {
        channels.add(ctx.channel())
        super.handlerAdded(ctx)
    }

    fun closeAll() {
        for (channel in channels) channel.close().syncUninterruptibly()
        channels.clear()
    }
}
