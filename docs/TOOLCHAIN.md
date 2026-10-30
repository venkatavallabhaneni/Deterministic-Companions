# Verified Minecraft 26.2 environment

Verified on 2026-09-12 using official Fabric material, downloaded Maven/version metadata, the actual Mojang Minecraft **26.2** jars, Loom-generated sources, compilation and a dedicated-server launch.

| Component | Pinned and exercised |
|---|---|
| Minecraft Java Edition | 26.2 |
| Java | 25; local Adoptium 25.0.3 toolchain |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.156.0+26.2 |
| Fabric Loom | 1.17.20, `net.fabricmc.fabric-loom` plugin |
| Gradle | 9.5.1, checked-in wrapper |
| Mappings | Unobfuscated official game names; no Yarn/mappings dependency |

Fabric's [26.2 engineering update](https://www.fabricmc.net/2026/06/15/262.html) recommends Loom 1.17 and Gradle 9.5.1, and identifies Loader 0.19.3 at release. The [official 26.2 example project](https://github.com/FabricMC/fabric-example-mod/tree/26.2) specifies Java 25 and the unobfuscated Loom dependency layout. Live metadata also listed newer Loader 0.19.5 and API 0.160.0+26.2 releases; this project pins the versions actually compiled and launched rather than using dynamic `latest` dependencies.

## APIs checked in the actual 26.2 source

| Concern | Verified API |
|---|---|
| Server player | `net.minecraft.server.level.ServerPlayer(MinecraftServer, ServerLevel, GameProfile, ClientInformation)` |
| Joining | `PlayerList.placeNewPlayer(Connection, ServerPlayer, CommonListenerCookie)` |
| Identity | `GameProfile(UUID, String)` and `CommonListenerCookie.createInitial(profile, false)` |
| Local transport | `Connection(PacketFlow.SERVERBOUND)`; three-argument `send(Packet, ChannelFutureListener, boolean)` |
| Real player ticking | `ServerPlayer.tick()` plus `doTick()` for the normally network-driven player update |
| Movement authority | Override `Player.isClientAuthoritative()` to false so server collision/fall logic runs |
| Exhaustion/statistics | `ServerPlayer.checkMovementStatistics(dx, dy, dz)` |
| Player load acknowledgement | `ServerGamePacketListenerImpl.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket())` |
| Movement | Player `xxa` / `zza`, vanilla travel/physics, `jumpFromGround`, rotation setters |
| Mining | `ServerPlayerGameMode.handleBlockBreakAction(BlockPos, ServerboundPlayerActionPacket.Action, Direction, int, int)` with START/STOP/ABORT |
| Break timing | `BlockState.getDestroyProgress(Player, BlockGetter, BlockPos)`; game mode tracks mining ticks |
| Placement | `ServerPlayerGameMode.useItemOn(ServerPlayer, Level, ItemStack, InteractionHand, BlockHitResult)` |
| Food/item use | `ServerPlayerGameMode.useItem(...)` and normal use-duration ticking |
| Melee | `Player.attack(Entity)`, `getAttackStrengthScale`, `isWithinEntityInteractionRange` |
| Commands | Fabric `CommandRegistrationCallback.EVENT`, Brigadier `Commands.literal/argument` |
| Permissions | `CommandSourceStack.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)`; not the older integer `hasPermission` API |
| Registry IDs | `net.minecraft.resources.Identifier`; `BuiltInRegistries.*.getOptional(Identifier)` |
| Inventory selection | `Inventory.getSelectedSlot`, `setSelectedSlot`, `getItem`, `setItem` |
| Persistence | `PlayerList.loadPlayerData(NameAndId)`, `TagValueInput.create`, `Player.load(ValueInput)`, `PlayerList.remove` |

The [current Carpet fake-player source](https://github.com/gnembon/fabric-carpet/blob/master/src/main/java/carpet/patches/EntityPlayerMPFake.java) was consulted to confirm the genuine player/listener lifecycle pattern. Carpet is not bundled or required. This mod has its own local endpoint, controllers, tasks, policies and navigation.

`genSources` and `build` were run separately because combining them in one Gradle invocation triggered Loom's output-dependency validation. This does not affect the ordinary `clean build` command.

The launched server log reports Minecraft 26.2, Loader 0.19.3, API 0.156.0+26.2, Java 25, and this mod. It then stops at the unaccepted EULA; full world startup has not been validated.
