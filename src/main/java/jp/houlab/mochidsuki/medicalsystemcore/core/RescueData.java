package jp.houlab.mochidsuki.medicalsystemcore.core;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public class RescueData {
    //public static final List<RescueData> RESCUE_DATA_LIST = new ArrayList<>();

    private static int nextId = 1;



    private final int id; // 各インスタンス固有のID
    private String name;
    private RescueCategory category;
    private long reportTime;
    private String description;
    private boolean isDispatch;
    private boolean isTreatment;
    private BlockPos location;
    private String memo;

    public RescueData(String name, RescueCategory category, String description, BlockPos location) {
        this.id = nextId++; // IDを自動で割り振り、カウンターをインクリメント
        this.name = name;
        this.category = category;
        this.description = description;
        this.isDispatch = false;
        this.isTreatment = false;
        this.location = location;
        this.reportTime = System.currentTimeMillis(); // ★ 2. オブジェクト生成時の現在時刻を記録

    }

    public int getId() {
        return id;
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
        this.name = buf.readUtf();
        this.category = buf.readEnum(RescueCategory.class);
        this.reportTime = buf.readLong();
        this.description = buf.readUtf();
        this.isDispatch = buf.readBoolean();
        this.isTreatment = buf.readBoolean();
        this.location = buf.readBlockPos();
        this.memo = buf.readUtf();
    }

    /**
     * インスタンスのデータをネットワークバッファに書き込むメソッド
     * @param buf ネットワークバッファ
     */
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(id);
        buf.writeUtf(name);
        buf.writeEnum(category);
        buf.writeLong(reportTime);
        buf.writeUtf(description);
        buf.writeBoolean(isDispatch);
        buf.writeBoolean(isTreatment);
        buf.writeBlockPos(location);
        buf.writeUtf(memo != null ? memo : "");
    }

}
