package com.merine.rebuild.system.menu;

import java.util.List;

/**
 * 前端已注册的导航图标清单（代码侧注册表）。
 *
 * 图标是代码里的 React 组件，数据库存不了组件，只存名称：菜单的目录与页面节点
 * 只能从这里挑选，未登记的名称一律拒绝，避免库中出现渲染不出来的字符串。
 * 前端 app/iconRegistry.tsx 保存同一份「名称 → 组件与中文名」映射，
 * 用于选择预览与导航渲染；新增图标必须两侧同时登记（与 RegisteredRoutes 同一约定）。
 */
public final class RegisteredIcons {

    public record Icon(String name, String label) {
    }

    private static final List<Icon> ALL = List.of(
            new Icon("TeamOutlined", "团队"),
            new Icon("UserOutlined", "用户"),
            new Icon("UserAddOutlined", "新增用户"),
            new Icon("UsergroupAddOutlined", "用户组"),
            new Icon("ContactsOutlined", "通讯录"),
            new Icon("IdcardOutlined", "证件"),
            new Icon("CustomerServiceOutlined", "客服"),
            new Icon("AuditOutlined", "审计"),
            new Icon("SafetyCertificateOutlined", "安全认证"),
            new Icon("SafetyOutlined", "安全"),
            new Icon("LockOutlined", "权限"),
            new Icon("KeyOutlined", "密钥"),
            new Icon("EyeOutlined", "查看"),
            new Icon("ScanOutlined", "扫描"),
            new Icon("MenuOutlined", "菜单"),
            new Icon("AppstoreOutlined", "应用"),
            new Icon("ApartmentOutlined", "组织架构"),
            new Icon("ClusterOutlined", "集群"),
            new Icon("DeploymentUnitOutlined", "部署单元"),
            new Icon("PartitionOutlined", "分组"),
            new Icon("ProjectOutlined", "项目"),
            new Icon("BuildOutlined", "楼宇"),
            new Icon("BookOutlined", "字典手册"),
            new Icon("ReadOutlined", "阅读"),
            new Icon("ProfileOutlined", "档案"),
            new Icon("FileOutlined", "文件"),
            new Icon("FileTextOutlined", "文本文件"),
            new Icon("FileSearchOutlined", "档案检索"),
            new Icon("FileDoneOutlined", "办结文件"),
            new Icon("FolderOutlined", "文件夹"),
            new Icon("FolderOpenOutlined", "打开文件夹"),
            new Icon("InboxOutlined", "收件箱"),
            new Icon("DatabaseOutlined", "数据库"),
            new Icon("TableOutlined", "数据表"),
            new Icon("DashboardOutlined", "仪表盘"),
            new Icon("BarChartOutlined", "柱状统计"),
            new Icon("LineChartOutlined", "折线趋势"),
            new Icon("PieChartOutlined", "占比分析"),
            new Icon("AreaChartOutlined", "面积趋势"),
            new Icon("RadarChartOutlined", "雷达分析"),
            new Icon("FundProjectionScreenOutlined", "分析大屏"),
            new Icon("RiseOutlined", "增长趋势"),
            new Icon("MonitorOutlined", "监控"),
            new Icon("DesktopOutlined", "终端"),
            new Icon("CloudOutlined", "云"),
            new Icon("CloudServerOutlined", "服务器"),
            new Icon("HddOutlined", "存储"),
            new Icon("ApiOutlined", "接口"),
            new Icon("GlobalOutlined", "网络"),
            new Icon("NodeIndexOutlined", "节点"),
            new Icon("ClockCircleOutlined", "时间"),
            new Icon("ScheduleOutlined", "计划任务"),
            new Icon("CalendarOutlined", "日历"),
            new Icon("FieldTimeOutlined", "工时"),
            new Icon("HistoryOutlined", "历史"),
            new Icon("SyncOutlined", "同步"),
            new Icon("ReloadOutlined", "刷新"),
            new Icon("BellOutlined", "通知"),
            new Icon("NotificationOutlined", "公告"),
            new Icon("MessageOutlined", "消息"),
            new Icon("CommentOutlined", "评论"),
            new Icon("SendOutlined", "发送"),
            new Icon("MailOutlined", "邮件"),
            new Icon("EnvironmentOutlined", "位置"),
            new Icon("CompassOutlined", "方位"),
            new Icon("AimOutlined", "目标"),
            new Icon("FlagOutlined", "标记"),
            new Icon("HomeOutlined", "首页"),
            new Icon("SettingOutlined", "设置"),
            new Icon("ToolOutlined", "工具"),
            new Icon("SlidersOutlined", "参数"),
            new Icon("ExperimentOutlined", "试验"),
            new Icon("BugOutlined", "缺陷"),
            new Icon("CodeOutlined", "代码"),
            new Icon("LinkOutlined", "链接"),
            new Icon("ShareAltOutlined", "分享"),
            new Icon("SwapOutlined", "流转"),
            new Icon("BranchesOutlined", "分支"),
            new Icon("SearchOutlined", "检索"),
            new Icon("FilterOutlined", "筛选"),
            new Icon("UploadOutlined", "上传"),
            new Icon("DownloadOutlined", "下载"),
            new Icon("ImportOutlined", "导入"),
            new Icon("ExportOutlined", "导出"),
            new Icon("EditOutlined", "编辑"),
            new Icon("FormOutlined", "表单"),
            new Icon("ThunderboltOutlined", "任务"),
            new Icon("StarOutlined", "收藏"),
            new Icon("TagOutlined", "标签"),
            new Icon("PaperClipOutlined", "附件"),
            new Icon("PictureOutlined", "图片"),
            new Icon("VideoCameraOutlined", "视频"),
            new Icon("WarningOutlined", "警示"),
            new Icon("CheckCircleOutlined", "完成"),
            new Icon("InfoCircleOutlined", "说明"),
            new Icon("QuestionCircleOutlined", "帮助"),
            new Icon("ShopOutlined", "站点"),
            new Icon("TruckOutlined", "运输"),
            new Icon("MoneyCollectOutlined", "费用"),
            new Icon("CalculatorOutlined", "核算"));

    private RegisteredIcons() {
    }

    public static List<Icon> all() {
        return ALL;
    }

    public static boolean contains(String name) {
        return ALL.stream().anyMatch(icon -> icon.name().equals(name));
    }
}
