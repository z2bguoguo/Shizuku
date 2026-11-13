package moe.shizuku.starter.concurrent;

public final class AutomationProtocol {
    private AutomationProtocol() { }
    public static final String DESCRIPTOR = "rikka.shizuku.concurrent.AutomationHub";
    public static final String EXTRA = "automationBroker";
    public static final String METHOD = "getAutomationBroker";
    public static final int GET = 10005;
    public static final int REGISTER = 1, UNREGISTER = 2, CALL = 3, STATS = 4;
}
