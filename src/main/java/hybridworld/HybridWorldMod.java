package hybridworld;

import java.util.Map;
import java.util.function.Consumer;

import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorldServer;
import io.github.opencubicchunks.cubicchunks.cubicgen.CustomCubicMod;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.logging.log4j.Logger;

import hybridworld.world.gen.HybridTerrainGenerator;
import hybridworld.world.gen.StructureInitEventHandler;
import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.api.worldgen.VanillaCompatibilityGeneratorProviderBase;
import io.github.opencubicchunks.cubicchunks.cubicgen.common.biome.CubicBiome;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.populator.DefaultDecorator;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.populator.PrePopulator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeEnd;
import net.minecraft.world.biome.BiomeHell;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber
@Mod(modid = HybridWorldMod.MODID, name = HybridWorldMod.NAME, version = HybridWorldMod.VERSION, dependencies = HybridWorldMod.DEPENCIES)
public class HybridWorldMod {
	public static final String MODID = "hybridworld";	
	public static final String NAME = "Hybrid world";
	public static final String VERSION = "0.2.3";
	public static final String DEPENCIES = "required:cubicchunks@[0.0.989.0,);required:cubicgen@[0.0.67.0,);required:forge@[14.23.3.2658,)";

	public static Logger LOGGER;

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		LOGGER = event.getModLog();
		MinecraftForge.EVENT_BUS.register(new Object() {
		    @SubscribeEvent
		    public void registerVanillaCompatibilityGeneratorProvider(RegistryEvent.Register<VanillaCompatibilityGeneratorProviderBase> event) {
		        event.getRegistry().register(new VanillaCompatibilityGeneratorProviderBase() {

		            @Override
		            public ICubeGenerator provideGenerator(IChunkGenerator vanillaChunkGenerator, World world) {
		                return new HybridTerrainGenerator(vanillaChunkGenerator, world);
		            }
		        }.setRegistryName(new ResourceLocation(MODID, "hybrid"))
		                .setUnlocalizedName("hybrid.gui.worldmenu.type"));
		    }
		});
		MinecraftForge.TERRAIN_GEN_BUS.register(new StructureInitEventHandler());
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
	}

	@EventHandler
	public void serverStart(FMLServerStartingEvent event) {
		event.registerServerCommand(new CommandBase() {
			@Override
			public String getName() {
				return "hybridworld_reload";
			}

			@Override
			public String getUsage(ICommandSender sender) {
				return "/hybridworld_reload";
			}

			@Override
			public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
				for (WorldServer world : DimensionManager.getWorlds()) {
					if (world == null || !((ICubicWorld) world).isCubicWorld()) {
						continue;
					}
					ICubeGenerator cubeGenerator = ((ICubicWorldServer) world).getCubeGenerator();
					if (cubeGenerator instanceof HybridTerrainGenerator) {
						((HybridTerrainGenerator) cubeGenerator).onLoad(world);
						sender.sendMessage(new TextComponentString("Preset for dimension " + world.provider.getDimension() + " has been reloaded. Note that this may cause issues with mods."));
					}
				}
			}

			@Override
			public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
				if (sender instanceof EntityPlayer) {
					//this permission is registered by CWG
					return PermissionAPI.hasPermission((EntityPlayer) sender, CustomCubicMod.MODID + ".command.reload_preset");
				} else {
					return super.checkPermission(server, sender);
				}
			}
		});
	}
	
	@NetworkCheckHandler
	public boolean checkModLists(Map<String, String> modList, Side sideIn) {
		return true;
	}
	
	@SubscribeEvent
	public static void registerCubicBiomes(RegistryEvent.Register<CubicBiome> event) {
		autoRegister(event, BiomeHell.class, b -> b
				.addBlockReplacer(CubicBiome.terrainShapeReplacer())
				.addBlockReplacer(CubicBiome.oceanWaterReplacer())
				.decoratorProvider(PrePopulator::new)
				.decoratorProvider(DefaultDecorator.Ores::new));
		autoRegister(event, BiomeEnd.class, b -> b
				.addBlockReplacer(CubicBiome.terrainShapeReplacer())
				.decoratorProvider(PrePopulator::new)
				.decoratorProvider(DefaultDecorator.Ores::new));
	}
	 
	private static void autoRegister(RegistryEvent.Register<CubicBiome> event, Class<? extends Biome> cl,
			Consumer<CubicBiome.Builder> cons) {
		ForgeRegistries.BIOMES.getValuesCollection().stream()
				.filter(x -> x.getRegistryName().getNamespace().equals("minecraft"))
				.filter(x -> x.getClass() == cl).forEach(b -> {
					CubicBiome.Builder builder = CubicBiome.createForBiome(b);
					cons.accept(builder);
					CubicBiome biome = builder.defaultPostDecorators()
							.setRegistryName(MODID, b.getRegistryName().getPath()).create();
					event.getRegistry().register(biome);
				});
	}
}
