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
import net.rsprot.protocol.game.incoming.misc.client.WindowStatus
import net.rsprot.protocol.game.incoming.buttons.If1Button
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.incoming.misc.user.ClientCheat
import net.rsprot.protocol.game.incoming.misc.user.MoveGameClick
import net.rsprot.protocol.game.incoming.misc.user.MoveMinimapClick
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
        builder.addListener(If1Button::class.java, MessageConsumer { player, message ->
            world.onButton(player, message.interfaceId, message.componentId, 1)
        })
        builder.addListener(If3Button::class.java, MessageConsumer { player, message ->
            world.onButton(player, message.interfaceId, message.componentId, message.op)
        })
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
