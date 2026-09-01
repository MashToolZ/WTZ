package xyz.mashtoolz.wtz.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

@Config(name = "wtz/config")
public class WTZConfig implements ConfigData {

    public static final double DEFAULT_MOUNT_STATS_DRAG_PCT_X = 74.84;
    public static final double DEFAULT_MOUNT_STATS_DRAG_PCT_Y = 29.79;
    public static final double DEFAULT_MOUNT_JUMP_DRAG_PCT_X = 3.91;
    public static final double DEFAULT_MOUNT_JUMP_DRAG_PCT_Y = 38.05;
    public static final double DEFAULT_MOUNT_ENERGY_DRAG_PCT_X = 1.25;
    public static final double DEFAULT_MOUNT_ENERGY_DRAG_PCT_Y = 38.05;

    private static ConfigHolder<WTZConfig> holder;

    public static WTZConfig register() {
        holder = AutoConfig.register(WTZConfig.class, GsonConfigSerializer::new);
        return holder.getConfig();
    }

    public static ConfigHolder<WTZConfig> holder() {
        return holder;
    }

    public static void save() {
        if (holder != null) {
            holder.getConfig().normalizeValues();
            holder.save();
        }
    }

    
    public boolean mountHelperEnabled = false;
    public float mountHelperLabelScale = 0.5f;
    public boolean mountHelperHideMaxed = false;
    public int mountHelperMaxedTimeout = 10;
    public int mountHelperMaxedOpacity = 30;

    public boolean mountStatsEnabled = false;
    public boolean mountStatsAutoUpdate = false;
    public boolean mountStatsTrackHeld = false;
    public boolean mountStatsShowWhenNotMounted = false;
    public int mountStatsBgOpacity = 50;
    public double mountStatsDragPctX = DEFAULT_MOUNT_STATS_DRAG_PCT_X;
    public double mountStatsDragPctY = DEFAULT_MOUNT_STATS_DRAG_PCT_Y;
    public float mountStatsDragScale = 1.0f;
    public boolean mountStatsEditLocked = false;
    public boolean mountJumpOverlayEnabled = true;
    public boolean mountJumpAlwaysShow = false;
    public boolean mountJumpHorizontal = false;
    public int mountJumpRotation = -1;
    public double mountJumpDragPctX = DEFAULT_MOUNT_JUMP_DRAG_PCT_X;
    public double mountJumpDragPctY = DEFAULT_MOUNT_JUMP_DRAG_PCT_Y;
    public float mountJumpDragScale = 1.0f;
    public boolean mountJumpEditLocked = false;
    public boolean mountEnergyOverlayEnabled = true;
    public boolean mountEnergyHideVanilla = true;
    public boolean mountEnergyHorizontal = false;
    public int mountEnergyRotation = -1;
    public double mountEnergyDragPctX = DEFAULT_MOUNT_ENERGY_DRAG_PCT_X;
    public double mountEnergyDragPctY = DEFAULT_MOUNT_ENERGY_DRAG_PCT_Y;
    public float mountEnergyDragScale = 1.0f;
    public boolean mountEnergyEditLocked = false;
    
    public boolean mountItemOverlayEnabled = false;
    public boolean mountItemOverlayPotentialEnabled = false;
    public boolean mountItemOverlayBarsEnabled = false;
    public MountItemOverlayModifierKey mountItemOverlayBarsModifierKey = MountItemOverlayModifierKey.NONE;
    public boolean mountItemOverlayBarsAlwaysShowInHotbar = false;
    public boolean mountItemOverlaySkinColorsEnabled = false;
    public boolean mountItemOverlayHorseInitialsEnabled = true;
    public boolean mountItemOverlayWyvernInitialsEnabled = true;
    public boolean mountItemOverlayAdasaurInitialsEnabled = true;
    public boolean mountSkinReportingEnabled = true;
    public boolean mountBreedReportingEnabled = true;
    public boolean mountHorseColorAlertsEnabled = true;
    public MountHorseAlertPrimaryColor mountHorseAlertPrimaryColor = MountHorseAlertPrimaryColor.Any;
    public MountHorseAlertSecondaryColor mountHorseAlertSecondaryColor = MountHorseAlertSecondaryColor.Any;
    public boolean mountWyvernColorAlertsEnabled = true;
    public MountSkinAlertPrimaryColor mountWyvernAlertPrimaryColor = MountSkinAlertPrimaryColor.Any;
    public MountSkinAlertSecondaryColor mountWyvernAlertSecondaryColor = MountSkinAlertSecondaryColor.Any;
    public boolean mountAdasaurColorAlertsEnabled = true;
    public MountAdasaurAlertPrimaryColor mountAdasaurAlertPrimaryColor = MountAdasaurAlertPrimaryColor.Any;
    public MountAdasaurAlertSecondaryColor mountAdasaurAlertSecondaryColor = MountAdasaurAlertSecondaryColor.Any;
    
    public boolean qualityOfLifeEnabled = false;
    public boolean qolRightClickBack = false;
    public boolean qolHideActionbarInChat = false;
    public boolean qolActionbarAboveChat = false;
    public boolean qolMacOSMovementKeyFix = false;
    
    public boolean shoppingListEnabled = false;
    public boolean shoppingListAutoOpenTradeMarket = false;
    public float shoppingListScale = 1.0f;
    public double mountBankIndexerPctX = 85.0;
    public double mountBankIndexerPctY = 12.0;
    public float mountBankIndexerScale = 1.0f;
    public boolean bankFiltersEnabled = true;
    public boolean bankFilterMountFiltersEnabled = true;
    
    public boolean mountCameraEnabled = false;
    public boolean mountCameraScrollZoom = false;
    public float mountCameraZoomDistance = 0.0f;
    public int mountCameraFov = 29;
    public double mountCameraOffsetZ = 4.0;
    public boolean mountCameraAutoPerspective = false;
    public boolean mountCameraFreeLook = false;
    
    public boolean shoutTTSEnabled = false;
    public String shoutTTSToken = "";
    public int shoutTTSVolume = 40;
    public TTSVoice shoutTTSVoice = TTSVoice.RANDOM;
    
    public boolean lookLineEnabled = false;
    public int lookLineMaxDistance = 10;
    public float lookLineWidth = 0.05f;
    public int lookLineColor = 0xFFFFFFFF;

    @Override
    @SuppressWarnings("RedundantThrows")
    public void validatePostLoad() throws ValidationException {
        normalizeValues();
    }

    private void normalizeValues() {
        mountHelperLabelScale = Math.clamp(mountHelperLabelScale, 0.1f, 2.0f);
        mountHelperMaxedTimeout = Math.clamp(mountHelperMaxedTimeout, 0, 60);
        mountHelperMaxedOpacity = Math.clamp(mountHelperMaxedOpacity, 0, 100);
        mountStatsBgOpacity = Math.clamp(mountStatsBgOpacity, 0, 100);
        mountStatsDragPctX = roundOverlayPct(mountStatsDragPctX);
        mountStatsDragPctY = roundOverlayPct(mountStatsDragPctY);
        mountStatsDragScale = roundOverlayScale(Math.clamp(mountStatsDragScale, 0.3f, 2.0f));
        mountJumpRotation = normalizeRotation(mountJumpRotation, mountJumpHorizontal);
        mountJumpHorizontal = mountJumpRotation % 2 != 0;
        mountJumpDragPctX = roundOverlayPct(mountJumpDragPctX);
        mountJumpDragPctY = roundOverlayPct(mountJumpDragPctY);
        mountJumpDragScale = roundOverlayScale(Math.clamp(mountJumpDragScale, 0.3f, 3.0f));
        mountEnergyRotation = normalizeRotation(mountEnergyRotation, mountEnergyHorizontal);
        mountEnergyHorizontal = mountEnergyRotation % 2 != 0;
        mountEnergyDragPctX = roundOverlayPct(mountEnergyDragPctX);
        mountEnergyDragPctY = roundOverlayPct(mountEnergyDragPctY);
        mountEnergyDragScale = roundOverlayScale(Math.clamp(mountEnergyDragScale, 0.3f, 3.0f));
        shoppingListScale = Math.clamp(shoppingListScale, 0.5f, 1.0f);
        mountBankIndexerPctX = roundOverlayPct(mountBankIndexerPctX);
        mountBankIndexerPctY = roundOverlayPct(mountBankIndexerPctY);
        mountBankIndexerScale = roundOverlayScale(Math.clamp(mountBankIndexerScale, 0.75f, 1.5f));
        mountCameraZoomDistance = Math.clamp(mountCameraZoomDistance, 0.0f, 15.0f);
        mountCameraFov = Math.clamp(mountCameraFov, 29, 110);
        mountCameraOffsetZ = Math.clamp(mountCameraOffsetZ, -5.0, 5.0);
        shoutTTSVolume = Math.clamp(shoutTTSVolume, 0, 100);
        lookLineMaxDistance = Math.clamp(lookLineMaxDistance, 1, 50);
        lookLineWidth = Math.clamp(lookLineWidth, 0.01f, 0.5f);
    }

    private static double roundOverlayPct(double value) {
        if (value < 0) return -1.0;
        return Math.round(Math.clamp(value, 0.0, 100.0) * 100.0) / 100.0;
    }

    private static float roundOverlayScale(float value) {
        return Math.round(value * 100.0f) / 100.0f;
    }

    private static int normalizeRotation(int rotation, boolean legacyHorizontal) {
        return rotation < 0 ? (legacyHorizontal ? 1 : 0) : Math.floorMod(rotation, 4);
    }
    

    private static Text option(String key) {
        return Text.translatable("text.autoconfig.wtz-config.option." + key);
    }

    private static Text tooltip(String key) {
        return Text.translatable("text.autoconfig.wtz-config.option." + key + ".tooltip");
    }

    private static Text category(String key) {
        return Text.translatable("text.autoconfig.wtz-config.category." + key);
    }

    public static Screen buildScreen(Screen parent) {
        WTZConfig c = holder.getConfig();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("text.autoconfig.wtz-config.title"))
                .setSavingRunnable(WTZConfig::save);
        ConfigEntryBuilder e = builder.entryBuilder();

        ConfigCategory mounts = builder.getOrCreateCategory(category("mounts"));
        SubCategoryBuilder mountHelper = e.startSubCategory(category("mountHelper"));
        mountHelper.add(e.startBooleanToggle(option("mountHelperEnabled"), c.mountHelperEnabled)
                .setTooltip(tooltip("mountHelperEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountHelperEnabled = v).build());
        mountHelper.add(e.startFloatField(option("mountHelperLabelScale"), c.mountHelperLabelScale)
                .setTooltip(tooltip("mountHelperLabelScale"))
                .setDefaultValue(0.5f).setMin(0.1f).setMax(2.0f).setSaveConsumer(v -> c.mountHelperLabelScale = v).build());
        mountHelper.add(e.startBooleanToggle(option("mountHelperHideMaxed"), c.mountHelperHideMaxed)
                .setTooltip(tooltip("mountHelperHideMaxed"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountHelperHideMaxed = v).build());
        mountHelper.add(e.startIntSlider(option("mountHelperMaxedTimeout"), c.mountHelperMaxedTimeout, 0, 60)
                .setTooltip(tooltip("mountHelperMaxedTimeout"))
                .setDefaultValue(10).setSaveConsumer(v -> c.mountHelperMaxedTimeout = v).build());
        mountHelper.add(e.startIntSlider(option("mountHelperMaxedOpacity"), c.mountHelperMaxedOpacity, 0, 100)
                .setTooltip(tooltip("mountHelperMaxedOpacity"))
                .setDefaultValue(30).setSaveConsumer(v -> c.mountHelperMaxedOpacity = v).build());
    
        mounts.addEntry(mountHelper.build());
        SubCategoryBuilder mountOverlays = e.startSubCategory(category("mountOverlays"));
        SubCategoryBuilder mountStats = e.startSubCategory(category("mountStats"));
        mountStats.add(e.startBooleanToggle(option("mountStatsEnabled"), c.mountStatsEnabled)
                .setTooltip(tooltip("mountStatsEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountStatsEnabled = v).build());
        mountStats.add(e.startBooleanToggle(option("mountStatsAutoUpdate"), c.mountStatsAutoUpdate)
                .setTooltip(tooltip("mountStatsAutoUpdate"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountStatsAutoUpdate = v).build());
        mountStats.add(e.startBooleanToggle(option("mountStatsTrackHeld"), c.mountStatsTrackHeld)
                .setTooltip(tooltip("mountStatsTrackHeld"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountStatsTrackHeld = v).build());
        mountStats.add(e.startBooleanToggle(option("mountStatsShowWhenNotMounted"), c.mountStatsShowWhenNotMounted)
                .setTooltip(tooltip("mountStatsShowWhenNotMounted"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountStatsShowWhenNotMounted = v).build());
        mountStats.add(e.startIntSlider(option("mountStatsBgOpacity"), c.mountStatsBgOpacity, 0, 100)
                .setTooltip(tooltip("mountStatsBgOpacity"))
                .setDefaultValue(50).setSaveConsumer(v -> c.mountStatsBgOpacity = v).build());
        mountOverlays.add(mountStats.build());
        SubCategoryBuilder mountEnergyBar = e.startSubCategory(category("mountEnergyBar"));
        mountEnergyBar.add(e.startBooleanToggle(option("mountEnergyOverlayEnabled"), c.mountEnergyOverlayEnabled)
                .setTooltip(tooltip("mountEnergyOverlayEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountEnergyOverlayEnabled = v).build());
        mountEnergyBar.add(e.startBooleanToggle(option("mountEnergyHideVanilla"), c.mountEnergyHideVanilla)
                .setTooltip(tooltip("mountEnergyHideVanilla"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountEnergyHideVanilla = v).build());
        mountOverlays.add(mountEnergyBar.build());
        SubCategoryBuilder mountJumpBar = e.startSubCategory(category("mountJumpBar"));
        mountJumpBar.add(e.startBooleanToggle(option("mountJumpOverlayEnabled"), c.mountJumpOverlayEnabled)
                .setTooltip(tooltip("mountJumpOverlayEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountJumpOverlayEnabled = v).build());
        mountJumpBar.add(e.startBooleanToggle(option("mountJumpAlwaysShow"), c.mountJumpAlwaysShow)
                .setTooltip(tooltip("mountJumpAlwaysShow"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountJumpAlwaysShow = v).build());
        mountOverlays.add(mountJumpBar.build());
        SubCategoryBuilder mountCamera = e.startSubCategory(category("mountCamera"));
        mountCamera.add(e.startBooleanToggle(option("mountCameraEnabled"), c.mountCameraEnabled)
                .setTooltip(tooltip("mountCameraEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountCameraEnabled = v).build());
        mountCamera.add(e.startBooleanToggle(option("mountCameraAutoPerspective"), c.mountCameraAutoPerspective)
                .setTooltip(tooltip("mountCameraAutoPerspective"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountCameraAutoPerspective = v).build());
        mountCamera.add(e.startBooleanToggle(option("mountCameraFreeLook"), c.mountCameraFreeLook)
                .setTooltip(tooltip("mountCameraFreeLook"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountCameraFreeLook = v).build());
        mountCamera.add(e.startBooleanToggle(option("mountCameraScrollZoom"), c.mountCameraScrollZoom)
                .setTooltip(tooltip("mountCameraScrollZoom"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountCameraScrollZoom = v).build());
        mountCamera.add(e.startIntSlider(option("mountCameraFov"), c.mountCameraFov, 29, 110)
                .setTooltip(tooltip("mountCameraFov"))
                .setTextGetter(v -> v == 29 ? Text.literal("Default") : Text.literal(String.valueOf(v)))
                .setDefaultValue(29).setSaveConsumer(v -> c.mountCameraFov = v).build());
        mountCamera.add(e.startDoubleField(option("mountCameraOffsetZ"), c.mountCameraOffsetZ)
                .setTooltip(tooltip("mountCameraOffsetZ"))
                .setDefaultValue(4.0).setMin(-5.0).setMax(5.0).setSaveConsumer(v -> c.mountCameraOffsetZ = v).build());
    
        mounts.addEntry(mountCamera.build());
        mounts.addEntry(mountOverlays.build());
        SubCategoryBuilder mountItemOverlay = e.startSubCategory(category("mountItemOverlay"));
        mountItemOverlay.add(e.startBooleanToggle(option("mountItemOverlayEnabled"), c.mountItemOverlayEnabled)
                .setTooltip(tooltip("mountItemOverlayEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountItemOverlayEnabled = v).build());
        mountItemOverlay.add(e.startBooleanToggle(option("mountItemOverlayPotentialEnabled"), c.mountItemOverlayPotentialEnabled)
                .setTooltip(tooltip("mountItemOverlayPotentialEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountItemOverlayPotentialEnabled = v).build());
        mountItemOverlay.add(e.startBooleanToggle(option("mountItemOverlayBarsEnabled"), c.mountItemOverlayBarsEnabled)
                .setTooltip(tooltip("mountItemOverlayBarsEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountItemOverlayBarsEnabled = v).build());
        mountItemOverlay.add(e.startEnumSelector(option("mountItemOverlayBarsModifierKey"), MountItemOverlayModifierKey.class, c.mountItemOverlayBarsModifierKey)
                .setTooltip(tooltip("mountItemOverlayBarsModifierKey"))
                .setDefaultValue(MountItemOverlayModifierKey.NONE)
                .setEnumNameProvider(v -> Text.translatable("text.autoconfig.wtz-config.option.mountItemOverlayBarsModifierKey." + v.name()))
                .setSaveConsumer(v -> c.mountItemOverlayBarsModifierKey = v).build());
        mountItemOverlay.add(e.startBooleanToggle(option("mountItemOverlayBarsAlwaysShowInHotbar"), c.mountItemOverlayBarsAlwaysShowInHotbar)
                .setTooltip(tooltip("mountItemOverlayBarsAlwaysShowInHotbar"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountItemOverlayBarsAlwaysShowInHotbar = v).build());
        mountItemOverlay.add(e.startBooleanToggle(option("mountItemOverlaySkinColorsEnabled"), c.mountItemOverlaySkinColorsEnabled)
                .setTooltip(tooltip("mountItemOverlaySkinColorsEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountItemOverlaySkinColorsEnabled = v).build());
        mounts.addEntry(mountItemOverlay.build());
        SubCategoryBuilder mountInitials = e.startSubCategory(category("mountInitials"));
        mountInitials.add(e.startBooleanToggle(option("mountItemOverlayHorseInitialsEnabled"), c.mountItemOverlayHorseInitialsEnabled)
                .setTooltip(tooltip("mountItemOverlayHorseInitialsEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountItemOverlayHorseInitialsEnabled = v).build());
        mountInitials.add(e.startBooleanToggle(option("mountItemOverlayWyvernInitialsEnabled"), c.mountItemOverlayWyvernInitialsEnabled)
                .setTooltip(tooltip("mountItemOverlayWyvernInitialsEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountItemOverlayWyvernInitialsEnabled = v).build());
        mountInitials.add(e.startBooleanToggle(option("mountItemOverlayAdasaurInitialsEnabled"), c.mountItemOverlayAdasaurInitialsEnabled)
                .setTooltip(tooltip("mountItemOverlayAdasaurInitialsEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountItemOverlayAdasaurInitialsEnabled = v).build());
        mounts.addEntry(mountInitials.build());

        SubCategoryBuilder mountReporting = e.startSubCategory(category("mountReporting"));
        mountReporting.add(e.startBooleanToggle(option("mountSkinReportingEnabled"), c.mountSkinReportingEnabled)
                .setTooltip(tooltip("mountSkinReportingEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountSkinReportingEnabled = v).build());
        mountReporting.add(e.startBooleanToggle(option("mountBreedReportingEnabled"), c.mountBreedReportingEnabled)
                .setTooltip(tooltip("mountBreedReportingEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountBreedReportingEnabled = v).build());
        mounts.addEntry(mountReporting.build());

        SubCategoryBuilder mountAlerts = e.startSubCategory(category("mountAlerts"));
        mountAlerts.add(e.startBooleanToggle(option("mountHorseColorAlertsEnabled"), c.mountHorseColorAlertsEnabled)
                .setTooltip(tooltip("mountHorseColorAlertsEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountHorseColorAlertsEnabled = v).build());
        mountAlerts.add(e.startEnumSelector(option("mountHorseAlertPrimaryColor"), MountHorseAlertPrimaryColor.class, c.mountHorseAlertPrimaryColor)
                .setTooltip(tooltip("mountHorseAlertPrimaryColor"))
                .setDefaultValue(MountHorseAlertPrimaryColor.Any)
                .setEnumNameProvider(v -> Text.translatable("text.autoconfig.wtz-config.option.mountHorseAlertPrimaryColor." + v.name()))
                .setSaveConsumer(v -> c.mountHorseAlertPrimaryColor = v).build());
        mountAlerts.add(e.startEnumSelector(option("mountHorseAlertSecondaryColor"), MountHorseAlertSecondaryColor.class, c.mountHorseAlertSecondaryColor)
                .setTooltip(tooltip("mountHorseAlertSecondaryColor"))
                .setDefaultValue(MountHorseAlertSecondaryColor.Any)
                .setEnumNameProvider(v -> Text.translatable("text.autoconfig.wtz-config.option.mountHorseAlertSecondaryColor." + v.name()))
                .setSaveConsumer(v -> c.mountHorseAlertSecondaryColor = v).build());
        mountAlerts.add(e.startBooleanToggle(option("mountWyvernColorAlertsEnabled"), c.mountWyvernColorAlertsEnabled)
                .setTooltip(tooltip("mountWyvernColorAlertsEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountWyvernColorAlertsEnabled = v).build());
        mountAlerts.add(e.startEnumSelector(option("mountWyvernAlertPrimaryColor"), MountSkinAlertPrimaryColor.class, c.mountWyvernAlertPrimaryColor)
                .setTooltip(tooltip("mountWyvernAlertPrimaryColor"))
                .setDefaultValue(MountSkinAlertPrimaryColor.Any)
                .setEnumNameProvider(v -> Text.translatable("text.autoconfig.wtz-config.option.mountWyvernAlertPrimaryColor." + v.name()))
                .setSaveConsumer(v -> c.mountWyvernAlertPrimaryColor = v).build());
        mountAlerts.add(e.startEnumSelector(option("mountWyvernAlertSecondaryColor"), MountSkinAlertSecondaryColor.class, c.mountWyvernAlertSecondaryColor)
                .setTooltip(tooltip("mountWyvernAlertSecondaryColor"))
                .setDefaultValue(MountSkinAlertSecondaryColor.Any)
                .setEnumNameProvider(v -> Text.translatable("text.autoconfig.wtz-config.option.mountWyvernAlertSecondaryColor." + v.name()))
                .setSaveConsumer(v -> c.mountWyvernAlertSecondaryColor = v).build());
        mountAlerts.add(e.startBooleanToggle(option("mountAdasaurColorAlertsEnabled"), c.mountAdasaurColorAlertsEnabled)
                .setTooltip(tooltip("mountAdasaurColorAlertsEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.mountAdasaurColorAlertsEnabled = v).build());
        mountAlerts.add(e.startEnumSelector(option("mountAdasaurAlertPrimaryColor"), MountAdasaurAlertPrimaryColor.class, c.mountAdasaurAlertPrimaryColor)
                .setTooltip(tooltip("mountAdasaurAlertPrimaryColor"))
                .setDefaultValue(MountAdasaurAlertPrimaryColor.Any)
                .setEnumNameProvider(v -> Text.translatable("text.autoconfig.wtz-config.option.mountAdasaurAlertPrimaryColor." + v.name()))
                .setSaveConsumer(v -> c.mountAdasaurAlertPrimaryColor = v).build());
        mountAlerts.add(e.startEnumSelector(option("mountAdasaurAlertSecondaryColor"), MountAdasaurAlertSecondaryColor.class, c.mountAdasaurAlertSecondaryColor)
                .setTooltip(tooltip("mountAdasaurAlertSecondaryColor"))
                .setDefaultValue(MountAdasaurAlertSecondaryColor.Any)
                .setEnumNameProvider(v -> Text.translatable("text.autoconfig.wtz-config.option.mountAdasaurAlertSecondaryColor." + v.name()))
                .setSaveConsumer(v -> c.mountAdasaurAlertSecondaryColor = v).build());

        mounts.addEntry(mountAlerts.build());
        ConfigCategory qol = builder.getOrCreateCategory(category("qualityOfLife"));
        qol.addEntry(e.startBooleanToggle(option("qualityOfLifeEnabled"), c.qualityOfLifeEnabled)
                .setTooltip(tooltip("qualityOfLifeEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.qualityOfLifeEnabled = v).build());
        qol.addEntry(e.startBooleanToggle(option("qolRightClickBack"), c.qolRightClickBack)
                .setTooltip(tooltip("qolRightClickBack"))
                .setDefaultValue(false).setSaveConsumer(v -> c.qolRightClickBack = v).build());
        qol.addEntry(e.startBooleanToggle(option("qolHideActionbarInChat"), c.qolHideActionbarInChat)
                .setTooltip(tooltip("qolHideActionbarInChat"))
                .setDefaultValue(false).setSaveConsumer(v -> c.qolHideActionbarInChat = v).build());
        qol.addEntry(e.startBooleanToggle(option("qolActionbarAboveChat"), c.qolActionbarAboveChat)
                .setTooltip(tooltip("qolActionbarAboveChat"))
                .setDefaultValue(false).setSaveConsumer(v -> c.qolActionbarAboveChat = v).build());
        qol.addEntry(e.startBooleanToggle(option("qolMacOSMovementKeyFix"), c.qolMacOSMovementKeyFix)
                .setTooltip(tooltip("qolMacOSMovementKeyFix"))
                .setDefaultValue(false).setSaveConsumer(v -> c.qolMacOSMovementKeyFix = v).build());
    
        ConfigCategory shoppingList = builder.getOrCreateCategory(category("shoppingList"));
        shoppingList.addEntry(e.startBooleanToggle(option("shoppingListEnabled"), c.shoppingListEnabled)
                .setTooltip(tooltip("shoppingListEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.shoppingListEnabled = v).build());
        shoppingList.addEntry(e.startBooleanToggle(option("shoppingListAutoOpenTradeMarket"), c.shoppingListAutoOpenTradeMarket)
                .setTooltip(tooltip("shoppingListAutoOpenTradeMarket"))
                .setDefaultValue(false).setSaveConsumer(v -> c.shoppingListAutoOpenTradeMarket = v).build());

        ConfigCategory bankFilters = builder.getOrCreateCategory(category("bankFilters"));
        bankFilters.addEntry(e.startBooleanToggle(option("bankFiltersEnabled"), c.bankFiltersEnabled)
                .setTooltip(tooltip("bankFiltersEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.bankFiltersEnabled = v).build());
        bankFilters.addEntry(e.startBooleanToggle(option("bankFilterMountFiltersEnabled"), c.bankFilterMountFiltersEnabled)
                .setTooltip(tooltip("bankFilterMountFiltersEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.bankFilterMountFiltersEnabled = v).build());
    
        ConfigCategory shoutTTS = builder.getOrCreateCategory(category("shoutTTS"));
        shoutTTS.addEntry(e.startBooleanToggle(option("shoutTTSEnabled"), c.shoutTTSEnabled)
                .setTooltip(tooltip("shoutTTSEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.shoutTTSEnabled = v).build());
        shoutTTS.addEntry(e.startStrField(option("shoutTTSToken"), c.shoutTTSToken)
                .setTooltip(tooltip("shoutTTSToken"))
                .setDefaultValue("").setSaveConsumer(v -> c.shoutTTSToken = v).build());
        shoutTTS.addEntry(e.startIntSlider(option("shoutTTSVolume"), c.shoutTTSVolume, 0, 100)
                .setTooltip(tooltip("shoutTTSVolume"))
                .setDefaultValue(40).setSaveConsumer(v -> c.shoutTTSVolume = v).build());
        shoutTTS.addEntry(e.startEnumSelector(option("shoutTTSVoice"), TTSVoice.class, c.shoutTTSVoice)
                .setTooltip(tooltip("shoutTTSVoice"))
                .setDefaultValue(TTSVoice.RANDOM)
                .setEnumNameProvider(v -> Text.translatable("text.autoconfig.wtz-config.option.shoutTTSVoice." + v.name()))
                .setSaveConsumer(v -> c.shoutTTSVoice = v).build());
    
        ConfigCategory lookLine = builder.getOrCreateCategory(category("lookLine"));
        lookLine.addEntry(e.startBooleanToggle(option("lookLineEnabled"), c.lookLineEnabled)
                .setTooltip(tooltip("lookLineEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.lookLineEnabled = v).build());
        lookLine.addEntry(e.startIntSlider(option("lookLineMaxDistance"), c.lookLineMaxDistance, 1, 50)
                .setTooltip(tooltip("lookLineMaxDistance"))
                .setDefaultValue(10).setSaveConsumer(v -> c.lookLineMaxDistance = v).build());
        lookLine.addEntry(e.startFloatField(option("lookLineWidth"), c.lookLineWidth)
                .setTooltip(tooltip("lookLineWidth"))
                .setDefaultValue(0.05f).setMin(0.01f).setMax(0.5f).setSaveConsumer(v -> c.lookLineWidth = v).build());
        lookLine.addEntry(e.startAlphaColorField(option("lookLineColor"), c.lookLineColor)
                .setTooltip(tooltip("lookLineColor"))
                .setDefaultValue(0xFFFFFFFF).setSaveConsumer(v -> c.lookLineColor = v).build());

        return builder.build();
    }

    public enum TTSVoice {
        RANDOM("random"),
        AUSSIE_MALE_1("en_au_001"),
        AUSSIE_MALE_2("en_au_002"),
        BRITISH_MALE_1("en_uk_001"),
        BRITISH_MALE_2("en_uk_003"),
        US_FEMALE_1("en_us_001"),
        US_FEMALE_2("en_us_002"),
        US_MALE_1("en_us_006"),
        US_MALE_2("en_us_007"),
        US_MALE_3("en_us_009"),
        US_MALE_4("en_us_010"),
        NARRATOR("en_male_narration"),
        EMOTIONAL("en_female_emotional"),
        CODY("en_male_cody");

        private final String id;

        TTSVoice(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public enum MountItemOverlayModifierKey {
        NONE,
        CTRL,
        SHIFT,
        ALT
    }

    public enum MountSkinAlertPrimaryColor {
        Any,
        Azure,
        Infernal,
        Ebony,
        Golden,
        Cerulean,
        Bronze,
        Hollow,
        Jade,
        Fledge,
        Mystic
    }

    public enum MountSkinAlertSecondaryColor {
        Any,
        Kander,
        Horn,
        Cinder,
        Tusk,
        Ivory,
        Rose,
        Quartz,
        Onyx,
        Shell,
        Sapphire
    }

    public enum MountHorseAlertPrimaryColor {
        Any,
        Bay,
        Gray,
        Black,
        Chestnut,
        Silver,
        Cherry,
        Tan,
        Beige,
        Gold,
        White
    }

    public enum MountHorseAlertSecondaryColor {
        Any,
        Argent,
        Dawn,
        Night,
        Reddish,
        Fawn,
        Dusk,
        Pale,
        Ash,
        Sable,
        Rich
    }

    public enum MountAdasaurAlertPrimaryColor {
        Any,
        Crimson,
        Cobalt,
        Ash,
        Dusk,
        Amber,
        Emerald,
        Albino,
        Sable,
        Plum,
        Dust
    }

    public enum MountAdasaurAlertSecondaryColor {
        Any,
        Moss,
        Bleach,
        Tawny,
        Sage,
        Blood,
        Raven,
        Misty,
        Royal,
        Rose,
        Maroon
    }
}
