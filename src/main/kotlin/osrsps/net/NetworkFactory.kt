package osrsps.net

import net.rsprot.compression.provider.HuffmanCodecProvider
import net.rsprot.crypto.rsa.RsaKeyPair
import net.rsprot.protocol.api.AbstractNetworkServiceFactory
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import net.rsprot.protocol.api.GameConnectionHandler
import net.rsprot.protocol.api.bootstrap.BootstrapBuilder
import net.rsprot.protocol.api.InetAddressValidator
import net.rsprot.protocol.api.handlers.INetAddressHandlers
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
import net.rsprot.protocol.game.incoming.buttons.IfScriptTrigger
import net.rsprot.protocol.game.incoming.locs.OpLoc6
import net.rsprot.protocol.game.incoming.locs.OpLocT
import net.rsprot.protocol.game.incoming.locs.OpLocV2
import net.rsprot.protocol.game.incoming.messaging.MessagePrivate
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
import net.rsprot.protocol.game.incoming.players.OpPlayer
import net.rsprot.protocol.game.incoming.social.FriendListAdd
import net.rsprot.protocol.game.incoming.social.FriendListDel
import net.rsprot.protocol.game.incoming.social.IgnoreListAdd
import net.rsprot.protocol.game.incoming.social.IgnoreListDel
import net.rsprot.protocol.game.incoming.resumed.ResumePCountDialog
import net.rsprot.protocol.game.incoming.resumed.ResumePauseButton
import net.rsprot.protocol.loginprot.incoming.pow.NopProofOfWorkProvider
import net.rsprot.protocol.message.codec.incoming.GameMessageConsumerRepositoryBuilder
import net.rsprot.protocol.message.codec.incoming.MessageConsumer
import net.rsprot.protocol.message.codec.incoming.provider.DefaultGameMessageConsumerRepositoryProvider
import net.rsprot.protocol.message.codec.incoming.provider.GameMessageConsumerRepositoryProvider
import osrsps.game.AllSettings
import osrsps.game.ChatFilters
import osrsps.game.Player
import osrsps.game.SettingsSide
import osrsps.game.World
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/** Wires our cache, keys and login handling into RSProt's network service. */
@OptIn(ExperimentalUnsignedTypes::class)
class NetworkFactory(
    private val port: Int,
    private val rsa: RsaKeyPair,
    private val huffman: HuffmanCodecProvider,
    private val js5: Js5GroupProvider,
    private val world: World,
    /** Addresses that relay many players' connections, such as a forwarding VPS; see [RelayAwareValidator]. */
    private val relays: Set<String> = emptySet(),
    /** Who may say whose a connection really is; see [ProxyProtocol]. */
    private val proxy: ProxyProtocol = ProxyProtocol.OFF,
    /** Counts the JS5 connections let in, for /status; see [RelayAwareValidator]. */
    private val js5Connections: AtomicLong = AtomicLong(),
) : AbstractNetworkServiceFactory<Player>() {
    override val ports: List<Int> get() = listOf(port)

    private val listeners = ServerChannelTracker()

    // RSProt's shutdown only stops the event loops (Netty 4.2 leaves the listening sockets open), so we
    // keep hold of the server channels ourselves and close them in [closeListeners]. The proxy acceptor reads a relay's
    // PROXY header in front of RSProt's handlers, so RSProt knows each connection by the player's own address.
    override fun getBootstrapBuilder(): BootstrapBuilder =
        super.getBootstrapBuilder().configureBootstrapExtra(Consumer { it.handler(serverHandlers(listeners, proxy.acceptor)) })

    /** Closes the listening sockets; call before shutting the network service down. */
    fun closeListeners() = listeners.closeAll()

    override val supportedClientTypes: List<OldSchoolClientType> get() = listOf(OldSchoolClientType.DESKTOP)

    override fun getRsaKeyPair(): RsaKeyPair = rsa

    override fun getHuffmanCodecProvider(): HuffmanCodecProvider = huffman

    override fun getJs5GroupProvider(): Js5GroupProvider = js5

    override fun getGameConnectionHandler(): GameConnectionHandler<Player> = world

    override fun getINetAddressHandlers(): INetAddressHandlers = INetAddressHandlers(RelayAwareValidator(relays, js5Connections = js5Connections))

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
        builder.addListener(SetChatFilterSettings::class.java, MessageConsumer { player, message ->
            world.onChatFilters(player, ChatFilters(message.publicChatFilter, message.privateChatFilter, message.tradeChatFilter))
        })
        builder.addListener(If1Button::class.java, MessageConsumer { player, message ->
            world.onButton(player, message.interfaceId, message.componentId, -1, 1)
        })
        builder.addListener(If3Button::class.java, MessageConsumer { player, message ->
            world.onButton(player, message.interfaceId, message.componentId, message.sub, message.op, message.obj)
        })
        // The packet doesn't say its arguments' types; a script we don't know is only logged, its buffer released.
        builder.addListener(IfScriptTrigger::class.java, MessageConsumer { player, message ->
            val types = SettingsSide.SCRIPT_TRIGGERS[message.crc] ?: AllSettings.SCRIPT_TRIGGERS[message.crc]
            if (types == null) {
                message.release()
                world.onUnreadScriptTrigger(player, message.interfaceId, message.componentId, message.sub, message.crc, "is not one we know")
                return@MessageConsumer
            }
            val args = runCatching { message.decode(types) }.getOrElse {
                world.onUnreadScriptTrigger(player, message.interfaceId, message.componentId, message.sub, message.crc, "has arguments we can't read: ${it.message}")
                return@MessageConsumer
            }
            world.onScriptTrigger(player, message.interfaceId, message.componentId, message.sub, args)
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
        builder.addListener(OpLocT::class.java, MessageConsumer { player, message ->
            world.onLocTarget(
                player, message.id, message.x, message.z, message.selectedInterfaceId, message.selectedComponentId,
                message.selectedSub, message.selectedObj, message.controlKey,
            )
        })
        builder.addListener(OpLoc6::class.java, MessageConsumer { player, message -> world.onLocExamine(player, message.id) })
        builder.addListener(OpObjV2::class.java, MessageConsumer { player, message ->
            world.onGroundObjOp(player, message.id, message.x, message.z, message.op, message.controlKey)
        })
        builder.addListener(OpObj6::class.java, MessageConsumer { player, message ->
            world.onGroundObjExamine(player, message.id, message.x, message.z)
        })
        builder.addListener(OpPlayer::class.java, MessageConsumer { player, message ->
            world.onPlayerOp(player, message.index, message.op, message.controlKey)
        })
        builder.addListener(ResumePCountDialog::class.java, MessageConsumer { player, message ->
            world.onResumeCount(player, message.count)
        })
        builder.addListener(ResumePauseButton::class.java, MessageConsumer { player, message ->
            world.onResumePauseButton(player, message.interfaceId, message.componentId, message.sub)
        })
        builder.addListener(FriendListAdd::class.java, MessageConsumer { player, message -> world.onFriendAdd(player, message.name) })
        builder.addListener(FriendListDel::class.java, MessageConsumer { player, message -> world.onFriendDelete(player, message.name) })
        builder.addListener(MessagePrivate::class.java, MessageConsumer { player, message -> world.onPrivateMessage(player, message.name, message.message) })
        builder.addListener(IgnoreListAdd::class.java, MessageConsumer { player, message -> world.onIgnoreAdd(player, message.name) })
        builder.addListener(IgnoreListDel::class.java, MessageConsumer { player, message -> world.onIgnoreDelete(player, message.name) })
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

/**
 * RSProt's per-address connection limit, game and JS5 each, except for [relays]: a relay connection without a PROXY
 * header arrives from the relay's address, so the usual limit would let only a handful of them in at once. One with a
 * header is counted by the player's own address like anyone else's (see [ProxyProtocol]). Logins through a relay are
 * also slowed by name (osrsps.game.LoginGuard).
 *
 * Each JS5 connection let in is counted in [js5Connections]. RSProt asks about one only after its revision checked out,
 * so the count going up means a client of the server's revision reached us and started loading: the test VM reads it
 * from /status to see that a client which can't log in by itself still got that far.
 */
class RelayAwareValidator(
    private val relays: Set<String>,
    private val limit: Int = LIMIT,
    private val relayLimit: Int = RELAY_LIMIT,
    private val js5Connections: AtomicLong = AtomicLong(),
) : InetAddressValidator {
    private fun limitFor(address: String) = if (address in relays) relayLimit else limit

    override fun acceptGameConnection(address: String, activeGameConnections: Int): Boolean =
        activeGameConnections < limitFor(address)

    override fun acceptJs5Connection(address: String, activeJs5Connections: Int, seed: IntArray): Boolean =
        (activeJs5Connections < limitFor(address)).also { if (it) js5Connections.incrementAndGet() }

    companion object {
        /** RSProt's own default. */
        const val LIMIT = 10

        /** Room for a full world, as each player holds a game connection and, while loading, a JS5 one. */
        const val RELAY_LIMIT = 2 * World.MAX_PLAYERS
    }
}
