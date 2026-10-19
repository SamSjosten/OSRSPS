package osrsps.net

import net.rsprot.compression.provider.HuffmanCodecProvider
import net.rsprot.crypto.rsa.RsaKeyPair
import net.rsprot.protocol.api.AbstractNetworkServiceFactory
import net.rsprot.protocol.api.GameConnectionHandler
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
