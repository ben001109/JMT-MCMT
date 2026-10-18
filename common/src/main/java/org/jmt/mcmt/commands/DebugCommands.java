package org.jmt.mcmt.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import org.jmt.mcmt.Constants;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class DebugCommands {

	public static LiteralArgumentBuilder<CommandSourceStack> registerDebug(LiteralArgumentBuilder<CommandSourceStack> root) {
		return root
			.then(Commands.literal("getBlockState")
				.then(Commands.argument("location", Vec3Argument.vec3()).executes(ctx -> {
					Coordinates loc = Vec3Argument.getCoordinates(ctx, "location");
					BlockPos bp = loc.getBlockPos(ctx.getSource());
					ServerLevel level = ctx.getSource().getLevel();
					BlockState bs = level.getBlockState(bp);
					ConfigCommand.success(ctx, "Block at " + bp + " is " + BuiltInRegistries.BLOCK.getKey(bs.getBlock()));
					return 1;
				})))
			.then(Commands.literal("nbtdump")
				.then(Commands.argument("location", Vec3Argument.vec3()).executes(ctx -> {
					Coordinates loc = Vec3Argument.getCoordinates(ctx, "location");
					BlockPos bp = loc.getBlockPos(ctx.getSource());
					ServerLevel level = ctx.getSource().getLevel();
					BlockState bs = level.getBlockState(bp);
					BlockEntity be = level.getBlockEntity(bp);
					String block = BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString();
					if (be == null) {
						ConfigCommand.success(ctx, "Block at " + bp + " is " + block + " and has no block entity");
						return 1;
					}
					CompoundTag nbt = be.saveWithFullMetadata(level.registryAccess());
					Component pretty = NbtUtils.toPrettyComponent(nbt);
					ConfigCommand.success(ctx, "Block at " + bp + " is " + block + " with block entity NBT:");
					ctx.getSource().sendSuccess(() -> pretty, true);
					return 1;
				})))
			.then(Commands.literal("tick").requires(src -> src.hasPermission(2))
				.then(Commands.literal("te")
					.then(Commands.argument("location", Vec3Argument.vec3()).executes(ctx -> {
						Coordinates loc = Vec3Argument.getCoordinates(ctx, "location");
						BlockPos bp = loc.getBlockPos(ctx.getSource());
						ServerLevel level = ctx.getSource().getLevel();
						BlockEntity be = level.getBlockEntity(bp);
						if (tickOnce(level, bp, be)) {
							ConfigCommand.success(ctx, "Ticked " + be.getClass().getName() + " at " + bp);
							return 1;
						}
						ConfigCommand.failure(ctx, "No tickable block entity at " + bp);
						return 0;
					}))))
			.then(Commands.literal("classpathDump").requires(src -> src.hasPermission(2)).executes(ctx -> {
				Path base = Paths.get("classpath_dump/");
				try {
					Files.createDirectories(base);
				} catch (IOException e) {
					Constants.LOG.error("Could not create classpath dump directory", e);
				}
				Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).flatMap(path -> {
					File file = new File(path);
					if (file.isDirectory()) {
						String[] jars = file.list((d, n) -> n.endsWith(".jar"));
						return jars == null ? Arrays.stream(new String[0]) : Arrays.stream(jars);
					}
					return Arrays.stream(new String[] { path });
				}).filter(s -> s.endsWith(".jar")).map(Paths::get).forEach(path -> {
					try {
						Files.copy(path, base.resolve(path.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						Constants.LOG.error("Could not copy {}", path, e);
					}
				});
				ConfigCommand.success(ctx, "Classpath dumped to: " + base.toAbsolutePath());
				return 1;
			}));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static boolean tickOnce(ServerLevel level, BlockPos bp, BlockEntity be) {
		if (be == null) return false;
		BlockState state = be.getBlockState();
		if (!(state.getBlock() instanceof EntityBlock eb)) return false;
		BlockEntityTicker ticker = eb.getTicker(level, state, (BlockEntityType) be.getType());
		if (ticker == null) return false;
		ticker.tick(level, bp, state, be);
		return true;
	}
}
