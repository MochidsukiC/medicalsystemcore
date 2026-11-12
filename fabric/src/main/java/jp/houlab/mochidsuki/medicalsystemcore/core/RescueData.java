package jp.houlab.mochidsuki.medicalsystemcore.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import jp.houlab.mochidsuki.medicalsystemcore.common.model.RescueCategory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public class RescueData {

    /** RescueCategory は名前(name())で保存する。未知の名前は読み込みエラーにする(壊れたデータを黙って受け入れない)。 */
    private static final Codec<RescueCategory> CATEGORY_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                try {
                    return DataResult.success(RescueCategory.valueOf(name));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "未知の RescueCategory: " + name);
                }
            },
            RescueCategory::name);

    /**
     * ワールドのセーブデータ用。全フィールドを保存する。memo は null のときキーを書かない。
     * キーが欠けた・値が不正な場合は読み込みエラーにする(memo だけは欠落を null として扱う)。
     */
    public static final Codec<RescueData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(RescueData::getId),
            UUIDUtil.CODEC.fieldOf("reporterUuid").forGetter(RescueData::getReporterUuid),
            Codec.STRING.fieldOf("name").forGetter(RescueData::getName),
            CATEGORY_CODEC.fieldOf("category").forGetter(RescueData::getCategory),
            Codec.LONG.fieldOf("reportTime").forGetter(RescueData::getReportTime),
            Codec.STRING.fieldOf("description").forGetter(RescueData::getDescription),
            Codec.BOOL.fieldOf("isDispatch").forGetter(RescueData::isDispatch),
            Codec.BOOL.fieldOf("isTreatment").forGetter(RescueData::isTreatment),
            BlockPos.CODEC.fieldOf("location").forGetter(RescueData::getLocation),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(RescueData::getDimension),
            Codec.STRING.optionalFieldOf("memo").forGetter(data -> Optional.ofNullable(data.getMemo()))
    ).apply(instance, (id, reporterUuid, name, category, reportTime, description, isDispatch, isTreatment, location, dimension, memo) ->
            new RescueData(id, reporterUuid, name, category, reportTime, description, isDispatch, isTreatment, location, dimension,
                    memo.orElse(null))));

    private final int id; // 各インスタンス固有のID(採番は RescueSavedData が行う)
    private String name;
    /** 通報者の UUID。照合に使う(表示名やプロファイル名は変わりうるため name では照合しない)。 */
    private final UUID reporterUuid;
    private RescueCategory category;
    private long reportTime;
    private String description;
    private boolean isDispatch;
    private boolean isTreatment;
    private BlockPos location;
    private ResourceKey<Level> dimension;
    private String memo;

    /**
     * 新規の通報を作る。id は RescueSavedData が採番した値を渡す(ここでは採番しない)。
     */
    public RescueData(int id, UUID reporterUuid, String name, RescueCategory category, String description, BlockPos location, ResourceKey<Level> dimension) {
        this.id = id;
        this.reporterUuid = reporterUuid;
        this.name = name;
        this.category = category;
        this.description = description;
        this.isDispatch = false;
        this.isTreatment = false;
        this.location = location;
        this.dimension = dimension;
        this.reportTime = System.currentTimeMillis();
    }

    private RescueData(int id, UUID reporterUuid, String name, RescueCategory category, long reportTime, String description,
                       boolean isDispatch, boolean isTreatment, BlockPos location, ResourceKey<Level> dimension, String memo) {
        this.id = id;
        this.reporterUuid = reporterUuid;
        this.name = name;
        this.category = category;
        this.reportTime = reportTime;
        this.description = description;
        this.isDispatch = isDispatch;
        this.isTreatment = isTreatment;
        this.location = location;
        this.dimension = dimension;
        this.memo = memo;
    }

    public int getId() {
        return id;
    }

    public UUID getReporterUuid() {
        return reporterUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RescueCategory getCategory() {
        return category;
    }

    public void setCategory(RescueCategory category) {
        this.category = category;
    }

    public long getReportTime() {
        return reportTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDispatch() {
        return isDispatch;
    }

    public void setDispatch(boolean dispatch) {
        isDispatch = dispatch;
    }

    public boolean isTreatment() {
        return isTreatment;
    }

    public void setTreatment(boolean treatment) {
        isTreatment = treatment;
    }

    public BlockPos getLocation() {
        return location;
    }

    public void setLocation(BlockPos location) {
        this.location = location;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public void setDimension(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    public String getDimensionDisplayName() {
        if (dimension == null) return "不明";
        String path = dimension.identifier().getPath();
        switch (path) {
            case "overworld": return "オーバーワールド";
            case "the_nether": return "ネザー";
            case "the_end": return "ジ・エンド";
            default: return dimension.identifier().toString();
        }
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }


    /**
     * ネットワークバッファからデータを読み込んでインスタンスを生成するコンストラクタ
     * @param buf ネットワークバッファ
     */
    public RescueData(FriendlyByteBuf buf) {
        this.id = buf.readInt();
        this.reporterUuid = buf.readUUID();
        this.name = buf.readUtf();
        this.category = buf.readEnum(RescueCategory.class);
        this.reportTime = buf.readLong();
        this.description = buf.readUtf();
        this.isDispatch = buf.readBoolean();
        this.isTreatment = buf.readBoolean();
        this.location = buf.readBlockPos();
        Identifier dimId = buf.readIdentifier();
        this.dimension = ResourceKey.create(Registries.DIMENSION, dimId);
        this.memo = buf.readUtf();
    }

    /**
     * インスタンスのデータをネットワークバッファに書き込むメソッド
     * @param buf ネットワークバッファ
     */
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(id);
        buf.writeUUID(reporterUuid);
        buf.writeUtf(name);
        buf.writeEnum(category);
        buf.writeLong(reportTime);
        buf.writeUtf(description);
        buf.writeBoolean(isDispatch);
        buf.writeBoolean(isTreatment);
        buf.writeBlockPos(location);
        buf.writeIdentifier(dimension.identifier());
        buf.writeUtf(memo != null ? memo : "");
    }

}
