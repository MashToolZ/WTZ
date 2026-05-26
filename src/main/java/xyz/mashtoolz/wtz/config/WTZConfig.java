package xyz.mashtoolz.wtz.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

@Config(name = "wtz/config")
public class WTZConfig implements ConfigData {

    private static ConfigHolder<WTZConfig> holder;

    public static WTZConfig register() {
        holder = AutoConfig.register(WTZConfig.class, GsonConfigSerializer::new);
        return holder.getConfig();
    }

    public static ConfigHolder<WTZConfig> holder() {
        return holder;
    }

    public static void save() {
        if (holder != null) holder.save();
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
    public Anchor mountStatsAnchor = Anchor.MIDDLE_RIGHT;
    public int mountStatsOffsetX = 0;
    public int mountStatsOffsetY = 0;
    public float mountStatsScale = 1.0f;

    
    public boolean mountItemOverlayEnabled = false;
    public boolean mountItemOverlayPotentialEnabled = false;
    public boolean mountItemOverlayBarsEnabled = false;
    public boolean mountSkinReportingEnabled = false;

    
    public boolean qualityOfLifeEnabled = false;
    public boolean qolRightClickBack = false;
    public boolean qolHideActionbarInChat = false;
    public boolean qolActionbarAboveChat = false;

    
    public boolean shoppingListEnabled = false;
    public boolean shoppingListAutoOpenTradeMarket = false;
    public float shoppingListScale = 1.0f;
    public boolean bankFiltersEnabled = true;
    public boolean bankFilterMountTypeEnabled = true;
    public boolean bankFilterMountPrimaryColorEnabled = true;
    public boolean bankFilterMountSecondaryColorEnabled = true;

    
    public boolean mountCameraEnabled = false;
    public boolean mountCameraScrollZoom = false;
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
    public void validatePostLoad() throws ValidationException {
        mountHelperLabelScale = clamp(mountHelperLabelScale, 0.1f, 2.0f);
        mountHelperMaxedTimeout = Math.max(0, Math.min(60, mountHelperMaxedTimeout));
        mountHelperMaxedOpacity = Math.max(0, Math.min(100, mountHelperMaxedOpacity));
        mountStatsOffsetX = Math.max(-50, Math.min(50, mountStatsOffsetX));
        mountStatsOffsetY = Math.max(-50, Math.min(50, mountStatsOffsetY));
        mountStatsScale = clamp(mountStatsScale, 0.1f, 2.0f);
        shoppingListScale = clamp(shoppingListScale, 0.5f, 1.0f);
        mountCameraFov = Math.max(29, Math.min(110, mountCameraFov));
        mountCameraOffsetZ = Math.max(-5.0, Math.min(5.0, mountCameraOffsetZ));
        shoutTTSVolume = Math.max(0, Math.min(100, shoutTTSVolume));
        lookLineMaxDistance = Math.max(1, Math.min(50, lookLineMaxDistance));
        lookLineWidth = clamp(lookLineWidth, 0.01f, 0.5f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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

        ConfigCategory mountHelper = builder.getOrCreateCategory(category("mountHelper"));
        mountHelper.addEntry(e.startBooleanToggle(option("mountHelperEnabled"), c.mountHelperEnabled)
                .setTooltip(tooltip("mountHelperEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountHelperEnabled = v).build());
        mountHelper.addEntry(e.startFloatField(option("mountHelperLabelScale"), c.mountHelperLabelScale)
                .setTooltip(tooltip("mountHelperLabelScale"))
                .setDefaultValue(0.5f).setMin(0.1f).setMax(2.0f).setSaveConsumer(v -> c.mountHelperLabelScale = v).build());
        mountHelper.addEntry(e.startBooleanToggle(option("mountHelperHideMaxed"), c.mountHelperHideMaxed)
                .setTooltip(tooltip("mountHelperHideMaxed"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountHelperHideMaxed = v).build());
        mountHelper.addEntry(e.startIntSlider(option("mountHelperMaxedTimeout"), c.mountHelperMaxedTimeout, 0, 60)
                .setTooltip(tooltip("mountHelperMaxedTimeout"))
                .setDefaultValue(10).setSaveConsumer(v -> c.mountHelperMaxedTimeout = v).build());
        mountHelper.addEntry(e.startIntSlider(option("mountHelperMaxedOpacity"), c.mountHelperMaxedOpacity, 0, 100)
                .setTooltip(tooltip("mountHelperMaxedOpacity"))
                .setDefaultValue(30).setSaveConsumer(v -> c.mountHelperMaxedOpacity = v).build());

        
        ConfigCategory mountStats = builder.getOrCreateCategory(category("mountStats"));
        mountStats.addEntry(e.startBooleanToggle(option("mountStatsEnabled"), c.mountStatsEnabled)
                .setTooltip(tooltip("mountStatsEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountStatsEnabled = v).build());
        mountStats.addEntry(e.startBooleanToggle(option("mountStatsAutoUpdate"), c.mountStatsAutoUpdate)
                .setTooltip(tooltip("mountStatsAutoUpdate"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountStatsAutoUpdate = v).build());
        mountStats.addEntry(e.startBooleanToggle(option("mountStatsTrackHeld"), c.mountStatsTrackHeld)
                .setTooltip(tooltip("mountStatsTrackHeld"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountStatsTrackHeld = v).build());
        mountStats.addEntry(e.startBooleanToggle(option("mountStatsShowWhenNotMounted"), c.mountStatsShowWhenNotMounted)
                .setTooltip(tooltip("mountStatsShowWhenNotMounted"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountStatsShowWhenNotMounted = v).build());
        mountStats.addEntry(e.startEnumSelector(option("mountStatsAnchor"), Anchor.class, c.mountStatsAnchor)
                .setTooltip(tooltip("mountStatsAnchor"))
                .setDefaultValue(Anchor.MIDDLE_RIGHT)
                .setEnumNameProvider(v -> Text.translatable("text.autoconfig.wtz-config.option.mountStatsAnchor." + v.name()))
                .setSaveConsumer(v -> c.mountStatsAnchor = v).build());
        mountStats.addEntry(e.startIntSlider(option("mountStatsOffsetX"), c.mountStatsOffsetX, -50, 50)
                .setTooltip(tooltip("mountStatsOffsetX"))
                .setDefaultValue(0).setSaveConsumer(v -> c.mountStatsOffsetX = v).build());
        mountStats.addEntry(e.startIntSlider(option("mountStatsOffsetY"), c.mountStatsOffsetY, -50, 50)
                .setTooltip(tooltip("mountStatsOffsetY"))
                .setDefaultValue(0).setSaveConsumer(v -> c.mountStatsOffsetY = v).build());
        mountStats.addEntry(e.startFloatField(option("mountStatsScale"), c.mountStatsScale)
                .setTooltip(tooltip("mountStatsScale"))
                .setDefaultValue(1.0f).setMin(0.1f).setMax(2.0f).setSaveConsumer(v -> c.mountStatsScale = v).build());

        ConfigCategory mountCamera = builder.getOrCreateCategory(category("mountCamera"));
        mountCamera.addEntry(e.startBooleanToggle(option("mountCameraEnabled"), c.mountCameraEnabled)
                .setTooltip(tooltip("mountCameraEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountCameraEnabled = v).build());
        mountCamera.addEntry(e.startBooleanToggle(option("mountCameraAutoPerspective"), c.mountCameraAutoPerspective)
                .setTooltip(tooltip("mountCameraAutoPerspective"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountCameraAutoPerspective = v).build());
        mountCamera.addEntry(e.startBooleanToggle(option("mountCameraFreeLook"), c.mountCameraFreeLook)
                .setTooltip(tooltip("mountCameraFreeLook"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountCameraFreeLook = v).build());
        mountCamera.addEntry(e.startBooleanToggle(option("mountCameraScrollZoom"), c.mountCameraScrollZoom)
                .setTooltip(tooltip("mountCameraScrollZoom"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountCameraScrollZoom = v).build());
        mountCamera.addEntry(e.startIntSlider(option("mountCameraFov"), c.mountCameraFov, 29, 110)
                .setTooltip(tooltip("mountCameraFov"))
                .setTextGetter(v -> v == 29 ? Text.literal("Default") : Text.literal(String.valueOf(v)))
                .setDefaultValue(29).setSaveConsumer(v -> c.mountCameraFov = v).build());
        mountCamera.addEntry(e.startDoubleField(option("mountCameraOffsetZ"), c.mountCameraOffsetZ)
                .setTooltip(tooltip("mountCameraOffsetZ"))
                .setDefaultValue(4.0).setMin(-5.0).setMax(5.0).setSaveConsumer(v -> c.mountCameraOffsetZ = v).build());

        
        ConfigCategory mountItemOverlay = builder.getOrCreateCategory(category("mountItemOverlay"));
        mountItemOverlay.addEntry(e.startBooleanToggle(option("mountItemOverlayEnabled"), c.mountItemOverlayEnabled)
                .setTooltip(tooltip("mountItemOverlayEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountItemOverlayEnabled = v).build());
        mountItemOverlay.addEntry(e.startBooleanToggle(option("mountItemOverlayPotentialEnabled"), c.mountItemOverlayPotentialEnabled)
                .setTooltip(tooltip("mountItemOverlayPotentialEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountItemOverlayPotentialEnabled = v).build());
        mountItemOverlay.addEntry(e.startBooleanToggle(option("mountItemOverlayBarsEnabled"), c.mountItemOverlayBarsEnabled)
                .setTooltip(tooltip("mountItemOverlayBarsEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountItemOverlayBarsEnabled = v).build());

        
        ConfigCategory mountSkinReporting = builder.getOrCreateCategory(category("mountSkinReporting"));
        mountSkinReporting.addEntry(e.startBooleanToggle(option("mountSkinReportingEnabled"), c.mountSkinReportingEnabled)
                .setTooltip(tooltip("mountSkinReportingEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.mountSkinReportingEnabled = v).build());

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

        
        ConfigCategory shoppingList = builder.getOrCreateCategory(category("shoppingList"));
        shoppingList.addEntry(e.startBooleanToggle(option("shoppingListEnabled"), c.shoppingListEnabled)
                .setTooltip(tooltip("shoppingListEnabled"))
                .setDefaultValue(false).setSaveConsumer(v -> c.shoppingListEnabled = v).build());
        shoppingList.addEntry(e.startBooleanToggle(option("shoppingListAutoOpenTradeMarket"), c.shoppingListAutoOpenTradeMarket)
                .setTooltip(tooltip("shoppingListAutoOpenTradeMarket"))
                .setDefaultValue(false).setSaveConsumer(v -> c.shoppingListAutoOpenTradeMarket = v).build());
        shoppingList.addEntry(e.startFloatField(option("shoppingListScale"), c.shoppingListScale)
                .setTooltip(tooltip("shoppingListScale"))
                .setDefaultValue(1.0f).setMin(0.5f).setMax(1.0f).setSaveConsumer(v -> c.shoppingListScale = v).build());

        ConfigCategory bankFilters = builder.getOrCreateCategory(category("bankFilters"));
        bankFilters.addEntry(e.startBooleanToggle(option("bankFiltersEnabled"), c.bankFiltersEnabled)
                .setTooltip(tooltip("bankFiltersEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.bankFiltersEnabled = v).build());
        bankFilters.addEntry(e.startBooleanToggle(option("bankFilterMountTypeEnabled"), c.bankFilterMountTypeEnabled)
                .setTooltip(tooltip("bankFilterMountTypeEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.bankFilterMountTypeEnabled = v).build());
        bankFilters.addEntry(e.startBooleanToggle(option("bankFilterMountPrimaryColorEnabled"), c.bankFilterMountPrimaryColorEnabled)
                .setTooltip(tooltip("bankFilterMountPrimaryColorEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.bankFilterMountPrimaryColorEnabled = v).build());
        bankFilters.addEntry(e.startBooleanToggle(option("bankFilterMountSecondaryColorEnabled"), c.bankFilterMountSecondaryColorEnabled)
                .setTooltip(tooltip("bankFilterMountSecondaryColorEnabled"))
                .setDefaultValue(true).setSaveConsumer(v -> c.bankFilterMountSecondaryColorEnabled = v).build());

        
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

    public enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

        public int anchorX(int screenWidth, int boxWidth) {
            return switch (this) {
                case TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> 0;
                case TOP_CENTER, MIDDLE_CENTER, BOTTOM_CENTER -> (screenWidth - boxWidth) / 2;
                case TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT -> screenWidth - boxWidth;
            };
        }

        public int anchorY(int screenHeight, int boxHeight) {
            return switch (this) {
                case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0;
                case MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT -> (screenHeight - boxHeight) / 2;
                case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenHeight - boxHeight;
            };
        }
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
}
