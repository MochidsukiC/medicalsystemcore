package jp.houlab.mochidsuki.medicalsystemcore.core;

import jp.houlab.mochidsuki.medicalsystemcore.common.model.RescueCategory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class RescueData {

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

    /**
     * ワールドのセーブデータ(NBT)へ書き出す。全フィールドを書く。memo は null のときキーを書かない。
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putUUID("reporterUuid", reporterUuid);
        tag.putString("name", name);
        tag.putString("category", category.name());
        tag.putLong("reportTime", reportTime);
        tag.putString("description", description);
        tag.putBoolean("isDispatch", isDispatch);
        tag.putBoolean("isTreatment", isTreatment);
        tag.put("location", NbtUtils.writeBlockPos(location));
        tag.putString("dimension", dimension.location().toString());
        if (memo != null) {
            tag.putString("memo", memo);
        }
        return tag;
    }

    /**
     * {@link #save()} の逆変換。キーが欠けた・値が不正な場合は例外にして、壊れたデータを黙って受け入れない。
     */
    public static RescueData load(CompoundTag tag) {
        return new RescueData(
                tag.getInt("id"),
                tag.getUUID("reporterUuid"),
                tag.getString("name"),
                RescueCategory.valueOf(tag.getString("category")),
                tag.getLong("reportTime"),
                tag.getString("description"),
                tag.getBoolean("isDispatch"),
                tag.getBoolean("isTreatment"),
                NbtUtils.readBlockPos(tag.getCompound("location")),
                ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("dimension"))),
                tag.contains("memo") ? tag.getString("memo") : null
        );
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
        String path = dimension.location().getPath();
        switch (path) {
            case "overworld": return "オーバーワールド";
            case "the_nether": return "ネザー";
            case "the_end": return "ジ・エンド";
            default: return dimension.location().toString();
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
        ResourceLocation dimRL = buf.readResourceLocation();
        this.dimension = ResourceKey.create(Registries.DIMENSION, dimRL);
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
        buf.writeResourceLocation(dimension.location());
        buf.writeUtf(memo != null ? memo : "");
    }

}
