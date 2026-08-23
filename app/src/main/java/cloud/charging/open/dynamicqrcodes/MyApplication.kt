package cloud.charging.open.dynamicqrcodes

import android.app.Application
import android.util.Log

class MyApplication : Application() {

    override fun onCreate() {

        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->

            Log.e("UncaughtException", "Uncaught exception in thread: ${thread.name}", throwable)

            // Hand the exception back to Android: without this the process
            // stays alive in a broken state and the crash never reaches the
            // Play Console.
            defaultHandler?.uncaughtException(thread, throwable)

        }

    }

}
