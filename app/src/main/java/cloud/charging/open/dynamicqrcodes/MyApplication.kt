package cloud.charging.open.dynamicqrcodes;

import android.app.Application
import android.util.Log

class MyApplication : Application() {
    override fun onCreate() {

        super.onCreate()

        // Set the default uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("UncaughtException", "Uncaught exception in thread: ${thread.name}", throwable)
            // Optionally, you can restart the app or log the exception to a server
        }

    }
}
