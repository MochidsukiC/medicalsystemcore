package jp.houlab.mochidsuki.medicalsystemcore.core;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class RescueData {
    public static final List<RescueData> RESCUE_DATA_LIST = new ArrayList<>();


    private String name;
    private RescueCategory category;
    private long reportTime;
    private String description;
    private boolean isDispatch;
    private boolean isTreatment;
    private BlockPos location;
    private String memo;

    public RescueData(String name, RescueCategory category, String description, BlockPos location) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.isDispatch = false;
        this.isTreatment = false;
        this.location = location;
        this.reportTime = System.currentTimeMillis(); // ★ 2. オブジェクト生成時の現在時刻を記録

        RESCUE_DATA_LIST.add(0,this);
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


}
