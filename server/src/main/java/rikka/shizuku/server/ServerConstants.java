package rikka.shizuku.server;

public class ServerConstants {

    public static final int MANAGER_APP_NOT_FOUND = 50;

    public static final String PERMISSION = "moe.shizuku.manager.permission.API_V23";
    public static final String MANAGER_APPLICATION_ID = rikka.shizuku.common.BuildConfig.MANAGER_APPLICATION_ID;
    public static final String REQUEST_PERMISSION_ACTION = MANAGER_APPLICATION_ID + ".intent.action.REQUEST_PERMISSION";

    public static final int BINDER_TRANSACTION_getApplications = 10001;
    public static final int BINDER_TRANSACTION_getConnectionDiagnostics = 10004;
    public static final int BINDER_TRANSACTION_getBlacklist = 10002;
    public static final int BINDER_TRANSACTION_setBlacklist = 10003;
}
