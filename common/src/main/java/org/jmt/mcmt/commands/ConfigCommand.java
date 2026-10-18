package org.jmt.mcmt.commands;

import java.util.function.Function;

import org.jmt.mcmt.MCMTCommon;
import org.jmt.mcmt.config.MCMTConfig;
import org.jmt.mcmt.serdes.SerDesRegistry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ConfigCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> mcmt = Commands.literal("mcmt");
		mcmt = mcmt.then(registerConfig(Commands.literal("config")));
		mcmt = mcmt.then(DebugCommands.registerDebug(Commands.literal("debug")));
		mcmt = StatsCommand.registerStatus(mcmt);
		dispatcher.register(mcmt);
	}

	static void success(CommandContext<CommandSourceStack> ctx, String text) {
		Component c = Component.literal(text);
		ctx.getSource().sendSuccess(() -> c, true);
	}

	static void failure(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendFailure(Component.literal(text));
	}

	private static int toggle(CommandContext<CommandSourceStack> ctx, String label, boolean newValue) {
		success(ctx, label + " is now " + (newValue ? "disabled" : "enabled"));
		return 1;
	}

	/**
	 * Returns true when the block entity has a server ticker, i.e. it would be ticked by
	 * {@code Level.tickBlockEntities}. In 1.21 block entities no longer implement
	 * {@code TickingBlockEntity} themselves; the ticker comes from the block.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	static boolean isTickable(ServerLevel level, BlockEntity be) {
		if (be == null) return false;
		BlockState state = be.getBlockState();
		if (!(state.getBlock() instanceof EntityBlock eb)) return false;
		BlockEntityTicker ticker = eb.getTicker(level, state, (BlockEntityType) be.getType());
		return ticker != null;
	}

	/** Runs {@code action} on the tickable block entity the player is looking at, or reports why not. */
	private static int withTargetedBlockEntity(CommandContext<CommandSourceStack> ctx, Function<BlockEntity, String> action) throws CommandSyntaxException {
		HitResult hit = ctx.getSource().getPlayerOrException().pick(20, 0.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			failure(ctx, "Look at a block within 20 blocks");
			return 0;
		}
		BlockPos bp = ((BlockHitResult) hit).getBlockPos();
		ServerLevel level = ctx.getSource().getLevel();
		BlockEntity be = level.getBlockEntity(bp);
		if (!isTickable(level, be)) {
			failure(ctx, "That block doesn't contain a tickable block entity!");
			return 0;
		}
		success(ctx, action.apply(be));
		return 1;
	}

	public static LiteralArgumentBuilder<CommandSourceStack> registerConfig(LiteralArgumentBuilder<CommandSourceStack> root) {
		return root
			.then(Commands.literal("toggle").requires(src -> src.hasPermission(2))
				.executes(ctx -> toggle(ctx, "MCMT", MCMTConfig.disabled = !MCMTConfig.disabled))
				.then(Commands.literal("te").executes(ctx -> toggle(ctx, "MCMT's block entity threading", MCMTConfig.disableTileEntity = !MCMTConfig.disableTileEntity)))
				.then(Commands.literal("entity").executes(ctx -> toggle(ctx, "MCMT's entity threading", MCMTConfig.disableEntity = !MCMTConfig.disableEntity)))
				.then(Commands.literal("environment").executes(ctx -> toggle(ctx, "MCMT's environment threading", MCMTConfig.disableEnvironment = !MCMTConfig.disableEnvironment)))
				.then(Commands.literal("world").executes(ctx -> toggle(ctx, "MCMT's world threading", MCMTConfig.disableWorld = !MCMTConfig.disableWorld)))
				.then(Commands.literal("chunkprovider").executes(ctx -> toggle(ctx, "MCMT's SCP threading", MCMTConfig.disableChunkProvider = !MCMTConfig.disableChunkProvider))))
			.then(Commands.literal("state").executes(ctx -> {
				StringBuilder sb = new StringBuilder("MCMT is currently " + (MCMTConfig.disabled ? "disabled" : "enabled"));
				if (!MCMTConfig.disabled) {
					sb.append(" World:").append(MCMTConfig.disableWorld ? "disabled" : "enabled");
					sb.append(" Entity:").append(MCMTConfig.disableEntity ? "disabled" : "enabled");
					sb.append(" TE:").append(MCMTConfig.disableTileEntity ? "disabled"
							: "enabled" + (MCMTConfig.chunkLockModded ? "(ChunkLocking Modded)" : ""));
					sb.append(" Env:").append(MCMTConfig.disableEnvironment ? "disabled" : "enabled");
					sb.append(" SCP:").append(MCMTConfig.disableChunkProvider ? "disabled" : "enabled");
				}
				success(ctx, sb.toString());
				return 1;
			}))
			.then(Commands.literal("save").requires(src -> src.hasPermission(2)).executes(ctx -> {
				success(ctx, "Saving MCMT config to disk...");
				MCMTCommon.saveConfig();
				success(ctx, "Done!");
				return 1;
			}))
			.then(Commands.literal("temanage").requires(src -> src.hasPermission(2))
				.then(Commands.literal("list").executes(ctx -> {
					StringBuilder sb = new StringBuilder("TE whitelist: ");
					MCMTConfig.teWhiteList.forEach(c -> sb.append(c.getName()).append(' '));
					sb.append(MCMTConfig.teUnfoundWhiteList);
					sb.append(" | TE blacklist: ");
					MCMTConfig.teBlackList.forEach(c -> sb.append(c.getName()).append(' '));
					sb.append(MCMTConfig.teUnfoundBlackList);
					success(ctx, sb.toString());
					return 1;
				}))
				.then(Commands.literal("target").requires(src -> src.isPlayer())
					.then(Commands.literal("whitelist").executes(ctx -> withTargetedBlockEntity(ctx, be -> {
						MCMTConfig.teWhiteList.add(be.getClass());
						MCMTConfig.teBlackList.remove(be.getClass());
						return "Added " + be.getClass().getName() + " to TE Whitelist";
					})))
					.then(Commands.literal("blacklist").executes(ctx -> withTargetedBlockEntity(ctx, be -> {
						MCMTConfig.teBlackList.add(be.getClass());
						MCMTConfig.teWhiteList.remove(be.getClass());
						return "Added " + be.getClass().getName() + " to TE Blacklist";
					})))
					.then(Commands.literal("remove").executes(ctx -> withTargetedBlockEntity(ctx, be -> {
						MCMTConfig.teBlackList.remove(be.getClass());
						MCMTConfig.teWhiteList.remove(be.getClass());
						return "Removed " + be.getClass().getName() + " from TE classlists";
					})))
					.then(Commands.literal("willtick").executes(ctx -> withTargetedBlockEntity(ctx, be -> {
						boolean serial = SerDesRegistry.DefaultFilter.filterTE(be);
						return "That TE " + (serial ? "will not" : "will") + " tick fully parallelised";
					})))));
	}
}
