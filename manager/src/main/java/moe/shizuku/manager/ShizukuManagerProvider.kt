package moe.shizuku.manager

import android.os.Binder
import android.os.Bundle
import androidx.core.os.bundleOf
import moe.shizuku.api.BinderContainer
import moe.shizuku.manager.utils.Logger.LOGGER
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_TOKEN
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.server.ktx.workerHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShizukuManagerProvider : ShizukuProvider() {

    companion object {
        private const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
        private const val METHOD_SEND_USER_SERVICE = "sendUserService"
    }

    override fun onCreate(): Boolean {
        disableAutomaticSuiInitialization()
        return super.onCreate()
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == moe.shizuku.starter.concurrent.AutomationProtocol.METHOD) {
            check(Binder.getCallingUid() == 0 || Binder.getCallingUid() == 2000) { "Privileged user-service process required" }
            if (!Shizuku.pingBinder()) return null
            val identity = Binder.clearCallingIdentity()
            val request = android.os.Parcel.obtain()
            val response = android.os.Parcel.obtain()
            try {
                request.writeInterfaceToken("moe.shizuku.server.IShizukuService")
                check(Shizuku.getBinder()?.transact(moe.shizuku.starter.concurrent.AutomationProtocol.GET, request, response, 0) == true)
                response.readException()
                return Bundle().apply { putBinder(moe.shizuku.starter.concurrent.AutomationProtocol.EXTRA, response.readStrongBinder()) }
            } finally {
                request.recycle(); response.recycle(); Binder.restoreCallingIdentity(identity)
            }
        }
        if (extras == null) return null

        return if (method == METHOD_SEND_USER_SERVICE) {
            try {
                extras.classLoader = BinderContainer::class.java.classLoader

                val token = extras.getString(USER_SERVICE_ARG_TOKEN) ?: return null
                val binder = extras.getParcelable<BinderContainer>(EXTRA_BINDER)?.binder ?: return null

                // Complete the common path on this provider thread. A queued shared
                // worker can miss clients such as GKD that only wait three seconds.
                if (Shizuku.pingBinder()) {
                    val identity = Binder.clearCallingIdentity()
                    try {
                        Shizuku.attachUserService(binder, bundleOf(USER_SERVICE_ARG_TOKEN to token))
                        return bundleOf(EXTRA_BINDER to BinderContainer(Shizuku.getBinder()))
                    } finally {
                        Binder.restoreCallingIdentity(identity)
                    }
                }

                val countDownLatch = CountDownLatch(1)
                var reply: Bundle? = Bundle()

                val listener = object : Shizuku.OnBinderReceivedListener {

                    override fun onBinderReceived() {
                        try {
                            Shizuku.attachUserService(binder, bundleOf(
                                USER_SERVICE_ARG_TOKEN to token
                            ))
                            reply!!.putParcelable(EXTRA_BINDER, BinderContainer(Shizuku.getBinder()))
                        } catch (e: Throwable) {
                            LOGGER.e(e, "attachUserService failed")
                            reply = null
                        }

                        Shizuku.removeBinderReceivedListener(this)

                        countDownLatch.countDown()
                    }
                }

                Shizuku.addBinderReceivedListenerSticky(listener, workerHandler)

                return try {
                    if (countDownLatch.await(15, TimeUnit.SECONDS)) {
                        reply
                    } else {
                        LOGGER.e("Manager Binder not received in 15s; user service handoff failed")
                        null
                    }
                } finally {
                    Shizuku.removeBinderReceivedListener(listener)
                }
            } catch (e: Throwable) {
                LOGGER.e(e, "sendUserService")
                null
            }
        } else {
            super.call(method, arg, extras)
        }
    }
}
