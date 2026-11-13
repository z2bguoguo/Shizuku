package moe.shizuku.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import moe.shizuku.starter.concurrent.AutomationProtocol
import moe.shizuku.starter.concurrent.ManagerBootstrap
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_TOKEN

/** The manifest restricts senders to android.permission.DUMP (shell/root). */
class BootstrapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            intent.extras?.getBinder(ManagerBootstrap.SERVER)?.let {
                Shizuku.onBinderReceived(it, context.packageName)
            }
            val callback = intent.extras?.getBinder(ManagerBootstrap.CALLBACK) ?: return
            val server = Shizuku.getBinder()?.takeIf { it.pingBinder() } ?: return
            val request = Parcel.obtain()
            val response = Parcel.obtain()
            val broker: IBinder?
            try {
                request.writeInterfaceToken("moe.shizuku.server.IShizukuService")
                check(server.transact(AutomationProtocol.GET, request, response, 0))
                response.readException()
                broker = response.readStrongBinder()
            } finally { request.recycle(); response.recycle() }
            if (broker == null) return
            val relay = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code != 1) return super.onTransact(code, data, reply, flags)
                    check(getCallingUid() == 0 || getCallingUid() == 2000) { "Shell/root required" }
                    data.enforceInterface(ManagerBootstrap.DESCRIPTOR)
                    val service = requireNotNull(data.readStrongBinder())
                    val token = requireNotNull(data.readString())
                    val identity = clearCallingIdentity()
                    try {
                        Shizuku.attachUserService(service, Bundle().apply { putString(USER_SERVICE_ARG_TOKEN, token) })
                        reply?.writeNoException()
                        reply?.writeStrongBinder(server)
                        return true
                    } finally { restoreCallingIdentity(identity) }
                }
            }
            val delivery = Parcel.obtain()
            try {
                delivery.writeInterfaceToken(ManagerBootstrap.DESCRIPTOR)
                delivery.writeStrongBinder(relay)
                delivery.writeStrongBinder(broker)
                check(callback.transact(1, delivery, null, IBinder.FLAG_ONEWAY))
            } finally { delivery.recycle() }
        } catch (failure: Exception) {
            android.util.Log.w("ShizukuBootstrap", "Background bootstrap failed", failure)
        }
    }
}
