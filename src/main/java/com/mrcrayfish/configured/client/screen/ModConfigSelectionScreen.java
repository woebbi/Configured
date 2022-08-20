package com.mrcrayfish.configured.client.screen;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.configured.api.ConfigType;
import com.mrcrayfish.configured.api.IModConfig;
import com.mrcrayfish.configured.client.screen.widget.IconButton;
import com.mrcrayfish.configured.config.ConfigUtil;
import com.mrcrayfish.configured.util.ConfigHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.commons.io.FilenameUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Author: MrCrayfish
 */
public class ModConfigSelectionScreen extends ListMenuScreen
{
    private final Map<ConfigType, Set<IModConfig>> configMap;

    public ModConfigSelectionScreen(Screen parent, Component title, ResourceLocation background, Map<ConfigType, Set<IModConfig>> configMap)
    {
        super(parent, title, background, 30);
        this.configMap = configMap;
    }

    @Override
    protected void constructEntries(List<Item> entries)
    {
        Set<IModConfig> localConfigs = this.getLocalConfigs();
        if(!localConfigs.isEmpty())
        {
            entries.add(new TitleItem(new TranslatableComponent("configured.gui.title.client_configuration").getString()));
            List<Item> localEntries = new ArrayList<>();
            localConfigs.forEach(config -> localEntries.add(new FileItem(config)));
            Collections.sort(localEntries);
            entries.addAll(localEntries);

        }
        Set<IModConfig> remoteConfigs = this.getRemoteConfigs();
        if(!remoteConfigs.isEmpty())
        {
            entries.add(new TitleItem(new TranslatableComponent("configured.gui.title.server_configuration").getString()));
            List<Item> remoteEntries = new ArrayList<>();
            remoteConfigs.forEach(config -> remoteEntries.add(new FileItem(config)));
            Collections.sort(remoteEntries);
            entries.addAll(remoteEntries);
        }
    }

    @Override
    protected void init()
    {
        super.init();
        this.addRenderableWidget(new Button(this.width / 2 - 75, this.height - 29, 150, 20, CommonComponents.GUI_BACK, button -> this.minecraft.setScreen(this.parent)));
    }

    private Set<IModConfig> getLocalConfigs()
    {
        return this.configMap.entrySet().stream().filter(entry -> {
            return !entry.getKey().isServer();
        }).flatMap(entry -> entry.getValue().stream()).collect(Collectors.toSet());
    }

    private Set<IModConfig> getRemoteConfigs()
    {
        return this.configMap.entrySet().stream().filter(entry -> {
            ConfigType type = entry.getKey();
            return type.isServer() && type.getDist().orElse(null) != Dist.DEDICATED_SERVER;
        }).flatMap(entry -> entry.getValue().stream()).collect(Collectors.toSet());
    }

    public class FileItem extends Item
    {
        protected final IModConfig config;
        protected final Component title;
        protected final Component fileName;
        protected final Button modifyButton;
        @Nullable
        protected final Button restoreButton;

        public FileItem(IModConfig config)
        {
            super(createLabelFromModConfig(config));
            this.config = config;
            this.title = this.createTrimmedFileName(createLabelFromModConfig(config));
            this.fileName = this.createTrimmedFileName(config.getFileName()).withStyle(ChatFormatting.GRAY);
            this.modifyButton = this.createModifyButton(config);
            this.modifyButton.active = canEditConfig(Minecraft.getInstance().player, config);
            this.restoreButton = this.createRestoreButton(config);
            this.updateRestoreDefaultButton();
        }

        private void showRestoreScreen()
        {
            ConfirmationScreen confirmScreen = new ConfirmationScreen(ModConfigSelectionScreen.this, new TranslatableComponent("configured.gui.restore_message"), result -> {
                if(!result)
                    return true;
                this.config.restoreDefaults();
                this.config.syncToServer();
                this.updateRestoreDefaultButton();
                return true;
            });
            confirmScreen.setBackground(ModConfigSelectionScreen.this.background);
            confirmScreen.setPositiveText(new TranslatableComponent("configured.gui.restore").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            confirmScreen.setNegativeText(CommonComponents.GUI_CANCEL);
            Minecraft.getInstance().setScreen(confirmScreen);
        }

        private MutableComponent createTrimmedFileName(String fileName)
        {
            MutableComponent trimmedFileName = new TextComponent(fileName);
            if(Minecraft.getInstance().font.width(fileName) > 150)
            {
                trimmedFileName = new TextComponent(Minecraft.getInstance().font.plainSubstrByWidth(fileName, 140) + "...");
            }
            return trimmedFileName;
        }

        /**
         * Creates and returns a new modify button instance. Since server configurations are handled
         * different, the label and click handler of this button is different if the given ModConfig
         * instance is of the server type.
         *
         * @param config
         * @return
         */
        private Button createModifyButton(IModConfig config)
        {
            boolean canRestore = canRestoreConfig(Minecraft.getInstance().player, config);
            boolean worldConfig = !ConfigHelper.isPlayingGame() && ConfigHelper.isWorldConfig(config);
            String langKey = worldConfig ? "configured.gui.select_world" : "configured.gui.modify";
            return new IconButton(0, 0, worldConfig ? 44 : 33, 0, canRestore ? 60 : 82, new TranslatableComponent(langKey), button ->
            {
                if(!button.isActive() || !button.visible)
                    return;

                if(!ConfigHelper.isPlayingGame())
                {
                    if(ConfigHelper.isWorldConfig(config))
                    {
                        Minecraft.getInstance().setScreen(new WorldSelectionScreen(ModConfigSelectionScreen.this, ModConfigSelectionScreen.this.background, config, this.title));
                    }
                    else if(config.getType() != ConfigType.DEDICATED_SERVER)
                    {
                        ModList.get().getModContainerById(config.getModId()).ifPresent(container ->
                        {
                            Minecraft.getInstance().setScreen(new ConfigScreen(ModConfigSelectionScreen.this, new TextComponent(container.getModInfo().getDisplayName()), config, ModConfigSelectionScreen.this.background));
                        });
                    }
                }
                else if(config.getType().isServer() && !config.getType().isSync())
                {
                    ModList.get().getModContainerById(config.getModId()).ifPresent(container ->
                    {
                        Minecraft.getInstance().setScreen(new RequestScreen(ModConfigSelectionScreen.this, new TextComponent(container.getModInfo().getDisplayName()), ModConfigSelectionScreen.this.background, config));
                    });
                }
                else
                {
                    ModList.get().getModContainerById(config.getModId()).ifPresent(container ->
                    {
                        Minecraft.getInstance().setScreen(new ConfigScreen(ModConfigSelectionScreen.this, new TextComponent(container.getModInfo().getDisplayName()), config, ModConfigSelectionScreen.this.background));
                    });
                }
            }, (button, poseStack, mouseX, mouseY) ->
            {
                if(button.isHoveredOrFocused())
                {
                    if(ConfigHelper.isPlayingGame() && !ConfigHelper.isConfiguredInstalledOnServer())
                    {
                        ModConfigSelectionScreen.this.renderTooltip(poseStack, Minecraft.getInstance().font.split(new TranslatableComponent("configured.gui.not_installed").withStyle(ChatFormatting.RED), Math.max(ModConfigSelectionScreen.this.width / 2 - 43, 170)), mouseX, mouseY);
                    }
                    else if(ConfigHelper.isPlayingGame() && !ConfigHelper.hasPermissionToEdit(Minecraft.getInstance().player, config))
                    {
                        ModConfigSelectionScreen.this.renderTooltip(poseStack, Minecraft.getInstance().font.split(new TranslatableComponent("configured.gui.no_permission").withStyle(ChatFormatting.RED), Math.max(ModConfigSelectionScreen.this.width / 2 - 43, 170)), mouseX, mouseY);
                    }
                }
            });
        }

        private Button createRestoreButton(IModConfig config)
        {
            if(canRestoreConfig(Minecraft.getInstance().player, config))
            {
                IconButton restoreButton = new IconButton(0, 0, 0, 0, onPress -> this.showRestoreScreen(), (button, poseStack, mouseX, mouseY) ->
                {
                    if(button.isHoveredOrFocused())
                    {
                        boolean permission = canRestoreConfig(Minecraft.getInstance().player, config);
                        if(permission && button.isActive())
                        {
                            ModConfigSelectionScreen.this.renderTooltip(poseStack, Minecraft.getInstance().font.split(new TranslatableComponent("configured.gui.reset_all"), Math.max(ModConfigSelectionScreen.this.width / 2 - 43, 170)), mouseX, mouseY);
                        }
                        else if(!permission)
                        {
                            ModConfigSelectionScreen.this.renderTooltip(poseStack, Minecraft.getInstance().font.split(new TranslatableComponent("configured.gui.no_permission").withStyle(ChatFormatting.RED), Math.max(ModConfigSelectionScreen.this.width / 2 - 43, 170)), mouseX, mouseY);
                        }
                    }
                });
                restoreButton.active = ConfigHelper.hasPermissionToEdit(Minecraft.getInstance().player, config);
                return restoreButton;
            }
            return null;
        }

        @Override
        public void render(PoseStack poseStack, int x, int top, int left, int width, int p_230432_6_, int mouseX, int mouseY, boolean p_230432_9_, float partialTicks)
        {
            Screen.drawString(poseStack, Minecraft.getInstance().font, this.title, left + 28, top + 2, 0xFFFFFF);
            Screen.drawString(poseStack, Minecraft.getInstance().font, this.fileName, left + 28, top + 12, 0xFFFFFF);
            float brightness = true ? 1.0F : 0.5F;
            RenderSystem.setShaderTexture(0, IconButton.ICONS);
            RenderSystem.setShaderColor(brightness, brightness, brightness, 1.0F);
            blit(poseStack, left + 4, top, 18, 22, this.getIconU(), 11, 9, 11, 64, 64);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            this.modifyButton.x = left + width - 83;
            this.modifyButton.y = top;
            this.modifyButton.render(poseStack, mouseX, mouseY, partialTicks);

            if(this.restoreButton != null)
            {
                this.restoreButton.x = left + width - 21;
                this.restoreButton.y = top;
                this.restoreButton.render(poseStack, mouseX, mouseY, partialTicks);
            }
        }

        private int getIconU()
        {
            return switch(this.config.getType())
            {
                //TODO update this for the new types
                case UNIVERSAL -> 9;
                case SERVER, SERVER_SYNC, WORLD, WORLD_SYNC -> 18;
                default -> 0;
            };
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            if(this.restoreButton != null)
            {
                return ImmutableList.of(this.modifyButton, this.restoreButton);
            }
            return ImmutableList.of(this.modifyButton);
        }

        /**
         * Updates the active state of the restore default button. It will only be active if values are
         * different from their default.
         */
        private void updateRestoreDefaultButton()
        {
            if(this.config != null && this.restoreButton != null && canRestoreConfig(Minecraft.getInstance().player, this.config))
            {
                this.restoreButton.active = this.config.isChanged();
            }
        }
    }

    /**
     * Tries to create a readable label from the file name of the given mod config.
     *
     * @param config
     * @return
     */
    private static String createLabelFromModConfig(IModConfig config)
    {
        if(config.getTranslationKey() != null) {
            return I18n.get(config.getTranslationKey());
        }
        String fileName = config.getFileName();
        fileName = fileName.replace(config.getModId() + "-", "");
        if(fileName.endsWith(".toml")) {
            fileName = fileName.substring(0, fileName.length() - ".toml".length());
        }
        fileName = FilenameUtils.getName(fileName);
        fileName = ConfigScreen.createLabel(fileName);
        return fileName;
    }

    public static boolean canEditConfig(@Nullable Player player, IModConfig config)
    {
        return switch(config.getType())
        {
            case CLIENT -> FMLEnvironment.dist.isClient();
            case UNIVERSAL, MEMORY -> true;
            case SERVER, WORLD, SERVER_SYNC, WORLD_SYNC -> !ConfigHelper.isPlayingGame() || ConfigHelper.isConfiguredInstalledOnServer() && ConfigHelper.hasPermissionToEdit(player, config);
            case DEDICATED_SERVER -> false;
        };
    }

    public static boolean canRestoreConfig(Player player, IModConfig config)
    {
        return switch(config.getType())
        {
            case CLIENT, UNIVERSAL, MEMORY -> true;
            case SERVER -> !ConfigHelper.isPlayingGame();
            case SERVER_SYNC -> !ConfigHelper.isPlayingGame() || ConfigHelper.isConfiguredInstalledOnServer() && ConfigHelper.hasPermissionToEdit(player, config);
            case WORLD -> false;
            case WORLD_SYNC -> ConfigHelper.isPlayingGame() && ConfigHelper.isConfiguredInstalledOnServer() && ConfigHelper.hasPermissionToEdit(player, config);
            case DEDICATED_SERVER -> false;
        };
    }
}
