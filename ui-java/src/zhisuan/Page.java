package zhisuan;

import javax.swing.JPanel;

/** 页面基类：持有 API 客户端，支持切页时刷新 */
public abstract class Page extends JPanel {
    protected final ApiClient api;

    protected Page(ApiClient api) {
        this.api = api;
        setBackground(Ui.BG);
    }

    /** 每次切换到该页时回调（EDT） */
    public void onShow() {
    }
}
