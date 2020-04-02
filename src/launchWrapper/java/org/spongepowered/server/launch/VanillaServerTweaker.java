/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.server.launch;

import static com.google.common.io.Resources.getResource;
import static org.spongepowered.asm.mixin.MixinEnvironment.Side.SERVER;
import static org.spongepowered.server.launch.VanillaCommandLine.ACCESS_TRANSFORMER;
import static org.spongepowered.server.launch.VanillaCommandLine.NO_REDIRECT_STDOUT;
import static org.spongepowered.server.launch.VanillaCommandLine.SCAN_CLASSPATH;
import static org.spongepowered.server.launch.VanillaCommandLine.SCAN_FULL_CLASSPATH;
import static org.spongepowered.server.launch.VanillaLaunch.Environment.DEVELOPMENT;
import static org.spongepowered.server.launch.VanillaLaunch.Environment.PRODUCTION;

import joptsimple.OptionSet;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.io.IoBuilder;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IRemapper;
import org.spongepowered.common.launch.SpongeLaunch;
import org.spongepowered.server.launch.plugin.VanillaLaunchPluginManager;
import org.spongepowered.server.launch.transformer.at.AccessTransformers;
import org.spongepowered.server.launch.transformer.deobf.SrgRemapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class VanillaServerTweaker implements ITweaker {

    private static final String FORGE_GRADLE_CSV_DIR = "net.minecraftforge.gradle.GradleStart.csvDir";

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        if (gameDir == null) {
            gameDir = new File("");
        }
        SpongeLaunch.initPaths(gameDir);
        OptionSet options = VanillaCommandLine.parse(args);

        if (!options.has(NO_REDIRECT_STDOUT)) {
            System.setOut(IoBuilder.forLogger("STDOUT").setLevel(Level.INFO).buildPrintStream());
            System.setErr(IoBuilder.forLogger("STDERR").setLevel(Level.ERROR).buildPrintStream());
        }

        List<String> unrecognizedOptions = VanillaCommandLine.getUnrecognizedOptions();
        if (!unrecognizedOptions.isEmpty()) {
            VanillaLaunch.getLogger().warn("Found unrecognized command line option(s): {}", unrecognizedOptions);
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader loader) {
        VanillaLaunch.getLogger().info("Initializing Sponge...");

        // Command line options should be always parsed at this point
        OptionSet options = VanillaCommandLine.getOptions().get();

        configureLaunchClassLoader(loader);

        // Configure class transformers
        configureDeobfuscation(loader);
        registerAccessTransformers(options);
        configureMixinEnvironment();

        searchPlugins(options);

        // Register remaining class transformers
        registerTransformers(loader);

        VanillaLaunch.getLogger().info("Initialization finished. Starting Minecraft server...");
    }

    private static void configureDeobfuscation(LaunchClassLoader loader) {
        // Check if we're running in de-obfuscated environment already
        VanillaLaunch.getLogger().debug("Applying runtime de-obfuscation...");
        if (VanillaLaunch.ENVIRONMENT == PRODUCTION) {
            // Enable Notch->Searge deobfuscation
            VanillaLaunch.getLogger().info("De-obfuscation mappings are provided by MCP (http://www.modcoderpack.com)");
            Launch.blackboard.put("vanilla.cmap", Resources.getResource("mappings.cmap"));
            loader.registerTransformer("org.spongepowered.server.launch.transformer.deobf.NotchDeobfuscationTransformer");
        } else {
            // Enable Searge->MCP deobfuscation (if running in ForgeGradle)
            String mcpDir = System.getProperty(FORGE_GRADLE_CSV_DIR);
            if (mcpDir != null) {
                Launch.blackboard.put("vanilla.mcp_mappings", Paths.get(mcpDir));
                loader.registerTransformer("org.spongepowered.server.launch.transformer.deobf.SeargeDeobfuscationTransformer");
            } else {
                VanillaLaunch.getLogger().warn("SRG -> MCP de-obfuscation is disabled because MCP mappings cannot be found");
            }
        }
    }

    private static void registerAccessTransformers(OptionSet options) {
        VanillaLaunch.getLogger().debug("Registering access transformers...");
        try {
            // Apply our access transformers
            AccessTransformers.register(Resources.getResource("META-INF/common_at.cfg"));
        } catch (IOException e) {
            throw new LaunchException("Failed to register SpongeCommon/SpongeVanilla access transformers", e);
        }

        // Apply access transformers from command line
        for (String at : options.valuesOf(ACCESS_TRANSFORMER)) {
            try {
                // First check if the AT exists as file
                Path path = Paths.get(at);
                if (Files.isReadable(path)) {
                    AccessTransformers.register(path);
                } else {
                    // Try as resource in classpath instead
                    AccessTransformers.register(Resources.getResource(at));
                }
            } catch (IOException e) {
                VanillaLaunch.getLogger().error("Failed to load access transformer from {}", at, e);
            }
        }
    }

    static void configureMixinEnvironment() {
        VanillaLaunch.getLogger().debug("Initializing Mixin environment...");
        SpongeLaunch.setupMixinEnvironment();

        Mixins.addConfiguration("mixins.vanilla.core.json");
        Mixins.addConfiguration("mixins.vanilla.entityactivation.json");
        Mixins.addConfiguration("mixins.vanilla.chunkio.json");
        Mixins.addConfiguration("mixins.vanilla.optimization.json");

        MixinEnvironment.getDefaultEnvironment().setSide(SERVER);

        // Add our remapper to Mixin's remapper chain
        SrgRemapper remapper = VanillaLaunch.getRemapper();
        if (remapper instanceof IRemapper) {
            MixinEnvironment.getDefaultEnvironment().getRemappers().add((IRemapper) remapper);
        }
    }

    private static void searchPlugins(OptionSet options) {
        VanillaLaunch.getLogger().debug("Searching for plugins...");

        try {
            // Search for plugins (and apply access transformers if available)
            VanillaLaunchPluginManager.findPlugins(
                    VanillaLaunch.ENVIRONMENT == DEVELOPMENT || options.has(SCAN_CLASSPATH), options.has(SCAN_FULL_CLASSPATH));
        } catch (IOException e) {
            throw new LaunchException("Failed to search for plugins", e);
        }
    }

    private static void registerTransformers(LaunchClassLoader loader) {
        // Register the access transformer (at this point new access transformers can be no longer registered)
        loader.registerTransformer("org.spongepowered.server.launch.transformer.at.AccessTransformer");

        // Superclass transformer
        loader.registerTransformer(SpongeLaunch.SUPERCLASS_TRANSFORMER);
        SpongeLaunch.setupSuperClassTransformer();

    }

    @Override
    public String getLaunchTarget() {
        return "org.spongepowered.server.SpongeVanilla";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    static void configureLaunchClassLoader(LaunchClassLoader loader) {
        SpongeLaunch.addJreExtensionsToClassPath();

        // Logging
        loader.addClassLoaderExclusion("org.slf4j.");
        loader.addClassLoaderExclusion("net.minecrell.terminalconsole.");
        loader.addClassLoaderExclusion("org.jline.");
        loader.addClassLoaderExclusion("com.sun.");
        loader.addClassLoaderExclusion("com.mojang.util.QueueLogAppender");

        // Sponge Launch
        loader.addClassLoaderExclusion("joptsimple.");
        loader.addClassLoaderExclusion("com.google.common.");
        loader.addClassLoaderExclusion("org.spongepowered.common.launch.");
        loader.addClassLoaderExclusion("org.spongepowered.server.launch.");
        loader.addClassLoaderExclusion("org.spongepowered.plugin.");
        loader.addTransformerExclusion("org.spongepowered.common.event.tracking.PhaseTracker");
        loader.addTransformerExclusion("org.spongepowered.common.event.tracking.TrackingUtil");

        // Don't allow transforming libraries
        loader.addTransformerExclusion("com.google.");
        loader.addTransformerExclusion("org.apache.");
        loader.addTransformerExclusion("io.netty.");
        loader.addTransformerExclusion("com.flowpowered.");
        loader.addTransformerExclusion("it.unimi.dsi.fastutil.");
        loader.addTransformerExclusion("com.github.benmanes.caffeine.");
        // Guice
        loader.addTransformerExclusion("org.aopalliance.");
        // Configurate
        loader.addTransformerExclusion("ninja.leaping.configurate.");
        loader.addTransformerExclusion("com.typesafe.config.");
        loader.addTransformerExclusion("org.yaml.snakeyaml.");
        // Database connectors
        loader.addTransformerExclusion("com.zaxxer.hikari.");
        loader.addTransformerExclusion("org.h2.");
        loader.addTransformerExclusion("org.mariadb.");
        loader.addTransformerExclusion("org.sqlite.");
    }

}
