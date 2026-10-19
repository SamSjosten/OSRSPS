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
import net.rsprot.protocol.loginprot.incoming.pow.NopProofOfWorkProvider
import net.rsprot.protocol.message.codec.incoming.GameMessageConsumerRepositoryBuilder
import net.rsprot.protocol.message.codec.incoming.MessageConsumer
import net.rsprot.protocol.message.codec.incoming.provider.DefaultGameMessageConsumerRepositoryProvider
import net.rsprot.protocol.message.codec.incoming.provider.GameMessageConsumerRepositoryProvider
import osrsps.game.Player
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/** Wires our cache, keys and login handling into RSProt's network service. */
@OptIn(ExperimentalUnsignedTypes::class)
class NetworkFactory(
    private val port: Int,
    private val rsa: RsaKeyPair,
    private val huffman: HuffmanCodecProvider,
    private val js5: Js5GroupProvider,
    private val connectionHandler: GameConnectionHandler<Player>,
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

    override fun getGameConnectionHandler(): GameConnectionHandler<Player> = connectionHandler

    // Proof of work only slows down logins on a private test server.
    override fun getLoginHandlers(): LoginHandlers = LoginHandlers(proofOfWorkProvider = NopProofOfWorkProvider)

    override fun getGameMessageConsumerRepositoryProvider(): GameMessageConsumerRepositoryProvider<Player> {
        val builder = GameMessageConsumerRepositoryBuilder<Player>()
        builder.addListener(MapBuildComplete::class.java, MessageConsumer { _, _ -> })
        builder.addListener(NoTimeout::class.java, MessageConsumer { _, _ -> })
        builder.addListener(Idle::class.java, MessageConsumer { _, _ -> })
        builder.addListener(WindowStatus::class.java, MessageConsumer { player, message -> player.windowMode = message.windowMode })
        return DefaultGameMessageConsumerRepositoryProvider(builder.build())
    }
}

@ChannelHandler.Sharable
private class ServerChannelTracker : ChannelInboundHandlerAdapter() {
    private val channels = CopyOnWriteArrayList<io.netty.channel.Channel>()

    override fun channelActive(ctx: ChannelHandlerContext) {
        channels.add(ctx.channel())
        super.channelActive(ctx)
    }

    fun closeAll() {
        for (channel in channels) channel.close().syncUninterruptibly()
        channels.clear()
    }
}
